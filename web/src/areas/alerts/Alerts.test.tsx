import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { render } from '@testing-library/react'
import { appRoutes } from '../../app/routes'
import { AppProviders } from '../../app/AppProviders'
import { admin, fakeAuth, resourceId, tenantA, tenantB } from '../../test/adminFixture'
import type { Alert } from './AlertsWorkspace'

const alertId = '00000000-0000-4000-8000-000000000010'
const nextId = '00000000-0000-4000-8000-000000000011'
const stamp = '2026-10-14T12:00:00Z'
function alert(id = alertId, tenantId = tenantA): Alert { return { id, tenantId, alertType: 'TRAILER_MISSING', severity: 'WARNING', status: 'OPEN', version: 0, createdAt: stamp,
  acknowledgedAt: null, acknowledgedBy: null, resolvedAt: null, resolvedBy: null, resolutionReason: null, relatedEntityType: 'TRAILER',
  relatedResource: { id: resourceId, operationalNumber: 'TR-100', designatedCustomerId: null, locationState: 'AT_LOCATION', currentLocationId: resourceId }, context: {}, pushSummary: {},
} }
function fixture(path = '/admin/alerts', capabilities = ['ALERT_VIEW', 'ALERT_ACKNOWLEDGE', 'ALERT_RESOLVE'], multiple = false) {
  const auth = fakeAuth(), me = admin(); me.capabilities = capabilities
  const b = admin(tenantB, 'Other Tenant'); b.capabilities = capabilities
  const records = [alert(), alert(nextId)], requests: { method: string; path: string; body?: Record<string, unknown> }[] = []
  let failure: { status: number; code: string } | undefined
  let responseOverride: ((url: URL) => Promise<Response> | Response | null) | undefined
  const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } })
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input, options) => {
    const url = new URL(String(input)), method = options?.method ?? 'GET', body = options?.body ? JSON.parse(String(options.body)) as Record<string, unknown> : undefined
    requests.push({ method, path: url.pathname + url.search, body })
    const overridden = responseOverride?.(url); if (overridden) return overridden
    if (url.pathname.endsWith('/me')) return response({ userId: resourceId, memberships: multiple ? [me, b] : [me], email: null, displayName: null })
    const id = url.pathname.split('/alerts/')[1]?.split('/')[0]
    if (!id) {
      const page = url.searchParams.has('cursor') ? [records[1]!] : [records[0]!]
      return response({ items: page.map(a => ({ ...a, tenantId: url.pathname.includes(tenantB) ? tenantB : tenantA })), nextCursor: url.searchParams.has('cursor') ? null : 'next-page' })
    }
    const a = records.find(r => r.id === id)!
    if (method === 'POST') {
      if (failure) return response(failure, failure.status)
      if (url.pathname.endsWith('/acknowledgement')) Object.assign(a, { status: 'ACKNOWLEDGED', acknowledgedBy: resourceId, acknowledgedAt: stamp, version: a.version + 1 })
      else Object.assign(a, { status: 'RESOLVED', resolvedBy: resourceId, resolvedAt: stamp, resolutionReason: body?.reason, version: a.version + 1 })
    }
    return response(a)
  }))
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } })
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  const view = render(<AppProviders auth={auth} client={cache} baseUrl="https://api.example.test/api/v1"><RouterProvider router={router} /></AppProviders>)
  return { ...view, auth, router, records, requests, cache, response, fail(next: typeof failure) { failure = next }, override(next: typeof responseOverride) { responseOverride = next } }
}
afterEach(() => vi.unstubAllGlobals())
it.each(['admin', 'operations'])('renders %s inbox from real capability data and paginates', async area => {
  const u = userEvent.setup(), f = fixture(`/${area}/alerts`)
  expect(await screen.findByRole('heading', { name: 'Alerts' })).toBeInTheDocument()
  expect(await screen.findByRole('table', { name: 'Operational Alerts' })).toHaveTextContent('TR-100')
  await u.click(screen.getByRole('button', { name: 'Load more Alerts' }))
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Load more Alerts' })).not.toBeInTheDocument())
  expect(f.requests.some(r => r.path.includes('cursor=next-page'))).toBe(true)
})
it('filters on explicit Apply and sends bounded supported fields', async () => {
  const u = userEvent.setup(), f = fixture(); await screen.findByRole('table', { name: 'Operational Alerts' })
  await u.selectOptions(screen.getByLabelText('Status'), 'RESOLVED'); await u.selectOptions(screen.getByLabelText('Severity'), 'WARNING'); await u.click(screen.getByRole('button', { name: 'Apply filters' }))
  await waitFor(() => expect(f.requests.some(r => r.path.includes('status=RESOLVED') && r.path.includes('severity=WARNING') && r.path.includes('limit=25'))).toBe(true))
})
it('acknowledgement freezes version and requires explicit confirmation', async () => {
  const u = userEvent.setup(), f = fixture(`/admin/alerts/${alertId}`); await screen.findByRole('heading', { name: 'Alert detail' })
  await u.click(screen.getByRole('button', { name: 'Acknowledge' })); expect(f.requests.filter(r => r.method === 'POST')).toHaveLength(0)
  await u.click(screen.getByRole('button', { name: 'Confirm acknowledgement' })); await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(f.requests.find(r => r.method === 'POST')?.body).toEqual({ expectedVersion: 0 }); expect(screen.getByText('ACKNOWLEDGED')).toBeInTheDocument()
})
it('cancel does not issue an acknowledgement command', async () => {
  const u = userEvent.setup(), f = fixture(`/admin/alerts/${alertId}`); await screen.findByRole('button', { name: 'Acknowledge' }); await u.click(screen.getByRole('button', { name: 'Acknowledge' })); await u.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(f.requests.filter(r => r.method === 'POST')).toHaveLength(0)
})
it('resolution requires a reason and never sends a physical correction', async () => {
  const u = userEvent.setup(), f = fixture(`/admin/alerts/${alertId}`); await screen.findByRole('button', { name: 'Resolve' }); await u.click(screen.getByRole('button', { name: 'Resolve' }))
  expect(screen.getByRole('button', { name: 'Confirm resolution' })).toBeDisabled()
  await u.type(screen.getByLabelText('Resolution reason'), 'Recorded physical recovery'); await u.click(screen.getByRole('button', { name: 'Confirm resolution' })); await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(f.requests.filter(r => r.method === 'POST')).toEqual([{ method: 'POST', path: `/api/v1/tenants/${tenantA}/alerts/${alertId}/resolution`, body: { expectedVersion: 0, reason: 'Recorded physical recovery' } }])
})
it('Missing condition disables resolution while location remains unknown', async () => {
  const f = fixture(`/admin/alerts/${alertId}`); f.records[0]!.relatedResource.locationState = 'LOCATION_UNKNOWN'; f.records[0]!.relatedResource.currentLocationId = null
  expect(await screen.findByRole('button', { name: 'Resolve' })).toBeDisabled(); expect(screen.getByText(/physical recovery through an authorized correction/)).toBeInTheDocument()
})
it('view capability alone never shows write actions', async () => {
  fixture(`/admin/alerts/${alertId}`, ['ALERT_VIEW']); await screen.findByRole('heading', { name: 'Alert detail' })
  expect(screen.queryByRole('button', { name: 'Acknowledge' })).not.toBeInTheDocument(); expect(screen.queryByRole('button', { name: 'Resolve' })).not.toBeInTheDocument()
})
it('missing view capability blocks direct route and performs no Alert query', async () => {
  const f = fixture('/admin/alerts', []); await screen.findByRole('heading', { name: 'Alerts access is not available' }); expect(f.requests).toHaveLength(1)
})
it('stale version requires reload and another conscious review', async () => {
  const u = userEvent.setup(), f = fixture(`/admin/alerts/${alertId}`); f.fail({ status: 409, code: 'STALE_VERSION' }); await screen.findByRole('button', { name: 'Acknowledge' }); await u.click(screen.getByRole('button', { name: 'Acknowledge' })); await u.click(screen.getByRole('button', { name: 'Confirm acknowledgement' }))
  await screen.findByRole('button', { name: 'Reload latest' }); expect(f.requests.filter(r => r.method === 'POST')).toHaveLength(1); await u.click(screen.getByRole('button', { name: 'Reload latest' })); expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})
