import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { CustomerStatus, CustomerTable } from './CustomerPresentation'
import { customerReadFixture, inventoryTrailer, evidenceFile, documentId } from '../../test/customerReadFixture'
import { orderFixture, orderId, stopId, order, trip } from '../../test/customerOrderFixture'
import { customerId, customerB, dispatcher, response } from '../../test/customerFixture'
import { createCustomerReviewApi } from '../../dev/customer-fixtures'

it('uses one shared overflow instruction per Customer table instead of duplicating the previous portal hint', () => {
  render(<CustomerTable headings={['Document']} caption="Related Customer documents"><tr><td>BOL</td></tr></CustomerTable>)
  const region = screen.getByRole('region', { name: 'Related Customer documents' })
  Object.defineProperties(region, { clientWidth: { value: 350 }, scrollWidth: { value: 900 } })
  fireEvent.resize(window)
  expect(screen.getAllByText(/^Scroll horizontally/)).toHaveLength(1)
  expect(region).toHaveAccessibleDescription(/Keyboard: focus this table/)
})

it.each([['UNKNOWN','unknown'],['LOCATION_UNKNOWN','unknown'],['UPLOADED','neutral'],['DELIVERED','neutral'],['COMPLETED','success'],['CANCELLING','attention']])('keeps %s explicit without inventing a different domain outcome', (value,tone)=>{
  render(<CustomerStatus value={value!}/>);expect(screen.getByText(value!)).toHaveAttribute('data-tone',tone)
})
it('groups actual dashboard previews and retains bounded reads without internal directories',async()=>{
  const f=customerReadFixture('/customer');await screen.findByRole('heading',{name:'Customer dashboard'})
  await screen.findByRole('heading',{name:'Recent Evidence'});await waitFor(()=>expect(screen.queryByText('Loading…')).not.toBeInTheDocument())
  expect(screen.getByRole('region',{name:'Order activity'})).toHaveTextContent('up to five records')
  expect(screen.getByRole('region',{name:'Resources and evidence'})).toHaveTextContent('Customer-scoped previews')
  expect(f.requests.filter(r=>r.path.endsWith('/orders')).every(r=>r.query.get('limit')==='5'&&r.query.get('customerId')===customerId)).toBe(true)
  expect(f.requests.some(r=>/dispatch\/board|\/drivers|\/tractors|\/audit/.test(r.path))).toBe(false)
})
it('keeps URL filters and browser history independent of table presentation',async()=>{
  const u=userEvent.setup(),f=orderFixture();await screen.findByRole('link',{name:'ACME-101'})
  await u.selectOptions(screen.getByLabelText('Order state'),'PLANNED');await screen.findByText('No Orders found in this Customer scope.')
  expect(f.router.state.location.search).toContain('status=PLANNED')
  await act(async()=>{await f.router.navigate(-1)});await screen.findByRole('link',{name:'ACME-101'})
  expect(screen.getByLabelText('Order state')).toHaveValue('')
  await act(async()=>{await f.router.navigate(1)});await screen.findByText('No Orders found in this Customer scope.')
  expect(screen.getByLabelText('Order state')).toHaveValue('PLANNED')
})
it('associates creation validation with its named form and never submits an invalid request',async()=>{
  const u=userEvent.setup(),f=orderFixture('/customer/orders/new');await screen.findByRole('heading',{name:'Create Order'})
  await u.click(screen.getByRole('button',{name:'Review'}));await u.click(screen.getByLabelText('I reviewed this Order and its requested itinerary.'));await u.click(screen.getByRole('button',{name:'Confirm and create Order'}))
  await screen.findByText(/Review required references/)
  expect(screen.getByRole('form',{name:'Order request'})).toHaveAttribute('aria-describedby','customer-create-error')
  expect(f.requests.some(r=>r.method==='POST')).toBe(false)
})
it('supports keyboard step selection and preserves the draft when returning to Information',async()=>{
  const u=userEvent.setup();orderFixture('/customer/orders/new');const number=await screen.findByLabelText('Order number (required)');await u.type(number,'DRAFT-KEYBOARD')
  const cargo=screen.getByRole('button',{name:'Cargo'});cargo.focus();await u.keyboard('{Enter}');expect(cargo).toHaveAttribute('aria-current','step')
  await u.click(screen.getByRole('button',{name:'Information'}));expect(screen.getByLabelText('Order number (required)')).toHaveValue('DRAFT-KEYBOARD')
  expect(screen.getAllByRole('button').filter(b=>b.getAttribute('aria-current')==='step')).toHaveLength(1)
})
it('keeps the requested Stop reorder and human-readable address fields keyboard accessible',async()=>{
  const u=userEvent.setup();orderFixture('/customer/orders/new');await screen.findByRole('heading',{name:'Create Order'});await u.click(screen.getByRole('button',{name:'Requested Stops'}));await u.type(screen.getByLabelText('Destination name'),'First')
  expect(screen.getByLabelText('Address line 1 (optional)')).toBeInTheDocument();await u.click(screen.getByRole('button',{name:'Add requested Stop'}));await u.type(screen.getAllByLabelText('Destination name')[1]!,'Second')
  screen.getByRole('button',{name:'Move Stop 2 up'}).focus();await u.keyboard('{Enter}');expect(screen.getAllByLabelText('Destination name')[0]).toHaveValue('Second')
})
it('separates customer Order details from actual mapped delivery and evidence relationships',async()=>{
  const f=orderFixture(`/customer/orders/${orderId}`);f.state.trips=[trip()];await screen.findByRole('heading',{name:'Order ACME-101'})
  expect(screen.getByRole('heading',{name:'Order information'})).toBeInTheDocument();expect(screen.getByRole('heading',{name:'Requested itinerary'})).toBeInTheDocument()
  const delivery=await screen.findByRole('link',{name:'Delivery and authorized evidence'});expect(delivery).toHaveAttribute('href',`/customer/deliveries/${orderId}/${stopId}`)
  expect(screen.queryByRole('link',{name:/operations\/trips|audit/i})).not.toBeInTheDocument()
})
it('restores keyboard focus after dismissing a Customer-specific action dialog',async()=>{
  const u=userEvent.setup();orderFixture(`/customer/orders/${orderId}`);const trigger=await screen.findByRole('button',{name:'Edit before execution'});await u.click(trigger)
  const dialog=screen.getByRole('dialog',{name:'Edit Order before execution'});expect(dialog).toHaveClass('customer-dialog');expect(within(dialog).getByRole('button',{name:'Cancel'})).toHaveFocus()
  await u.keyboard('{Escape}');expect(screen.queryByRole('dialog')).not.toBeInTheDocument();expect(trigger).toHaveFocus()
})
it('keeps destructive cancellation separate and blocks repeat writes after a version conflict',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`);f.handlers.set(f.root+`/orders/${orderId}/cancellations`,async()=>response({code:'STALE_VERSION'},409))
  await u.click(await screen.findByRole('button',{name:'Cancel Order'}));const d=screen.getByRole('dialog');expect(d).toHaveClass('customer-dialog--danger')
  await u.type(screen.getByLabelText('Cancellation reason'),'Reviewed customer request');await u.click(screen.getByLabelText('I reviewed this cancellation and its consequences.'));await u.click(within(d).getByRole('button',{name:'Confirm cancellation'}))
  await screen.findByText(/record changed while you were editing/);expect(within(d).getByRole('button',{name:'Confirm cancellation'})).toBeDisabled();expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(1)
})
it('keeps delivered and administratively completed Orders distinct in the redesigned detail',async()=>{
  const f=orderFixture(`/customer/orders/${orderId}`);f.state.orders=[order({status:'DELIVERED'})];await screen.findByRole('heading',{name:'Order ACME-101'})
  expect(await screen.findByRole('button',{name:'Complete delivered Order'})).toBeInTheDocument();expect(screen.getByText('DELIVERED',{selector:'.status-badge'})).toHaveAttribute('data-tone','neutral')
  expect(screen.queryByRole('button',{name:'Edit before execution'})).not.toBeInTheDocument()
})
it('does not promote Last Known to Current Location in the redesigned inventory',async()=>{
  const f=customerReadFixture();f.readState.trailers=[inventoryTrailer({locationState:'LOCATION_UNKNOWN',currentLocationId:null,currentLocationName:null})]
  const row=await screen.findByRole('row',{name:/T114/});expect(within(row).getByText('LOCATION_UNKNOWN')).toHaveAttribute('data-tone','unknown');expect(within(row).getByText('Historical Yard')).toBeInTheDocument();expect(within(row).getByText('Unknown')).toBeInTheDocument()
})
it('preserves published versus accepted evidence and never authorizes a file during rendering',async()=>{
  const f=customerReadFixture(`/customer/documents/${documentId}`);await screen.findByRole('heading',{name:'BOL document'})
  expect(screen.getByRole('heading',{name:'Secure file access'})).toBeInTheDocument();expect(screen.getByText('File publication does not independently establish accepted POD.')).toBeInTheDocument()
  expect(screen.getByText('SIGNED_BOL')).toBeInTheDocument();expect(f.requests.every(r=>r.method==='GET')).toBe(true);expect(screen.queryByRole('link',{name:'Open authorized file'})).not.toBeInTheDocument()
})
it('rejects foreign accepted-file data before revealing the new document surfaces',async()=>{
  const f=customerReadFixture('/customer/documents');f.handlers.set(f.readRoot+'/documents',async()=>response({customerId,items:[evidenceFile({customerId:customerB,orderNumber:'PRIVATE-FOREIGN'})],nextCursor:null}));await screen.findByRole('alert');expect(screen.queryByText('PRIVATE-FOREIGN')).not.toBeInTheDocument();expect(screen.queryByRole('link',{name:'View BOL'})).not.toBeInTheDocument()
})
it('retires redesigned private document content and cache when Customer changes',async()=>{
  const u=userEvent.setup(),f=customerReadFixture('/customer',[dispatcher([customerId,customerB])]);await screen.findByRole('heading',{name:'Select a Customer'});await u.selectOptions(screen.getByLabelText('Active Customer'),customerId);await screen.findByRole('heading',{name:'Customer dashboard'})
  await act(async()=>{await f.router.navigate(`/customer/documents/${documentId}`)});await screen.findByRole('heading',{name:'BOL document'});await u.selectOptions(screen.getByLabelText('Active Customer'),customerB);await screen.findByRole('heading',{name:'Customer dashboard'})
  expect(screen.queryByRole('heading',{name:'Secure file access'})).not.toBeInTheDocument();expect(f.cache.getQueryCache().getAll().some(q=>q.queryKey[0]==='customer'&&q.queryKey[4]===customerId)).toBe(false)
})
it('rejects every isolated DEMO write including download authorization',async()=>{
  const api=createCustomerReviewApi();for(const method of ['POST','PATCH','DELETE'] as const)await expect(api.request('/tenants/demo/orders',{method,body:{}})).rejects.toMatchObject({problem:{status:503}})
  await expect(api.request('/tenants/demo/documents/demo/download-url',{method:'POST',body:{}})).rejects.toMatchObject({problem:{status:503,code:'OBJECT_STORAGE_UNAVAILABLE'}})
})
