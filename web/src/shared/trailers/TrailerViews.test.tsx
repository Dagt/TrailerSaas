import { act, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { adminFixture, admin, customerId, resourceId, sample, siteId, stamps, tenantA, tenantB } from '../../test/adminFixture'
import { operationsFixture, operationsMembership, order, orderId, trip, tripId } from '../../test/operationsFixture'
import type { TrailerDetail, TrailerTimelineItem, TrailerTimelinePage } from './contracts'

afterEach(() => vi.unstubAllGlobals())
function detail(): TrailerDetail {
  return { ...(sample('trailers') as TrailerDetail), operationalNumber: 'BOX-100', designatedCustomerName: 'Acme Packaging',
    designatedCustomer: { id: customerId, name: 'Acme Packaging' }, currentLocationName: 'Dock 12', lastKnownLocationName: 'Yard A',
    currentLocation: { id: resourceId, name: 'Dock 12', type: 'DOCK', siteId, siteName: 'Receiving facility' },
    lastKnownLocation: { id: siteId, name: 'Yard A', type: 'YARD', siteId, siteName: 'Origin facility' }, locationState: 'AT_LOCATION',
    couplingState: 'COUPLED', currentDriverId: resourceId, currentDriverName: 'Maria Driver', currentTractorId: resourceId, currentTractorOperationalNumber: 'TR-100',
    lastDriverName: 'Previous Driver', lastDriver: { id: siteId, name: 'Previous Driver' }, lastTractorId: resourceId, lastTractorOperationalNumber: 'TR-100',
    currentTractor: { id: resourceId, operationalNumber: 'TR-100' }, lastTractor: { id: resourceId, operationalNumber: 'TR-100' },
    currentCoupling: { id: resourceId, driverId: resourceId, driverName: 'Maria Driver', tractorId: resourceId, tractorOperationalNumber: 'TR-100', tripId, hookedAt: stamps.createdAt, droppedAt: null },
    lastCoupling: null, lastSeenAt: stamps.createdAt, openManifests: [{ id: resourceId, status: 'OPEN', version: 0, updatedAt: stamps.updatedAt, ordersTruncated: false,
      orders: [{ id: orderId, customerId, customerName: 'Acme Packaging', orderNumber: 'ORD-100', status: 'PLANNED' }] }], manifestSummaryTruncated: false }
}
function event(type = 'HOOKED', id = 'event-1'): TrailerTimelineItem {
  return { id, eventType: type, actorType: 'HUMAN_USER', sourceType: 'USER_ACTION', occurredAt: '2026-10-01T12:00:00Z', recordedAt: '2026-10-03T12:00:00Z',
    reason: 'Physically confirmed', locationId: resourceId, locationName: 'Dock 12', driverId: resourceId, driverName: 'Maria Driver', tractorId: resourceId,
    tractorOperationalNumber: 'TR-100', tripId, tripStopId: null, changes: {} }
}
function fixture(path: string, memberships = [operationsMembership()]) {
  const f = path.startsWith('/admin') ? adminFixture(path, memberships) : operationsFixture(path, { memberships })
  let t = detail(), page: TrailerTimelinePage = { items: [event()], nextCursor: null }, nextPage: TrailerTimelinePage = { items: [event('DROPPED', 'event-2')], nextCursor: null }
  let failSearch = false, failNext = false, empty = false
  const original = f.fetcher.getMockImplementation()!
  f.fetcher.mockImplementation(async (input, init) => {
    const url = new URL(String(input)), response = await original(input, init)
    const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } })
    // Admin's fixture also needs the real Operations read contracts after cross-area navigation.
    if (path.startsWith('/admin')) {
      if (url.pathname.endsWith(`/orders/${orderId}`)) return json(order())
      if (url.pathname.endsWith(`/trips/${tripId}`)) return json(trip())
      if (url.pathname.endsWith(`/trips/${tripId}/timeline`)) return json({ items: [], nextCursor: null })
    }
    if (url.pathname.includes(`/trailers/${resourceId}/timeline`)) {
      if (url.searchParams.has('cursor') && failNext) return json({ status: 500, code: 'UNEXPECTED_FAILURE', traceId: 'read-support' }, 500)
      return json(url.searchParams.has('cursor') ? nextPage : page)
    }
    if (url.pathname.endsWith(`/trailers/${resourceId}`)) return json(t)
    if (url.pathname.endsWith('/trailers')) {
      if (failSearch && url.searchParams.has('operationalNumber')) return json({ status: 500, traceId: 'search-support' }, 500)
      return json({ items: empty ? [] : [t] })
    }
    return response
  })
  return { ...f, setDetail(value: Partial<TrailerDetail>) { t = { ...t, ...value } }, pages(first: TrailerTimelinePage, next = nextPage) { page = first; nextPage = next },
    searchFailure(value: boolean) { failSearch = value }, nextFailure(value: boolean) { failNext = value }, empty(value: boolean) { empty = value } }
}
it('Operations nav and Dashboard quick search navigate with the unchanged display search number', async () => {
  const u = userEvent.setup(), f = fixture('/operations')
  expect(await screen.findByRole('link', { name: 'Trailers' })).toHaveAttribute('href', '/operations/trailers')
  const form = within(await screen.findByRole('form', { name: 'Trailer quick search' }))
  await u.type(form.getByLabelText('Trailer quick search'), '  tr-123  '); await u.click(form.getByRole('button', { name: 'Find Trailer' }))
  await waitFor(() => expect(f.router.state.location.pathname).toBe('/operations/trailers'))
  expect(new URLSearchParams(f.router.state.location.search).get('operationalNumber')).toBe('  tr-123  ')
  expect(await screen.findByRole('table', { name: 'Trailer search results' })).toBeInTheDocument()
})
it('search renders enriched fields without per-row Driver/Site requests and links to detail', async () => {
  const f = fixture('/operations/trailers?operationalNumber=tr-123')
  const table = within(await screen.findByRole('table', { name: 'Trailer search results' }))
  for (const value of ['Acme Packaging', 'Dock 12', 'Yard A', 'Coupled', 'In service', 'Maria Driver', 'TR-100']) expect(table.getByText(value)).toBeInTheDocument()
  expect(table.getByRole('link', { name: 'BOX-100' })).toHaveAttribute('href', `/operations/trailers/${resourceId}`)
  expect(f.requests.some(r => /\/drivers|\/sites|\/tractors/.test(r.path))).toBe(false)
})
it('empty exact lookup has useful no-result text and can be cleared', async () => {
  const u = userEvent.setup(), f = fixture('/operations/trailers?operationalNumber=missing'); f.empty(true)
  expect(await screen.findByText('No Trailer found for that operational number.')).toBeInTheDocument()
  await u.click(screen.getByRole('button', { name: 'Clear search' }))
  expect(screen.getByLabelText('Operational number')).toHaveValue('')
  expect(screen.getByText('Enter an operational number to find a Trailer.')).toBeInTheDocument()
})
it('malformed or empty search does not issue an API search', async () => {
  const u = userEvent.setup(), f = fixture('/operations/trailers')
  await u.click(await screen.findByRole('button', { name: 'Search' }))
  expect(screen.getByRole('alert')).toHaveTextContent('Enter a Trailer number')
  expect(f.requests.some(r => r.path.includes('/trailers?'))).toBe(false)
})
it('failed search displays safe support reference and explicit read retry', async () => {
  const u = userEvent.setup(), f = fixture('/operations/trailers?operationalNumber=BOX-100'); f.searchFailure(true)
  expect(await screen.findByText('Support reference: search-support')).toBeInTheDocument()
  f.searchFailure(false); await u.click(screen.getByRole('button', { name: 'Retry' }))
  expect(await screen.findByRole('table', { name: 'Trailer search results' })).toBeInTheDocument()
})
it.each([['AT_LOCATION', 'Dock 12'], ['IN_TRANSIT', 'In transit'], ['LOCATION_UNKNOWN', 'Unknown']])('detail distinguishes current from last-known for %s', async (state, label) => {
  const f = fixture(`/operations/trailers/${resourceId}`); f.setDetail({ locationState: state, currentLocation: state === 'AT_LOCATION' ? detail().currentLocation : null })
  const section = (await screen.findByRole('heading', { name: 'Location' })).closest('section')!
  const ui = within(section)
  expect(ui.getByText('Current location').nextElementSibling).toHaveTextContent(label)
  expect(ui.getByText('Last known location').nextElementSibling).toHaveTextContent('Yard A')
  expect(ui.getByText(/previous observation/)).toBeInTheDocument()
})
it('coupled detail uses physical resource names, authoritative last Driver and links to Trip and Order', async () => {
  fixture(`/operations/trailers/${resourceId}`)
  expect(await screen.findByRole('heading', { name: 'Physical coupling' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Open Trip' })).toHaveAttribute('href', `/operations/trips/${tripId}`)
  expect(screen.getByRole('link', { name: 'ORD-100' })).toHaveAttribute('href', `/operations/orders/${orderId}`)
  expect(screen.getByText('Previous Driver')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Manifest / planned load' })).toBeInTheDocument()
  expect(screen.getByText(/do not confirm cargo physically loaded/)).toBeInTheDocument()
  expect(screen.getByText('Evidence integration is not implemented yet. This view cannot determine whether evidence exists.')).toBeInTheDocument()
  expect(screen.queryByText(/No linked evidence|No evidence/)).not.toBeInTheDocument()
  expect(screen.queryByText(/Hook Trailer|Mark OOS|Correct location/)).not.toBeInTheDocument()
})
it.each(['/operations', '/admin'])('%s presents 20 Manifests and a 50-Order preview without false empty allocations', async area => {
  const f = fixture(`${area}/trailers/${resourceId}`, area === '/admin' ? [admin()] : [operationsMembership()])
  const base = detail().openManifests[0]!
  const manifests = Array.from({ length: 20 }, (_, i) => ({ ...base, id: `manifest-${i.toString().padStart(2, '0')}`,
    ordersTruncated: i >= 16, orders: Array.from({ length: i < 16 ? 3 : i === 16 ? 2 : 0 }, (_, j) => ({ ...base.orders[0]!, id: `order-${i}-${j}`, orderNumber: `ORDER-${i}-${j}` })) }))
  // Three allocations exist per Manifest; the last ten visible Orders exceed the global cap.
  f.setDetail({ openManifests: manifests, manifestSummaryTruncated: true })
  const ui = within((await screen.findByRole('heading', { name: 'Manifest / planned load' })).closest('section')!)
  expect(ui.getAllByRole('heading', { level: 3 })).toHaveLength(20)
  expect(ui.getAllByRole('link', { name: /^ORDER-/ })).toHaveLength(50)
  expect(ui.queryByText('No Orders allocated.')).not.toBeInTheDocument()
  expect(ui.getAllByText('Orders are outside this bounded planning preview.')).toHaveLength(3)
  expect(ui.getByText('Additional Orders are outside this bounded planning preview.')).toBeInTheDocument()
  expect(ui.getByText('Planning preview limited to 20 OPEN Manifests and 50 Orders.')).toBeInTheDocument()
})
it('genuinely empty allocations remain distinguishable from a limited planning preview', async () => {
  const f = fixture(`/operations/trailers/${resourceId}`)
  f.setDetail({ openManifests: [{ ...detail().openManifests[0]!, orders: [], ordersTruncated: false }] })
  expect(await screen.findByText('No Orders allocated.')).toBeInTheDocument()
  expect(screen.queryByText(/outside this bounded planning preview/)).not.toBeInTheDocument()
})
it.each(['/operations', '/admin'])('%s Order link opens the existing read-only route and preserves planning terminology', async area => {
  const u = userEvent.setup(), f = fixture(`${area}/trailers/${resourceId}`, area === '/admin' ? [admin()] : [operationsMembership()])
  expect(await screen.findByText(/do not confirm cargo physically loaded/)).toBeInTheDocument()
  await u.click(screen.getByRole('link', { name: 'ORD-100' }))
  expect(await screen.findByRole('heading', { name: 'ORD-100' })).toBeInTheDocument()
  expect(f.router.state.location.pathname).toBe(`/operations/orders/${orderId}`)
  expect(screen.getByRole('heading', { name: 'Requested Stops' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Save|Complete Order/ })).not.toBeInTheDocument()
})
it.each(['/operations', '/admin'])('%s physical coupling Trip link opens the implemented Trip detail', async area => {
  const u = userEvent.setup(), f = fixture(`${area}/trailers/${resourceId}`, area === '/admin' ? [admin()] : [operationsMembership()])
  await u.click(await screen.findByRole('link', { name: 'Open Trip' }))
  expect(await screen.findByRole('heading', { name: 'Executable Stops' })).toBeInTheDocument()
  expect(f.router.state.location.pathname).toBe(`/operations/trips/${tripId}`)
  expect(screen.getByText('Expected Trailer')).toBeInTheDocument()
  expect(screen.getByText(/Expected Trailer is not physical coupling/)).toBeInTheDocument()
})
it('uncoupled detail retains last coupling without showing a fabricated current Trip', async () => {
  const f = fixture(`/operations/trailers/${resourceId}`);f.setDetail({ currentCoupling: null, couplingState: 'UNCOUPLED', currentDriverName: null, currentTractorOperationalNumber: null, lastCoupling: { ...detail().currentCoupling!, droppedAt: stamps.updatedAt } })
  expect(await screen.findByText('Uncoupled')).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Open Trip' })).not.toBeInTheDocument()
  expect(screen.getByText(/Latest coupling: TR-100/)).toBeInTheDocument()
})
it.each(['HOOKED','DROPPED','DEPARTED','ARRIVED','YARD_MOVED','LOCATION_CORRECTED','LOCATION_MARKED_UNKNOWN','LOCATION_RECOVERED','LOAD_STATE_CHANGED','MARKED_OOS','RETURNED_TO_SERVICE','DESIGNATED_CUSTOMER_CHANGED'])('timeline has human label and physical time for %s', async type => {
  const f = fixture(`/operations/trailers/${resourceId}`);f.pages({ items: [event(type)], nextCursor: null })
  const ui = within(await screen.findByRole('list', { name: 'Trailer movement timeline' }))
  expect(ui.getByText(type.toLowerCase().replaceAll('_',' ').replace(/^./,c=>c.toUpperCase()))).toBeInTheDocument()
  expect(ui.getByText('Physically confirmed')).toBeInTheDocument()
  expect(ui.getByText('Actor type: Human user · Source: User action')).toBeInTheDocument()
  expect(ui.getByText('Dock 12 · Maria Driver · TR-100')).toBeInTheDocument()
  expect(ui.getByText(/Recorded/)).toBeInTheDocument()
  const physicalTime = new Date(event().occurredAt).toLocaleString('en-US', { year:'numeric',month:'short',day:'numeric',hour:'numeric',minute:'2-digit',timeZoneName:'shortOffset' })
  expect(ui.getByText(physicalTime)).toHaveAttribute('dateTime',event().occurredAt)
  expect(ui.getByText(physicalTime)).toHaveTextContent(/GMT/)
  expect(ui.queryByText(/"metadata"|"tenantId"|"actorUserId"/)).not.toBeInTheDocument()
})
it('timeline Load more uses opaque cursor without client reordering', async () => {
  const u = userEvent.setup(), f = fixture(`/operations/trailers/${resourceId}`)
  f.pages({ items: [event()], nextCursor: 'opaque-position' })
  await u.click(await screen.findByRole('button', { name: 'Load more movements' }))
  await waitFor(() => expect(screen.getByRole('list', { name: 'Trailer movement timeline' }).querySelectorAll('li')).toHaveLength(2))
  expect(f.requests.some(r => r.path.endsWith('/timeline?cursor=opaque-position'))).toBe(true)
  expect(screen.queryByRole('button', { name: 'Load more movements' })).not.toBeInTheDocument()
})
it('a denied continuation hides previously visible internal movements', async () => {
  const u = userEvent.setup(), f = fixture(`/operations/trailers/${resourceId}`)
  f.pages({ items: [event()], nextCursor: 'opaque-position' })
  const original = f.fetcher.getMockImplementation()!
  f.fetcher.mockImplementation(async (input, init) => {
    const url = new URL(String(input))
    if (url.pathname.endsWith('/timeline') && url.searchParams.has('cursor')) return new Response(JSON.stringify({ detail: 'synthetic-private-context' }), { status: 403, headers: { 'Content-Type': 'application/problem+json' } })
    return original(input, init)
  })
  await u.click(await screen.findByRole('button', { name: 'Load more movements' }))
  await screen.findByRole('alert')
  expect(screen.queryByRole('list', { name: 'Trailer movement timeline' })).not.toBeInTheDocument()
  expect(screen.queryByText('synthetic-private-context')).not.toBeInTheDocument()
})
it('pagination failure retains prior movement rows, read detail and permits bounded retry', async () => {
  const u = userEvent.setup(), f = fixture(`/operations/trailers/${resourceId}`)
  f.pages({ items: [event()], nextCursor: 'opaque-position' });f.nextFailure(true)
  await u.click(await screen.findByRole('button', { name: 'Load more movements' }))
  expect(await screen.findByText('Support reference: read-support')).toBeInTheDocument()
  expect(within(screen.getByRole('list', { name: 'Trailer movement timeline' })).getByText('Hooked')).toBeInTheDocument();expect(screen.getByRole('heading', { name: 'Physical coupling' })).toBeInTheDocument()
  f.nextFailure(false); await u.click(screen.getByRole('button', { name: 'Retry' }))
  expect(await screen.findByText('Dropped')).toBeInTheDocument()
})
it('Admin uses the same rich detail and timeline with updated backend designation copy', async () => {
  fixture(`/admin/trailers/${resourceId}`, [admin()])
  expect(await screen.findByRole('heading', { name: 'Physical coupling' })).toBeInTheDocument()
  expect(await within(await screen.findByRole('list', { name: 'Trailer movement timeline' })).findByText('Hooked')).toBeInTheDocument()
  expect(screen.getByText(/Audited designation changes are supported by the backend/)).toBeInTheDocument()
  expect(screen.queryByText(/workflow, which is not available yet/)).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Change designated Customer' })).toBeDisabled()
})
it('Admin enriched list uses names and coupling without per-row catalog lookups', async () => {
  const f = fixture('/admin/trailers', [admin()])
  const table = within(await screen.findByRole('table', { name: 'Trailers' }))
  for (const text of ['Acme Packaging','Dock 12','Coupled','Maria Driver','Previous Driver']) expect(table.getByText(text)).toBeInTheDocument()
  expect(f.requests.some(r=>/\/drivers|\/tractors|\/locations\//.test(r.path))).toBe(false)
})
it('read-only Order supporting route displays references, requested Stops and cargo without mutation controls', async () => {
  fixture(`/operations/orders/${orderId}`)
  expect(await screen.findByRole('heading', { name: 'ORD-100' })).toBeInTheDocument()
  expect(screen.getByText('PO-1')).toBeInTheDocument();expect(screen.getByText('BOL-1')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Requested Stops' })).toBeInTheDocument()
  expect(screen.getByText('No cargo lines specified.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Save|Cancel Order|Complete Order/ })).not.toBeInTheDocument()
})
it('switching Tenant clears Trailer detail and timeline cache before opening the new workspace', async () => {
  const u = userEvent.setup(), f = fixture(`/operations/trailers/${resourceId}`, [operationsMembership(tenantA,'First workspace'),operationsMembership(tenantB,'Second workspace')])
  await u.selectOptions(await screen.findByLabelText('Active Tenant'), tenantA)
  await waitFor(() => expect(f.router.state.location.pathname).toBe('/operations'))
  await act(async () => { await f.router.navigate(`/operations/trailers/${resourceId}`) })
  expect(await within(await screen.findByRole('list', { name: 'Trailer movement timeline' })).findByText('Hooked')).toBeInTheDocument()
  expect(f.cache.getQueryCache().getAll().some(q=>q.queryKey[0]==='operations' && q.queryKey.includes('trailer-timeline'))).toBe(true)
  await u.selectOptions(screen.getByLabelText('Active Tenant'), tenantB)
  await waitFor(()=>expect(f.router.state.location.pathname).toBe('/operations'))
  expect(f.cache.getQueryCache().getAll().some(q=>q.queryKey[0]==='operations' && q.queryKey[1]===tenantA)).toBe(false)
  expect(screen.queryByText('Hooked')).not.toBeInTheDocument()
  expect(f.requests.some(r=>r.path.includes(`/tenants/${tenantB}/dispatch/board`))).toBe(true)
})
