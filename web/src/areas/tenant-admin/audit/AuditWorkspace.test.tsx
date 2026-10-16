import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { auditAdmin, auditEvent, auditFixture, auditId, auditUser } from '../../../test/auditFixture'
import { fakeAuth, resourceId, tenantA, tenantB } from '../../../test/adminFixture'
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } })
afterEach(() => vi.unstubAllGlobals())
it('shows capability-gated navigation and the real read-only table', async () => {
  const f = auditFixture()
  expect(await screen.findByRole('link', { name: 'Audit' })).toBeInTheDocument()
  const table = await screen.findByRole('table', { name: 'Administrative Audit events' })
  expect(within(table).getByText(`User ${auditUser}`)).toBeInTheDocument()
  expect(within(table).getByText('Customer Updated')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /undo|restore|delete|edit/i })).not.toBeInTheDocument()
  expect(f.requests.every(r => r.method === 'GET')).toBe(true)
  expect(localStorage.length).toBe(0); expect(sessionStorage.length).toBe(0)
})
it.each(['missing capability', 'TRANSPORT_OPERATIONS', 'CUSTOMER_DISPATCHER', 'DRIVER', 'YARD_SPOTTER', 'SUSPENDED', 'DISABLED', 'inactive Tenant'])('does not load Audit for %s, including direct URLs', async condition => {
  const m = auditAdmin()
  if (condition === 'missing capability') m.capabilities = []
  else if (condition === 'SUSPENDED' || condition === 'DISABLED') m.status = condition
  else if (condition === 'inactive Tenant') m.tenantActive = false
  else m.roles[0]!.roleCode = condition
  const f = auditFixture(`/admin/audit/${auditId}`, [m])
  await screen.findByRole('heading', { name: /access is not available/ })
  expect(screen.queryByRole('link', { name: 'Audit' })).not.toBeInTheDocument()
  expect(f.requests.some(r => r.path.includes('/audit-events'))).toBe(false)
})
it('requires the shared Firebase session', async () => {
  const f = auditFixture('/admin/audit', [auditAdmin()], fakeAuth(null))
  await screen.findByRole('button', { name: 'Sign in' }); expect(f.requests).toHaveLength(0)
})
it('renders historical actor, exact time, null reason and a readable before/after comparison', async () => {
  auditFixture(`/admin/audit/${auditId}`)
  await screen.findByRole('heading', { name: 'Audit event detail' })
  expect(screen.getByText(`User ${auditUser}`)).toBeInTheDocument()
  expect(screen.getByText(/even after access is disabled/)).toBeInTheDocument()
  const table = screen.getByRole('table', { name: 'Recorded field comparison' })
  expect(within(table).getByText('Old Customer')).toBeInTheDocument(); expect(within(table).getByText('New Customer')).toBeInTheDocument()
  expect(screen.getByText('No reason recorded')).toBeInTheDocument()
  expect(screen.getByText('2026-10-16T18:00:00Z')).toBeInTheDocument()
  expect(document.querySelector('time')?.getAttribute('dateTime')).toBe('2026-10-16T18:00:00Z')
})
it.each(['SYSTEM', 'INTEGRATION'])('renders %s with no invented human actor', async type => {
  const f = auditFixture(`/admin/audit/${auditId}`)
  f.handler(async () => json({ ...auditEvent(), actor: { type, userId: null, membershipId: null }, sourceType: type === 'SYSTEM' ? 'SYSTEM_PROCESS' : 'EXTERNAL_INTEGRATION' }))
  await screen.findByRole('heading', { name: 'Audit event detail' })
  expect(screen.queryByText(`User ${auditUser}`)).not.toBeInTheDocument()
  expect(screen.getByText('Not applicable')).toBeInTheDocument()
})
it('shows creation, absent fields, explicit null and safe text without HTML execution', async () => {
  const f = auditFixture(`/admin/audit/${auditId}`)
  f.handler(async () => json({ ...auditEvent(), actionCode: 'CUSTOMER_CREATED', beforeData: null, afterData: { name: '<script>synthetic</script>', version: 0 } }))
  await screen.findByText('This creation has no previous snapshot.')
  expect(screen.getByText('<script>synthetic</script>')).toBeInTheDocument()
  expect(screen.getAllByText('Not recorded (field absent)')).toHaveLength(2)
  expect(document.querySelector('script')).toBeNull()
})
it('shows withheld sensitive edits when presence did not change', async () => {
  const f = auditFixture(`/admin/audit/${auditId}`)
  f.handler(async () => json({ ...auditEvent(), entityType: 'TRAILER', actionCode: 'TRAILER_UPDATED', beforeData: { capabilityNotesPresent: true, redactedChanges: [], version: 2 }, afterData: { capabilityNotesPresent: true, redactedChanges: ['capabilityNotes'], version: 3 } }))
  await screen.findByText('Sensitive field changed — value withheld')
  expect(screen.getByRole('row', { name: /Capability Notes Sensitive field/ })).toHaveTextContent('Withheld')
})
it('applies exact filters in the URL and request, then starts a new first page', async () => {
  const u = userEvent.setup(), f = auditFixture()
  await screen.findByRole('table')
  await u.type(screen.getByLabelText('Actor User ID'), auditUser)
  await u.selectOptions(screen.getByLabelText('Action code'), 'CUSTOMER_UPDATED')
  await u.type(screen.getByLabelText('From (inclusive, RFC3339)'), '2026-10-16T13:00:00.123-05:00')
  await u.click(screen.getByRole('button', { name: 'Apply filters' }))
  await waitFor(() => expect(f.requests.at(-1)?.path).toContain('actorUserId=' + auditUser))
  expect(new URLSearchParams(f.router.state.location.search).get('from')).toBe('2026-10-16T13:00:00.123-05:00')
  expect(new URL('https://test' + f.requests.at(-1)!.path).searchParams.has('cursor')).toBe(false)
})
it.each(['?actorUserId=invalid', '?from=2026-10-17T00:00:00Z&to=2026-10-16T00:00:00Z', '?actorUserId=x&actorUserId=y'])('rejects invalid URL filters before HTTP %s', async search => {
  const f = auditFixture('/admin/audit' + search)
  await screen.findByRole('alert'); expect(f.requests.some(r => r.path.includes('/audit-events'))).toBe(false)
})
it('rejects invalid form filters without replacing the valid query', async () => {
  const u = userEvent.setup(), f = auditFixture(); await screen.findByRole('table')
  await u.type(screen.getByLabelText('Entity ID'), 'wrong')
  await u.click(screen.getByRole('button', { name: 'Apply filters' }))
  expect(screen.getByRole('alert')).toHaveTextContent('Check the UUIDs')
  expect(f.requests.filter(r => r.path.includes('/audit-events'))).toHaveLength(1)
})
it('loads the next real cursor and detects a repeated cursor without looping', async () => {
  const u = userEvent.setup(), f = auditFixture()
  f.handler(async url => json({ items: [{ ...auditEvent(), id: url.searchParams.has('cursor') ? resourceId : auditId }], nextCursor: 'nextPage' }))
  await u.click(await screen.findByRole('button', { name: 'Load more Audit events' }))
  await screen.findByRole('alert')
  expect(f.requests.filter(r => r.path.includes('/audit-events'))).toHaveLength(2)
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})
it('loads several pages without inventing a total and resets on filter change', async () => {
  const u = userEvent.setup(), f = auditFixture()
  f.handler(async url => json({ items: [{ ...auditEvent(), id: url.searchParams.has('cursor') ? resourceId : auditId }], nextCursor: url.searchParams.has('cursor') ? null : 'nextPage' }))
  await u.click(await screen.findByRole('button', { name: 'Load more Audit events' }))
  await waitFor(() => expect(screen.getAllByRole('link', { name: /View Audit event/ })).toHaveLength(2))
  expect(screen.queryByRole('button', { name: 'Load more Audit events' })).not.toBeInTheDocument()
  await u.selectOptions(screen.getByLabelText('Entity type'), 'CUSTOMER'); await u.click(screen.getByRole('button', { name: 'Apply filters' }))
  await waitFor(() => expect(screen.getAllByRole('link', { name: /View Audit event/ })).toHaveLength(1))
  expect(f.requests.at(-1)?.path).not.toContain('cursor=')
})
it.each([401, 403, 404, 500])('handles %s safely and never renders prior detail after failed refresh', async status => {
  const u = userEvent.setup(), f = auditFixture(`/admin/audit/${auditId}`)
  await screen.findByText('Old Customer')
  f.handler(async () => json({ detail: 'synthetic-protected-other-Tenant' }, status))
  await u.click(screen.getByRole('button', { name: 'Refresh event' }))
  if (status === 401) await screen.findByRole('button', { name: 'Sign in' }); else await screen.findByRole('alert')
  expect(screen.queryByText('Old Customer')).not.toBeInTheDocument()
  expect(screen.queryByText('synthetic-protected-other-Tenant')).not.toBeInTheDocument()
})
it('rejects a malformed HTTP 200 and a mismatched detail ID', async () => {
  const f = auditFixture(`/admin/audit/${auditId}`)
  f.handler(async () => json({ ...auditEvent(), id: resourceId }))
  await screen.findByRole('alert'); expect(screen.queryByRole('table')).not.toBeInTheDocument()
})
it('retains no private data on logout during a late response', async () => {
  const u = userEvent.setup(), f = auditFixture()
  let finish!: (r: Response) => void
  f.handler(() => new Promise(resolve => { finish = resolve }))
  await waitFor(() => expect(finish).toBeDefined())
  const request = f.requests.find(r => r.path.includes('/audit-events'))!
  await u.click(screen.getByRole('button', { name: 'Sign out' }))
  await act(async () => finish(json({ items: [auditEvent()], nextCursor: null })))
  expect(request.signal?.aborted).toBe(true)
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  expect(f.cache.getQueryCache().getAll()).toHaveLength(0)
})
it('fences Tenant changes and clears private old-Tenant cache', async () => {
  const u = userEvent.setup(), f = auditFixture('/admin/audit', [auditAdmin(tenantA, 'Alpha'), auditAdmin(tenantB, 'Beta')])
  await u.selectOptions(await screen.findByLabelText('Active Tenant'), tenantA)
  await act(async () => f.router.navigate('/admin/audit'))
  let finish!: (r: Response) => void
  f.handler(() => new Promise(resolve => { finish = resolve }))
  // Restart a read after the first result and keep its network response pending.
  await screen.findByRole('button', { name: 'Refresh Audit' })
  await waitFor(() => expect(f.requests.some(r => r.path.includes('/audit-events'))).toBe(true))
  if (!finish) await u.click(screen.getByRole('button', { name: 'Refresh Audit' }))
  await waitFor(() => expect(finish).toBeDefined())
  const request = f.requests.filter(r => r.path.includes('/audit-events')).at(-1)!
  await u.selectOptions(screen.getByLabelText('Active Tenant'), tenantB)
  await act(async () => finish(json({ items: [auditEvent()], nextCursor: null })))
  expect(request.signal?.aborted).toBe(true)
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  expect(f.cache.getQueryCache().getAll().some(q => q.queryKey[0] === 'audit' && q.queryKey.includes(tenantA))).toBe(false)
})
it('removes Audit when a membership/capability is revoked during the session', async () => {
  const f = auditFixture(); await screen.findByRole('table')
  f.memberships[0]!.capabilities = []
  await act(async () => f.cache.invalidateQueries({ queryKey: ['me'] }))
  await screen.findByRole('heading', { name: 'Audit access is not available' })
  await waitFor(() => expect(f.cache.getQueryCache().getAll().some(q => q.queryKey[0] === 'audit')).toBe(false))
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})
it('provides labelled keyboard navigation and an honest empty state', async () => {
  const u = userEvent.setup(), f = auditFixture()
  f.handler(async () => json({ items: [], nextCursor: null }))
  await screen.findByText('No administrative changes match these filters.')
  screen.getByLabelText('Actor User ID').focus(); await u.tab()
  expect(screen.getByLabelText('Action code')).toHaveFocus()
  expect(screen.getByRole('form', { name: 'Audit filters' })).toBeInTheDocument()
})
