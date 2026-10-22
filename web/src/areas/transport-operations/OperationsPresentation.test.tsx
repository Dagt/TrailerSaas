import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { operationsFixture, operationsMembership, orderId, otherOrderId, tripId, trip } from '../../test/operationsFixture'
import { resourceId, tenantA, tenantB } from '../../test/adminFixture'
import { createOperationsReviewApi } from '../../dev/operations-fixtures'
import { demoId } from '../../dev/tenant-admin-fixtures'

afterEach(() => vi.unstubAllGlobals())

it('labels dashboard counts as bounded previews and preserves authorized Alert access', async () => {
  const f = operationsFixture('/operations', { memberships: [{ ...operationsMembership(), capabilities:['ALERT_VIEW'] }] })
  await screen.findByRole('table', { name:'Active Trips' })
  expect(screen.getByText(/Bounded previews, not Tenant totals/)).toHaveTextContent('Up to 20 Orders and 20 Trips')
  expect(screen.getByText(/^Generated /)).toBeInTheDocument()
  expect(screen.getByRole('link', { name:'View operational Alerts' })).toHaveAttribute('href','/operations/alerts')
  expect(screen.getByRole('group', { name:'Filter dispatch preview' })).toBeInTheDocument()
  expect(screen.getByText('Incomplete assignment')).toHaveAttribute('data-tone','attention')
  expect(f.requests.every(r => r.method === 'GET' && !/timeline|history/.test(r.path))).toBe(true)
})

it('keeps all queue filters, multiple selection and filter-reset behavior in the new toolbar', async () => {
  const u=userEvent.setup(), f=operationsFixture('/operations/orders'); f.page('more_demo')
  const first=await screen.findByRole('checkbox',{name:'Select ORD-100'})
  await u.click(first); await u.click(screen.getByRole('checkbox',{name:'Select ORD-200'}))
  expect(screen.getByRole('status')).toHaveTextContent('2 Order(s) selected')
  await u.click(screen.getByRole('button',{name:'Load more Orders'}))
  await waitFor(()=>expect(f.requests.some(r=>r.path.includes('cursor=more_demo'))).toBe(true))
  expect(first).toBeChecked()
  expect(new URL(screen.getByRole('link',{name:'Plan selected Orders (2)'}).getAttribute('href')!,'https://example.test').searchParams.getAll('orderId')).toEqual([orderId,otherOrderId])
  await u.selectOptions(screen.getByLabelText('Order status'),'PLANNED')
  expect(screen.queryByRole('link',{name:/Plan selected Orders/})).not.toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('Select eligible Orders')
})

it('opens the existing read-only Order detail directly from the queue identifier', async () => {
  const u=userEvent.setup(), f=operationsFixture('/operations/orders')
  await u.click(await screen.findByRole('link',{name:'ORD-100'}))
  await screen.findByRole('heading',{name:'Order overview'})
  expect(f.router.state.location.pathname).toBe(`/operations/orders/${orderId}`)
  expect(screen.getByRole('heading',{name:'Requested Stops'})).toBeInTheDocument()
  expect(screen.getByRole('heading',{name:'Cargo summary'})).toBeInTheDocument()
  expect(screen.queryByRole('button',{name:/save|cancel order|edit order/i})).not.toBeInTheDocument()
  expect(f.requests.every(r=>r.method==='GET')).toBe(true)
})

it('keeps terminal Orders visible but unavailable for planning selection', async () => {
  const f=operationsFixture('/operations/orders')
  f.orders(f.orders().map((o,i)=>i===0?{...o,status:'COMPLETED'}:o))
  expect(await screen.findByRole('checkbox',{name:'Select ORD-100'})).toBeDisabled()
  expect(screen.getByRole('checkbox',{name:'Select ORD-200'})).toBeEnabled()
  expect(screen.getByRole('status')).toHaveTextContent('Select eligible Orders')
})

it('presents one planning entry and a named keyboard-scrollable Trips table', async () => {
  operationsFixture('/operations/trips')
  const table=await screen.findByRole('table',{name:'Trips'})
  expect(screen.getByRole('link',{name:'Plan a Trip'})).toHaveClass('ops-primary-link')
  expect(table.closest('[role="region"]')).toHaveAttribute('tabindex','0')
  expect(within(table).getByRole('columnheader',{name:'Expected Trailer'})).toBeInTheDocument()
  expect(within(table).getByRole('columnheader',{name:'Updated'})).toBeInTheDocument()
})

