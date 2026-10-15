import { useContext, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { useAuth } from '../../shared/auth/context'
import { ApiProblem } from '../../shared/api/client'
import { ConfirmDialog, DataTable, DetailList, EmptyState, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../../shared/ui/components'
import { useAdminTenant } from '../tenant-admin/tenant'
import { OperationsTenant } from '../transport-operations/queries'

const alertSchema = z.object({
  id: z.uuid(), tenantId: z.uuid(), alertType: z.literal('TRAILER_MISSING'), severity: z.literal('WARNING'),
  status: z.enum(['OPEN', 'ACKNOWLEDGED', 'RESOLVED']), version: z.number().int().nonnegative(), createdAt: z.iso.datetime({ offset: true }),
  acknowledgedBy: z.uuid().nullable(), acknowledgedAt: z.iso.datetime({ offset: true }).nullable(), resolvedBy: z.uuid().nullable(), resolvedAt: z.iso.datetime({ offset: true }).nullable(), resolutionReason: z.string().nullable(),
  relatedEntityType: z.literal('TRAILER'),
  relatedResource: z.object({ id: z.uuid(), operationalNumber: z.string(), designatedCustomerId: z.uuid().nullable(), locationState: z.string(), currentLocationId: z.uuid().nullable() }).strict(),
  context: z.record(z.string(), z.string()), pushSummary: z.record(z.string(), z.number().int().nonnegative()),
}).strict().refine(a => (a.acknowledgedAt === null) === (a.acknowledgedBy === null) &&
  (a.status === 'OPEN' ? a.acknowledgedAt === null && a.resolvedAt === null && a.resolvedBy === null && a.resolutionReason === null :
    a.status === 'ACKNOWLEDGED' ? a.acknowledgedAt !== null && a.resolvedAt === null && a.resolvedBy === null && a.resolutionReason === null :
      a.resolvedAt !== null && a.resolvedBy !== null && !!a.resolutionReason?.trim()), 'Invalid Alert lifecycle receipt')
export type Alert = z.infer<typeof alertSchema>
const pageSchema = z.object({ items: z.array(alertSchema).max(100), nextCursor: z.string().max(512).nullable() }).strict()
type Context = { tenantId: string; capabilities?: string[] }
export function AdminAlerts() { return <AlertsWorkspace owner={useAdminTenant()} area="admin" /> }
export function OperationsAlerts() {
  const owner = useContext(OperationsTenant)
  if (!owner) throw new Error('An active Operations Tenant is required.')
  return <AlertsWorkspace owner={owner} area="operations" />
}
export function AlertsWorkspace({ owner, area }: { owner: Context; area: 'admin' | 'operations' }) {
  const { identity } = useAuth(), { alertId } = useParams()
  return <AlertsWorkspaceContent key={`${owner.tenantId}:${identity?.uid}:${alertId ?? ''}`} owner={owner} area={area} />
}
function AlertsWorkspaceContent({ owner, area }: { owner: Context; area: 'admin' | 'operations' }) {
  const { api, identity } = useAuth(), cache = useQueryClient(), { alertId } = useParams()
  const [draft, setDraft] = useState({ status: 'OPEN', alertType: '', severity: '', relatedEntityType: '', from: '', to: '' }), [filters, setFilters] = useState<Record<string, string>>({ status: 'OPEN' })
  const [action, setAction] = useState<{ kind: 'acknowledgement' | 'resolution'; alert: Alert } | null>(null), [reason, setReason] = useState('')
  const active = useRef(true), sending = useRef(false), abort = useRef<AbortController | null>(null)
  useEffect(() => { active.current = true; return () => { active.current = false; abort.current?.abort() } }, [])
  const key = [area === 'admin' ? 'tenant' : 'operations', owner.tenantId, 'alerts', identity?.uid], root = `/tenants/${owner.tenantId}/alerts`
  const allowed = owner.capabilities?.includes('ALERT_VIEW') ?? false
  const pages = useInfiniteQuery({ queryKey: [...key, 'list', filters], enabled: allowed && !alertId, initialPageParam: null as string | null,
    queryFn: async ({ signal, pageParam }) => {
      const q = new URLSearchParams({ ...filters, limit: '25', ...(pageParam ? { cursor: pageParam } : {}) })
      const page = pageSchema.parse(await api.request<unknown>(`${root}?${q}`, { signal }))
      if (page.items.some(a => a.tenantId !== owner.tenantId) || page.nextCursor === pageParam && pageParam !== null) throw new ApiProblem({ status: 502, code: 'UNEXPECTED_RESPONSE' })
      return page
    }, getNextPageParam: last => last.nextCursor ?? undefined,
  })
  const detail = useQuery({ queryKey: [...key, 'detail', alertId], enabled: allowed && !!alertId, queryFn: async ({ signal }) => {
    const a = alertSchema.parse(await api.request<unknown>(`${root}/${alertId}`, { signal }))
    if (a.id !== alertId || a.tenantId !== owner.tenantId) throw new ApiProblem({ status: 502, code: 'UNEXPECTED_RESPONSE' })
    return a
  } })
  const command = useMutation({ retry: false, mutationFn: async (intent: { kind: 'acknowledgement' | 'resolution'; alert: Alert; reason: string }) => {
    abort.current = new AbortController()
    const body = { expectedVersion: intent.alert.version, ...(intent.kind === 'resolution' ? { reason: intent.reason.trim() } : {}) }
    const result = alertSchema.parse(await api.request<unknown>(`${root}/${intent.alert.id}/${intent.kind}`, { method: 'POST', body, signal: abort.current.signal }))
    if (result.id !== intent.alert.id || result.tenantId !== owner.tenantId || result.version !== intent.alert.version + 1 || result.status !== (intent.kind === 'resolution' ? 'RESOLVED' : 'ACKNOWLEDGED')) throw new ApiProblem({ status: 502, code: 'UNEXPECTED_RESPONSE' })
    return result
  }, onSuccess: result => {
    if (!active.current) return
    cache.setQueryData([...key, 'detail', alertId], result)
    void cache.invalidateQueries({ queryKey: [...key, 'list'] }); setAction(null); setReason('')
  } })
  const submit = async () => {
    if (!action || sending.current || action.kind === 'resolution' && !reason.trim()) return
    sending.current = true
    try { await command.mutateAsync({ ...action, reason }) } catch { /* ErrorBanner is safe; no mutation retry. */ }
    finally { sending.current = false }
  }
  const reload = () => { setAction(null); setReason(''); command.reset(); void detail.refetch() }
  if (!allowed) return <EmptyState><h1>Alerts access is not available</h1><p>Refresh your session to verify your current capabilities.</p></EmptyState>
  if (alertId) {
    if (detail.isPending) return <LoadingState />
    if (detail.isError) return <ErrorBanner error={detail.error} retry={() => void detail.refetch()} />
    const a = detail.data, recovered = a.relatedResource.locationState === 'AT_LOCATION' && a.relatedResource.currentLocationId !== null
    return <section><PageHeader title="Alert detail" description="The persisted Alert is authoritative. Push acceptance does not confirm phone delivery."><button onClick={reload}>Refresh Alert</button><Link to={`/${area}/alerts`}>Back to Alerts</Link></PageHeader>
      <StatusBadge>{a.status}</StatusBadge>
      <DetailList items={[
        ['Type', a.alertType], ['Severity', a.severity], ['Created', a.createdAt], ['Version', String(a.version)],
        ['Trailer', <Link to={`/${area}/trailers/${a.relatedResource.id}`}>{a.relatedResource.operationalNumber}</Link>], ['Recorded location state', a.relatedResource.locationState],
        ['Acknowledged', a.acknowledgedAt], ['Acknowledged by', a.acknowledgedBy], ['Resolved', a.resolvedAt], ['Resolved by', a.resolvedBy], ['Resolution reason', a.resolutionReason],
      ]} />
      <p>Acknowledgement means attention, not physical recovery. Resolving an Alert never changes Trailer location.</p>
      {Object.entries(a.pushSummary).map(([status, count]) => <p key={status}>Push transport: {status} · {count} recipient(s)</p>)}
      {a.status === 'OPEN' && owner.capabilities?.includes('ALERT_ACKNOWLEDGE') && <button onClick={() => { command.reset(); setAction({ kind: 'acknowledgement', alert: a }) }}>Acknowledge</button>}
      {a.status !== 'RESOLVED' && owner.capabilities?.includes('ALERT_RESOLVE') && <><button disabled={!recovered} onClick={() => { command.reset(); setAction({ kind: 'resolution', alert: a }); setReason('') }}>Resolve</button>{!recovered && <p>Record the Trailer's physical recovery through an authorized correction before resolving.</p>}</>}
      {action && <ConfirmDialog title={action.kind === 'resolution' ? 'Resolve Alert' : 'Acknowledge Alert'} confirmLabel={action.kind === 'resolution' ? 'Confirm resolution' : 'Confirm acknowledgement'} onConfirm={() => void submit()} onCancel={() => { setAction(null); command.reset() }} busy={command.isPending} confirmDisabled={action.kind === 'resolution' && !reason.trim()}>
        <p>{action.alert.relatedResource.operationalNumber} · reviewed version {action.alert.version}</p>
        {action.kind === 'resolution' && <label>Resolution reason<textarea maxLength={4000} value={reason} onChange={e => setReason(e.target.value)} /></label>}
        {command.isError && <ErrorBanner error={command.error} reload={reload} />}
      </ConfirmDialog>}
    </section>
  }
  const items = [...new Map(pages.data?.pages.flatMap(page => page.items).map(a => [a.id, a]) ?? []).values()]
  return <section><PageHeader title="Alerts" description="Operational conditions remain available even if push is missed."><button onClick={() => void pages.refetch()}>Refresh Alerts</button></PageHeader>
    <form onSubmit={e => { e.preventDefault(); setFilters(Object.fromEntries(Object.entries(draft).filter(([, v]) => v).map(([k, v]) => [k, k === 'from' || k === 'to' ? new Date(v).toISOString() : v]))) }}>
      {Object.entries({ status: ['', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED'], alertType: ['', 'TRAILER_MISSING'], severity: ['', 'WARNING'], relatedEntityType: ['', 'TRAILER'] }).map(([name, choices]) => <label key={name}>{({ status: 'Status', alertType: 'Alert type', severity: 'Severity', relatedEntityType: 'Related resource' })[name]}<select aria-label={({ status: 'Status', alertType: 'Alert type', severity: 'Severity', relatedEntityType: 'Related resource' })[name]} value={draft[name as keyof typeof draft]} onChange={e => setDraft({ ...draft, [name]: e.target.value })}>{choices.map(value => <option key={value} value={value}>{value || 'All'}</option>)}</select></label>)}
      <label>From<input type="datetime-local" value={draft.from} onChange={e => setDraft({ ...draft, from: e.target.value })} /></label><label>To<input type="datetime-local" value={draft.to} onChange={e => setDraft({ ...draft, to: e.target.value })} /></label><button>Apply filters</button>
    </form>
    {pages.isPending ? <LoadingState /> : pages.isError ? <ErrorBanner error={pages.error} retry={() => void pages.refetch()} /> : !items.length ? <EmptyState>No Alerts match these filters.</EmptyState> : <DataTable caption="Operational Alerts" headings={['Trailer', 'Type', 'Severity', 'Status', 'Created']}>
      {items.map(a => <tr key={a.id}><td><Link to={`/${area}/alerts/${a.id}`}>{a.relatedResource.operationalNumber}</Link></td><td>{a.alertType}</td><td>{a.severity}</td><td>{a.status}</td><td>{a.createdAt}</td></tr>)}
    </DataTable>}
    {pages.hasNextPage && <button disabled={pages.isFetchingNextPage} onClick={() => void pages.fetchNextPage()}>Load more Alerts</button>}
  </section>
}
