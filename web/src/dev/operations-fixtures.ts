/** Synthetic read fixtures for the isolated development entry; every write rejects. */
import { ApiProblem, type ApiClient } from '../shared/api/client'
import { createAdminReviewApi, demoId, demoMembership } from './tenant-admin-fixtures'
import type { DispatchBoard, OrderDetail, TripDetail, TripStatus, AssignmentHistory, OperationalHistory } from '../areas/transport-operations/contracts'

export const operationsDemoId = (n: number) => `1210${String(n).padStart(4, '0')}-0000-4000-8000-000000000001`
const stamp = '2026-10-19T13:00:00Z'
const stamps = { version: 3, createdAt: '2026-10-16T13:00:00Z', updatedAt: stamp }
const orders: OrderDetail[] = [10,11,12,13].map((n, i) => ({ ...stamps, id: operationsDemoId(n), customerId: demoId(i % 2 ? 11 : 10), orderNumber: `DEMO-ORD-${104 + i}`, status: i === 2 ? 'COMPLETED' : i === 1 ? 'PLANNED' : 'OPEN', poNumber: `DEMO-PO-${104 + i}`, bolReference: i ? null : 'DEMO-BOL-104', shipmentNumber: null, deliveredAt: i === 2 ? stamp : null, completedAt: i === 2 ? stamp : null, cancelledAt: null,
  requestedStops: [{ id: operationsDemoId(100 + i * 2), sequenceNumber: 1, stopType: 'PICKUP', siteId: demoId(20), locationId: demoId(22), siteSnapshot: { name: 'DEMO · Distribution Site', timezone: 'America/Chicago' }, scheduledAt: '2026-10-20T14:00:00Z', instructions: 'DEMO · Check in at the receiving desk.' }, { id: operationsDemoId(101 + i * 2), sequenceNumber: 2, stopType: 'DELIVERY', siteId: demoId(20), locationId: null, siteSnapshot: { name: 'DEMO · Receiving facility' }, scheduledAt: null, instructions: 'DEMO · Confirm unloading instructions at arrival.' }],
  cargoLines: [{ id: operationsDemoId(200 + i), description: 'DEMO · Packaged materials — layout review only', quantity: 240, palletCount: 12, weight: 9600, weightUnit: 'LB', createdAt: stamp }, { id: operationsDemoId(210 + i), description: 'DEMO · Additional handling supplies', quantity: null, palletCount: null, weight: null, weightUnit: null, createdAt: stamp }],
}))
const trips: TripDetail[] = ([['IN_TRANSIT',30],['PLANNED',31],['CANCELLING',32],['ASSIGNED',33]] as const).map(([status,n]) => ({ ...stamps, id: operationsDemoId(n), status, currentDriverId: status === 'PLANNED' ? null : demoId(30), currentTractorId: status === 'PLANNED' ? null : demoId(40), plannedInitialTrailerId: demoId(50), startedAt: ['IN_TRANSIT','CANCELLING'].includes(status) ? stamp : null, completedAt: null, cancelledAt: null, assignmentHistory: [], operationalHistory: [],
  stops: orders[0]!.requestedStops.map((s,i) => ({ ...s, id: operationsDemoId(300+n+i), ...stamps, orderIds: [orders[0]!.id], status: i === 0 && status === 'IN_TRANSIT' ? 'COMPLETED' : 'PENDING', arrivedAt: i === 0 && status === 'IN_TRANSIT' ? stamp : null, serviceStartedAt: null, completedAt: i === 0 && status === 'IN_TRANSIT' ? stamp : null, departedAt: null })),
}))
const assignment: AssignmentHistory = { id: operationsDemoId(400), eventType: 'DRIVER_ASSIGNED', driverId: demoId(30), tractorId: null, trailerId: null, actorUserId: demoId(2), occurredAt: '2026-10-16T14:00:00Z', reason: 'DEMO · Initial dispatch assignment.', metadata: null }
const reroute: OperationalHistory = { id: operationsDemoId(401), eventType: 'TRIP_REROUTED', tripStopId: trips[0]!.stops[1]!.id, actorUserId: demoId(2), targetDriverId: demoId(30), occurredAt: '2026-10-18T14:00:00Z', reason: 'DEMO · Receiving instructions changed.', metadata: { previous: { siteSnapshot: { name: 'DEMO · Original destination' } }, replacement: { siteSnapshot: { name: 'DEMO · Revised receiving destination' }, scheduledAt: '2026-10-20T16:00:00Z', instructions: 'DEMO · Use the north receiving entrance.' } } }

