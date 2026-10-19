import { MasterDataStatus } from './MasterDataStatus'
import { TrailerQuickSearch } from './TrailerPages'
import { useState } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, DataTable, EmptyState, ErrorBanner, LoadingState } from '../../shared/ui/components'
import { OperationsHeader as PageHeader, OperationsStatus } from './OperationsPresentation'
import { key, useNames, useOperations, useOrderPages, useTripPages } from './queries'
import { orderStatuses, tripStatuses } from './contracts'
import type { TripSummary } from './contracts'
import { human, when, reference } from './display'
export function Dashboard() {
  const { api, tenantId, capabilities } = useOperations(), names = useNames()
  const [customerId, customer] = useState(''), [driverId, driver] = useState(''), [status, state] = useState('')
  const query = useQuery({ queryKey: key(tenantId, 'dashboard', { customerId, driverId, status }), queryFn: ({ signal }) => api.board({ customerId, driverId, status }, signal) })
  return <div className="ops-page"><PageHeader title="Dispatch dashboard" description="Current planning, assignments and work needing attention." eyebrow="Dispatch overview">{capabilities?.includes('ALERT_VIEW')&&<Link to="/operations/alerts">View operational Alerts</Link>}</PageHeader>
    {query.isSuccess && <p className="ops-generated">Generated <time dateTime={query.data.generatedAt} title={query.data.generatedAt}>{when(query.data.generatedAt)}</time>. Driver and Trip status filters apply to Trips and attention.</p>}
    <div className="ops-dashboard-tools">
    <TrailerQuickSearch />
    <fieldset className="operations-filters"><legend>Filter dispatch preview</legend><label>Customer <select value={customerId} onChange={e => customer(e.target.value)}><option value="">All Customers</option>{names.customers.data?.items.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
      <label>Driver <select value={driverId} onChange={e => driver(e.target.value)}><option value="">All Drivers</option>{names.drivers.data?.items.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      <label>Trip status <select value={status} onChange={e => state(e.target.value)}><option value="">All active Trips</option>{tripStatuses.filter(s => !['COMPLETED', 'CANCELLED'].includes(s)).map(s => <option key={s}>{s}</option>)}</select></label></fieldset></div>
    <MasterDataStatus queries={[names.customers, names.drivers, names.tractors, names.trailers, names.sites]} />
    {query.isPending ? <LoadingState /> : query.isError ? <ErrorBanner error={query.error} retry={() => void query.refetch()} /> : <>
      <div className="dispatch-metrics"><article><span>Pending Orders</span><strong>{query.data.pendingOrders.length}</strong><small>Latest unplanned work · up to 20</small></article><article><span>Active Trips</span><strong>{query.data.activeTrips.length}</strong><small>Current projection · up to 20</small></article><article><a href="#dispatch-attention">Needs attention</a><strong>{query.data.attentionItems.length}</strong><small>Within the displayed Trips</small></article></div>
      <p className="ops-preview-note">Bounded previews, not Tenant totals. Up to 20 Orders and 20 Trips are shown; attention refers to the displayed Trips.</p>
      <Card title="Pending Orders" description="Latest pending work returned for this Customer filter." className="ops-table-card" actions={<Link to="/operations/orders">View planning queue</Link>}>
        {!query.data.pendingOrders.length ? <EmptyState>No pending Orders match this Customer.</EmptyState> : <DataTable caption="Pending Orders" headings={['Order', 'Customer', 'Status', 'Created', 'Action']}>
          {query.data.pendingOrders.map(o => <tr key={o.id}><th scope="row"><Link to={`/operations/orders/${o.id}`}>{o.orderNumber}</Link></th><td>{names.customer(o.customerId)}</td><td><OperationsStatus value={o.status} /></td><td>{when(o.createdAt)}</td><td><Link to={`/operations/trips/new?orderId=${o.id}`}>Plan Trip for {o.orderNumber}</Link></td></tr>)}</DataTable>}
      </Card>
      <div id="dispatch-attention"><Card title="Attention" description="Recorded assignment and cancellation conditions within this preview.">{!query.data.attentionItems.length ? <EmptyState>No assignment or cancellation attention in the displayed Trips.</EmptyState> : <ul className="attention-list">{query.data.attentionItems.map(a => <li key={a.tripId}><OperationsStatus value={a.type} /> <Link to={`/operations/trips/${a.tripId}`}>{reference(a.tripId)}</Link></li>)}</ul>}</Card></div>
      <Card title="Active Trips" description="Expected resources and the next planned Stop, without implying physical coupling." className="ops-table-card" actions={<Link to="/operations/trips">View all Trips</Link>}>
        {!query.data.activeTrips.length ? <EmptyState>No active Trips match these filters.</EmptyState> : <DataTable caption="Active Trips" headings={['Trip', 'Status', 'Driver', 'Tractor', 'Expected Trailer', 'Next planned Stop', 'Action']}>
          {query.data.activeTrips.map(t => <tr key={t.id}><th scope="row">{reference(t.id)}</th><td><OperationsStatus value={t.status} /></td><td>{names.driver(t.currentDriverId)}</td><td>{names.tractor(t.currentTractorId)}</td><td>{names.trailer(t.plannedInitialTrailerId)}</td><td>{t.nextPlannedStop ? <>{t.nextPlannedStop.sequenceNumber}. {names.site(t.nextPlannedStop.siteId, t.nextPlannedStop.snapshotName)}<small>{human(t.nextPlannedStop.stopType)} · {when(t.nextPlannedStop.scheduledAt)}</small></> : 'No future Stop'}</td><td><Link to={`/operations/trips/${t.id}`}>Open {reference(t.id)}</Link></td></tr>)}</DataTable>}
      </Card>
    </>}
  </div>
}
export function OrdersQueue() {
  const names = useNames()
  const [customerId, customer] = useState(''), [status, state] = useState(''), [unplanned, planning] = useState('true'), [selected, select] = useState<string[]>([])
  const query = useOrderPages({ customerId, status, unplanned })
  const rows = query.data?.pages.flatMap(p => p.items) ?? []
  const reset = (change: () => void) => { select([]); change() }
  return <div className="ops-page"><PageHeader title="Orders planning queue" description="Select Orders for executable Trip planning. Manifest allocation alone is not operational planning." />
    <MasterDataStatus queries={[names.customers, names.drivers, names.tractors, names.trailers, names.sites]} /><fieldset className="operations-filters"><legend>Filter planning work</legend><label>Customer <select value={customerId} onChange={e => reset(() => customer(e.target.value))}><option value="">All Customers</option>{names.customers.data?.items.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
      <label>Order status <select value={status} onChange={e => reset(() => state(e.target.value))}><option value="">All states</option>{orderStatuses.map(s => <option key={s}>{s}</option>)}</select></label>
      <label>Planning <select value={unplanned} onChange={e => reset(() => planning(e.target.value))}><option value="true">Unplanned work</option><option value="">All Orders</option><option value="false">With planning or terminal</option></select></label></fieldset>
    <div className="ops-selection"><p role="status">{selected.length ? `${selected.length} Order(s) selected for planning.` : 'Select eligible Orders to start a Trip plan.'}</p>{!!selected.length && <Link className="button-link ops-primary-link" to={`/operations/trips/new?${new URLSearchParams(selected.map(id => ['orderId', id]))}`}>Plan selected Orders ({selected.length})</Link>}</div>
    <Card title="Planning work" description="Selection is retained across loaded pages and cleared when a filter changes." className="ops-table-card">
    {query.isPending ? <LoadingState /> : <>{query.isError && <ErrorBanner error={query.error} retry={() => void query.refetch()} />}
      {!rows.length ? <EmptyState>No Orders match these filters.</EmptyState> : <DataTable caption="Orders planning queue" headings={['Select', 'Order', 'Customer', 'Status', 'PO / BOL', 'Created']}>
        {rows.map(o => <tr key={o.id}><td><input type="checkbox" aria-label={`Select ${o.orderNumber}`} disabled={!['OPEN', 'PLANNED'].includes(o.status)} checked={selected.includes(o.id)} onChange={e => select(e.target.checked ? [...selected, o.id] : selected.filter(id => id !== o.id))} /></td><th scope="row"><Link to={`/operations/orders/${o.id}`}>{o.orderNumber}</Link></th><td>{names.customer(o.customerId)}</td><td><OperationsStatus value={o.status} /></td><td>{o.poNumber ?? '—'} / {o.bolReference ?? '—'}</td><td>{when(o.createdAt)}</td></tr>)}</DataTable>}
      {query.hasNextPage && <Button className="ops-pagination" disabled={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>Load more Orders</Button>}</>}
    </Card>
  </div>
}
export function TripsList() {
  const names = useNames()
  const [customerId, customer] = useState(''), [driverId, driver] = useState(''), [status, state] = useState('')
  const query = useTripPages({ customerId, driverId, status })
  const rows = query.data?.pages.flatMap(p => p.items) ?? []
  return <div className="ops-page"><PageHeader title="Trips" description="Operational plans and current assignments."><Link className="ops-primary-link" to="/operations/trips/new">Plan a Trip</Link></PageHeader>
    <MasterDataStatus queries={[names.customers, names.drivers, names.tractors, names.trailers, names.sites]} /><fieldset className="operations-filters"><legend>Filter Trips</legend><label>Customer <select value={customerId} onChange={e => customer(e.target.value)}><option value="">All Customers</option>{names.customers.data?.items.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
      <label>Driver <select value={driverId} onChange={e => driver(e.target.value)}><option value="">All Drivers</option>{names.drivers.data?.items.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
      <label>Trip status <select value={status} onChange={e => state(e.target.value)}><option value="">All states</option>{tripStatuses.map(s => <option key={s}>{s}</option>)}</select></label></fieldset>
    <Card title="Trip directory" description="Loaded plans and current assignments. Expected Trailer is a planning reference." className="ops-table-card">
    {query.isPending ? <LoadingState /> : <>{query.isError && <ErrorBanner error={query.error} retry={() => void query.refetch()} />}
      {!rows.length ? <EmptyState>No Trips match these filters.</EmptyState> : <TripTable rows={rows} />}
      {query.hasNextPage && <Button className="ops-pagination" disabled={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>Load more Trips</Button>}</>}
    </Card>
  </div>
}
function TripTable({ rows }: { rows: TripSummary[] }) {
  const names = useNames()
  return <DataTable caption="Trips" headings={['Trip', 'Status', 'Driver', 'Tractor', 'Expected Trailer', 'Created', 'Updated', 'Action']}>
    {rows.map(t => <tr key={t.id}><th scope="row">{reference(t.id)}</th><td><OperationsStatus value={t.status} />{t.status === 'PLANNED' && (!t.currentDriverId || !t.currentTractorId) && <small>Incomplete assignment</small>}</td><td>{names.driver(t.currentDriverId)}</td><td>{names.tractor(t.currentTractorId)}</td><td>{names.trailer(t.plannedInitialTrailerId)}</td><td>{when(t.createdAt)}</td><td>{when(t.updatedAt)}</td><td><Link to={`/operations/trips/${t.id}`}>Open {reference(t.id)}</Link></td></tr>)}
  </DataTable>
}
