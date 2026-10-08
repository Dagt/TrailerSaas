import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { adminFixture, resourceId } from '../../test/adminFixture'

afterEach(() => vi.unstubAllGlobals())
it('Settings exposes only threshold/timezone and nullable PATCH; no retention controls', async () => {
  const user = userEvent.setup(); const f = adminFixture('/admin/settings')
  const form = within(await screen.findByRole('form', { name: 'Edit Settings' }))
  expect(form.getByLabelText('Stale Trailer threshold (hours)')).toHaveValue(72)
  expect(screen.queryByText(/retention/i)).not.toBeInTheDocument()
  await user.clear(form.getByLabelText('Stale Trailer threshold (hours)')); await user.type(form.getByLabelText('Stale Trailer threshold (hours)'), '96')
  await user.type(form.getByLabelText('Default timezone'), 'America/Chicago')
  await user.click(form.getByRole('button', { name: 'Save changes' }))
  await screen.findByText('Saved.')
  expect(f.requests.find(r => r.method === 'PATCH')?.body).toEqual({ expectedVersion: 0, staleTrailerThresholdHours: 96, defaultTimezone: 'America/Chicago' })
  await user.clear(form.getByLabelText('Default timezone')); await user.click(form.getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(f.requests.filter(r => r.method === 'PATCH')[1]?.body).toEqual({ expectedVersion: 1, defaultTimezone: null }))
})
it('STALE_VERSION never auto-replays; explicit Reload latest resets baseline before the user can save again', async () => {
  const user = userEvent.setup(); const f = adminFixture(`/admin/tractors/${resourceId}`)
  const form = within(await screen.findByRole('form', { name: 'Edit Tractor' }))
  await user.clear(form.getByLabelText('Operational number')); await user.type(form.getByLabelText('Operational number'), 'Edited')
  f.fail({ status: 409, code: 'STALE_VERSION' })
  await user.click(form.getByRole('button', { name: 'Save changes' }))
  expect(await screen.findByText('This record changed while you were editing it. Review the latest version before saving again.')).toBeInTheDocument()
  expect(form.getByRole('button', { name: 'Save changes' })).toBeDisabled()
  expect(f.requests.filter(r => r.method === 'PATCH')).toHaveLength(1)
  f.rows.tractors![0]!.version = 1; f.rows.tractors![0]!.operationalNumber = 'Latest'
  await user.click(screen.getByRole('button', { name: 'Reload latest' }))
  await waitFor(() => expect(form.getByLabelText('Operational number')).toHaveValue('Latest'))
  expect(f.requests.filter(r => r.method === 'PATCH')).toHaveLength(1)
  await user.clear(form.getByLabelText('Operational number')); await user.type(form.getByLabelText('Operational number'), 'Reviewed change')
  await user.click(form.getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(f.requests.filter(r => r.method === 'PATCH')[1]?.body).toEqual({ expectedVersion: 1, operationalNumber: 'Reviewed change' }))
})
it('Settings uses the same explicit stale-version reload flow', async () => {
  const user = userEvent.setup(); const f = adminFixture('/admin/settings')
  const form = within(await screen.findByRole('form', { name: 'Edit Settings' }))
  f.fail({ status: 409, code: 'STALE_VERSION' }); await user.click(form.getByRole('button', { name: 'Save changes' }))
  await screen.findByRole('button', { name: 'Reload latest' })
  f.settings.version = 4; f.settings.staleTrailerThresholdHours = 48
  await user.click(screen.getByRole('button', { name: 'Reload latest' }))
  await waitFor(() => expect(form.getByLabelText('Stale Trailer threshold (hours)')).toHaveValue(48))
  expect(f.requests.filter(r => r.method === 'PATCH')).toHaveLength(1)
})
it.each([
  [409, 'RESOURCE_CONFLICT', 'This change conflicts with existing data.'],
  [403, 'TENANT_ACCESS_DENIED', 'You do not have access to this action.'],
  [403, 'CAPABILITY_DENIED', 'You do not have access to this action.'],
  [403, 'CUSTOMER_SCOPE_DENIED', 'You do not have access to this action.'],
  [404, 'RESOURCE_NOT_FOUND', 'The resource was not found.'],
  [500, undefined, 'The service is temporarily unavailable.'],
] as const)('safe Problem UX for %s/%s', async (status, code, message) => {
  const user = userEvent.setup(); const f = adminFixture('/admin/settings')
  const form = within(await screen.findByRole('form', { name: 'Edit Settings' }))
  f.fail({ status, code, traceId: 'support-trace-1' })
  await user.click(form.getByRole('button', { name: 'Save changes' }))
  expect(await screen.findByText(new RegExp(message.replace('.', '\\.')))).toBeInTheDocument()
  expect(screen.getByText('Support reference: support-trace-1')).toBeInTheDocument()
  expect(screen.queryByText(/SQL|constraint|synthetic-secret/)).not.toBeInTheDocument()
})
it('VALIDATION_FAILED maps known fields onto labelled inputs', async () => {
  const user = userEvent.setup(); const f = adminFixture('/admin/settings')
  const form = within(await screen.findByRole('form', { name: 'Edit Settings' }))
  f.fail({ status: 400, code: 'VALIDATION_FAILED', errors: [{ field: 'defaultTimezone', message: 'must be a valid value' }] })
  await user.click(form.getByRole('button', { name: 'Save changes' }))
  expect(await screen.findByText('must be a valid value')).toBeInTheDocument()
  expect(form.getByLabelText('Default timezone')).toHaveAttribute('aria-invalid', 'true')
})
it('401 closes the Admin workspace and offers safe session revalidation without replaying PATCH', async () => {
  const user = userEvent.setup(); const f = adminFixture('/admin/settings')
  const form = within(await screen.findByRole('form', { name: 'Edit Settings' }))
  f.fail({ status: 401, code: 'AUTHENTICATION_REQUIRED' })
  await user.click(form.getByRole('button', { name: 'Save changes' }))
  expect(await screen.findByRole('button', { name: 'Refresh session' })).toBeInTheDocument()
  expect(screen.queryByRole('form', { name: 'Edit Settings' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Refresh session' }))
  await screen.findByRole('form', { name: 'Edit Settings' })
  expect(f.auth.token).toHaveBeenCalledWith(true)
  expect(f.requests.filter(r => r.method === 'PATCH')).toHaveLength(1)
})

it('safe 5xx offers an explicit retry and never replays the mutation automatically', async () => {
  const user = userEvent.setup(); const f = adminFixture('/admin/settings')
  const form = within(await screen.findByRole('form', { name: 'Edit Settings' }))
  f.fail({ status: 503, traceId: 'support-trace' })
  await user.click(form.getByRole('button', { name: 'Save changes' }))
  await screen.findByRole('button', { name: 'Retry' })
  expect(f.requests.filter(r => r.method === 'PATCH')).toHaveLength(1)
  await user.click(screen.getByRole('button', { name: 'Retry' }))
  await screen.findByText('Saved.')
  expect(f.requests.filter(r => r.method === 'PATCH')).toHaveLength(2)
})

it('empty master-data lists retain the create form and show a real empty state', async () => {
  const user = userEvent.setup(); const f = adminFixture('/admin/customers'); f.rows.customers = []
  expect(screen.getByRole('status')).toHaveTextContent('Loading')
  await screen.findByText('No customer records found.')
  await user.click(screen.getByRole('button', { name: 'New Customer' }))
  await screen.findByRole('form', { name: 'Create Customer' })
  expect(screen.getByText('No customer records found.')).toBeInTheDocument()
})

it('list infrastructure errors have a support reference and an explicit read retry', async () => {
  const user = userEvent.setup(); const f = adminFixture('/admin/tractors')
  const original = f.fetcher.getMockImplementation()!
  let failed = false
  f.fetcher.mockImplementation(async (input, options) => {
    if (String(input).endsWith('/tractors') && !failed) {
      failed = true
      return new Response(JSON.stringify({ traceId: 'read-trace' }), { status: 503, headers: { 'Content-Type': 'application/problem+json' } })
    }
    return original(input, options)
  })
  await screen.findByText('Support reference: read-trace')
  await user.click(screen.getByRole('button', { name: 'Retry' }))
  await screen.findByRole('link', { name: 'T114' })
})
