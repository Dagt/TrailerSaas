import { vi } from 'vitest'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { QueryClient } from '@tanstack/react-query'
import { AppProviders } from '../app/AppProviders'
import { appRoutes } from '../app/routes'
import type { AuthBoundary, SessionIdentity } from '../shared/auth/boundary'
import type { Membership, MasterRecord } from '../areas/tenant-admin/contracts'

export const tenantA = '00000000-0000-4000-8000-000000000001'
export const tenantB = '00000000-0000-4000-8000-000000000002'
export const resourceId = '00000000-0000-4000-8000-000000000003'
export const customerId = '00000000-0000-4000-8000-000000000004'
export const siteId = '00000000-0000-4000-8000-000000000005'
export const qrId = '00000000-0000-4000-8000-000000000006'
export const publicToken = 'AAAAAAAAAAAAAAAAAAAAAA'
export const admin = (id = tenantA, name = 'Admin Tenant'): Membership => ({ tenantId: id, tenantName: name, status: 'ACTIVE', roles: [{ roleCode: 'TENANT_ADMIN', scopeType: 'TENANT', customerId: null }] })
export const stamps = { version: 0, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' }
export function sample(kind: string, id = resourceId): MasterRecord {
  const common = { ...stamps, id, name: `Original ${kind}`, active: true }
  if (kind === 'customers') return { ...common, status: 'ACTIVE' }
  if (kind === 'sites') return { ...common, address: { line1: 'Original street', line2: null, city: null, stateRegion: null, postalCode: null, countryCode: null }, timezone: 'UTC', latitude: null, longitude: null, geofenceRadiusM: null }
  if (kind === 'locations') return { ...common, siteId, customerId: null, type: 'YARD', code: null }
  if (kind === 'drivers') return { ...common, licenseNumber: null, medicalCertificateExpires: null, userId: null }
  if (kind === 'tractors') return { ...stamps, id, operationalNumber: 'T114', active: true }
  return { ...stamps, id, operationalNumber: 'T114', active: true, designatedCustomerId: customerId, trailerType: null, capabilityNotes: null, usagePurpose: null, locationState: 'LOCATION_UNKNOWN', currentLocationId: null, lastKnownLocationId: null, lastSeenAt: null, lastDriverId: null, loadState: 'UNKNOWN', serviceabilityState: 'IN_SERVICE' }
}
export function fakeAuth(initial: SessionIdentity | null = { uid: 'firebase-user' }): AuthBoundary & { emit(identity: SessionIdentity | null): void } {
  let listener: ((identity: SessionIdentity | null) => void) | undefined
  return { configured: true, subscribe(next) { listener = next; next(initial); return () => { listener = undefined } },
    signIn: vi.fn(async () => { listener?.({ uid: 'firebase-user' }) }), token: vi.fn(async () => 'synthetic-secret-firebase-token'),
    signOut: vi.fn(async () => { listener?.(null) }), emit(next) { listener?.(next) },
  }
}
export function adminFixture(path = '/admin', memberships = [admin()], auth = fakeAuth()) {
  const requests: { method: string; path: string; body?: Record<string, unknown>; headers: Headers }[] = []
  const rows: Record<string, Record<string, unknown>[]> = {}
  const settings: Record<string, unknown> = { staleTrailerThresholdHours: 72, defaultTimezone: null, version: 0 }
  for (const kind of ['customers', 'sites', 'drivers', 'tractors', 'trailers', 'locations']) rows[kind] = [sample(kind, kind === 'customers' ? customerId : kind === 'sites' ? siteId : resourceId) as unknown as Record<string, unknown>]
  let qrs: Record<string, unknown>[] = []
  let nextFailure: { status: number; code?: string; errors?: { field: string; message: string }[]; traceId?: string } | undefined
  const response = (body: unknown, status = 200) => new Response(status === 204 ? null : JSON.stringify(body), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } })
  const fetcher = vi.fn<typeof fetch>(async (input, options) => {
    const url = new URL(String(input)); const method = options?.method ?? 'GET'
    const body = options?.body ? JSON.parse(String(options.body)) as Record<string, unknown> : undefined
    requests.push({ method, path: url.pathname + url.search, body, headers: new Headers(options?.headers) })
    if (url.pathname === '/api/v1/me') return response({ userId: 'internal-user', email: 'user@example.test', displayName: 'Admin', memberships })
    const [, resourcePath = ''] = url.pathname.split(/\/tenants\/[^/]+\//)
    const parts = resourcePath.split('/'); const kind = parts[0]!
    if (nextFailure && method !== 'GET') { const failure = nextFailure; nextFailure = undefined; return response(failure, failure.status) }
    if (kind === 'settings') {
      if (method === 'PATCH') Object.assign(settings, body, { version: Number(settings.version) + 1 })
      return response(settings)
    }
    if (kind === 'customers' && parts[2] === 'sites') return response({ id: resourceId, customerId: parts[1], siteId: body?.siteId, createdAt: stamps.createdAt }, method === 'DELETE' ? 204 : 201)
    if (resourcePath.includes('qr-identities')) {
      if (parts[4] === 'revocation') { qrs = qrs.map(q => q.id === parts[3] ? { ...q, active: false, revokedAt: stamps.updatedAt } : q); return response(null, 204) }
      if (method === 'POST') { const qr = { id: qrs.length ? crypto.randomUUID() : qrId, trailerId: resourceId, publicToken, operationalNumber: 'T114', active: true, issuedAt: stamps.createdAt, revokedAt: null }; qrs = [qr, ...qrs]; return response(qr, 201) }
      return response({ items: qrs })
    }
    if (kind === 'trailers' && parts[2] === 'timeline') return response({ items: [], nextCursor: null })
    const locationList = kind === 'sites' && parts[2] === 'locations'
    const resource = locationList ? 'locations' : kind
    const list = rows[resource] ?? []
    if (method === 'GET') return response(parts[1] && !locationList ? list.find(r => r.id === parts[1]) : { items: list })
    if (method === 'POST') { const row = { ...sample(resource), ...body }; rows[resource] = [row as unknown as Record<string, unknown>]; return response(row, 201) }
    const row = list.find(r => r.id === parts[1])!
    if (method === 'DELETE' && parts[2] === 'user-link') { Object.assign(row, { userId: null, version: Number(row.version) + 1 }); return response(null, 204) }
    if (method === 'PATCH') { Object.assign(row, body, { version: Number(row.version) + 1 }); return response(row) }
    return response({ code: 'RESOURCE_NOT_FOUND' }, 404)
  })
  vi.stubGlobal('fetch', fetcher)
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } })
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  const view = render(<AppProviders auth={auth} client={cache} baseUrl="https://api.example.test/api/v1"><RouterProvider router={router} /></AppProviders>)
  return { ...view, cache, router, auth, rows, requests, fetcher, settings, fail(failure: typeof nextFailure) { nextFailure = failure },
    qrs(rows?: Record<string, unknown>[]) { if (rows) qrs = rows; return qrs } }
}
