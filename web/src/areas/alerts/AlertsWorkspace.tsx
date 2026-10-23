import { useContext, useEffect, useRef, useState } from 'react'
import type { PropsWithChildren } from 'react'
import { Link, useParams } from 'react-router'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { useAuth } from '../../shared/auth/context'
import { ApiProblem } from '../../shared/api/client'
import { Card, ConfirmDialog, DataTable, DetailList, EmptyState, ErrorBanner, LoadingState } from '../../shared/ui/components'
import { useAdminTenant } from '../tenant-admin/tenant'
import { OperationsTenant } from '../transport-operations/queries'
import { AdminPageHeader, AdminPanel, AdminStatus } from '../tenant-admin/AdminPresentation'
import { OperationsHeader, OperationsStatus } from '../transport-operations/OperationsPresentation'
import { displayTime } from '../../shared/ui/dateTime'

const alertSchema = z.object({
  id: z.uuid(), tenantId: z.uuid(), alertType: z.enum(['TRAILER_MISSING', 'STALE_TRAILER']), severity: z.literal('WARNING'),
  status: z.enum(['OPEN', 'ACKNOWLEDGED', 'RESOLVED']), version: z.number().int().nonnegative(), createdAt: z.iso.datetime({ offset: true }),
  acknowledgedBy: z.uuid().nullable(), acknowledgedAt: z.iso.datetime({ offset: true }).nullable(), resolvedBy: z.uuid().nullable(), resolvedAt: z.iso.datetime({ offset: true }).nullable(), resolutionReason: z.string().nullable(),
  relatedEntityType: z.literal('TRAILER'),
  relatedResource: z.object({ id: z.uuid(), operationalNumber: z.string(), designatedCustomerId: z.uuid().nullable(), locationState: z.string(), currentLocationId: z.uuid().nullable() }).strict(),
  context: z.record(z.string(), z.string()), pushSummary: z.record(z.string(), z.number().int().nonnegative()),
  originActorType: z.enum(['HUMAN_USER','SYSTEM']).optional(), resolutionActorType: z.enum(['HUMAN_USER','SYSTEM']).nullable().optional(),
  detection: z.object({ detectedAt:z.iso.datetime({offset:true}),lastSeenAt:z.iso.datetime({offset:true}),thresholdHours:z.number().int().positive(),elapsedHours:z.number().nonnegative(),jobId:z.uuid() }).strict().nullable().optional(),
  resolutionEvidence: z.object({ lastSeenAt:z.iso.datetime({offset:true}),thresholdHours:z.number().int().positive(),jobId:z.uuid() }).strict().nullable().optional(),
}).strict().refine(a => (a.acknowledgedAt === null) === (a.acknowledgedBy === null) &&
  (a.status === 'OPEN' ? a.acknowledgedAt === null && a.resolvedAt === null && a.resolvedBy === null && a.resolutionReason === null :
    a.status === 'ACKNOWLEDGED' ? a.acknowledgedAt !== null && a.resolvedAt === null && a.resolvedBy === null && a.resolutionReason === null :
      a.resolvedAt !== null && (a.resolvedBy !== null || a.alertType==='STALE_TRAILER'&&a.resolutionActorType==='SYSTEM'&&a.resolutionEvidence!=null) && !!a.resolutionReason?.trim()), 'Invalid Alert lifecycle receipt')
  .refine(a=>a.alertType!=='STALE_TRAILER'||a.originActorType==='SYSTEM'&&a.detection!=null&&a.detection.elapsedHours>=a.detection.thresholdHours,'Invalid stale detection evidence')
