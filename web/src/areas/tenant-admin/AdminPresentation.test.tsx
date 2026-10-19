import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { admin, adminFixture, customerId, resourceId, siteId } from '../../test/adminFixture'
import { auditFixture, auditId } from '../../test/auditFixture'
import { AdminStatus } from './AdminPresentation'
import { definitions } from './resources'
import { createAdminReviewApi, demoId } from '../../dev/tenant-admin-fixtures'

afterEach(() => vi.unstubAllGlobals())

it.each(['customers', 'locations', 'drivers', 'tractors', 'trailers'] as const)('presents %s with one primary create action and a named scrollable table', async kind => {
  adminFixture(`/admin/${kind}`)
  const table = await screen.findByRole('table', { name: definitions[kind === 'locations' ? 'sites' : kind].title })
  const create = screen.getByRole('button', { name: /^New / })
  expect(create).toHaveClass('ui-button--primary')
  expect(table.closest('[role="region"]')).toHaveAttribute('tabindex', '0')
  expect(table.closest('.admin-table-panel')).toBeInTheDocument()
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
})

it.each([[], ['ALERT_VIEW'], ['AUDIT_VIEW'], ['ALERT_VIEW','AUDIT_VIEW']].map(capabilities => ({ capabilities })))('keeps Overview quick links within $capabilities', async ({ capabilities }) => {
  const f = adminFixture('/admin', [{ ...admin(), capabilities, tenantActive: true }])
  await screen.findByRole('heading', { name: 'Master data' })
  expect(!!screen.queryByRole('link', { name: 'Manage Alerts' })).toBe(capabilities.includes('ALERT_VIEW'))
  expect(!!screen.queryByRole('link', { name: 'Manage Audit' })).toBe(capabilities.includes('AUDIT_VIEW'))
  expect(screen.getByRole('link', { name: 'Manage Locations' })).toHaveAttribute('href', '/admin/locations')
  expect(f.requests.every(r => r.path === '/api/v1/me')).toBe(true)
})

it('does not advertise Audit from Overview for an inactive Tenant', async () => {
  adminFixture('/admin', [{ ...admin(), tenantActive: false, capabilities: ['AUDIT_VIEW'] }])
  await screen.findByRole('heading', { name: 'Tenant controls' })
  expect(screen.queryByRole('link', { name: 'Manage Audit' })).not.toBeInTheDocument()
})

it('groups all existing Site fields once and links help plus validation without changing the write', async () => {
  const u = userEvent.setup(), f = adminFixture(`/admin/sites/${siteId}`)
  const form = await screen.findByRole('form', { name: 'Edit Physical Site' })
  for (const name of ['Facility details','Business address','Geofence configuration']) expect(within(form).getByRole('group', { name })).toBeInTheDocument()
  for (const field of definitions.sites.fields) expect(within(form).getAllByLabelText(field.label)).toHaveLength(1)
  const timezone = within(form).getByLabelText('Timezone')
  expect(timezone).toHaveAccessibleDescription('Use an IANA timezone, such as America/Chicago.')
  await u.clear(timezone); await u.type(timezone, 'invalid-zone')
  await u.click(within(form).getByRole('button', { name: 'Save changes' }))
  expect(timezone).toHaveAttribute('aria-invalid', 'true')
  expect(timezone).toHaveAccessibleDescription(/Use an IANA timezone.*valid IANA timezone/)
  expect(f.requests.every(r => r.method === 'GET')).toBe(true)
})

it('separates a destructive action visually and keeps the reviewed versioned command', async () => {
  const u = userEvent.setup(), f = adminFixture(`/admin/customers/${customerId}`)
  const form = await screen.findByRole('form', { name: 'Edit Customer' })
  const trigger = within(form).getByRole('button', { name: 'Deactivate' })
  expect(trigger).toHaveClass('ui-button--danger')
  await u.click(trigger)
  const dialog = screen.getByRole('dialog', { name: 'Deactivate Customer?' })
  expect(dialog).toHaveClass('admin-dialog')
  await u.click(within(dialog).getByRole('button', { name: 'Deactivate' }))
  await screen.findByText('Saved.')
  expect(f.requests.find(r => r.method === 'PATCH')?.body).toEqual({ expectedVersion: 0, status: 'INACTIVE' })
})

