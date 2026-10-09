import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { operationsFixture, order, orderId, otherOrderId, tripId } from '../../test/operationsFixture'
import { resourceId, siteId } from '../../test/adminFixture'
import { createSchema, draftStops, actions } from './planning'
import { trip } from '../../test/operationsFixture'
afterEach(() => vi.unstubAllGlobals())
async function start(two = false) {
  const f = operationsFixture(`/operations/trips/new?orderId=${orderId}${two ? `&orderId=${otherOrderId}` : ''}`)
  const u = userEvent.setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Continue to Stops' })).toBeEnabled())
  await u.click(screen.getByRole('button', { name: 'Continue to Stops' }))
  return { f, u }
}
it('initializes separate executable drafts even when requested Site/name/address match', async () => {
  await start(true)
  expect(screen.getByRole('group', { name: 'Stop 1' })).toBeInTheDocument()
  expect(screen.getByRole('group', { name: 'Stop 2' })).toBeInTheDocument()
  expect(screen.queryByRole('group', { name: 'Stop 3' })).not.toBeInTheDocument()
  expect(screen.getAllByText('Frozen requested destination: Frozen receiving')).toHaveLength(2)
})
it('reorders independent draft Stops and adds BASE_RETURN only explicitly', async () => {
  const { u } = await start(true)
  expect(screen.queryByDisplayValue('BASE_RETURN')).not.toBeInTheDocument()
  await u.click(screen.getByRole('button', { name: 'Move Stop 2 up' }))
  const first = screen.getByRole('group', { name: 'Orders served at Stop 1' })
  expect(within(first).getByRole('checkbox', { name: 'ORD-200' })).toBeChecked()
  expect(within(first).getByRole('checkbox', { name: 'ORD-100' })).not.toBeChecked()
  await u.click(screen.getByRole('button', { name: 'Add return to base' }))
  expect(screen.getByLabelText('Stop 3 type')).toHaveValue('BASE_RETURN')
  expect(within(screen.getByRole('group', { name: 'Orders served at Stop 3' })).getAllByRole('checkbox').every(c => !(c as HTMLInputElement).checked)).toBe(true)
})
it('blocks uncovered Orders before POST while allowing operational Stops with no Orders', async () => {
  const { u, f } = await start()
  await u.click(within(screen.getByRole('group', { name: 'Orders served at Stop 1' })).getByRole('checkbox', { name: 'ORD-100' }))
  await u.click(screen.getByRole('button', { name: 'Continue to Resources' }))
  await u.click(screen.getByRole('button', { name: 'Continue to Review' }))
  await u.click(screen.getByRole('button', { name: 'Create Trip' }))
  expect(await screen.findByText('Every selected Order must be covered by a Stop, without other Orders.')).toBeInTheDocument()
  expect(f.requests.filter(r => r.method === 'POST')).toHaveLength(0)
})
it('requires an explicit type and Site for new Stops', async () => {
  const { u, f } = await start()
  await u.click(screen.getByRole('button', { name: 'Add Stop' }))
  expect(screen.getByLabelText('Stop 2 type')).toHaveValue('')
  expect(screen.getByLabelText('Stop 2 Site')).toHaveValue('')
  await u.click(screen.getByRole('button', { name: 'Continue to Resources' })); await u.click(screen.getByRole('button', { name: 'Continue to Review' })); await u.click(screen.getByRole('button', { name: 'Create Trip' }))
  expect(f.requests.filter(r => r.method === 'POST')).toHaveLength(0)
})
it.each([false, true])('previews incomplete/complete assignment and sends exact create contract (complete=%s)', async complete => {
  const { u, f } = await start()
  await u.click(screen.getByRole('button', { name: 'Continue to Resources' }))
  if (complete) {
    await u.selectOptions(screen.getByLabelText('Driver (optional)'), resourceId)
    await u.selectOptions(screen.getByLabelText('Tractor (optional)'), resourceId)
  }
  expect(screen.getByRole('status')).toHaveTextContent(complete ? 'ASSIGNED' : 'PLANNED')
  expect(screen.getByLabelText('Expected Trailer (optional)')).toHaveValue('')
  await u.click(screen.getByRole('button', { name: 'Continue to Review' }))
  await u.click(screen.getByRole('button', { name: 'Create Trip' }))
  await waitFor(() => expect(f.router.state.location.pathname).toBe(`/operations/trips/${tripId}`))
  const body = f.requests.find(r => r.method === 'POST')!.body!
  expect(body).toEqual({ orderIds: [orderId], stops: [{ sequenceNumber: 1, stopType: 'DELIVERY', siteId, locationId: null, siteSnapshot: { name: 'Frozen receiving', timezone: 'America/Chicago' }, scheduledAt: '2026-10-09T15:00:00Z', orderIds: [orderId], instructions: 'Original appointment' }], driverId: complete ? resourceId : null, tractorId: complete ? resourceId : null, plannedInitialTrailerId: null })
  expect(JSON.stringify(body)).not.toContain('orderStopId')
  expect(f.requests.filter(r => r.method === 'POST')).toHaveLength(1)
})
it('only offers active resources and active IN_SERVICE Trailers', async () => {
  const f = operationsFixture(`/operations/trips/new?orderId=${orderId}`), u = userEvent.setup()
  f.rows.drivers!.push({ ...f.rows.drivers![0], id: otherOrderId, name: 'Inactive Driver', active: false })
  f.rows.tractors!.push({ ...f.rows.tractors![0], id: otherOrderId, operationalNumber: 'Inactive Tractor', active: false })
  f.rows.trailers!.push({ ...f.rows.trailers![0], id: otherOrderId, operationalNumber: 'OOS Trailer', serviceabilityState: 'OUT_OF_SERVICE' })
  await waitFor(() => expect(screen.getByRole('button', { name: 'Continue to Stops' })).toBeEnabled())
  await u.click(screen.getByRole('button', { name: 'Continue to Stops' })); await u.click(screen.getByRole('button', { name: 'Continue to Resources' }))
  expect(screen.queryByRole('option', { name: 'Inactive Driver' })).not.toBeInTheDocument()
  expect(screen.queryByRole('option', { name: 'Inactive Tractor' })).not.toBeInTheDocument()
  expect(screen.queryByRole('option', { name: 'OOS Trailer' })).not.toBeInTheDocument()
})
it('preserves frozen ad-hoc requested snapshot without creating Site metadata', async () => {
  const o = order(); o.requestedStops[0]!.siteId = null; o.requestedStops[0]!.siteSnapshot = { name: 'Ad hoc destination', address: { line1: 'Frozen street' }, latitude: 32.7 }
  const f = operationsFixture(`/operations/trips/new?orderId=${orderId}`, { orders: [o] }), u = userEvent.setup()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Continue to Stops' })).toBeEnabled())
  await u.click(screen.getByRole('button', { name: 'Continue to Stops' })); await u.click(screen.getByRole('button', { name: 'Continue to Resources' })); await u.click(screen.getByRole('button', { name: 'Continue to Review' })); await u.click(screen.getByRole('button', { name: 'Create Trip' }))
  await waitFor(() => expect(f.requests.some(r => r.method === 'POST')).toBe(true))
  expect((f.requests.find(r => r.method === 'POST')?.body?.stops as { siteSnapshot: unknown }[])[0]?.siteSnapshot).toEqual(o.requestedStops[0]!.siteSnapshot)
})
it('shows safe server validation without losing the planning draft', async () => {
  const { f, u } = await start()
  await u.click(screen.getByRole('button', { name: 'Continue to Resources' })); await u.click(screen.getByRole('button', { name: 'Continue to Review' }))
  f.fail({ status: 400, code: 'VALIDATION_FAILED', traceId: 'plan-validation' })
  await u.click(screen.getByRole('button', { name: 'Create Trip' }))
  expect(await screen.findByText('Support reference: plan-validation')).toBeInTheDocument()
  expect(screen.getByText(/Original appointment/)).toBeInTheDocument()
})
it('never retries an uncertain create and reconciles through Trip reads', async () => {
  const { f, u } = await start()
  await u.click(screen.getByRole('button', { name: 'Continue to Resources' })); await u.click(screen.getByRole('button', { name: 'Continue to Review' }))
  f.fail({ status: 503, traceId: 'uncertain-create' }, true)
  await u.click(screen.getByRole('button', { name: 'Create Trip' }))
  expect(await screen.findByText(/Your draft has been preserved/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Create Trip' })).toBeDisabled()
  expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
  await u.click(screen.getByRole('button', { name: '2. Stops' }))
  await u.click(screen.getByLabelText('Stop 1 appointment')); await u.keyboard('{Enter}')
  expect(f.requests.filter(r => r.method === 'POST')).toHaveLength(1)
  await u.click(screen.getByRole('button', { name: 'Continue to Resources' })); await u.click(screen.getByRole('button', { name: 'Continue to Review' }))
  await u.click(screen.getByRole('link', { name: 'Reconcile in Trip list' }))
  expect(await screen.findByRole('link', { name: 'Open Trip 10000000' })).toBeInTheDocument()
  expect(f.requests.filter(r => r.method === 'POST')).toHaveLength(1)
})
it('validates unknown requested Stop types explicitly instead of inventing a type', () => {
  const o = order(); o.requestedStops[0]!.stopType = 'Future requested mode'
  expect(draftStops([o])[0]?.stopType).toBe('')
})
it.each(['duplicate orders', 'duplicate sequence', 'foreign mapping', 'missing coverage', 'bad instant'])('validates planning %s before submit', condition => {
  const r = { orderIds: [orderId], stops: draftStops([order()]), driverId: null, tractorId: null, plannedInitialTrailerId: null }
  if (condition === 'duplicate orders') r.orderIds.push(orderId)
  if (condition === 'duplicate sequence') r.stops.push({ ...r.stops[0]! })
  if (condition === 'foreign mapping') r.stops[0]!.orderIds = [otherOrderId]
  if (condition === 'missing coverage') r.stops[0]!.orderIds = []
  if (condition === 'bad instant') r.stops[0]!.scheduledAt = '2026-10-09T15:00'
  expect(createSchema.safeParse(r).success).toBe(false)
})
it.each(['IN_TRANSIT', 'AT_STOP'] as const)('allows filling a null expected Trailer in %s without ordinary active replacement', state => {
  expect(actions(trip(state)).trailer).toBe(true)
  expect(actions({ ...trip(state), plannedInitialTrailerId: resourceId }).trailer).toBe(false)
})

it('requires the Review step even when Enter submits a valid Stop draft', async () => {
  const { f, u } = await start()
  await u.click(screen.getByLabelText('Stop 1 appointment')); await u.keyboard('{Enter}')
  expect(f.requests.filter(r => r.method === 'POST')).toHaveLength(0)
  expect(screen.getByRole('heading', { name: 'Executable Stops' })).toBeInTheDocument()
})