export type Alert = z.infer<typeof alertSchema>
const pageSchema = z.object({ items: z.array(alertSchema).max(100), nextCursor: z.string().max(512).nullable() }).strict()
type Context = { tenantId: string; capabilities?: string[] }
function AlertRegion({ area, className, operationsClassName, legend, children }: PropsWithChildren<{ area: 'admin' | 'operations'; className: string; operationsClassName: string; legend?: string }>) {
  return area === 'admin' ? <div className={className}>{children}</div> : legend ? <fieldset className={operationsClassName}><legend>{legend}</legend>{children}</fieldset> : <div className={operationsClassName}>{children}</div>
}
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
    if (detail.isPending) return area === 'admin' ? <div className="admin-page"><AdminPageHeader title="Loading Alert" description="Reading the persisted condition." /><LoadingState /></div> : <div className="ops-page"><OperationsHeader title="Loading Alert" /><LoadingState /></div>
    if (detail.isError) {
      const error = <ErrorBanner error={detail.error} retry={() => void detail.refetch()} />
      return area === 'admin' ? <div className="admin-page"><AdminPageHeader title="Alert unavailable"><Link to="/admin/alerts">Back to Alerts</Link></AdminPageHeader>{error}</div> : <div className="ops-page"><OperationsHeader title="Alert unavailable"><Link to="/operations/alerts">Back to Alerts</Link></OperationsHeader>{error}</div>
    }
    const a = detail.data, recovered = a.alertType==='STALE_TRAILER'||a.relatedResource.locationState === 'AT_LOCATION' && a.relatedResource.currentLocationId !== null
    const Heading = area === 'admin' ? AdminPageHeader : OperationsHeader
    return <section className={area === 'admin' ? 'admin-alerts admin-page' : 'ops-page ops-alerts'}><Heading title="Alert detail" description="The persisted Alert is authoritative. Push acceptance does not confirm phone delivery."><button className={area === 'operations' ? 'ui-button ui-button--secondary' : undefined} onClick={reload}>Refresh Alert</button><Link to={`/${area}/alerts`}>Back to Alerts</Link></Heading>
      {area === 'admin' ? <AdminStatus value={a.status} /> : <OperationsStatus value={a.status} label={a.status} />}
      <AlertRegion area={area} className="admin-audit-metadata" operationsClassName="ops-alert-metadata"><DetailList items={[
        ['Type', a.alertType], ['Severity', a.severity], ['Created', displayTime(a.createdAt)], ['Version', String(a.version)],
        ['Trailer', <Link to={`/${area}/trailers/${a.relatedResource.id}`}>{a.relatedResource.operationalNumber}</Link>], ['Recorded location state', a.relatedResource.locationState],
        ['Acknowledged', a.acknowledgedAt ? displayTime(a.acknowledgedAt) : null], ['Acknowledged by', a.acknowledgedBy], ['Resolved', a.resolvedAt ? displayTime(a.resolvedAt) : null], ['Resolved by', a.resolvedBy], ['Resolution reason', a.resolutionReason],
        ['Origin',a.originActorType??'HUMAN_USER'], ['Resolution actor',a.resolutionActorType??null],
        ...(a.detection ? [
          ['Last physical observation',displayTime(a.detection.lastSeenAt)], ['Threshold at detection',String(a.detection.thresholdHours)+' hours'],
          ['Elapsed at detection',a.detection.elapsedHours.toFixed(1)+' hours'], ['Detected',displayTime(a.detection.detectedAt)],
        ] as [string,string][] : []),
      ]} /></AlertRegion>
      <AlertRegion area={area} className="admin-panel" operationsClassName="ops-alert-action-panel">
      <p>Acknowledgement means attention, not physical recovery. Resolving an Alert never changes Trailer location.</p>
      {a.alertType==='STALE_TRAILER'&&<p>Physical freshness uses observation time. Administrative edits and old offline receipt time do not count as new activity. Automatic resolution may also reflect a changed Tenant threshold.</p>}
      {Object.entries(a.pushSummary).map(([status, count]) => <p key={status}>Push transport: {status} · {count} recipient(s)</p>)}
      {a.status === 'OPEN' && owner.capabilities?.includes('ALERT_ACKNOWLEDGE') && <button className={area === 'operations' ? 'ui-button ui-button--primary' : undefined} onClick={() => { command.reset(); setAction({ kind: 'acknowledgement', alert: a }) }}>Acknowledge</button>}
      {a.status !== 'RESOLVED' && owner.capabilities?.includes('ALERT_RESOLVE') && <><button className={area === 'operations' ? 'ui-button ui-button--secondary' : undefined} disabled={!recovered} onClick={() => { command.reset(); setAction({ kind: 'resolution', alert: a }); setReason('') }}>Resolve</button>{!recovered && <p>Record the Trailer's physical recovery through an authorized correction before resolving.</p>}</>}
      </AlertRegion>
      {action && <ConfirmDialog className={area === 'admin' ? 'admin-dialog' : 'ops-dialog'} title={action.kind === 'resolution' ? 'Resolve Alert' : 'Acknowledge Alert'} confirmLabel={action.kind === 'resolution' ? 'Confirm resolution' : 'Confirm acknowledgement'} onConfirm={() => void submit()} onCancel={() => { setAction(null); command.reset() }} busy={command.isPending} confirmDisabled={action.kind === 'resolution' && !reason.trim()}>
        <p>{action.alert.relatedResource.operationalNumber} · reviewed version {action.alert.version}</p>
        {action.kind === 'resolution' && <label>Resolution reason<textarea maxLength={4000} value={reason} onChange={e => setReason(e.target.value)} /></label>}
        {command.isError && <ErrorBanner error={command.error} reload={reload} />}
      </ConfirmDialog>}
    </section>
  }
  const items = [...new Map(pages.data?.pages.flatMap(page => page.items).map(a => [a.id, a]) ?? []).values()]
  const Heading = area === 'admin' ? AdminPageHeader : OperationsHeader
  const records = <DataTable caption="Operational Alerts" headings={['Trailer', 'Type', 'Severity', 'Status', 'Created']}>
    {items.map(a => <tr key={a.id}><td><Link to={`/${area}/alerts/${a.id}`}>{a.relatedResource.operationalNumber}</Link></td><td>{a.alertType}</td><td>{area === 'admin' ? <AdminStatus value={a.severity} /> : <OperationsStatus value={a.severity} label={a.severity} />}</td><td>{area === 'admin' ? <AdminStatus value={a.status} /> : <OperationsStatus value={a.status} label={a.status} />}</td><td><time dateTime={a.createdAt}>{displayTime(a.createdAt)}</time></td></tr>)}
  </DataTable>
  return <section className={area === 'admin' ? 'admin-alerts admin-page' : 'ops-page ops-alerts'}><Heading title="Alerts" description="Operational conditions remain available even if push is missed."><button className={area === 'operations' ? 'ui-button ui-button--secondary' : undefined} onClick={() => void pages.refetch()}>Refresh Alerts</button></Heading>
    <form onSubmit={e => { e.preventDefault(); setFilters(Object.fromEntries(Object.entries(draft).filter(([, v]) => v).map(([k, v]) => [k, k === 'from' || k === 'to' ? new Date(v).toISOString() : v]))) }}>
      <AlertRegion area={area} className="admin-alert-filters" operationsClassName="ops-alert-filters" legend="Filter recorded Alerts">
      {Object.entries({ status: ['', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED'], alertType: ['', 'TRAILER_MISSING', 'STALE_TRAILER'], severity: ['', 'WARNING'], relatedEntityType: ['', 'TRAILER'] }).map(([name, choices]) => <label key={name}>{({ status: 'Status', alertType: 'Alert type', severity: 'Severity', relatedEntityType: 'Related resource' })[name]}<select aria-label={({ status: 'Status', alertType: 'Alert type', severity: 'Severity', relatedEntityType: 'Related resource' })[name]} value={draft[name as keyof typeof draft]} onChange={e => setDraft({ ...draft, [name]: e.target.value })}>{choices.map(value => <option key={value} value={value}>{value || 'All'}</option>)}</select></label>)}
      <label>From<input type="datetime-local" value={draft.from} onChange={e => setDraft({ ...draft, from: e.target.value })} /></label><label>To<input type="datetime-local" value={draft.to} onChange={e => setDraft({ ...draft, to: e.target.value })} /></label><AlertRegion area={area} className="actions" operationsClassName="ops-alert-actions"><button className={area === 'operations' ? 'ui-button ui-button--primary' : undefined} type={area === 'admin' ? 'submit' : undefined}>Apply filters</button></AlertRegion>
      </AlertRegion>
    </form>
    {pages.isPending ? <LoadingState /> : pages.isError ? <ErrorBanner error={pages.error} retry={() => void pages.refetch()} /> : !items.length ? <EmptyState>No Alerts match these filters.</EmptyState> : area === 'admin' ? <AdminPanel title="Recorded Alerts" description="Persisted conditions and their current lifecycle status." className="admin-table-panel">{records}</AdminPanel> : <Card title="Recorded Alerts" description="Persisted operational conditions; acknowledgement is not physical recovery." className="ops-table-card">{records}</Card>}
    {pages.hasNextPage && <button className={area === 'admin' ? 'admin-pagination' : 'ops-pagination ui-button ui-button--secondary'} disabled={pages.isFetchingNextPage} onClick={() => void pages.fetchNextPage()}>Load more Alerts</button>}
  </section>
}
