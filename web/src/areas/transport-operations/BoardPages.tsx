import { MasterDataStatus } from './MasterDataStatus'
import { TrailerQuickSearch } from './TrailerPages'
import { useState } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { DataTable, EmptyState, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../../shared/ui/components'
import { key, useNames, useOperations, useOrderPages, useTripPages } from './queries'
import { orderStatuses, tripStatuses } from './contracts'
import type { TripSummary } from './contracts'
import { human, when, reference } from './display'
export function Dashboard() {
  const { api, tenantId } = useOperations(), names = useNames()
  const [customerId, customer] = useState(''), [driverId, driver] = useState(''), [status, state] = useState('')
  const query = useQuery({ queryKey: key(tenantId, 'dashboard', { customerId, driverId, status }), queryFn: ({ signal }) => api.board({ customerId, driverId, status }, signal) })
  return <><PageHeader title="Dispatch dashboard" description="Current planning, assignments and work needing attention." />
    <TrailerQuickSearch />
    <MasterDataStatus queries={[names.customers, names.drivers, names.tractors, names.trailers, names.sites]} /><div className="operations-filters"><label>Customer <select value={customerId} onChange={e => customer(e.target.value)}><option value="">All Customers</option>{names.customers.data?.items.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
      <label>Driver <select value={driverId} onChange={e => driver(e.target.value)}><option value="">All Drivers</option>{names.drivers.data?.items.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      <label>Trip status <select value={status} onChange={e => state(e.target.value)}><option value="">All active Trips</option>{tripStatuses.filter(s => !['COMPLETED', 'CANCELLED'].includes(s)).map(s => <option key={s}>{s}</option>)}</select></label></div>
    {query.isPending ? <LoadingState /> : query.isError ? <ErrorBanner error={query.error} retry={() => void query.refetch()} /> : <>
      <div className="dispatch-metrics"><article><span>Pending Orders</span><strong>{query.data.pendingOrders.length}</strong><small>Latest unplanned work · up to 20</small></article><article><span>Active Trips</span><strong>{query.data.activeTrips.length}</strong><small>Current projection · up to 20</small></article><article><span>Needs attention</span><strong>{query.data.attentionItems.length}</strong><small>Within the displayed Trips</small></article></div>
      <section><div className="page-header"><h2>Pending Orders</h2><Link to="/operations/orders">View planning queue</Link></div>
        {!query.data.pendingOrders.length ? <EmptyState>No pending Orders match this Customer.</EmptyState> : <DataTable caption="Pending Orders" headings={['Order', 'Customer', 'Status', 'Created', 'Action']}>
          {query.data.pendingOrders.map(o => <tr key={o.id}><th scope="row">{o.orderNumber}</th><td>{names.customer(o.customerId)}</td><td><StatusBadge>{human(o.status)}</StatusBadge></td><td>{when(o.createdAt)}</td><td><Link to={`/operations/trips/new?orderId=${o.id}`}>Plan Trip for {o.orderNumber}</Link></td></tr>)}</DataTable>}
      </section>
      <section><div className="page-header"><h2>Active Trips</h2><Link to="/operations/trips">View all Trips</Link></div>
        {!query.data.activeTrips.length ? <EmptyState>No active Trips match these filters.</EmptyState> : <DataTable caption="Active Trips" headings={['Trip', 'Status', 'Driver', 'Tractor', 'Expected Trailer', 'Next planned Stop', 'Action']}>
          {query.data.activeTrips.map(t => <tr key={t.id}><th scope="row">{reference(t.id)}</th><td><StatusBadge>{human(t.status)}</StatusBadge></td><td>{names.driver(t.currentDriverId)}</td><td>{names.tractor(t.currentTractorId)}</td><td>{names.trailer(t.plannedInitialTrailerId)}</td><td>{t.nextPlannedStop ? <>{t.nextPlannedStop.sequenceNumber}. {names.site(t.nextPlannedStop.siteId, t.nextPlannedStop.snapshotName)}<small>{human(t.nextPlannedStop.stopType)} · {when(t.nextPlannedStop.scheduledAt)}</small></> : 'No future Stop'}</td><td><Link to={`/operations/trips/${t.id}`}>Open {reference(t.id)}</Link></td></tr>)}</DataTable>}
      </section><section><h2>Attention</h2>{!query.data.attentionItems.length ? <EmptyState>No assignment or cancellation attention in the displayed Trips.</EmptyState> : <ul className="attention-list">{query.data.attentionItems.map(a => <li key={a.tripId}><StatusBadge>{human(a.type)}</StatusBadge> <Link to={`/operations/trips/${a.tripId}`}>{reference(a.tripId)}</Link></li>)}</ul>}</section>
      <p className="muted">Generated {when(query.data.generatedAt)}. Driver and Trip status filters apply to Trips and attention.</p>
    </>}
  </>
}
export function OrdersQueue() {
  const names = useNames()
  const [customerId, customer] = useState(''), [status, state] = useState(''), [unplanned, planning] = useState('true'), [selected, select] = useState<string[]>([])
  const query = useOrderPages({ customerId, status, unplanned })
  const rows = query.data?.pages.flatMap(p => p.items) ?? []
  const reset = (change: () => void) => { select([]); change() }
  return <><PageHeader title="Orders planning queue" description="Select Orders for executable Trip planning. Manifest allocation alone is not operational planning." />
    <MasterDataStatus queries={[names.customers, names.drivers, names.tractors, names.trailers, names.sites]} /><div className="operations-filters"><label>Customer <select value={customerId} onChange={e => reset(() => customer(e.target.value))}><option value="">All Customers</option>{names.customers.data?.items.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
      <label>Order status <select value={status} onChange={e => reset(() => state(e.target.value))}><option value="">All states</option>{orderStatuses.map(s => <option key={s}>{s}</option>)}</select></label>
      <label>Planning <select value={unplanned} onChange={e => reset(() => planning(e.target.value))}><option value="true">Unplanned work</option><option value="">All Orders</option><option value="false">With planning or terminal</option></select></label></div>
    {!!selected.length && <Link className="button-link" to={`/operations/trips/new?${new URLSearchParams(selected.map(id => ['orderId', id]))}`}>Plan selected Orders ({selected.length})</Link>}
    {query.isPending ? <LoadingState /> : <>{query.isError && <ErrorBanner error={query.error} retry={() => void query.refetch()} />}
      {!rows.length ? <EmptyState>No Orders match these filters.</EmptyState> : <DataTable caption="Orders planning queue" headings={['Select', 'Order', 'Customer', 'Status', 'PO / BOL', 'Created']}>
        {rows.map(o => <tr key={o.id}><td><input type="checkbox" aria-label={`Select ${o.orderNumber}`} disabled={!['OPEN', 'PLANNED'].includes(o.status)} checked={selected.includes(o.id)} onChange={e => select(e.target.checked ? [...selected, o.id] : selected.filter(id => id !== o.id))} /></td><th scope="row">{o.orderNumber}</th><td>{names.customer(o.customerId)}</td><td><StatusBadge>{human(o.status)}</StatusBadge></td><td>{o.poNumber ?? '—'} / {o.bolReference ?? '—'}</td><td>{when(o.createdAt)}</td></tr>)}</DataTable>}
      {query.hasNextPage && <button disabled={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>Load more Orders</button>}</>}
  </>
}
export function TripsList() {
  const names = useNames()
  const [customerId, customer] = useState(''), [driverId, driver] = useState(''), [status, state] = useState('')
  const query = useTripPages({ customerId, driverId, status })
  const rows = query.data?.pages.flatMap(p => p.items) ?? []
  return <><PageHeader title="Trips" description="Operational plans and current assignments."><Link to="/operations/trips/new">Plan a Trip</Link></PageHeader>
    <MasterDataStatus queries={[names.customers, names.drivers, names.tractors, names.trailers, names.sites]} /><div className="operations-filters"><label>Customer <select value={customerId} onChange={e => customer(e.target.value)}><option value="">All Customers</option>{names.customers.data?.items.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
      <label>Driver <select value={driverId} onChange={e => driver(e.target.value)}><option value="">All Drivers</option>{names.drivers.data?.items.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      <label>Trip status <select value={status} onChange={e => state(e.target.value)}><option value="">All states</option>{tripStatuses.map(s => <option key={s}>{s}</option>)}</select></label></div>
    {query.isPending ? <LoadingState /> : <>{query.isError && <ErrorBanner error={query.error} retry={() => void query.refetch()} />}
      {!rows.length ? <EmptyState>No Trips match these filters.</EmptyState> : <TripTable rows={rows} />}
      {query.hasNextPage && <button disabled={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>Load more Trips</button>}</>}
  </>
}
function TripTable({ rows }: { rows: TripSummary[] }) {
  const names = useNames()
  return <DataTable caption="Trips" headings={['Trip', 'Status', 'Driver', 'Tractor', 'Expected Trailer', 'Created', 'Updated', 'Action']}>
    {rows.map(t => <tr key={t.id}><th scope="row">{reference(t.id)}</th><td><StatusBadge>{human(t.status)}</StatusBadge>{t.status === 'PLANNED' && (!t.currentDriverId || !t.currentTractorId) && <small>Incomplete assignment</small>}</td><td>{names.driver(t.currentDriverId)}</td><td>{names.tractor(t.currentTractorId)}</td><td>{names.trailer(t.plannedInitialTrailerId)}</td><td>{when(t.createdAt)}</td><td>{when(t.updatedAt)}</td><td><Link to={`/operations/trips/${t.id}`}>Open {reference(t.id)}</Link></td></tr>)}
  </DataTable>
}
