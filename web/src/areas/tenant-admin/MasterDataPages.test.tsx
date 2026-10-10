import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { adminFixture, resourceId, siteId, tenantA } from '../../test/adminFixture'
import type { ResourceKind } from './resources'

afterEach(() => vi.unstubAllGlobals())
it.each([
  ['customers', 'Customer', 'Name', 'New Customer'],
  ['sites', 'Physical Site', 'Name', 'New Physical Site'],
  ['drivers', 'Driver', 'Name', 'New Driver'],
  ['tractors', 'Tractor', 'Operational number', 'New Tractor'],
  ['trailers', 'Trailer', 'Operational number', 'New Trailer'],
] as const)('%s supports create, detail, edit, confirmed deactivation and explicit reactivation without hard delete', async (kind, singular, label, button) => {
  const user = userEvent.setup()
  const f = adminFixture(`/admin/${kind === 'sites' ? 'locations' : kind}`)
  await user.click(await screen.findByRole('button', { name: button }))
  const create = within(screen.getByRole('form', { name: `Create ${singular}` }))
  await user.type(create.getByLabelText(label), 'New record')
  if (kind === 'sites') await user.type(create.getByLabelText('Timezone'), 'America/Chicago')
  await user.click(create.getByRole('button', { name: 'Create' }))
  await user.click(await screen.findByRole('link', { name: 'New record' }))
  const edit = within(await screen.findByRole('form', { name: `Edit ${singular}` }))
  await user.clear(edit.getByLabelText(label)); await user.type(edit.getByLabelText(label), 'Edited record')
  await user.click(edit.getByRole('button', { name: 'Save changes' }))
  await screen.findByRole('heading', { name: 'Edited record' })
  await user.click(edit.getByRole('button', { name: 'Deactivate' }))
  const confirmation = within(screen.getByRole('dialog'))
  expect(confirmation.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  await user.click(confirmation.getByRole('button', { name: 'Deactivate' }))
  await user.click(await screen.findByRole('button', { name: 'Reactivate' }))
  await waitFor(() => expect(f.requests.filter(r => r.method === 'PATCH')).toHaveLength(3))
  const patches = f.requests.filter(r => r.method === 'PATCH')
  expect(patches[0]!.body).toHaveProperty('expectedVersion', 0)
  expect(patches[1]!.body).toEqual({ expectedVersion: 1, ...(kind === 'customers' ? { status: 'INACTIVE' } : { active: false }) })
  expect(patches[2]!.body).toEqual({ expectedVersion: 2, ...(kind === 'customers' ? { status: 'ACTIVE' } : { active: true }) })
  expect(f.requests.some(r => r.method === 'DELETE')).toBe(false)
})
it('edits independently nullable Site coordinates/address without inventing paired-coordinate rules', async () => {
  const user = userEvent.setup(); const f = adminFixture(`/admin/sites/${siteId}`)
  const form = within(await screen.findByRole('form', { name: 'Edit Physical Site' }))
  await user.type(form.getByLabelText('Latitude'), '40.5')
  await user.clear(form.getByLabelText('Address line 1'))
  await user.click(form.getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(f.requests.find(r => r.method === 'PATCH')?.body).toEqual({ expectedVersion: 0, latitude: 40.5, address: { line1: null } }))
})
it('Operational Locations remain inside the original Site and support create/edit/deactivate/reactivate with Customer context', async () => {
  const user = userEvent.setup(); const f = adminFixture(`/admin/sites/${siteId}`)
  await user.click(await screen.findByRole('button', { name: 'New Operational Location' }))
  const form = within(screen.getByRole('form', { name: 'Create Operational Location' }))
  await user.type(form.getByLabelText('Name'), 'Dock 1')
  await user.selectOptions(form.getByLabelText('Location type'), 'DOCK')
  await user.click(form.getByRole('button', { name: 'Create' }))
  await user.click(await screen.findByRole('button', { name: 'Dock 1' }))
  const edit = within(await screen.findByRole('form', { name: 'Edit Operational Location' }))
  await user.type(edit.getByLabelText('Code'), 'D1'); await user.click(edit.getByRole('button', { name: 'Save changes' }))
  await screen.findByText('Saved.')
  await user.click(edit.getByRole('button', { name: 'Deactivate' })); await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Deactivate' }))
  await user.click(await screen.findByRole('button', { name: 'Reactivate' }))
  await waitFor(() => expect(f.requests.filter(r => r.method === 'PATCH' && r.path.includes('/locations/'))).toHaveLength(3))
  expect(f.requests.find(r => r.method === 'POST')?.path).toBe(`/api/v1/tenants/${tenantA}/sites/${siteId}/locations`)
  for (const request of f.requests.filter(r => r.method === 'PATCH')) expect(request.body).not.toHaveProperty('siteId')
})
it('Trailer generic PATCH cannot send designation or physical state, and designation action is disabled', async () => {
  const user = userEvent.setup(); const f = adminFixture(`/admin/trailers/${resourceId}`)
  const form = within(await screen.findByRole('form', { name: 'Edit Trailer' }))
  expect(form.queryByLabelText('Designated Customer')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Change designated Customer' })).toBeDisabled()
  expect(screen.getByText(/Audited designation changes are supported by the backend/)).toBeInTheDocument()
  expect(screen.queryByText(/workflow, which is not available yet/)).not.toBeInTheDocument()
  await user.type(form.getByLabelText('Capability notes'), 'Preserved note')
  await user.click(form.getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(f.requests.find(r => r.method === 'PATCH')?.body).toEqual({ expectedVersion: 0, capabilityNotes: 'Preserved note' }))
  const body = f.requests.find(r => r.method === 'PATCH')!.body!
  for (const key of ['designatedCustomerId', 'locationState', 'currentLocationId', 'lastKnownLocationId', 'lastSeenAt', 'lastDriverId', 'loadState', 'serviceabilityState']) expect(body).not.toHaveProperty(key)
})
it('Driver displays linked account and unlinks only through the supported expectedVersion command', async () => {
  const user = userEvent.setup(); const f = adminFixture(`/admin/drivers/${resourceId}`)
  f.rows.drivers![0]!.userId = '00000000-0000-4000-8000-000000000009'
  expect(await screen.findByText('Linked account')).toBeInTheDocument()
  expect(screen.queryByLabelText('User ID')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Unlink account' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Unlink' }))
  expect(await screen.findByText('No linked account')).toBeInTheDocument()
  expect(f.requests.find(r => r.method === 'DELETE')?.body).toEqual({ expectedVersion: 0 })
})
it('Customer association removal uses only a server-confirmed exact pair; no invented GET directory', async () => {
  const user = userEvent.setup(); const f = adminFixture(`/admin/customers/00000000-0000-4000-8000-000000000004`)
  await screen.findByLabelText('Physical Site')
  await waitFor(() => expect(screen.getByRole('option', { name: 'Original sites' })).toBeInTheDocument())
  await user.selectOptions(screen.getByLabelText('Physical Site'), siteId)
  await user.click(screen.getByRole('button', { name: 'Associate Site' }))
  await user.click(await screen.findByRole('button', { name: 'Remove association' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Remove association' }))
  await waitFor(() => expect(f.requests.some(r => r.method === 'DELETE' && r.path.endsWith(`/sites/${siteId}`))).toBe(true))
  expect(f.requests.some(r => r.method === 'GET' && /customers\/[^/]+\/sites/.test(r.path))).toBe(false)
})
it.each(['customers', 'drivers', 'tractors', 'trailers'] as ResourceKind[])('%s rejects blank names/numbers before transport', async kind => {
  const user = userEvent.setup(); const f = adminFixture(`/admin/${kind}`)
  await user.click(await screen.findByRole('button', { name: `New ${kind === 'customers' ? 'Customer' : kind === 'drivers' ? 'Driver' : kind === 'tractors' ? 'Tractor' : 'Trailer'}` }))
  await user.click(screen.getByRole('button', { name: 'Create' }))
  expect(await screen.findByRole('alert')).toBeInTheDocument()
  expect(f.requests.some(r => r.method === 'POST')).toBe(false)
})
