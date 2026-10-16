import { MasterDataStatus } from './MasterDataStatus'
import { useState } from 'react'
import { useParams } from 'react-router'
import { useInfiniteQuery, useQueries, useQuery } from '@tanstack/react-query'
import { useAuth } from '../../shared/auth/context'
import { historyIdentity, type HistoryPosition } from './historyContracts'
import { DataTable, DetailList, EmptyState, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../../shared/ui/components'
import { key, useLocations, useNames, useOperations } from './queries'
import { human, reference, when } from './display'
import type { AssignmentHistory, OperationalHistory, TripDetail, TripStop } from './contracts'
import { actions } from './planning'
import { TripAction, type ActionKind } from './TripAction'
export function TripDetailPage() {
  const { tripId = '' } = useParams(), { api, tenantId } = useOperations(), names = useNames()
  const { identity } = useAuth()
  const query = useQuery({ queryKey: key(tenantId, 'trip', tripId), queryFn: ({ signal }) => api.tripWithoutHistory(tripId, signal) })
  const timeline = useInfiniteQuery({ queryKey: key(tenantId, 'timeline', tripId, identity?.uid), enabled: !!query.data,
    initialPageParam: { seen: [], ids: [] } as HistoryPosition,
    queryFn: ({ signal, pageParam }) => api.timelinePage(tripId, pageParam, signal),
    getNextPageParam: (last, all): HistoryPosition | undefined => last.nextCursor ? { cursor: last.nextCursor, seen: all.flatMap(p => p.nextCursor ? [p.nextCursor] : []), ids: all.flatMap(p => p.items.map(historyIdentity)) } : undefined,
  })
  const ids = [...new Set(query.data?.stops.flatMap(s => s.orderIds) ?? [])]
  const orders = useQueries({ queries: ids.map(id => ({ queryKey: key(tenantId, 'order', id), queryFn: ({ signal }: { signal: AbortSignal }) => api.order(id, signal) })) })
  const orderName = (id: string) => orders.find(q => q.data?.id === id)?.data?.orderNumber ?? 'Order unavailable'
  const [action, choose] = useState<{ kind: ActionKind; snapshot: TripDetail; stop?: TripStop } | null>(null)
  if (query.isPending) return <LoadingState />
  if (query.isError) return <ErrorBanner error={query.error} retry={() => void query.refetch()} />
  const t = query.data, allowed = actions(t)
  const open = (kind: ActionKind, stop?: TripStop) => choose({ kind, snapshot: t, stop })
  const reload = () => { choose(null); void query.refetch(); void timeline.refetch() }
  return <><PageHeader title={reference(t.id)} description="Current operational planning. Expected Trailer is not physical coupling."><StatusBadge>{human(t.status)}</StatusBadge></PageHeader>
    <MasterDataStatus queries={[names.customers, names.drivers, names.tractors, names.trailers, names.sites]} /><DetailList items={[["Version", t.version], ["Created", when(t.createdAt)], ["Updated", when(t.updatedAt)]]} />
    <section className="dispatch-card"><h2>Current assignments</h2><DetailList items={[["Driver", names.driver(t.currentDriverId)], ["Tractor", names.tractor(t.currentTractorId)], ["Expected Trailer", names.trailer(t.plannedInitialTrailerId)]]} />
      <div className="operations-actions">{allowed.driver && <button onClick={() => open('driver')}>Assign / reassign Driver</button>}{allowed.tractor && <button onClick={() => open('tractor')}>Assign / reassign Tractor</button>}{allowed.trailer && <button onClick={() => open('trailer')}>Plan / change expected Trailer</button>}{allowed.substitution && <button onClick={() => open('substitution')}>Resolve alternate Trailer</button>}</div>
    </section>
    <section><h2>Executable Stops</h2><DataTable caption="Executable Stops" headings={['Sequence / type', 'Status', 'Destination', 'Appointment', 'Orders / instructions', 'Action']}>
      {[...t.stops].sort((a, b) => a.sequenceNumber - b.sequenceNumber).map(s => <tr key={s.id}><th scope="row">{s.sequenceNumber}. {human(s.stopType)}</th><td><StatusBadge>{human(s.status)}</StatusBadge></td><td><StopDestination stop={s} /></td><td>{when(s.scheduledAt)}</td><td>{s.orderIds.map(orderName).join(', ') || 'Operational visit'}<small>{s.instructions ?? 'No instructions'}</small></td><td>{allowed.reroute && ['PENDING', 'EN_ROUTE'].includes(s.status) && <button onClick={() => open('reroute', s)}>Reroute Stop {s.sequenceNumber}</button>}</td></tr>)}
    </DataTable></section>
    <section className="dispatch-card"><h2>Linked Orders</h2>{!ids.length ? <EmptyState>No Orders are currently mapped to these Stops.</EmptyState> : <ul>{ids.map(id => <li key={id}>{orderName(id)}{orders.find(q => q.data?.id === id)?.data && <> · {names.customer(orders.find(q => q.data?.id === id)!.data!.customerId)}</>}</li>)}</ul>}{orders.some(q => q.isError) && <p>Some linked Order details are unavailable.</p>}</section>
    <section className="dispatch-card"><h2>Operational timeline</h2><p className="muted">Oldest first. Assignments and operational instructions share one paginated history. Planning assignments do not prove physical coupling. Administrative Audit is separate.</p>
      {timeline.isPending ? <LoadingState /> : timeline.isError ? <ErrorBanner error={timeline.error} retry={() => void (timeline.isFetchNextPageError ? timeline.fetchNextPage() : timeline.refetch())} /> : !timeline.data.pages.some(p => p.items.length) ? <EmptyState>No operational events recorded.</EmptyState> : <ol className="operations-timeline" aria-label="Trip operational history">{timeline.data.pages.flatMap(p => p.items).map(e => <HistoryEntry key={historyIdentity(e)} event={e} />)}</ol>}
      {timeline.hasNextPage && !timeline.isError && <button disabled={timeline.isFetchingNextPage} onClick={() => void timeline.fetchNextPage()}>{timeline.isFetchingNextPage ? 'Loading history…' : 'Load more Trip events'}</button>}
      <h3>Recorded Stop execution</h3><p className="muted">These physical timestamps come from the current authorized Stop projection; no missing events are reconstructed.</p>
      <ul>{t.stops.filter(s => s.arrivedAt || s.serviceStartedAt || s.completedAt || s.departedAt).map(s => <li key={s.id}>Stop {s.sequenceNumber}: {s.arrivedAt && <>Arrived {when(s.arrivedAt)}. </>}{s.serviceStartedAt && <>Service started {when(s.serviceStartedAt)}. </>}{s.completedAt && <>Completed {when(s.completedAt)}. </>}{s.departedAt && <>Departed {when(s.departedAt)}.</>}</li>)}</ul>
    </section>
    <section className="dispatch-card"><h2>Available actions</h2>{allowed.cancellation ? <button className="danger" onClick={() => open('cancellation')}>Cancel Trip</button> : <p>No cancellation action is available in this state.</p>}<p className="muted">All actions are revalidated by the server. Execution and physical Trailer commands are not available here.</p></section>
    {action && <TripAction kind={action.kind} trip={action.snapshot} stop={action.stop} onClose={() => choose(null)} reload={reload} />}
  </>
}
function StopDestination({ stop }: { stop: TripStop }) {
  const names = useNames(), locations = useLocations(stop.siteId)
  return <>{names.site(stop.siteId, stop.siteSnapshot?.name)}{stop.locationId && <small>{locations.data?.items.find(l => l.id === stop.locationId)?.name ?? 'Location unavailable'}</small>}</>
}
function object(value: unknown): Record<string, unknown> { return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {} }
function string(value: unknown): string | null { return typeof value === 'string' ? value : null }
function HistoryEntry({ event }: { event: AssignmentHistory | OperationalHistory }) {
  const names = useNames(), m = event.metadata ?? {}, previous = object(m.previous), replacement = object(m.replacement)
  const destination = (d: Record<string, unknown>) => string(object(d.siteSnapshot).name) ?? names.site(string(d.siteId))
  const label = ({ DRIVER_ASSIGNED: 'Driver assigned', DRIVER_REASSIGNED: 'Driver reassigned', TRACTOR_ASSIGNED: 'Tractor assigned', TRACTOR_REASSIGNED: 'Tractor reassigned', TRAILER_PLANNED: 'Trailer planned', TRAILER_SUBSTITUTION_AUTHORIZED: 'Trailer substitution authorized', TRAILER_SUBSTITUTION_REJECTED: 'Trailer substitution rejected', TRIP_REROUTED: 'Trip rerouted', TRIP_CANCELLED: 'Trip cancelled before execution', TRIP_CANCELLATION_STARTED: 'Trip cancellation started', TRIP_CANCELLATION_FINISHED: 'Trip cancellation finished' } as Record<string, string>)[event.eventType] ?? human(event.eventType)
  return <li><strong>{label}</strong> <time dateTime={event.occurredAt}>{when(event.occurredAt)}</time>
    <small>Recorded actor: {event.actorUserId ? `User ${event.actorUserId}` : 'Not recorded'}</small>
    {'driverId' in event && <p>{event.driverId ? <><span>{names.driver(event.driverId)}</span> · Driver {event.driverId}</> : event.tractorId ? <><span>{names.tractor(event.tractorId)}</span> · Tractor {event.tractorId}</> : <><span>{names.trailer(event.trailerId)}</span> · Recorded Trailer {event.trailerId}</>}</p>}
    {string(m.previousDriverId) && <p>Previous Driver: {names.driver(string(m.previousDriverId))} · {string(m.previousDriverId)}</p>}{string(m.previousTractorId) && <p>Previous Tractor: {names.tractor(string(m.previousTractorId))} · {string(m.previousTractorId)}</p>}{string(m.previousTrailerId ?? m.expectedTrailerId) && <p>Previous expectation: {names.trailer(string(m.previousTrailerId ?? m.expectedTrailerId))} · {string(m.previousTrailerId ?? m.expectedTrailerId)}</p>}
    {event.eventType === 'TRIP_REROUTED' && <p>{destination(previous)} → {destination(replacement)}<br />Appointment: {when(string(replacement.scheduledAt))}<br />{string(replacement.instructions)}</p>}
    {string(m.resolutionType) && <p>Resolution: {human(string(m.resolutionType)!)}{string(m.destinationSiteId) && <> · {names.site(string(m.destinationSiteId))}</>}<br />{string(m.instructions)}</p>}
    {event.reason && <p>Reason: {event.reason}</p>}
  </li>
}