it('retains the four real planning steps and keeps Enter from submitting before Review', async () => {
  const u=userEvent.setup(), f=operationsFixture(`/operations/trips/new?orderId=${orderId}`)
  const steps=await screen.findByRole('navigation',{name:'Planning steps'})
  expect(within(steps).getAllByRole('button')).toHaveLength(4)
  await waitFor(()=>expect(screen.getByRole('button',{name:'Continue to Stops'})).toBeEnabled())
  await u.click(screen.getByRole('button',{name:'Continue to Stops'}))
  expect(within(steps).getByRole('button',{name:'2. Stops'})).toHaveAttribute('aria-current','step')
  const appointment=screen.getByLabelText('Stop 1 appointment')
  await u.click(appointment); await u.keyboard('{Enter}')
  expect(f.requests.some(r=>r.method==='POST')).toBe(false)
  expect(screen.queryByRole('button',{name:'Create Trip'})).not.toBeInTheDocument()
})

it('keeps keyboard Stop reordering and the final payload aligned with the displayed sequence', async () => {
  const u=userEvent.setup(), f=operationsFixture(`/operations/trips/new?orderId=${orderId}&orderId=${otherOrderId}`)
  await waitFor(()=>expect(screen.getByRole('button',{name:'Continue to Stops'})).toBeEnabled())
  await u.click(screen.getByRole('button',{name:'Continue to Stops'}))
  const move=screen.getByRole('button',{name:'Move Stop 2 up'}); move.focus(); await u.keyboard('{Enter}')
  expect(screen.getByRole('group',{name:'Orders served at Stop 1'})).toHaveTextContent('ORD-200')
  await u.click(screen.getByRole('button',{name:'Continue to Resources'})); await u.click(screen.getByRole('button',{name:'Continue to Review'}))
  expect(screen.getByRole('heading',{name:'Orders to plan'})).toBeInTheDocument()
  expect(screen.getByRole('heading',{name:'Stop sequence'})).toBeInTheDocument()
  expect(screen.getByRole('heading',{name:'Resource plan'})).toBeInTheDocument()
  await u.click(screen.getByRole('button',{name:'Create Trip'}))
  await waitFor(()=>expect(f.requests.some(r=>r.method==='POST')).toBe(true))
  expect((f.requests.find(r=>r.method==='POST')?.body?.stops as {orderIds:string[]}[])[0]?.orderIds).toEqual([otherOrderId])
})

it('exposes Stop validation at the named group without losing the draft', async () => {
  const u=userEvent.setup(), f=operationsFixture(`/operations/trips/new?orderId=${orderId}`)
  await waitFor(()=>expect(screen.getByRole('button',{name:'Continue to Stops'})).toBeEnabled())
  await u.click(screen.getByRole('button',{name:'Continue to Stops'})); await u.click(screen.getByRole('button',{name:'Add Stop'}))
  await u.click(screen.getByRole('button',{name:'Continue to Resources'})); await u.click(screen.getByRole('button',{name:'Continue to Review'})); await u.click(screen.getByRole('button',{name:'Create Trip'}))
  expect(screen.getByRole('group',{name:'Stop 2'})).toHaveAccessibleDescription()
  expect(screen.getByLabelText('Stop 2 type')).toHaveValue('')
  expect(f.requests.some(r=>r.method==='POST')).toBe(false)
})

it('keeps uncertain creation visibly blocked and offers read reconciliation', async () => {
  const u=userEvent.setup(),f=operationsFixture(`/operations/trips/new?orderId=${orderId}`)
  await waitFor(()=>expect(screen.getByRole('button',{name:'Continue to Stops'})).toBeEnabled())
  await u.click(screen.getByRole('button',{name:'Continue to Stops'})); await u.click(screen.getByRole('button',{name:'Continue to Resources'})); await u.click(screen.getByRole('button',{name:'Continue to Review'}))
  f.fail({status:503},true); await u.click(screen.getByRole('button',{name:'Create Trip'}))
  expect(await screen.findByText(/The result is uncertain/)).toBeInTheDocument()
  expect(screen.getByRole('button',{name:'Create Trip'})).toBeDisabled()
  expect(screen.getByRole('link',{name:'Reconcile in Trip list'})).toHaveAttribute('href','/operations/trips')
  await u.click(screen.getByRole('button',{name:'Create Trip'}))
  expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(1)
})

