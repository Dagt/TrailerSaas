import type { ApiClient } from '../../shared/api/client'
import type { Cancellation, CreateTrip, DispatchBoard, OrderDetail, OrderSummary, Page, Reroute, Substitution, Timeline, TripDetail, TripSummary } from './contracts'
import { historyPage, type HistoryPosition } from './historyContracts'
export type Filters = Record<string, string | undefined>
export function queryString(filters: Filters) {
  const q = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) if (value !== undefined && value !== '') q.set(key, value)
  return q.size ? `?${q}` : ''
}
export function operationsApi(client: ApiClient, tenant: string) {
  const base = `/tenants/${tenant}`
  const get = <T,>(path: string, signal?: AbortSignal) => client.request<T>(`${base}${path}`, { signal })
  const command = (id: string, endpoint: string, body: unknown) => client.request<TripDetail>(`${base}/trips/${id}/${endpoint}`, { method: 'POST', body })
  return {
    board: (f: Filters, signal?: AbortSignal) => get<DispatchBoard>(`/dispatch/board${queryString(f)}`, signal),
    orders: (f: Filters, signal?: AbortSignal) => get<Page<OrderSummary>>(`/orders${queryString(f)}`, signal),
    order: (id: string, signal?: AbortSignal) => get<OrderDetail>(`/orders/${id}`, signal),
    trips: (f: Filters, signal?: AbortSignal) => get<Page<TripSummary>>(`/trips${queryString(f)}`, signal),
    trip: (id: string, signal?: AbortSignal) => get<TripDetail>(`/trips/${id}`, signal),
    tripWithoutHistory: (id: string, signal?: AbortSignal) => get<TripDetail>(`/trips/${id}?includeHistory=false`, signal),
    timeline: (id: string, signal?: AbortSignal) => get<Timeline>(`/trips/${id}/timeline`, signal),
    timelinePage: async (id: string, position: HistoryPosition, signal?: AbortSignal) => historyPage(await get<unknown>(`/trips/${id}/timeline${queryString({ limit: '50', cursor: position.cursor })}`, signal), position),
    master: <T,>(path: string, signal?: AbortSignal) => get<{ items: T[] }>(path, signal),
    create: (r: CreateTrip) => client.request<TripDetail>(`${base}/trips`, { method: 'POST', body: {
      orderIds: r.orderIds, stops: r.stops.map(s => ({ sequenceNumber: s.sequenceNumber, stopType: s.stopType, siteId: s.siteId, locationId: s.locationId, siteSnapshot: s.siteSnapshot, scheduledAt: s.scheduledAt, orderIds: s.orderIds, instructions: s.instructions })),
      driverId: r.driverId, tractorId: r.tractorId, plannedInitialTrailerId: r.plannedInitialTrailerId,
    } }),
    assign: (id: string, kind: 'driver' | 'tractor' | 'trailer', expectedVersion: number, resourceId: string, reason: string) => command(id,
      kind === 'trailer' ? 'planned-trailer-assignments' : `${kind}-assignments`, { expectedVersion, [`${kind}Id`]: resourceId, reason }),
    substitute: (id: string, r: Substitution) => command(id, 'trailer-substitution-decisions', { expectedTrailerId: r.expectedTrailerId, requestedTrailerId: r.requestedTrailerId, decision: r.decision, reason: r.reason }),
    reroute: (id: string, r: Reroute) => command(id, 'reroutes', { expectedVersion: r.expectedVersion, affectedStopId: r.affectedStopId, newSiteId: r.newSiteId, newLocationId: r.newLocationId, scheduledAt: r.scheduledAt, reason: r.reason, instructions: r.instructions }),
    cancel: (id: string, r: Cancellation) => command(id, 'cancellations', { expectedVersion: r.expectedVersion, reason: r.reason,
      ...(r.resolutionType ? { resolutionType: r.resolutionType, destinationSiteId: r.destinationSiteId ?? null, instructions: r.instructions } : {}) }),
  }
}