export function createOperationsReviewApi(state = ''): ApiClient {
  const catalog = createAdminReviewApi(state)
  return { async request<T>(path: string, options?: Parameters<ApiClient['request']>[1]) {
    if (options?.signal?.aborted) throw new DOMException('Cancelled', 'AbortError')
    if (options?.method && options.method !== 'GET') throw new ApiProblem({ status: state === 'conflict' ? 409 : 503, code: state === 'conflict' ? 'STALE_VERSION' : 'ISOLATED_OPERATIONS_READ_ONLY' })
    if (path === '/me') return { userId: demoId(2), email: null, displayName: 'DEMO · Dispatch reviewer', memberships: state === 'no-membership' ? [] : [{ ...demoMembership, roles: [{ roleCode: 'TRANSPORT_OPERATIONS', scopeType: 'TENANT', customerId: null }], ...(state === 'denied' ? { capabilities: [] } : {}) }] } as T
    if (state === 'loading') return new Promise<T>((_resolve,reject) => options?.signal?.addEventListener('abort', () => reject(new DOMException('Cancelled','AbortError')), { once: true }))
    if (/^(401|403|404|500)$/.test(state)) throw new ApiProblem({ status: Number(state) })
    const url = new URL(path,'https://isolated.example.test'), parts = url.pathname.split('/').filter(Boolean).slice(2), cursor = url.searchParams.has('cursor')
    if (state === 'page-error' && cursor) throw new ApiProblem({ status: 503 })
    if (parts.join('/') === 'dispatch/board') {
      const board: DispatchBoard = { generatedAt: stamp, pendingOrders: state === 'empty' ? [] : orders.slice(0,2), activeTrips: state === 'empty' ? [] : trips.slice(0,3).map(t => ({ ...t, nextPlannedStop: { ...t.stops[1]!, snapshotName: 'DEMO · Receiving facility' } })), attentionItems: state === 'empty' ? [] : [{ type: 'INCOMPLETE_ASSIGNMENT', tripId: trips[1]!.id },{ type: 'CANCELLING', tripId: trips[2]!.id }] }
      return board as T
    }
    if (parts[0] === 'orders') {
      if (parts[1]) { const order = orders.find(o => o.id === parts[1]); if (!order) throw new ApiProblem({ status:404 }); return order as T }
      const filtered = orders.filter(o => (!url.searchParams.get('customerId') || o.customerId === url.searchParams.get('customerId')) && (!url.searchParams.get('status') || o.status === url.searchParams.get('status')) && (url.searchParams.get('unplanned') !== 'true' || ['OPEN','PLANNED'].includes(o.status)))
      return { items: state === 'empty' ? [] : cursor ? filtered.filter(o => o.id === operationsDemoId(13)) : filtered.filter(o => o.id !== operationsDemoId(13)), nextCursor: state === 'empty' || cursor ? null : 'demo_order_next_121' } as T
    }
    if (parts[0] === 'trips') {
      if (parts[2] === 'timeline') return { items: state === 'empty' ? [] : cursor ? [reroute] : [assignment], nextCursor: state === 'empty' || cursor ? null : 'demo_trip_events_next_121' } as T
      if (parts[1]) { const trip = trips.find(t => t.id === parts[1]); if (!trip) throw new ApiProblem({ status:404 }); return (state.startsWith('trip-') ? { ...trip, status: state.slice(5) as TripStatus } : trip) as T }
      const filtered = trips.filter(t => (!url.searchParams.get('status') || t.status === url.searchParams.get('status')) && (!url.searchParams.get('driverId') || t.currentDriverId === url.searchParams.get('driverId')))
      return { items: state === 'empty' ? [] : cursor ? filtered.slice(3) : filtered.slice(0,3), nextCursor: state === 'empty' || cursor ? null : 'demo_trip_next_121' } as T
    }
    if (parts[0] === 'trailers' && parts[2] === 'timeline') {
      const first = await catalog.request<{ items: { id:string; occurredAt:string }[] }>(url.pathname, options)
      return { items: state === 'empty' ? [] : first.items.map(e => cursor ? { ...e, id: operationsDemoId(450), occurredAt: '2026-10-15T14:00:00Z' } : e), nextCursor: state === 'empty' || cursor ? null : 'demo_movement_next_121' } as T
    }
    return catalog.request<T>(path, options)
  } }
}
