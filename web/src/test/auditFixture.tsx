import { vi } from 'vitest'
import { render } from '@testing-library/react'
import { QueryClient } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AppProviders } from '../app/AppProviders'
import { appRoutes } from '../app/routes'
import { admin, fakeAuth, resourceId, tenantA } from './adminFixture'
import type { Membership } from '../areas/tenant-admin/contracts'
export const auditId = '10000000-0000-4000-8000-000000000061'
export const auditUser = '10000000-0000-4000-8000-000000000062'
export const auditEvent = () => ({ id: auditId, actor: { type: 'HUMAN_USER', userId: auditUser, membershipId: resourceId }, sourceType: 'USER_ACTION', actionCode: 'CUSTOMER_UPDATED', entityType: 'CUSTOMER', entityId: resourceId, beforeData: { name: 'Old Customer', status: 'ACTIVE', version: 2 }, afterData: { name: 'New Customer', status: 'INACTIVE', version: 3 }, reason: null, occurredAt: '2026-10-16T18:00:00Z' })
export const auditAdmin = (id = tenantA, name = 'Admin Tenant'): Membership => ({ ...admin(id, name), capabilities: ['AUDIT_VIEW'] })
export function auditFixture(path = '/admin/audit', memberships = [auditAdmin()], auth = fakeAuth()) {
  const requests: { path: string; signal?: AbortSignal | null; method: string }[] = []
  let handler: (url: URL) => Promise<Response> = async url => new Response(JSON.stringify(url.pathname.endsWith(`/audit-events/${auditId}`) ? auditEvent() : { items: [auditEvent()], nextCursor: null }), { headers: { 'Content-Type': 'application/json' } })
  let meStatus = 200
  const fetcher = vi.fn<typeof fetch>(async (input, init) => {
    const url = new URL(String(input)); requests.push({ path: url.pathname + url.search, signal: init?.signal, method: init?.method ?? 'GET' })
    if (url.pathname.endsWith('/me')) return new Response(JSON.stringify({ userId: auditUser, email: null, displayName: 'Admin', memberships }), { status: meStatus, headers: { 'Content-Type': meStatus === 200 ? 'application/json' : 'application/problem+json' } })
    return handler(url)
  })
  vi.stubGlobal('fetch', fetcher)
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } })
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  const view = render(<AppProviders auth={auth} client={cache} baseUrl="https://api.example.test/api/v1"><RouterProvider router={router} /></AppProviders>)
  return { ...view, cache, router, requests, auth, memberships, handler(next: typeof handler) { handler = next }, meFailure(status: number) { meStatus = status } }
}
