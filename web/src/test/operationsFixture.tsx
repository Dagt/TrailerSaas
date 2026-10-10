import { vi } from 'vitest'
import { render } from '@testing-library/react'
import { QueryClient } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AppProviders } from '../app/AppProviders'
import { appRoutes } from '../app/routes'
import { admin, customerId, fakeAuth, resourceId, sample, siteId, stamps, tenantA } from './adminFixture'
import type { Membership } from '../areas/tenant-admin/contracts'
import type { DispatchBoard, OrderDetail, TripDetail } from '../areas/transport-operations/contracts'
export const tripId = '10000000-0000-4000-8000-000000000001'
export const orderId = '10000000-0000-4000-8000-000000000002'
export const otherOrderId = '10000000-0000-4000-8000-000000000003'
export const stopId = '10000000-0000-4000-8000-000000000004'
export const alternateId = '10000000-0000-4000-8000-000000000005'
export function operationsMembership(id = tenantA, name = 'Operations Tenant'): Membership {
  return { ...admin(id, name), roles: [{ roleCode: 'TRANSPORT_OPERATIONS', scopeType: 'TENANT', customerId: null }] }
}
export function order(id = orderId, number = 'ORD-100'): OrderDetail {
  return { id, customerId, orderNumber: number, status: 'OPEN', ...stamps, poNumber: 'PO-1', shipmentNumber: null, bolReference: 'BOL-1', deliveredAt: null, completedAt: null, cancelledAt: null, cargoLines: [],
    requestedStops: [{ id: stopId, sequenceNumber: 1, stopType: 'DELIVERY', siteId, locationId: null, siteSnapshot: { name: 'Frozen receiving', timezone: 'America/Chicago' }, scheduledAt: '2026-10-09T15:00:00Z', instructions: 'Original appointment' }] }
}
export function trip(status: TripDetail['status'] = 'PLANNED'): TripDetail {
  const s = order().requestedStops[0]!
  return { id: tripId, status, currentDriverId: null, currentTractorId: null, plannedInitialTrailerId: null, ...stamps, startedAt: null, completedAt: null, cancelledAt: null, assignmentHistory: [], operationalHistory: [],
    stops: [{ ...s, id: stopId, ...stamps, status: 'PENDING', orderIds: [orderId], arrivedAt: null, serviceStartedAt: null, completedAt: null, departedAt: null }] }
}
export function operationsFixture(path = '/operations', options: { memberships?: Membership[]; trip?: TripDetail; orders?: OrderDetail[]; empty?: boolean; auth?: ReturnType<typeof fakeAuth> } = {}) {
  const auth = options.auth ?? fakeAuth(), memberships = options.memberships ?? [operationsMembership()]
  let currentTrip = structuredClone(options.trip ?? trip())
  let orders = options.orders ?? [order(), order(otherOrderId, 'ORD-200')]
  const rows: Record<string, Record<string, unknown>[]> = {}
  for (const kind of ['customers', 'sites', 'drivers', 'tractors', 'trailers', 'locations']) rows[kind] = [sample(kind, kind === 'customers' ? customerId : kind === 'sites' ? siteId : resourceId) as unknown as Record<string, unknown>]
  rows.customers![0]!.name = 'Acme Packaging'; rows.sites![0]!.name = 'Receiving facility'; rows.drivers![0]!.name = 'Maria Driver'; rows.tractors![0]!.operationalNumber = 'TR-100'; rows.trailers![0]!.operationalNumber = 'BOX-100'
  rows.trailers!.push({ ...rows.trailers![0], id: alternateId, operationalNumber: 'BOX-200' })
  let nextFailure: { status: number; code?: string; traceId?: string; errors?: { field: string; message: string }[] } | undefined
  let boardFailure: typeof nextFailure
  let nextCursor: string | null = null
  let uncertainCreates = false
  const requests: { method: string; path: string; body?: Record<string, unknown> }[] = []
  const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } })
  const fetcher = vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input)), method = init?.method ?? 'GET', body = init?.body ? JSON.parse(String(init.body)) as Record<string, unknown> : undefined
    requests.push({ method, path: url.pathname + url.search, body })
    if (url.pathname === '/api/v1/me') return response({ userId: 'internal', email: 'ops@example.test', displayName: 'Operations', memberships })
    const path = url.pathname.replace(/\/api\/v1\/tenants\/[^/]+/, '')
    if (method !== 'GET' && nextFailure) { const failure = nextFailure; nextFailure = undefined; if (uncertainCreates && path === '/trips') currentTrip = { ...currentTrip, id: tripId }; return response(failure, failure.status) }
    if (path === '/dispatch/board') {
      if (boardFailure) return response(boardFailure, boardFailure.status)
      const board: DispatchBoard = { generatedAt: stamps.createdAt, pendingOrders: options.empty ? [] : orders, activeTrips: options.empty ? [] : [{ ...currentTrip, nextPlannedStop: { ...currentTrip.stops[0]!, snapshotName: 'Frozen receiving' } }], attentionItems: options.empty ? [] : [{ type: currentTrip.status === 'CANCELLING' ? 'CANCELLING' : 'INCOMPLETE_ASSIGNMENT', tripId }] }
      return response(board)
    }
    if (path === '/orders') return response({ items: options.empty || url.searchParams.has('cursor') ? [] : orders, nextCursor: url.searchParams.has('cursor') ? null : nextCursor })
    if (path.startsWith('/orders/')) return response(orders.find(o => o.id === path.split('/')[2]))
    if (path === '/trips' && method === 'GET') return response({ items: options.empty || url.searchParams.has('cursor') ? [] : [currentTrip], nextCursor: url.searchParams.has('cursor') ? null : nextCursor })
    if (path === '/trips' && method === 'POST') {
      currentTrip = { ...trip(), status: body?.driverId && body?.tractorId ? 'ASSIGNED' : 'PLANNED', currentDriverId: body?.driverId as string | null, currentTractorId: body?.tractorId as string | null, plannedInitialTrailerId: body?.plannedInitialTrailerId as string | null }
      return response(currentTrip, 201)
    }
    if (path.startsWith('/trips/') && path.endsWith('/timeline')) return response({ items: [...currentTrip.assignmentHistory, ...currentTrip.operationalHistory] })
    if (path === `/trips/${tripId}`) return response(currentTrip)
    if (path.startsWith(`/trips/${tripId}/`) && method === 'POST') {
      const endpoint = path.split('/')[3]
      currentTrip.version += 1
      if (endpoint === 'driver-assignments') currentTrip.currentDriverId = body!.driverId as string
      if (endpoint === 'tractor-assignments') currentTrip.currentTractorId = body!.tractorId as string
      if (endpoint === 'planned-trailer-assignments') currentTrip.plannedInitialTrailerId = body!.trailerId as string
      if (endpoint === 'trailer-substitution-decisions' && body!.decision === 'AUTHORIZE') currentTrip.plannedInitialTrailerId = body!.requestedTrailerId as string
      if (endpoint === 'cancellations') currentTrip.status = ['IN_TRANSIT', 'AT_STOP'].includes(currentTrip.status) ? 'CANCELLING' : 'CANCELLED'
      return response(currentTrip)
    }
    const kind = path.includes('/locations') ? 'locations' : path.slice(1)
    return response({ items: rows[kind] ?? [] })
  })
  vi.stubGlobal('fetch', fetcher)
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } })
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  const view = render(<AppProviders client={cache} auth={auth} baseUrl="https://api.example.test/api/v1"><RouterProvider router={router} /></AppProviders>)
  return { ...view, router, cache, requests, rows, auth, fetcher,
    fail(failure: typeof nextFailure, saved = false) { nextFailure = failure; uncertainCreates = saved },
    boardFail(failure: typeof nextFailure) { boardFailure = failure },
    page(cursor: string | null) { nextCursor = cursor },
    trip(next?: TripDetail) { if (next) currentTrip = next; return currentTrip },
    orders(next?: OrderDetail[]) { if (next) orders = next; return orders },
  }
}
