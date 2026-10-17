import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { admin, adminFixture, fakeAuth, resourceId } from '../test/adminFixture'
import { operationsFixture, operationsMembership } from '../test/operationsFixture'
import { customerFixture } from '../test/customerFixture'

afterEach(() => vi.unstubAllGlobals())
it('provides a focusable main landmark and skip link on the real AppShell', async () => {
  adminFixture('/admin')
  await screen.findByRole('heading', { name: 'Tenant Admin' })
  const main = screen.getByRole('main')
  expect(main).toHaveAttribute('id', 'main-content')
  expect(main).toHaveAttribute('tabindex', '-1')
  expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main-content')
})
it.each([
  ['/admin/customers', 'Customers'], ['/admin/locations', 'Locations'], ['/admin/drivers', 'Drivers'],
  ['/admin/tractors', 'Tractors'], ['/admin/trailers', 'Trailers'], ['/admin/settings', 'Settings'],
])('keeps Admin deep entry %s active and preserves URL history', async (path, label) => {
  const u = userEvent.setup(), f = adminFixture(path)
  const nav = await screen.findByRole('navigation', { name: 'Admin navigation' })
  expect(within(nav).getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page')
  await u.click(within(nav).getByRole('link', { name: 'Overview' }))
  expect(f.router.state.location.pathname).toBe('/admin')
  await act(async () => f.router.navigate(-1))
  expect(within(nav).getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page')
  await act(async () => f.router.navigate(1))
  expect(within(nav).getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page')
})
it.each([[], ['ALERT_VIEW'], ['AUDIT_VIEW'], ['ALERT_VIEW', 'AUDIT_VIEW']].map(capabilities => ({ capabilities })))('supplies only authorized Admin links for $capabilities', async ({ capabilities }) => {
  adminFixture('/admin', [{ ...admin(), tenantActive: true, capabilities }])
  const nav = await screen.findByRole('navigation', { name: 'Admin navigation' })
  for (const [label, capability] of [['Alerts', 'ALERT_VIEW'], ['Audit', 'AUDIT_VIEW']]) {
    expect(!!within(nav).queryByRole('link', { name: label })).toBe(capabilities.includes(capability!))
  }
})
it.each([false, true])('preserves Operations Alerts capability visibility: %s', async authorized => {
  operationsFixture('/operations/trips', { memberships: [{ ...operationsMembership(), capabilities: authorized ? ['ALERT_VIEW'] : [] }] })
  const nav = await screen.findByRole('navigation', { name: 'Operations navigation' })
  expect(within(nav).getByRole('link', { name: 'Trips' })).toHaveAttribute('aria-current', 'page')
  expect(!!within(nav).queryByRole('link', { name: 'Alerts' })).toBe(authorized)
  expect(within(nav).queryByRole('link', { name: 'Settings' })).not.toBeInTheDocument()
})
it.each(['/customer/orders', '/customer/trailers', '/customer/deliveries', '/customer/documents'])('keeps Customer direct URL %s isolated', async path => {
  customerFixture(path)
  const nav = await screen.findByRole('navigation', { name: 'Customer navigation' })
  const active = within(nav).getAllByRole('link').filter(link => link.getAttribute('aria-current') === 'page')
  expect(active).toHaveLength(1)
  expect(active[0]).toHaveAttribute('href', path)
  expect(within(nav).getAllByRole('link').map(link => link.textContent)).toEqual(['Dashboard','Orders','Trailers','Deliveries','Documents'])
  expect(screen.queryByRole('link', { name: 'Tenant Admin' })).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Transport Operations' })).not.toBeInTheDocument()
})
it('shows the real unconfigured sign-in state without fabricated authentication', async () => {
  const auth = fakeAuth(null); auth.configured = false
  const f = adminFixture('/admin', [], auth)
  expect(await screen.findByText('Web sign-in is not configured. Contact your administrator.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Sign in' })).not.toBeInTheDocument()
  expect(f.requests).toHaveLength(0)
})
it('connects invalid sign-in fields to their readable error', async () => {
  adminFixture('/admin', [], fakeAuth(null))
  await userEvent.setup().click(await screen.findByRole('button', { name: 'Sign in' }))
  const email = screen.getByRole('textbox', { name: 'Email' })
  expect(email).toHaveAttribute('aria-invalid', 'true')
  expect(document.getElementById(email.getAttribute('aria-describedby')!)).toHaveTextContent('Enter an email and password.')
})
it('disables the primary sign-in action while authentication is pending and reports failure', async () => {
  const u = userEvent.setup(), auth = fakeAuth(null)
  let reject!: (reason: Error) => void
  vi.mocked(auth.signIn).mockImplementation(() => new Promise((_resolve, fail) => { reject = fail }))
  adminFixture('/admin', [], auth)
  await u.type(await screen.findByLabelText('Email'), 'user@example.test')
  await u.type(screen.getByLabelText('Password'), 'synthetic-password')
  await u.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(screen.getByRole('button', { name: 'Sign in' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Sign in' })).toHaveAttribute('aria-busy', 'true')
  await act(async () => reject(new Error('provider unavailable')))
  expect(await screen.findByRole('alert')).toHaveTextContent('Sign-in could not be completed')
  expect(screen.getByLabelText('Password')).toHaveValue('')
})
it('distinguishes an expired session and uses the existing refresh session boundary', async () => {
  const u = userEvent.setup(), f = adminFixture('/admin')
  await screen.findByRole('navigation', { name: 'Admin navigation' })
  f.cache.setQueryData(['tenant', 'private'], { id: resourceId })
  f.fetcher.mockResolvedValueOnce(new Response(JSON.stringify({ status: 401 }), { status: 401, headers: { 'Content-Type': 'application/problem+json' } }))
  await act(async () => { await f.cache.invalidateQueries({ queryKey: ['me'] }) })
  expect(await screen.findByRole('status')).toHaveTextContent('Your session has expired')
  await u.click(screen.getByRole('button', { name: 'Refresh session' }))
  await screen.findByRole('navigation', { name: 'Admin navigation' })
  expect(f.auth.token).toHaveBeenCalledWith(true)
  expect(f.cache.getQueryData(['tenant', 'private'])).toBeUndefined()
})
