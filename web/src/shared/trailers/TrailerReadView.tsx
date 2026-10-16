import { Link } from 'react-router'
import { useInfiniteQuery } from '@tanstack/react-query'
import { useAuth } from '../auth/context'
import { DataTable, DetailList, EmptyState, ErrorBanner, LoadingState, StatusBadge } from '../ui/components'
import { trailerReadApi } from './api'
import type { TrailerDetail } from './contracts'
import { currentLocationLabel, observedAt, readable } from './display'
import { ApiProblem } from '../api/client'
export function TrailerReadView({ trailer: t, tenantId, namespace }: { trailer: TrailerDetail; tenantId: string; namespace: 'operations' | 'tenant' }) {
  const coupling = t.currentCoupling
  return <div className="trailer-read-grid">
    <section className="dispatch-card"><h2>Location</h2><DetailList items={[
      ['Current location', currentLocationLabel(t)], ['Current Site', t.currentLocation?.siteName ?? 'Not recorded'],
      ['Last known location', t.lastKnownLocation?.name ?? 'Not recorded'], ['Last known Site', t.lastKnownLocation?.siteName ?? 'Not recorded'],
      ['Last seen', observedAt(t.lastSeenAt)], ['Location state', readable(t.locationState)],
    ]} /><p className="muted">Last known location is a previous observation, not a claim about the current location.</p></section>
    <section className="dispatch-card"><h2>Load / serviceability</h2><DetailList items={[
      ['Load state', readable(t.loadState)], ['Serviceability', readable(t.serviceabilityState)], ['Usage purpose', t.usagePurpose ? readable(t.usagePurpose) : 'Not specified'],
      ['Trailer type', t.trailerType], ['Capability notes', t.capabilityNotes],
    ]} /></section>
    <section className="dispatch-card"><h2>Physical coupling</h2><StatusBadge>{coupling ? 'Coupled' : 'Uncoupled'}</StatusBadge>
      {coupling ? <><DetailList items={[
        ['Current Tractor', coupling.tractorOperationalNumber], ['Current Driver', coupling.driverName], ['Hooked', observedAt(coupling.hookedAt)],
      ]} />{coupling.tripId && <Link to={`/operations/trips/${coupling.tripId}`}>Open Trip</Link>}</> : <p>No active physical coupling. A planned Trailer assignment is not a coupling.</p>}
      {t.lastCoupling && <p className="muted">Latest coupling: {t.lastCoupling.tractorOperationalNumber} · {t.lastCoupling.driverName} · hooked {observedAt(t.lastCoupling.hookedAt)}{t.lastCoupling.droppedAt && ` · dropped ${observedAt(t.lastCoupling.droppedAt)}`}</p>}
    </section>
    <section className="dispatch-card"><h2>Driver / Tractor</h2><DetailList items={[
      ['Current Driver', t.currentDriverName ?? 'Not coupled'], ['Current Tractor', t.currentTractorOperationalNumber ?? 'Not coupled'],
      ['Last Driver', t.lastDriver?.name ?? 'Not recorded'], ['Last Tractor', t.lastTractor?.operationalNumber ?? 'Not recorded'],
    ]} /><p className="muted">Last Driver is the last recorded Trailer observation; last Tractor comes from the latest coupling.</p></section>
    <section className="dispatch-card trailer-wide"><h2>Manifest / planned load</h2><p>Allocations describe planning. They do not confirm cargo physically loaded on this Trailer.</p>
      {!t.openManifests?.length ? <EmptyState>No visible OPEN Manifests.</EmptyState> : t.openManifests.map(m => <article key={m.id} className="manifest-summary"><h3>Manifest {m.id.slice(0, 8)} <StatusBadge>{m.status}</StatusBadge></h3>
        {!m.orders.length ? <p>{m.ordersTruncated ? 'Orders are outside this bounded planning preview.' : 'No Orders allocated.'}</p> : <DataTable caption={`Orders in Manifest ${m.id.slice(0, 8)}`} headings={['Order', 'Customer', 'Status']}>
          {m.orders.map(o => <tr key={o.id}><th scope="row"><Link to={`/operations/orders/${o.id}`}>{o.orderNumber}</Link></th><td>{o.customerName}</td><td>{readable(o.status)}</td></tr>)}</DataTable>}
        {m.ordersTruncated && m.orders.length > 0 && <p>Additional Orders are outside this bounded planning preview.</p>}
      </article>)}
      {t.manifestSummaryTruncated && <p>Planning preview limited to 20 OPEN Manifests and 50 Orders.</p>}
    </section>
    <section className="dispatch-card trailer-wide"><h2>Recent movement timeline</h2><TrailerTimeline tenantId={tenantId} trailerId={t.id} namespace={namespace} /></section>
    <section className="dispatch-card trailer-wide"><h2>Evidence</h2><p>Evidence integration is not implemented yet. This view cannot determine whether evidence exists.</p></section>
  </div>
}
export function TrailerTimeline({ tenantId, trailerId, namespace }: { tenantId: string; trailerId: string; namespace: 'operations' | 'tenant' }) {
  const { api, identity } = useAuth()
  const reads = trailerReadApi(api, tenantId)
  const query = useInfiniteQuery({ queryKey: [namespace, tenantId, 'trailer-timeline', trailerId, identity?.uid], initialPageParam: undefined as string | undefined,
    queryFn: ({ signal, pageParam }) => reads.timeline(trailerId, pageParam, signal), getNextPageParam: p => p.nextCursor ?? undefined })
  if (query.isPending) return <LoadingState />
  if (query.isError && query.error instanceof ApiProblem && [401, 403, 404].includes(query.error.problem.status)) return <ErrorBanner error={query.error} retry={() => void query.refetch()} />
  const items = query.data?.pages.flatMap(p => p.items) ?? []
  return <>
    <p className="muted">Newest physical observations first. Recorded time may be later after offline replay. This is operational movement history, separate from administrative Audit.</p>
    {query.isError && <ErrorBanner error={query.error} retry={() => void (query.isFetchNextPageError ? query.fetchNextPage() : query.refetch())} />}
    {!items.length && !query.isError ? <EmptyState>No recorded Trailer movements.</EmptyState> : <ol className="operations-timeline" aria-label="Trailer movement timeline">
      {items.map(e => <li key={e.id}><strong>{readable(e.eventType)}</strong><time dateTime={e.occurredAt}>{observedAt(e.occurredAt)}</time>
        <small>Actor type: {readable(e.actorType)} · Source: {readable(e.sourceType)}</small>
        {e.recordedAt !== e.occurredAt && <small>Recorded {observedAt(e.recordedAt)}</small>}
        {e.reason && <p>{e.reason}</p>}
        {(e.locationName || e.driverName || e.tractorOperationalNumber) && <p>{[e.locationName, e.driverName, e.tractorOperationalNumber].filter(Boolean).join(' · ')}</p>}
        {e.changes.previousLoadState && e.changes.newLoadState && <p>Load: {readable(e.changes.previousLoadState)} → {readable(e.changes.newLoadState)}</p>}
        {e.changes.previousServiceabilityState && e.changes.newServiceabilityState && <p>Serviceability: {readable(e.changes.previousServiceabilityState)} → {readable(e.changes.newServiceabilityState)}</p>}
        {e.changes.previousLocationState && <p>Previous location state: {readable(e.changes.previousLocationState)}</p>}
        {e.tripId && <Link to={`/operations/trips/${e.tripId}`}>Open related Trip</Link>}
      </li>)}
    </ol>}
    {query.hasNextPage && !query.isFetchNextPageError && <button disabled={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>Load more movements</button>}
  </>
}
