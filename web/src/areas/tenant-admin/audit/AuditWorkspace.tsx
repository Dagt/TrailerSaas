import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../../shared/auth/context'
import { ApiProblem } from '../../../shared/api/client'
import { DataTable, DetailList, EmptyState, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../../../shared/ui/components'
import { useAdminIdentity, useAdminTenant } from '../tenant'
import { auditApi, auditRoot, firstPosition, type AuditOwner, type AuditPosition } from './api'
import { auditActionCode, auditEntityType, parseFilters, uuid, type AuditEvent } from './contracts'
import { auditDiff, canonical, label, localTime } from './diff'

export function AuditWorkspace() {
  const tenant = useAdminTenant(), userId = useAdminIdentity(), { identity } = useAuth(), { auditEventId } = useParams()
  if (!tenant.capabilities?.includes('AUDIT_VIEW')) return <EmptyState><h1>Audit access is not available</h1><p>Your current membership does not grant Audit viewing.</p></EmptyState>
  const owner: AuditOwner = { firebaseUid: identity!.uid, userId, tenantId: tenant.tenantId }
  return <AuditContent key={`${owner.firebaseUid}:${userId}:${owner.tenantId}:${auditEventId ?? 'list'}`} owner={owner} id={auditEventId} />
}
function useRevalidateAccess(error: unknown) {
  const cache = useQueryClient(), { identity } = useAuth()
  useEffect(() => {
    if (error instanceof ApiProblem && error.problem.status === 403) void cache.invalidateQueries({ queryKey: ['me', identity?.uid] })
  }, [cache, error, identity?.uid])
}
function AuditContent({ owner, id }: { owner: AuditOwner; id?: string }) {
  return id ? uuid.safeParse(id).success ? <AuditDetail owner={owner} id={id} /> : <EmptyState><h1>Audit event unavailable</h1><p>The event reference is invalid.</p><Link to="/admin/audit">Back to Audit</Link></EmptyState> : <AuditList owner={owner} />
}
const fields = { actorUserId: 'Actor User ID', actionCode: 'Action code', entityType: 'Entity type', entityId: 'Entity ID', from: 'From (inclusive, RFC3339)', to: 'To (inclusive, RFC3339)' } as const
function AuditList({ owner }: { owner: AuditOwner }) {
  const { api } = useAuth(), [search, setSearch] = useSearchParams()
  const signature = search.toString()
  return <AuditListContent key={signature} owner={owner} api={auditApi(api, owner)} search={search} apply={setSearch} />
}
function AuditListContent({ owner, api, search, apply }: { owner: AuditOwner; api: ReturnType<typeof auditApi>; search: URLSearchParams; apply(value: URLSearchParams): void }) {
  const cache = useQueryClient()
  let filters: ReturnType<typeof parseFilters> = {}, invalid = false
  try { filters = parseFilters(search) } catch { invalid = true }
  const [draft, setDraft] = useState<Record<keyof typeof fields, string>>(() => Object.fromEntries(Object.keys(fields).map(key => [key, search.get(key) ?? ''])) as Record<keyof typeof fields, string>)
  const [formError, setFormError] = useState(false)
  const pages = useInfiniteQuery({ queryKey: [...auditRoot(owner), 'list', filters], enabled: !invalid, gcTime: 0, initialPageParam: firstPosition,
    queryFn: ({ signal, pageParam }) => api.list(filters, pageParam, signal),
    getNextPageParam: (last, all): AuditPosition | undefined => last.nextCursor ? { cursor: last.nextCursor, ids: all.flatMap(p => p.items.map(e => e.id)), cursors: all.flatMap(p => p.nextCursor ? [p.nextCursor] : []) } : undefined,
  })
  useRevalidateAccess(pages.error)
  const items = pages.data?.pages.flatMap(p => p.items) ?? []
  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    const params = new URLSearchParams(Object.entries(draft).filter(([, v]) => v !== ''))
    try { parseFilters(params); setFormError(false); apply(params) } catch { setFormError(true) }
  }
  return <section className="audit-workspace"><PageHeader title="Audit" description="Administrative changes, attributed to their recorded actor. Operational movements keep their separate histories.">
    <button type="button" disabled={invalid || pages.isFetching} onClick={() => void pages.refetch()}>Refresh Audit</button>
  </PageHeader>
    <form onSubmit={submit} noValidate aria-label="Audit filters"><fieldset className="audit-filters"><legend>Filter administrative history</legend>
      {Object.entries(fields).map(([key, title]) => <label key={key}>{title}{key === 'actionCode' || key === 'entityType' ? <select value={draft[key]} onChange={e => setDraft({ ...draft, [key]: e.target.value })}>
        <option value="">All</option>{(key === 'actionCode' ? auditActionCode.options : auditEntityType.options).map(v => <option key={v} value={v}>{label(v)}</option>)}
      </select> : <input value={draft[key as keyof typeof fields]} onChange={e => setDraft({ ...draft, [key]: e.target.value })} placeholder={key === 'from' || key === 'to' ? '2026-10-16T09:00:00-05:00' : 'Canonical UUID'} autoComplete="off" />}</label>)}
      <p className="muted">Enter exact timestamps with a timezone offset or Z. Both date boundaries are inclusive; displayed dates use your local timezone.</p>
      <div className="actions"><button type="submit">Apply filters</button><button type="button" onClick={() => apply(new URLSearchParams())}>Clear filters</button></div>
    </fieldset></form>
    {(invalid || formError) && <p role="alert">Check the UUIDs, action, entity and RFC3339 dates. From must not be later than To. Unknown or duplicate URL filters are not accepted.</p>}
    {!invalid && (pages.isPending ? <LoadingState /> : pages.isError ? <><ErrorBanner error={pages.error} retry={() => void (pages.isFetchNextPageError ? pages.fetchNextPage() : pages.refetch())} />{pages.error instanceof ApiProblem && pages.error.problem.status === 400 && <p>The history cursor or filters are no longer valid. Restart the query or review the filters.</p>}<button type="button" onClick={() => { void cache.resetQueries({ queryKey: [...auditRoot(owner), 'list', filters], exact: true }) }}>Restart history query</button></> : !items.length ? <EmptyState>No administrative changes match these filters.</EmptyState> : <>
      <p className="muted">Newest first. This is a bounded history page, not a total count.</p>
      <DataTable caption="Administrative Audit events" headings={['Timestamp', 'Actor', 'Actor type', 'Action', 'Entity', 'Source', 'Summary', 'Detail']}>
        {items.map(e => <tr key={e.id}><td><AuditTime at={e.occurredAt} /></td><td className="audit-reference">{actor(e)}</td><td>{label(e.actor.type)}</td><td>{label(e.actionCode)}</td><td>{label(e.entityType)}<small className="audit-reference">{e.entityId}</small></td><td>{label(e.sourceType)}</td><td>{summary(e)}</td><td><Link to={`/admin/audit/${e.id}${search.size ? `?${search}` : ''}`} aria-label={`View Audit event ${e.id}`}>View detail</Link></td></tr>)}
      </DataTable>
      {pages.hasNextPage && <button type="button" disabled={pages.isFetchingNextPage} onClick={() => void pages.fetchNextPage()}>{pages.isFetchingNextPage ? 'Loading history…' : 'Load more Audit events'}</button>}
    </>)}
  </section>
}
function actor(e: AuditEvent) { return e.actor.type === 'HUMAN_USER' ? `User ${e.actor.userId}` : label(e.actor.type) }
function summary(e: AuditEvent) {
  const changes = auditDiff(e).filter(r => r.state !== 'Unchanged')
  return `${e.beforeData === null ? 'Created · ' : ''}${changes.slice(0, 3).map(r => `${label(r.field)}${r.state === 'Withheld' ? ' (value withheld)' : ''}`).join(', ')}${changes.length > 3 ? '…' : ''}`
}
function AuditTime({ at }: { at: string }) { return <time dateTime={at} title={at}>{localTime(at)}</time> }
function AuditDetail({ owner, id }: { owner: AuditOwner; id: string }) {
  const { api } = useAuth(), [search] = useSearchParams()
  const query = useQuery({ queryKey: [...auditRoot(owner), 'detail', id], gcTime: 0, queryFn: ({ signal }) => auditApi(api, owner).detail(id, signal) })
  useRevalidateAccess(query.error)
  const back = `/admin/audit${search.size ? `?${search}` : ''}`
  if (query.isPending) return <LoadingState />
  if (query.isError) return <><Link to={back}>Back to Audit</Link><ErrorBanner error={query.error} retry={() => void query.refetch()} /></>
  const e = query.data
  return <section className="audit-workspace"><PageHeader title="Audit event detail" description="Read-only administrative evidence. Snapshots cannot be restored or replayed."><Link to={back}>Back to Audit</Link><Link to={`/admin/audit?${new URLSearchParams({ entityType: e.entityType, entityId: e.entityId })}`}>View entity history</Link><button disabled={query.isFetching} onClick={() => void query.refetch()}>Refresh event</button></PageHeader>
    <DetailList items={[
      ['Event ID', e.id], ['Actor', actor(e)], ['Actor type', label(e.actor.type)], ['Historical membership ID', e.actor.membershipId ?? 'Not applicable'],
      ['Source', label(e.sourceType)], ['Action', label(e.actionCode)], ['Entity type', label(e.entityType)], ['Entity ID', e.entityId],
      ['Occurred', <AuditTime at={e.occurredAt} />], ['Exact timestamp', e.occurredAt], ['Reason', e.reason ?? 'No reason recorded'],
    ]} />
    <p className="muted">User and membership references identify the recorded actor even after access is disabled. No current profile is substituted for a historical identity.</p>
    <h2>Before / after comparison</h2>
    {e.beforeData === null && <p>This creation has no previous snapshot.</p>}
    <DataTable caption="Recorded field comparison" headings={['Field', 'Before', 'After', 'Change']}>
      {auditDiff(e).map(row => <tr key={row.field}><th scope="row">{label(row.field)}</th>{row.state === 'Withheld' ? <td colSpan={2}>Sensitive field changed — value withheld</td> : <><td className="audit-value">{typeof row.before === 'string' ? <q>{row.before}</q> : canonical(row.before)}</td><td className="audit-value">{typeof row.after === 'string' ? <q>{row.after}</q> : canonical(row.after)}</td></>}<td><StatusBadge>{row.state}</StatusBadge></td></tr>)}
    </DataTable>
    <p className="muted">Not recorded means the field is absent; null is an explicitly recorded value. Arrays retain their recorded order. Omitted sensitive values cannot be reconstructed.</p>
  </section>
}
