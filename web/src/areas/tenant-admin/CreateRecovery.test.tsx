import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { adminFixture, siteId } from '../../test/adminFixture'

afterEach(() => vi.unstubAllGlobals())
const creates = [
  ['customers', '/admin/customers', 'Customer', 'Name', '/customers'],
  ['sites', '/admin/locations', 'Physical Site', 'Name', '/sites'],
  ['drivers', '/admin/drivers', 'Driver', 'Name', '/drivers'],
  ['tractors', '/admin/tractors', 'Tractor', 'Operational number', '/tractors'],
  ['trailers', '/admin/trailers', 'Trailer', 'Operational number', '/trailers'],
  ['locations', `/admin/sites/${siteId}`, 'Operational Location', 'Name', `/sites/${siteId}/locations`],
] as const

it.each(creates.flatMap(create => (['5xx', 'lost response'] as const).map(mode => [...create, mode] as const)))(
  '%s create reconciles %s %s %s %s %s without replaying a committed POST', async (kind, route, singular, label, path, mode) => {
    const user = userEvent.setup(); const f = adminFixture(route)
    await user.click(await screen.findByRole('button', { name: `New ${singular}` }))
    const form = within(screen.getByRole('form', { name: `Create ${singular}` }))
    const entered = `Committed ${kind}`
    await user.type(form.getByLabelText(label), entered)
    if (kind === 'sites') await user.type(form.getByLabelText('Timezone'), 'America/Chicago')
    if (kind === 'locations') await user.selectOptions(form.getByLabelText('Location type'), 'DOCK')
    const original = f.fetcher.getMockImplementation()!
    f.fetcher.mockImplementation(async (input, options) => {
      // Simulate the server committing before the client receives an uncertain result.
      const response = await original(input, options)
      if (options?.method === 'POST' && String(input).endsWith(path)) {
        if (mode === 'lost response') throw new Error('synthetic-secret-response-loss')
        return new Response(JSON.stringify({ traceId: 'create-support-trace', detail: 'synthetic-secret-db-detail' }), {
          status: 500, headers: { 'Content-Type': 'application/problem+json' },
        })
      }
      return response
    })
    await user.click(form.getByRole('button', { name: 'Create' }))
    await screen.findByText(/The creation result is uncertain/)
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
    expect(form.getByLabelText(label)).toHaveValue(entered)
    expect(form.getByRole('button', { name: 'Create' })).toBeDisabled()
    if (mode === '5xx') expect(screen.getByText('Support reference: create-support-trace')).toBeInTheDocument()
    expect(screen.queryByText(/synthetic-secret/)).not.toBeInTheDocument()
    const posts = () => f.requests.filter(r => r.method === 'POST')
    expect(posts()).toHaveLength(1)
    expect(posts()[0]!.headers.has('Idempotency-Key')).toBe(false)
    for (let check = 0; check < 2; check++) {
      await user.click(screen.getByRole('button', { name: 'Check latest records' }))
      const table = within(await screen.findByRole('table', { name: kind === 'locations' ? 'Operational Locations' : kind === 'sites' ? 'Locations' : `${singular}s` }))
      expect(await table.findByRole(kind === 'locations' ? 'button' : 'link', { name: entered })).toBeInTheDocument()
      await waitFor(() => expect(screen.getByRole('button', { name: 'Check latest records' })).toBeEnabled())
      expect(posts()).toHaveLength(1)
    }
    expect(f.requests.filter(r => r.method === 'GET' && r.path.endsWith(path)).length).toBeGreaterThanOrEqual(3)
    expect(form.getByLabelText(label)).toHaveValue(entered)
    await user.type(form.getByLabelText(label), '{enter}')
    expect(posts()).toHaveLength(1)
  },
)

it('Site timezone starts empty, is never derived from settings and must be explicitly valid before create', async () => {
  const user = userEvent.setup(); const f = adminFixture('/admin/locations')
  f.settings.defaultTimezone = 'America/Chicago'
  await user.click(await screen.findByRole('button', { name: 'New Physical Site' }))
  const form = within(screen.getByRole('form', { name: 'Create Physical Site' }))
  expect(form.getByLabelText('Timezone')).toHaveValue('')
  await user.type(form.getByLabelText('Name'), 'Explicit Site')
  await user.click(form.getByRole('button', { name: 'Create' }))
  expect(form.getByLabelText('Timezone')).toHaveAttribute('aria-invalid', 'true')
  expect(f.requests.some(r => r.method === 'POST')).toBe(false)
  await user.type(form.getByLabelText('Timezone'), 'Unknown/Zone')
  await user.click(form.getByRole('button', { name: 'Create' }))
  expect(f.requests.some(r => r.method === 'POST')).toBe(false)
  await user.clear(form.getByLabelText('Timezone')); await user.type(form.getByLabelText('Timezone'), 'Europe/Madrid')
  await user.click(form.getByRole('button', { name: 'Create' }))
  await screen.findByRole('link', { name: 'Explicit Site' })
  expect(f.requests.find(r => r.method === 'POST')?.body).toHaveProperty('timezone', 'Europe/Madrid')
  expect(f.requests.some(r => r.path.endsWith('/settings'))).toBe(false)
})

it('Operational Location type starts with an invalid placeholder and requires an explicit selection', async () => {
  const user = userEvent.setup(); const f = adminFixture(`/admin/sites/${siteId}`)
  await user.click(await screen.findByRole('button', { name: 'New Operational Location' }))
  const form = within(screen.getByRole('form', { name: 'Create Operational Location' }))
  expect(form.getByLabelText('Location type')).toHaveValue('')
  expect(form.getByRole('option', { name: 'Choose a location type' })).toHaveValue('')
  expect(form.getByRole('option', { name: 'Choose a location type' })).toBeDisabled()
  await user.type(form.getByLabelText('Name'), 'Explicit Dock')
  await user.click(form.getByRole('button', { name: 'Create' }))
  expect(form.getByLabelText('Location type')).toHaveAttribute('aria-invalid', 'true')
  expect(f.requests.some(r => r.method === 'POST')).toBe(false)
  await user.selectOptions(form.getByLabelText('Location type'), 'DOCK')
  await user.click(form.getByRole('button', { name: 'Create' }))
  await screen.findByRole('button', { name: 'Explicit Dock' })
  expect(f.requests.find(r => r.method === 'POST')?.body).toHaveProperty('type', 'DOCK')
})