it('malformed ACK cannot claim acknowledged state', async () => {
  const u = userEvent.setup(), f = fixture(`/admin/alerts/${alertId}`); await screen.findByRole('button', { name: 'Acknowledge' })
  f.override(url => url.pathname.endsWith('/acknowledgement') ? f.response({ ...alert(), status: 'ACKNOWLEDGED', version: 1 }) : null)
  await u.click(screen.getByRole('button', { name: 'Acknowledge' })); await u.click(screen.getByRole('button', { name: 'Confirm acknowledgement' })); await screen.findByRole('alert'); expect(screen.getByRole('dialog')).toBeInTheDocument(); expect(screen.queryByText('ACKNOWLEDGED')).not.toBeInTheDocument()
})
it('Tenant switch clears Alert caches and removes pending modal', async () => {
  const u = userEvent.setup(), f = fixture('/admin/alerts', undefined, true)
  await u.selectOptions(await screen.findByLabelText('Active Tenant'), tenantA); await act(() => f.router.navigate(`/admin/alerts/${alertId}`)); await screen.findByRole('button', { name: 'Acknowledge' }); await u.click(screen.getByRole('button', { name: 'Acknowledge' }))
  await u.selectOptions(screen.getByLabelText('Active Tenant'), tenantB); expect(screen.queryByRole('dialog')).not.toBeInTheDocument(); expect(f.cache.getQueryCache().getAll().some(q => q.queryKey[0] === 'tenant' && q.queryKey[1] === tenantA)).toBe(false)
})
it('signout during in-flight detail cannot render previous account resource', async () => {
  let release: ((value: Response) => void) | undefined
  const f = fixture(`/admin/alerts/${alertId}`); f.override(url => url.pathname.endsWith(`/alerts/${alertId}`) ? new Promise<Response>(resolve => { release = resolve }) : null)
  await waitFor(() => expect(release).toBeDefined()); act(() => f.auth.emit(null)); await screen.findByRole('button', { name: 'Sign in' }); await act(async () => release?.(f.response(alert())))
  expect(screen.queryByText('TR-100')).not.toBeInTheDocument(); expect(f.cache.getQueryCache().getAll()).toHaveLength(0)
})
it('empty and denied server responses stay truthful', async () => {
  const f = fixture(); f.override(url => url.pathname.includes('/alerts') ? f.response({ items: [], nextCursor: null }) : null)
  await screen.findByText('No Alerts match these filters.'); expect(screen.queryByText('TR-100')).not.toBeInTheDocument()
})
it.each([403, 404, 503])('server %s cannot produce false acknowledgement or automatic retry', async status => {
  const u=userEvent.setup(), f=fixture(`/admin/alerts/${alertId}`);f.fail({status,code:status===403?'CAPABILITY_REQUIRED':status===404?'RESOURCE_NOT_FOUND':'UNAVAILABLE'})
  await u.click(await screen.findByRole('button',{name:'Acknowledge'}));await u.click(screen.getByRole('button',{name:'Confirm acknowledgement'}));await screen.findByRole('alert')
  expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(1);expect(screen.queryByText('ACKNOWLEDGED')).not.toBeInTheDocument()
})
it('navigating to another occurrence disposes a previous confirmation', async()=>{
  const u=userEvent.setup(), f=fixture(`/admin/alerts/${alertId}`)
  await u.click(await screen.findByRole('button',{name:'Acknowledge'}));await act(()=>f.router.navigate(`/admin/alerts/${nextId}`))
  await screen.findByRole('heading',{name:'Alert detail'});expect(screen.queryByRole('dialog')).not.toBeInTheDocument();expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(0)
})
it('shows authorized stale physical evidence and keeps unapproved write actions absent',async()=>{
 const f=fixture(`/operations/alerts/${alertId}`,['ALERT_VIEW']);Object.assign(f.records[0]!,{alertType:'STALE_TRAILER',originActorType:'SYSTEM',detection:{detectedAt:stamp,lastSeenAt:'2026-10-11T04:00:00Z',thresholdHours:72,elapsedHours:80,jobId:nextId}})
 await screen.findByText('Last physical observation');expect(screen.getByText('80.0 hours')).toBeInTheDocument();expect(screen.getByText('72 hours')).toBeInTheDocument();expect(screen.getByText('SYSTEM')).toBeInTheDocument()
 expect(screen.queryByRole('button',{name:'Acknowledge'})).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:'Resolve'})).not.toBeInTheDocument();expect(screen.getByRole('link',{name:'TR-100'})).toHaveAttribute('href',`/operations/trailers/${resourceId}`)
})
it('accepts authoritative SYSTEM stale recovery in transit without inventing a resolver User',async()=>{
 const f=fixture(`/admin/alerts/${alertId}`,['ALERT_VIEW']);Object.assign(f.records[0]!,{alertType:'STALE_TRAILER',originActorType:'SYSTEM',status:'RESOLVED',resolvedAt:stamp,resolvedBy:null,resolutionActorType:'SYSTEM',resolutionReason:'Physical observation is fresh',detection:{detectedAt:stamp,lastSeenAt:'2026-10-11T04:00:00Z',thresholdHours:72,elapsedHours:80,jobId:nextId},resolutionEvidence:{lastSeenAt:stamp,thresholdHours:72,jobId:nextId}})
 f.records[0]!.relatedResource.locationState='IN_TRANSIT';f.records[0]!.relatedResource.currentLocationId=null
 await screen.findByText('RESOLVED');expect(screen.getByText('Physical observation is fresh')).toBeInTheDocument();expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})
it('rejects stale occurrences with fabricated or absent detection evidence',async()=>{
 const f=fixture(`/admin/alerts/${alertId}`,['ALERT_VIEW']);Object.assign(f.records[0]!,{alertType:'STALE_TRAILER',originActorType:'SYSTEM'})
 await screen.findByRole('alert');expect(screen.queryByText('TR-100')).not.toBeInTheDocument()
})
it('filters existing inbox by STALE_TRAILER without another endpoint',async()=>{
 const u=userEvent.setup(),f=fixture();await screen.findByRole('table',{name:'Operational Alerts'});await u.selectOptions(screen.getByLabelText('Alert type'),'STALE_TRAILER');await u.click(screen.getByRole('button',{name:'Apply filters'}))
 await waitFor(()=>expect(f.requests.some(r=>r.path.includes('alertType=STALE_TRAILER'))).toBe(true))
})