it('keeps assignment dialog Escape and focus restoration with no write', async () => {
  const u=userEvent.setup(),f=operationsFixture(`/operations/trips/${tripId}`)
  const trigger=await screen.findByRole('button',{name:'Assign / reassign Driver'})
  await u.click(trigger)
  const dialog=screen.getByRole('dialog',{name:'Assign / reassign Driver'})
  expect(dialog).toHaveClass('ops-dialog')
  expect(within(dialog).getByRole('button',{name:'Cancel'})).toHaveFocus()
  await u.keyboard('{Escape}')
  expect(trigger).toHaveFocus()
  expect(f.requests.every(r=>r.method==='GET')).toBe(true)
})

it('preserves version-conflict correction inside the new assignment dialog', async () => {
  const u=userEvent.setup(),f=operationsFixture(`/operations/trips/${tripId}`)
  await u.click(await screen.findByRole('button',{name:'Assign / reassign Driver'}))
  const dialog=screen.getByRole('dialog'); await u.selectOptions(within(dialog).getByLabelText('Driver'),resourceId); await u.type(within(dialog).getByLabelText('Reason'),'Reviewed change')
  f.fail({status:409,code:'STALE_VERSION'}); await u.click(within(dialog).getByRole('button',{name:'Save assignment'}))
  await within(dialog).findByRole('button',{name:'Reload latest'})
  expect(within(dialog).getByRole('button',{name:'Save assignment'})).toBeDisabled()
  expect(within(dialog).getByLabelText('Reason')).toHaveValue('Reviewed change')
  expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(1)
})

it('distinguishes active cancellation from a completed physical action', async () => {
  const u=userEvent.setup(); operationsFixture(`/operations/trips/${tripId}`,{trip:trip('IN_TRANSIT')})
  await u.click(await screen.findByRole('button',{name:'Cancel Trip'}))
  const d=screen.getByRole('dialog',{name:'Start cancellation'})
  expect(d).toHaveClass('ops-dialog--danger')
  expect(within(d).getByText(/Physical resolution is still required/)).toBeInTheDocument()
  expect(within(d).getByRole('button',{name:'Start cancellation'})).toBeDisabled()
})

it('retains safe cancellation focus, named validation and Escape recovery in the scrollable dialog', async () => {
  const u=userEvent.setup(),f=operationsFixture(`/operations/trips/${tripId}`,{trip:trip('IN_TRANSIT')})
  const trigger=await screen.findByRole('button',{name:'Cancel Trip'})
  await u.click(trigger)
  const dialog=screen.getByRole('dialog',{name:'Start cancellation'})
  expect(within(dialog).getByRole('heading',{name:'Start cancellation'})).toBeInTheDocument()
  expect(within(dialog).getByRole('button',{name:'Cancel'})).toHaveFocus()
  expect(within(dialog).getByRole('button',{name:'Start cancellation'})).toBeDisabled()
  await u.click(within(dialog).getByLabelText('I confirm these operational instructions.'))
  await u.click(within(dialog).getByRole('button',{name:'Start cancellation'}))
  await waitFor(()=>expect(within(dialog).getByLabelText('Reason')).toHaveAttribute('aria-invalid','true'))
  expect(within(dialog).getByLabelText('Reason')).toHaveAccessibleDescription('Required.')
  expect(within(dialog).getByLabelText('Instructions')).toHaveAttribute('aria-invalid','true')
  expect(f.requests.every(r=>r.method==='GET')).toBe(true)
  await u.keyboard('{Escape}')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(trigger).toHaveFocus()
})

function review(path:string,state='') {
  const f=operationsFixture(path), api=createOperationsReviewApi(state)
  f.fetcher.mockImplementation(async input=>{const u=new URL(String(input)); const data=await api.request(u.pathname.replace('/api/v1','')+u.search); return new Response(JSON.stringify(data),{headers:{'Content-Type':'application/json'}})})
  return f
}

