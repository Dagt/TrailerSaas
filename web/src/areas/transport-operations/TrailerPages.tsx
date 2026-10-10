import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../shared/auth/context'
import { DataTable, DetailList, EmptyState, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../../shared/ui/components'
import { trailerReadApi } from '../../shared/trailers/api'
import { TrailerReadView } from '../../shared/trailers/TrailerReadView'
import { currentLocationLabel, observedAt, readable } from '../../shared/trailers/display'
import { operationalNumber } from '../tenant-admin/resources'
import { key, useOperations, useMaster } from './queries'
import type { Customer, Site } from './contracts'

export function TrailerQuickSearch() {
  const navigate = useNavigate(), [number, setNumber] = useState(''), [error, setError] = useState(false)
  return <form className="trailer-search" aria-label="Trailer quick search" onSubmit={e => { e.preventDefault(); if (!operationalNumber.safeParse(number).success) { setError(true); return } navigate(`/operations/trailers?${new URLSearchParams({ operationalNumber: number })}`) }}>
    <label htmlFor="quick-trailer">Trailer quick search</label><input id="quick-trailer" value={number} onChange={e => { setNumber(e.target.value); setError(false) }} placeholder="Operational number" aria-invalid={error} aria-describedby={error ? 'quick-error' : undefined} />
    <button>Find Trailer</button>{error && <p id="quick-error" role="alert">Enter a Trailer number without control characters.</p>}
  </form>
}
export function TrailerSearchPage() {
  const { tenantId } = useOperations(), { api } = useAuth(), [params, setParams] = useSearchParams()
  const submitted = params.get('operationalNumber') ?? ''
  const [draft, setDraft] = useState(submitted), [invalid, setInvalid] = useState(false)
  const valid = operationalNumber.safeParse(submitted).success
  const query = useQuery({ queryKey: key(tenantId, 'trailers', { operationalNumber: submitted }), enabled: valid,
    queryFn: ({ signal }) => trailerReadApi(api, tenantId).search(submitted, signal) })
  return <><PageHeader title="Trailer search" description="Exact operational-number lookup. Physical state, last observation and planning remain separate." />
    <form className="trailer-search" aria-label="Trailer search" onSubmit={e => { e.preventDefault(); if (!operationalNumber.safeParse(draft).success) { setInvalid(true); return } setInvalid(false); if (draft === submitted) void query.refetch(); else setParams({ operationalNumber: draft }) }}>
      <label htmlFor="trailer-number">Operational number</label><input id="trailer-number" value={draft} onChange={e => setDraft(e.target.value)} aria-invalid={invalid} aria-describedby={invalid ? 'search-error' : undefined} />
      <button>Search</button><button type="button" onClick={() => { setDraft(''); setParams({}); setInvalid(false) }}>Clear search</button>
      {invalid && <p role="alert" id="search-error">Enter a Trailer number without control characters.</p>}
    </form>
    {!submitted ? <EmptyState>Enter an operational number to find a Trailer.</EmptyState> : !valid ? <ErrorBanner error={new Error('Invalid number')} /> : query.isPending ? <LoadingState /> : query.isError ? <ErrorBanner error={query.error} retry={() => void query.refetch()} /> : !query.data.items.length ? <EmptyState>No Trailer found for that operational number.</EmptyState> :
      <DataTable caption="Trailer search results" headings={['Trailer', 'Customer', 'Current location', 'Location state', 'Last known location', 'Load', 'Coupling', 'Serviceability', 'Last seen', 'Current Driver', 'Current Tractor']}>
        {query.data.items.map(t => <tr key={t.id}><th scope="row"><Link to={`/operations/trailers/${t.id}`}>{t.operationalNumber}</Link></th><td>{t.designatedCustomerName ?? 'Unassigned'}</td><td>{currentLocationLabel(t)}</td><td>{readable(t.locationState)}</td><td>{t.lastKnownLocationName ?? 'Not recorded'}</td><td>{readable(t.loadState)}</td><td><StatusBadge>{readable(t.couplingState)}</StatusBadge></td><td><StatusBadge>{readable(t.serviceabilityState)}</StatusBadge></td><td>{observedAt(t.lastSeenAt)}</td><td>{t.currentDriverName ?? 'Not coupled'}</td><td>{t.currentTractorOperationalNumber ?? 'Not coupled'}</td></tr>)}
      </DataTable>}
  </>
}
export function OperationalTrailerDetail() {
  const { trailerId } = useParams(), { tenantId } = useOperations(), { api } = useAuth()
  const query = useQuery({ queryKey: key(tenantId, 'trailer', trailerId), queryFn: ({ signal }) => trailerReadApi(api, tenantId).detail(trailerId!, signal) })
  if (query.isPending) return <LoadingState />
  if (query.isError) return <ErrorBanner error={query.error} retry={() => void query.refetch()} />
  const t = query.data
  return <><PageHeader title={t.operationalNumber} description={t.designatedCustomer?.name ?? 'No designated Customer'}><Link to="/operations/trailers">Back to Trailer search</Link><button onClick={() => void query.refetch()}>Refresh Trailer</button></PageHeader>
    <p><StatusBadge>{readable(t.serviceabilityState)}</StatusBadge> <StatusBadge>{t.active ? 'Active' : 'Inactive'}</StatusBadge></p>
    <TrailerReadView trailer={t} tenantId={tenantId} namespace="operations" /></>
}
export function OperationalOrderDetail() {
  const { orderId } = useParams(), { api, tenantId } = useOperations()
  const customers = useMaster<Customer>('/customers'), sites = useMaster<Site>('/sites')
  const query = useQuery({ queryKey: key(tenantId, 'order', orderId), queryFn: ({ signal }) => api.order(orderId!, signal) })
  if (query.isPending) return <LoadingState />
  if (query.isError) return <ErrorBanner error={query.error} retry={() => void query.refetch()} />
  const o = query.data
  return <><PageHeader title={o.orderNumber} description="Read-only Order references, requested destinations and cargo."><Link to="/operations/orders">Back to Orders</Link></PageHeader>
    <DetailList items={[
      ['Customer', customers.data?.items.find(c => c.id === o.customerId)?.name ?? 'Customer unavailable'], ['Status', readable(o.status)],
      ['PO', o.poNumber], ['Shipment', o.shipmentNumber], ['BOL', o.bolReference],
    ]} /><section><h2>Requested Stops</h2><ol>{o.requestedStops.map(s => <li key={s.id}>{s.sequenceNumber}. {s.siteSnapshot?.name ?? sites.data?.items.find(x => x.id === s.siteId)?.name ?? 'Destination unavailable'} · {readable(s.stopType)}<p>{s.scheduledAt ? observedAt(s.scheduledAt) : 'Not scheduled'}</p>{s.instructions && <p>{s.instructions}</p>}</li>)}</ol></section>
    <section><h2>Cargo summary</h2>{!o.cargoLines.length ? <EmptyState>No cargo lines specified.</EmptyState> : <DataTable caption="Order cargo" headings={['Description', 'Quantity', 'Pallets', 'Weight']}>
      {o.cargoLines.map(c => <tr key={c.id}><th scope="row">{c.description}</th><td>{c.quantity ?? 'Unspecified'}</td><td>{c.palletCount ?? 'Unspecified'}</td><td>{c.weight == null ? 'Unspecified' : `${c.weight} ${c.weightUnit ?? ''}`}</td></tr>)}</DataTable>}</section>
  </>
}
