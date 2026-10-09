import { screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { adminFixture, admin, tenantA, tenantB, fakeAuth } from '../../test/adminFixture'

afterEach(() => vi.unstubAllGlobals())
it('restores Firebase session and selects the sole ACTIVE Admin membership from /me', async () => {
  const fixture = adminFixture('/admin')
  expect(await screen.findByRole('navigation', { name: 'Admin navigation' })).toBeInTheDocument()
  expect(screen.getByText('Admin Tenant')).toBeInTheDocument()
  expect(fixture.requests[0]?.path).toBe('/api/v1/me')
  expect(fixture.requests[0]?.headers.get('Authorization')).toBe('Bearer synthetic-secret-firebase-token')
  expect(screen.queryByRole('link', { name: 'Alerts' })).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Audit' })).not.toBeInTheDocument()
})
it('requires selection for multiple Admin Tenants and isolates their queries without a prior-Tenant flash', async () => {
  const user = userEvent.setup()
  const f = adminFixture('/admin', [admin(tenantA, 'Alpha'), admin(tenantB, 'Beta')])
  expect(await screen.findByRole('heading', { name: 'Select a Tenant' })).toBeInTheDocument()
  await user.selectOptions(screen.getByLabelText('Active Tenant'), tenantA)
  await user.click(screen.getByRole('link', { name: 'Customers' }))
  expect(await screen.findByRole('link', { name: 'Original customers' })).toBeInTheDocument()
  expect(f.cache.getQueryData(['tenant', tenantA, '/customers'])).toBeDefined()
  f.rows.customers![0]!.name = 'Beta Customer'
  await user.selectOptions(screen.getByLabelText('Active Tenant'), tenantB)
  expect(screen.queryByText('Original customers')).not.toBeInTheDocument()
  expect(f.cache.getQueryData(['tenant', tenantA, '/customers'])).toBeUndefined()
  await user.click(screen.getByRole('link', { name: 'Customers' }))
  expect(await screen.findByRole('link', { name: 'Beta Customer' })).toBeInTheDocument()
  expect(f.requests.some(r => r.path === `/api/v1/tenants/${tenantB}/customers`)).toBe(true)
})
it.each(['SUSPENDED', 'DISABLED', 'TRANSPORT_OPERATIONS', 'DRIVER', 'no memberships'])('does not present Admin access for %s', async condition => {
  const membership = admin()
  if (condition === 'SUSPENDED' || condition === 'DISABLED') membership.status = condition
  else membership.roles[0]!.roleCode = condition
  const f = adminFixture('/admin/customers', condition === 'no memberships' ? [] : [membership])
  expect(await screen.findByRole('heading', { name: 'Admin access is not available' })).toBeInTheDocument()
  expect(f.requests).toHaveLength(1)
  expect(screen.queryByRole('navigation', { name: 'Admin navigation' })).not.toBeInTheDocument()
})
it('signs in through the injected boundary without storing/logging passwords, then clears Tenant cache on sign-out', async () => {
  const user = userEvent.setup(); const auth = fakeAuth(null)
  const f = adminFixture('/admin', [admin()], auth)
  const logger = vi.spyOn(console, 'log')
  await user.type(await screen.findByLabelText('Email'), 'admin@example.test')
  await user.type(screen.getByLabelText('Password'), 'synthetic-secret-password')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByRole('navigation', { name: 'Admin navigation' })).toBeInTheDocument()
  expect(auth.signIn).toHaveBeenCalledWith('admin@example.test', 'synthetic-secret-password')
  f.cache.setQueryData(['tenant', tenantA, '/customers'], { items: [] })
  await user.click(screen.getByRole('button', { name: 'Sign out' }))
  expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  expect(f.cache.getQueryCache().getAll()).toHaveLength(0)
  expect(logger).not.toHaveBeenCalled()
  expect(localStorage.length).toBe(0)
})
it('rechecks /me when the authenticated identity changes', async () => {
  const f = adminFixture('/admin')
  await screen.findByRole('navigation', { name: 'Admin navigation' })
  await act(async () => f.auth.emit({ uid: 'another-firebase-user' }))
  await waitFor(() => expect(f.requests.filter(r => r.path === '/api/v1/me')).toHaveLength(2))
})