it('keeps Physical Sites and confirmed-only Customer associations explicit', async () => {
  adminFixture(`/admin/customers/${customerId}`)
  await screen.findByRole('heading', { name: 'Customer ↔ Site associations' })
  expect(screen.getByText(/confirmed during this visit; a complete association directory is not available yet/)).toBeInTheDocument()
  expect(await screen.findByRole('button', { name: 'Associate Site' })).toBeDisabled()
})

it('keeps Driver credentials in their existing fields and the account action separate', async () => {
  adminFixture(`/admin/drivers/${resourceId}`)
  const form = await screen.findByRole('form', { name: 'Edit Driver' })
  expect(within(form).getByRole('group', { name: 'License and certificate' })).toBeInTheDocument()
  expect(within(form).getByLabelText('License number')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Driver account' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Link account' })).not.toBeInTheDocument()
})

it.each([['UNKNOWN','unknown'],['LOCATION_UNKNOWN','unknown'],['OUT_OF_SERVICE','attention'],['IN_TRANSIT','pending'],['RESOLVED','success'],['Revoked','neutral']])('labels %s with its semantic tone %s', (value, tone) => {
  render(<AdminStatus value={value!} />)
  expect(screen.getByText(value!)).toHaveAttribute('data-tone', tone)
})

it('organizes the Audit comparison without adding event commands or current-profile lookups', async () => {
  const f = auditFixture(`/admin/audit/${auditId}`)
  const panel = (await screen.findByRole('heading', { name: 'Before / after comparison' })).closest('section')!
  expect(within(panel).getByRole('table', { name: 'Recorded field comparison' })).toBeInTheDocument()
  expect(within(panel).getAllByText('Changed').every(e => e.getAttribute('data-tone') === 'pending')).toBe(true)
  expect(screen.queryByRole('button', { name: /undo|restore|edit|delete/i })).not.toBeInTheDocument()
  expect(f.requests.every(r => r.method === 'GET')).toBe(true)
})

it('keeps the new Admin surface out of Operations Alerts markup', async () => {
  const fixtureApi = createAdminReviewApi(), f = adminFixture('/operations/alerts', [{ ...admin(), capabilities: ['ALERT_VIEW'] }])
  f.fetcher.mockImplementation(async input => {
    const url = new URL(String(input))
    const data = await fixtureApi.request(url.pathname.replace('/api/v1', '') + url.search)
    return new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } })
  })
  const table = await screen.findByRole('table', { name: 'Operational Alerts' })
  expect(document.querySelector('.admin-alerts')).toBeNull()
  expect(document.querySelector('.admin-alert-filters')).toBeNull()
  expect(within(table).getByText('OPEN')).toHaveClass('status-badge')
  expect(within(table).getByText('OPEN')).toHaveAttribute('data-tone', 'attention')
  expect(table.closest('.ops-alerts')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Apply filters' })).not.toHaveAttribute('type')
})

it.each(['POST','PATCH','DELETE'] as const)('the isolated review rejects %s instead of manufacturing an accepted business change', async method => {
  await expect(createAdminReviewApi().request(`/tenants/${demoId(1)}/customers/${demoId(10)}`, { method, body: { name: 'DEMO change' } })).rejects.toMatchObject({ problem: { status: 503, code: 'ISOLATED_REVIEW_READ_ONLY' } })
})

it('does not persist the isolated entry in production and scopes QR print rules', () => {
  const entry = readFileSync('src/main.tsx', 'utf8'), html = readFileSync('index.html', 'utf8'), css = readFileSync('src/areas/tenant-admin/admin.css', 'utf8')
  expect(entry + html).not.toMatch(/tenant-admin-fixtures|dev\/tenant-admin|AuthContext.Provider/)
  expect(css).toContain('body:has(dialog.admin-qr-dialog[open]) > :not(dialog.admin-qr-dialog)')
  expect(css).not.toContain('@page')
  expect(css).not.toMatch(/#[0-9a-f]{3,8}\b/i)
})
