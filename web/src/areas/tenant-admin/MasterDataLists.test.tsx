import { screen, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { adminFixture, sample } from '../../test/adminFixture'

afterEach(() => vi.unstubAllGlobals())
it('Physical Sites list exposes address, timezone, configured geofence and status from each Site DTO', async () => {
  const f = adminFixture('/admin/locations')
  Object.assign(f.rows.sites![0]!, { name: 'Facility', address: { line1: '710 Kimberly Dr', line2: 'Suite 2', city: 'Carol Stream', stateRegion: 'IL', postalCode: '60188', countryCode: 'US' }, timezone: 'America/Chicago', latitude: 0, longitude: 0, geofenceRadiusM: 50, active: true })
  f.rows.sites!.push({ ...sample('sites', '00000000-0000-4000-8000-000000000007'), name: 'Partial geofence', address: null, latitude: 41, longitude: null, geofenceRadiusM: 50, active: false })
  const table = within(await screen.findByRole('table', { name: 'Locations' }))
  expect(table.getAllByRole('columnheader').map(h => h.textContent)).toEqual(['Name', 'Status', 'Address', 'Timezone', 'Geofence configured'])
  const configured = within(table.getByRole('row', { name: /Facility/ }))
  expect(configured.getByText('710 Kimberly Dr, Suite 2, Carol Stream, IL, 60188, US')).toBeInTheDocument()
  expect(configured.getByText('America/Chicago')).toBeInTheDocument()
  expect(configured.getByText('Yes')).toBeInTheDocument(); expect(configured.getByText('Active')).toBeInTheDocument()
  const partial = within(table.getByRole('row', { name: /Partial geofence/ }))
  expect(partial.getByText('No')).toBeInTheDocument(); expect(partial.getByText('Inactive')).toBeInTheDocument()
  expect(partial.getByText('Not provided')).toBeInTheDocument()
  expect(f.requests.filter(r => r.method === 'GET' && /\/sites\//.test(r.path))).toHaveLength(0)
})

it('Drivers list exposes license, real certificate expiration/attention, account and status without account lookups', async () => {
  const f = adminFixture('/admin/drivers')
  Object.assign(f.rows.drivers![0]!, { name: 'Linked Driver', licenseNumber: 'LIC-114', medicalCertificateExpires: '2000-01-01', userId: '00000000-0000-4000-8000-000000000009' })
  f.rows.drivers!.push({ ...sample('drivers', '00000000-0000-4000-8000-000000000007'), name: 'Unlinked Driver', active: false })
  const table = within(await screen.findByRole('table', { name: 'Drivers' }))
  expect(table.getAllByRole('columnheader').map(h => h.textContent)).toEqual(['Name', 'Status', 'License number', 'Certificate', 'Account'])
  const linked = within(table.getByRole('row', { name: /Linked Driver/ }))
  expect(linked.getByText('LIC-114')).toBeInTheDocument(); expect(linked.getByText('2000-01-01 — Expired')).toBeInTheDocument()
  expect(linked.getByText('Linked account')).toBeInTheDocument(); expect(linked.getByText('Active')).toBeInTheDocument()
  const unlinked = within(table.getByRole('row', { name: /Unlinked Driver/ }))
  expect(unlinked.getByText('No linked account')).toBeInTheDocument(); expect(unlinked.getByText('Inactive')).toBeInTheDocument()
  expect(unlinked.getAllByText('Not provided')).toHaveLength(2)
  expect(f.requests.some(r => /users|memberships|\/drivers\//.test(r.path))).toBe(false)
})

it('Trailers list uses existing Customer catalog and DTO states/last-seen with no Driver/Location N+1', async () => {
  const f = adminFixture('/admin/trailers')
  f.rows.customers![0]!.name = 'Hood Container'
  Object.assign(f.rows.trailers![0]!, { operationalNumber: 'BH  T114', locationState: 'AT_LOCATION', loadState: 'LOADED', serviceabilityState: 'OUT_OF_SERVICE', lastSeenAt: '2026-10-01T15:30:00Z', currentLocationId: '00000000-0000-4000-8000-000000000008', lastDriverId: '00000000-0000-4000-8000-000000000009' })
  f.rows.trailers!.push({ ...sample('trailers', '00000000-0000-4000-8000-000000000007'), operationalNumber: 'T115', designatedCustomerId: null, active: false })
  const table = within(await screen.findByRole('table', { name: 'Trailers' }))
  expect(table.getAllByRole('columnheader').map(h => h.textContent)).toEqual(['Operational number', 'Status', 'Designated Customer', 'Location', 'Load', 'Serviceability', 'Last seen'])
  const designated = within(table.getByRole('row', { name: /BH T114/ }))
  expect(await designated.findByText('Hood Container')).toBeInTheDocument()
  expect(designated.getByRole('link', { name: 'BH T114' }).textContent).toBe('BH  T114')
  for (const text of ['Active', 'AT_LOCATION', 'LOADED', 'OUT_OF_SERVICE', '2026-10-01T15:30:00Z']) expect(designated.getByText(text)).toBeInTheDocument()
  const unassigned = within(table.getByRole('row', { name: /T115/ }))
  for (const text of ['None', 'Inactive', 'LOCATION_UNKNOWN', 'UNKNOWN', 'IN_SERVICE', 'Not provided']) expect(unassigned.getByText(text)).toBeInTheDocument()
  expect(f.requests.filter(r => r.path.endsWith('/customers'))).toHaveLength(1)
  expect(f.requests.some(r => /\/drivers|\/sites\/[^/]+\/locations|\/locations\//.test(r.path))).toBe(false)
  expect(table.queryByText(/coupling|Trip|00000000/)).not.toBeInTheDocument()
})