it('keeps exact Trailer search and explicitly separates unknown Current from Last Known', async () => {
  review('/operations/trailers?operationalNumber=DEMO-TR-104')
  const table=await screen.findByRole('table',{name:'Trailer search results'})
  expect(within(table).getByText('Location unknown')).toHaveAttribute('data-tone','unknown')
  expect(within(table).getByText('DEMO · Receiving Dock 4')).toBeInTheDocument()
  expect(screen.getByLabelText('Operational number')).toHaveValue('DEMO-TR-104')
  expect(screen.queryByLabelText(/GPS|ETA|fuzzy/i)).not.toBeInTheDocument()
})

it('keeps paginated movement facts inside the shared Trailer view', async () => {
  const u=userEvent.setup();review(`/operations/trailers/${demoId(50)}`)
  const more=await screen.findByRole('button',{name:'Load more movements'})
  await u.click(more)
  await waitFor(()=>expect(within(screen.getByRole('list',{name:'Trailer movement timeline'})).getAllByRole('listitem')).toHaveLength(2))
  expect(screen.getByText(/Last known location is a previous observation/)).toBeInTheDocument()
  expect(screen.getByText(/planned Trailer assignment is not a coupling/)).toBeInTheDocument()
})

it('presents Operations Alert resolution with its required reason and restored focus',async()=>{
  const u=userEvent.setup();review(`/operations/alerts/${demoId(70)}`)
  const trigger=await screen.findByRole('button',{name:'Resolve'})
  await u.click(trigger)
  const d=screen.getByRole('dialog',{name:'Resolve Alert'})
  expect(d).toHaveClass('ops-dialog')
  expect(within(d).getByRole('button',{name:'Confirm resolution'})).toBeDisabled()
  await u.type(within(d).getByLabelText('Resolution reason'),'Reviewed recovery')
  expect(within(d).getByRole('button',{name:'Confirm resolution'})).toBeEnabled()
  await u.keyboard('{Escape}')
  expect(trigger).toHaveFocus()
})

it('does not query Alerts when the current Operations membership lacks ALERT_VIEW',async()=>{
  const f=review('/operations/alerts','denied')
  await screen.findByRole('heading',{name:'Alerts access is not available'})
  expect(screen.queryByRole('link',{name:'Alerts'})).not.toBeInTheDocument()
  expect(f.fetcher.mock.calls.some(([input])=>String(input).includes('/alerts'))).toBe(false)
})

it('retains Tenant cache retirement after navigation through the redesigned interior', async () => {
  const u=userEvent.setup(),f=operationsFixture('/operations/orders',{memberships:[operationsMembership(tenantA,'Alpha'),operationsMembership(tenantB,'Beta')]})
  await u.selectOptions(await screen.findByLabelText('Active Tenant'),tenantA)
  await screen.findByRole('heading',{name:'Dispatch dashboard'})
  await u.click(screen.getByRole('link',{name:'Trips'})); await screen.findByRole('table',{name:'Trips'})
  f.cache.setQueryData(['operations',tenantA,'private-preview'],{secret:'old owner'})
  await u.selectOptions(screen.getByLabelText('Active Tenant'),tenantB)
  expect(f.cache.getQueryData(['operations',tenantA,'private-preview'])).toBeUndefined()
  expect(f.router.state.location.pathname).toBe('/operations')
})

it.each(['POST','PATCH','DELETE'] as const)('isolated review rejects %s instead of confirming a Backend action',async method=>{
  await expect(createOperationsReviewApi().request(`/tenants/${demoId(1)}/trips`,{method,body:{}})).rejects.toMatchObject({problem:{status:503,code:'ISOLATED_OPERATIONS_READ_ONLY'}})
})

it('keeps the isolated entry out of production and Operations independent of Admin presentation',()=>{
  expect(readFileSync('src/main.tsx','utf8')+readFileSync('src/app/routes.tsx','utf8')).not.toMatch(/operations-fixtures|dev\/operations|isolated-operations-review/)
  expect(readFileSync('src/dev/operations.tsx','utf8')).toContain('import.meta.env.DEV')
  for(const file of ['OperationsPresentation','BoardPages','PlanTrip','TripDetailPage','TripAction','TrailerPages'])expect(readFileSync(`src/areas/transport-operations/${file}.tsx`,'utf8')).not.toMatch(/AdminPresentation|admin\.css/)
})
