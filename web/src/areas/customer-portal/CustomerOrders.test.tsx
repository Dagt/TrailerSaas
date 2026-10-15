import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { orderFixture, order, trip, commandReceipt, orderId, tripId, stopId, siteId, trailerId, alternateId } from '../../test/customerOrderFixture'
import { customerId, customerB, dispatcher, response } from '../../test/customerFixture'

afterEach(()=>vi.unstubAllGlobals())
const detail=()=>screen.findByRole('heading',{name:'Order ACME-101'})
const creation=()=>screen.findByRole('heading',{name:'Create Order'})
async function readyCreate(u:ReturnType<typeof userEvent.setup>) {
  await creation();await u.type(await screen.findByLabelText('Order number (required)'),'NEW-900')
  await u.click(screen.getByRole('button',{name:'Requested Stops'}));await u.type(screen.getByLabelText('Destination name'),'Customer receiving')
  await u.click(screen.getByRole('button',{name:'Review'}));await u.click(screen.getByLabelText('I reviewed this Order and its requested itinerary.'))
}
it('lists only active Customer Orders and preserves real state/planning filters through keyset pages',async()=>{
  const u=userEvent.setup(), f=orderFixture('/customer/orders?status=OPEN&unplanned=true&customerId=forged')
  f.handlers.set(f.root+'/orders',async(_options,q)=>response({items:[order({id:q?.get('cursor')?'00000000-0000-4000-8000-000000000111':orderId,orderNumber:q?.get('cursor')?'SECOND':'FIRST'})],nextCursor:q?.get('cursor')?null:'opaque-next'}))
  await screen.findByRole('link',{name:'FIRST'});await u.click(screen.getByRole('button',{name:'Load more Orders'}));await screen.findByRole('link',{name:'SECOND'})
  const requests=f.requests.filter(r=>r.path===f.root+'/orders')
  expect(requests.every(r=>r.query.get('customerId')===customerId&&r.query.get('status')==='OPEN'&&r.query.get('unplanned')==='true')).toBe(true)
  expect(requests.at(-1)?.query.get('cursor')).toBe('opaque-next')
  await u.selectOptions(screen.getByLabelText('Order state'),'DELIVERED')
  await waitFor(()=>expect(f.requests.some(r=>r.query.get('status')==='DELIVERED'&&!r.query.has('cursor'))).toBe(true))
})
it('uses real dashboard previews without pretending first-page totals or dispatch board access',async()=>{
  const f=orderFixture('/customer');f.state.orders=[order({status:'CANCELLING'})]
  await screen.findByRole('heading',{name:'Customer dashboard'});await screen.findByRole('heading',{name:'Orders in cancellation'})
  await waitFor(()=>expect(screen.getAllByRole('link',{name:'ACME-101'}).length).toBeGreaterThan(0))
  expect(screen.getAllByText(/Preview, not a total count/).length).toBe(7)
  expect(f.requests.some(r=>r.path.includes('/dispatch/board')||/\/trips$/.test(r.path))).toBe(false)
})
it('creates real OPEN work with optional references, multiple cargo lines and requested Stops after explicit review',async()=>{
  const u=userEvent.setup(),f=orderFixture('/customer/orders/new');await creation()
  await u.type(screen.getByLabelText('Order number (required)'),'NEW-900');await u.type(screen.getByLabelText('PO number (optional)'),'PO-90')
  await u.click(screen.getByRole('button',{name:'Cargo'}));await u.click(screen.getByRole('button',{name:'Add cargo line'}))
  await u.type(screen.getByLabelText('Description'),'Sheets');await u.type(screen.getByLabelText('Quantity'),'12.5')
  await u.click(screen.getByRole('button',{name:'Add cargo line'}));await u.type(screen.getAllByLabelText('Description')[1]!,'Boxes')
  await u.click(screen.getByRole('button',{name:'Requested Stops'}));await u.type(screen.getByLabelText('Destination name'),'First destination')
  await u.click(screen.getByRole('button',{name:'Add requested Stop'}));await u.type(screen.getAllByLabelText('Destination name')[1]!,'Second destination')
  await u.click(screen.getByRole('button',{name:'Move Stop 2 up'}));await u.click(screen.getByRole('button',{name:'Review'}))
  expect(f.requests.some(r=>r.method==='POST')).toBe(false)
  await u.click(screen.getByLabelText('I reviewed this Order and its requested itinerary.'));await u.click(screen.getByRole('button',{name:'Confirm and create Order'}))
  await screen.findByRole('heading',{name:'Order NEW-900'})
  const request=f.fetcher.mock.calls.find(([,options])=>options?.method==='POST')!
  const body=JSON.parse(String(request[1]?.body))
  expect(body).toMatchObject({customerId,orderNumber:'NEW-900',poNumber:'PO-90',shipmentNumber:null,plannedTrailerId:null,cargoLines:[{description:'Sheets',quantity:12.5,palletCount:null},{description:'Boxes',quantity:null}]})
  expect(body.requestedStops.map((s:{sequenceNumber:number;siteSnapshot:{name:string}})=>[s.sequenceNumber,s.siteSnapshot.name])).toEqual([[1,'Second destination'],[2,'First destination']])
  expect(request[1]?.headers).not.toHaveProperty('Idempotency-Key')
  expect(f.requests.some(r=>r.path.includes('/hook')||r.path.includes('/trips')&&r.method==='POST')).toBe(false)
})
it('validates required data without sending a malformed Order',async()=>{
  const u=userEvent.setup(),f=orderFixture('/customer/orders/new');await creation();await u.click(screen.getByRole('button',{name:'Review'}))
  await u.click(screen.getByLabelText('I reviewed this Order and its requested itinerary.'));await u.click(screen.getByRole('button',{name:'Confirm and create Order'}))
  await screen.findByText(/Review required references/);expect(f.requests.some(r=>r.method==='POST')).toBe(false)
})
it.each([503,408])('keeps an uncertain creation draft and blocks duplicate POST after %s',async status=>{
  const u=userEvent.setup(),f=orderFixture('/customer/orders/new')
  f.handlers.set(f.root+'/orders',async options=>options?.method==='POST'?response({},status):response({items:[],nextCursor:null}))
  await readyCreate(u);await u.click(screen.getByRole('button',{name:'Confirm and create Order'}));await screen.findByText(/Creation result is uncertain/)
  await u.click(screen.getByRole('button',{name:'Confirm and create Order'}));expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(1)
  await u.click(screen.getByRole('link',{name:'Review server Orders / return to list'}));await screen.findByRole('heading',{name:'Orders'})
  await u.click(await screen.findByRole('link',{name:'Create Order'}));await creation()
  expect(screen.getByLabelText('Order number (required)')).toHaveValue('NEW-900');expect(screen.getByText(/Creation result is uncertain/)).toBeInTheDocument()
})
it('retains draft through same-owner access revalidation and clears it on Customer change',async()=>{
  const u=userEvent.setup(),f=orderFixture('/customer/orders/new',[dispatcher([customerId,customerB])])
  await screen.findByRole('heading',{name:'Select a Customer'});await u.selectOptions(screen.getByLabelText('Active Customer'),customerId);await screen.findByRole('heading',{name:'Customer dashboard'})
  await act(async()=>{await f.router.navigate('/customer/orders/new')});await creation();await u.type(await screen.findByLabelText('Order number (required)'),'Private draft')
  await u.click(screen.getByRole('button',{name:'Refresh access'}));await creation();expect(await screen.findByLabelText('Order number (required)')).toHaveValue('Private draft')
  await u.selectOptions(screen.getByLabelText('Active Customer'),customerB);await screen.findByRole('heading',{name:'Customer dashboard'})
  await act(async()=>{await f.router.navigate('/customer/orders/new')});await creation();expect(await screen.findByLabelText('Order number (required)')).toHaveValue('')
})
it('displays references/cargo/requested work and sends only actual reference PATCH with received version',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`);await detail();await u.click(screen.getByRole('button',{name:'Edit before execution'}))
  await u.clear(screen.getByLabelText('PO number'));await u.type(screen.getByLabelText('PO number'),'Corrected')
  await u.click(screen.getByLabelText('I reviewed these pre-execution changes.'));await u.click(screen.getByRole('button',{name:'Save reviewed changes'}))
  await waitFor(()=>expect(f.state.orders[0]?.poNumber).toBe('Corrected'))
  const body=JSON.parse(String(f.fetcher.mock.calls.find(([,o])=>o?.method==='PATCH')?.[1]?.body))
  expect(body).toEqual({expectedVersion:0,poNumber:'Corrected',shipmentNumber:null,bolReference:null})
})
it('does not offer cargo/destination rewrite for committed planning',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`);f.state.orders[0]=order({status:'PLANNED'});f.state.requestEditable=false
  await detail();await u.click(screen.getByRole('button',{name:'Edit before execution'}))
  expect(screen.queryByLabelText('Revise unallocated cargo and requested itinerary')).not.toBeInTheDocument()
})
it('sends active cancellation to the Order only, leaving physical/shared Trip resolution authoritative',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`);f.state.orders[0]=order({status:'IN_PROGRESS'})
  let body:Record<string,unknown>|undefined
  f.handlers.set(f.root+`/orders/${orderId}/cancellations`,async options=>{body=JSON.parse(String(options?.body));f.state.orders[0]=order({status:'CANCELLING',version:1});return response(f.state.orders[0])})
  await detail();expect(screen.queryByRole('button',{name:'Edit before execution'})).not.toBeInTheDocument()
  await u.click(screen.getByRole('button',{name:'Start active cancellation'}));await u.type(screen.getByLabelText('Cancellation reason'),'Customer stopped remaining work')
  await u.selectOptions(screen.getByLabelText('Resolution'),'HOLD_CONTACT_DISPATCH');await u.type(screen.getByLabelText('Operational instructions'),'Contact dispatcher; hold remaining cargo')
  await u.click(screen.getByLabelText('I reviewed this cancellation and its consequences.'));await u.click(screen.getByRole('button',{name:'Confirm active cancellation'}))
  await waitFor(()=>expect(body).toMatchObject({expectedVersion:0,resolution:{type:'HOLD_CONTACT_DISPATCH',destinationSiteId:null,instructions:'Contact dispatcher; hold remaining cargo'}}))
  expect(f.requests.some(r=>/\/trips\/.*\/cancellations/.test(r.path))).toBe(false)
})
it.each(['OPEN','PLANNED','IN_PROGRESS','PARTIALLY_DELIVERED','DELIVERED','COMPLETED','CANCELLING','CANCELLED'] as const)('respects %s and never exposes a manual DELIVERED transition',async status=>{
  const f=orderFixture(`/customer/orders/${orderId}`);f.state.orders[0]=order({status});await detail()
  if(status==='DELIVERED')await screen.findByRole('button',{name:'Complete delivered Order'})
  else expect(screen.queryByRole('button',{name:'Complete delivered Order'})).not.toBeInTheDocument()
  expect(screen.queryByRole('button',{name:/mark delivered/i})).not.toBeInTheDocument()
})
it('administratively completes only with explicit confirmation and the actual DELIVERED version',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`);f.state.orders[0]=order({status:'DELIVERED',version:8})
  let body:unknown;f.handlers.set(f.root+`/orders/${orderId}/completion`,async o=>{body=JSON.parse(String(o?.body));f.state.orders[0]=order({status:'COMPLETED',version:9});return response(f.state.orders[0])})
  await detail();await u.click(await screen.findByRole('button',{name:'Complete delivered Order'}));expect(body).toBeUndefined()
  await u.click(screen.getByLabelText('I confirm administrative closure of this delivered Order.'));await u.click(screen.getByRole('button',{name:'Confirm administrative completion'}))
  await waitFor(()=>expect(body).toEqual({expectedVersion:8}))
})
it('reroutes a real mapped Stop with current Trip version and records no requested-Stop PATCH',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`);f.state.orders[0]=order({status:'IN_PROGRESS'});f.state.trips=[trip()]
  let body:unknown;f.handlers.set(f.root+`/trips/${tripId}/reroutes`,async o=>{body=JSON.parse(String(o?.body));return response(commandReceipt())})
  await detail();await u.click(await screen.findByRole('button',{name:'Change destination: Receiving'}))
  await u.type(screen.getByLabelText('Reason'),'Receiving relocated');await u.type(screen.getByLabelText('Instructions'),'Use the new receiving entrance')
  await u.click(screen.getByLabelText('I reviewed the previous and new operational destination.'));await u.click(screen.getByRole('button',{name:'Confirm operational reroute'}))
  await waitFor(()=>expect(body).toMatchObject({expectedVersion:12,affectedStopId:stopId,newSiteId:siteId,reason:'Receiving relocated',instructions:'Use the new receiving entrance'}))
  expect(f.requests.some(r=>r.method==='PATCH')).toBe(false)
})
it.each(['AUTHORIZE','REJECT'])('records explicit %s substitution without fake version, scan request or physical Hook',async decision=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`);f.state.orders[0]=order({status:'IN_PROGRESS'});f.state.trips=[trip()]
  let body:unknown;f.handlers.set(f.root+`/trips/${tripId}/trailer-substitution-decisions`,async o=>{body=JSON.parse(String(o?.body));return response(commandReceipt(decision==='AUTHORIZE'?alternateId:trailerId))})
  await detail();await u.click(await screen.findByRole('button',{name:'Decide alternate Trailer'}));await u.selectOptions(screen.getByLabelText('Decision'),decision)
  await u.selectOptions(screen.getByLabelText('Requested Trailer'),alternateId);await u.type(screen.getByLabelText('Decision reason'),'Reported equipment mismatch')
  await u.click(screen.getByLabelText('I reviewed the expected Trailer, requested Trailer and decision.'));await u.click(screen.getByRole('button',{name:decision==='AUTHORIZE'?'Authorize alternate Trailer':'Reject and retain expected Trailer'}))
  await waitFor(()=>expect(body).toEqual({expectedTrailerId:trailerId,requestedTrailerId:alternateId,decision,reason:'Reported equipment mismatch'}))
  expect(f.requests.some(r=>/hook|drop|scan/.test(r.path))).toBe(false)
})
it('blocks stale edit retries until Reload latest and another explicit review',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`)
  f.handlers.set(f.root+`/orders/${orderId}`,async o=>o?.method==='PATCH'?response({code:'STALE_VERSION'},409):response(f.state.orders[0]))
  await detail();await u.click(screen.getByRole('button',{name:'Edit before execution'}));await u.type(screen.getByLabelText('PO number'),' changed')
  await u.click(screen.getByLabelText('I reviewed these pre-execution changes.'));await u.click(screen.getByRole('button',{name:'Save reviewed changes'}))
  await screen.findByText(/record changed while you were editing/);expect(screen.getByRole('button',{name:'Save reviewed changes'})).toBeDisabled()
  expect(f.requests.filter(r=>r.method==='PATCH')).toHaveLength(1);expect(screen.getAllByRole('button',{name:'Reload latest'}).length).toBeGreaterThan(0)
})
it.each([401,403,404,409,503])('uses safe %s read failures without leaking protected metadata or retrying endlessly',async status=>{
  const f=orderFixture(`/customer/orders/${orderId}`);f.handlers.set(f.root+`/orders/${orderId}`,async()=>response({detail:'SECRET customer and driver',code:status===409?'STALE_VERSION':undefined},status))
  await waitFor(()=>expect(screen.queryByRole('heading',{name:'Order ACME-101'})).not.toBeInTheDocument())
  await waitFor(()=>expect(f.requests.filter(r=>r.path===f.root+`/orders/${orderId}`).length).toBeGreaterThan(0))
  expect(screen.queryByText(/SECRET/)).not.toBeInTheDocument();expect(f.requests.filter(r=>r.path===f.root+`/orders/${orderId}`)).toHaveLength(1)
})
it('rejects an HTTP200 Order for a different Customer before rendering it',async()=>{
  const f=orderFixture(`/customer/orders/${orderId}`);f.state.orders[0]=order({customerId:customerB,orderNumber:'FOREIGN-SECRET'})
  await screen.findByRole('alert');expect(screen.queryByText(/FOREIGN-SECRET/)).not.toBeInTheDocument()
})
it('treats malformed successful create as uncertain rather than an ACK',async()=>{
  const u=userEvent.setup(),f=orderFixture('/customer/orders/new');f.handlers.set(f.root+'/orders',async o=>o?.method==='POST'?response({id:orderId}):response({items:[],nextCursor:null}))
  await readyCreate(u);await u.click(screen.getByRole('button',{name:'Confirm and create Order'}));await screen.findByText(/Creation result is uncertain/)
  expect(f.router.state.location.pathname).toBe('/customer/orders/new');expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(1)
})
it('fences a pending mutation response after Customer switch and does not navigate the new scope',async()=>{
  const u=userEvent.setup(),f=orderFixture('/customer',[dispatcher([customerId,customerB])]);let finish!:(r:Response)=>void
  await screen.findByRole('heading',{name:'Select a Customer'});await u.selectOptions(screen.getByLabelText('Active Customer'),customerId);await screen.findByRole('heading',{name:'Customer dashboard'})
  await act(async()=>{await f.router.navigate('/customer/orders/new')});await readyCreate(u)
  f.handlers.set(f.root+'/orders',async o=>o?.method==='POST'?new Promise(r=>{finish=r}):response({items:[],nextCursor:null}))
  await u.click(screen.getByRole('button',{name:'Confirm and create Order'}));await waitFor(()=>expect(finish).toBeTypeOf('function'))
  await u.selectOptions(screen.getByLabelText('Active Customer'),customerB);await screen.findByRole('heading',{name:'Customer dashboard'})
  await act(async()=>finish(response(order(),201)))
  expect(f.router.state.location.pathname).toBe('/customer');expect(screen.queryByRole('heading',{name:'Order ACME-101'})).not.toBeInTheDocument()
  expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(1)
})
it('uses labelled keyboard-accessible controls and no direct arbitrary-UUID request',async()=>{
  const f=orderFixture('/customer/orders/not-a-uuid');await screen.findByText('Order unavailable.')
  expect(f.requests.some(r=>r.path.endsWith('/orders/not-a-uuid'))).toBe(false)
  const nav=screen.getByRole('navigation',{name:'Customer navigation'});expect(within(nav).getByRole('link',{name:'Orders'})).toHaveAttribute('href','/customer/orders')
})
it('reconciles a committed creation with lost ACK through reads without posting a duplicate',async()=>{
  const u=userEvent.setup(),f=orderFixture('/customer/orders/new'), original=f.handlers.get(f.root+'/orders')!
  f.handlers.set(f.root+'/orders',async(options,query)=>{const accepted=await original(options,query);return options?.method==='POST'?response({},503):accepted})
  await readyCreate(u);await u.click(screen.getByRole('button',{name:'Confirm and create Order'}));await screen.findByText(/Creation result is uncertain/)
  await u.click(screen.getByRole('link',{name:'Review server Orders / return to list'}));await u.click(await screen.findByRole('link',{name:'NEW-900'}))
  await screen.findByRole('heading',{name:'Order NEW-900'});expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(1)
})
it('accepts normalized destination choices only through explicit Customer-filtered Site and Location reads',async()=>{
  const u=userEvent.setup(),f=orderFixture('/customer/orders/new');await creation();await u.type(await screen.findByLabelText('Order number (required)'),'NORMALIZED')
  await u.selectOptions(screen.getByLabelText('Planned Trailer (optional)'),await screen.findByRole('option',{name:'T114'}))
  await u.click(screen.getByRole('button',{name:'Requested Stops'}));await u.selectOptions(screen.getByLabelText('stop-0 destination type'),'site')
  await u.selectOptions(screen.getByLabelText('Site'),await screen.findByRole('option',{name:'Authorized receiving'}))
  await u.click(screen.getByRole('button',{name:'Review'}));expect(screen.getByText(/Authorized receiving/)).toBeInTheDocument();expect(screen.getByText(/Planned Trailer: T114/)).toBeInTheDocument()
  await u.click(screen.getByLabelText('I reviewed this Order and its requested itinerary.'));await u.click(screen.getByRole('button',{name:'Confirm and create Order'}));await screen.findByRole('heading',{name:'Order NORMALIZED'})
  const body=JSON.parse(String(f.fetcher.mock.calls.find(([,options])=>options?.method==='POST')?.[1]?.body))
  expect(body.requestedStops[0]).toMatchObject({siteId,locationId:null,siteSnapshot:null})
  expect(body.plannedTrailerId).toBe(trailerId);await screen.findByText(/Authorized receiving.*current authorized Site label/)
  expect(f.requests.filter(r=>r.path.endsWith('/sites')||r.path.endsWith('/locations')).every(r=>r.query.get('customerId')===customerId)).toBe(true)
})
it('preserves pre-execution cancellation history and displays only the acknowledged terminal state',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`);let body:unknown
  f.handlers.set(f.root+`/orders/${orderId}/cancellations`,async o=>{body=JSON.parse(String(o?.body));f.state.orders[0]=order({status:'CANCELLED',version:1,cancelledAt:'2026-10-15T18:00:00Z'});return response(f.state.orders[0])})
  await detail();await u.click(await screen.findByRole('button',{name:'Cancel Order'}));expect(screen.queryByLabelText('Operational instructions')).not.toBeInTheDocument()
  await u.type(screen.getByLabelText('Cancellation reason'),'Customer no longer needs pickup');await u.click(screen.getByLabelText('I reviewed this cancellation and its consequences.'))
  expect(body).toBeUndefined();await u.click(screen.getByRole('button',{name:'Confirm cancellation'}))
  await waitFor(()=>expect(body).toEqual({expectedVersion:0,reason:'Customer no longer needs pickup'}));await screen.findByText('State CANCELLED · Version 1')
})
it('does not acknowledge a completed command whose successful response contradicts the requested outcome',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`);f.state.orders[0]=order({status:'DELIVERED',version:8})
  f.handlers.set(f.root+`/orders/${orderId}/completion`,async()=>response(order({status:'OPEN',version:9})))
  await detail();await u.click(await screen.findByRole('button',{name:'Complete delivered Order'}));await u.click(screen.getByLabelText('I confirm administrative closure of this delivered Order.'))
  await u.click(screen.getByRole('button',{name:'Confirm administrative completion'}));await screen.findByText(/server result is uncertain/)
  expect(screen.getByRole('dialog')).toBeInTheDocument();expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(1)
})
it('removes a stale reroute modal after an authoritative Trip revision changes',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`);f.state.orders[0]=order({status:'IN_PROGRESS'});f.state.trips=[trip()]
  await detail();await u.click(await screen.findByRole('button',{name:'Change destination: Receiving'}));expect(screen.getByRole('dialog')).toBeInTheDocument()
  f.state.trips=[trip({version:13})]
  await act(async()=>{await f.cache.invalidateQueries({predicate:q=>q.queryKey[5]==='order-progress'})})
  await waitFor(()=>expect(screen.queryByRole('dialog')).not.toBeInTheDocument());await screen.findByText(/Operational context changed/)
  expect(f.requests.some(r=>r.method==='POST')).toBe(false)
})
it('renders the real specific Order decision history separately from physical fulfillment',async()=>{
  const f=orderFixture(`/customer/orders/${orderId}`);f.state.orders[0]=order({status:'CANCELLING',version:1})
  f.handlers.set(f.root+`/orders/${orderId}/progress`,async()=>response({orderId,customerId,orderVersion:1,requestEditable:false,items:[],nextCursor:null,changes:[{version:1,changeType:'ORDER_CANCELLATION_REQUESTED',recordedAt:'2026-10-15T18:00:00Z',reason:'Remaining work stopped',resolution:{type:'HOLD_CONTACT_DISPATCH',destinationSiteId:null,instructions:'Hold remaining cargo'}}]}))
  await detail();await screen.findByRole('heading',{name:'Recent Order decisions'});expect(screen.getByText(/Hold remaining cargo/)).toBeInTheDocument()
  expect(screen.getByText(/generic audit are separate/)).toBeInTheDocument()
})
it('aborts submission on same-owner revalidation and retains an uncertain draft without late navigation',async()=>{
  const u=userEvent.setup(),f=orderFixture('/customer/orders/new');let finish!:(r:Response)=>void
  await readyCreate(u);f.handlers.set(f.root+'/orders',async options=>options?.method==='POST'?new Promise(r=>{finish=r}):response({items:[],nextCursor:null}))
  await u.click(screen.getByRole('button',{name:'Confirm and create Order'}));await waitFor(()=>expect(finish).toBeTypeOf('function'))
  const sending=f.requests.find(r=>r.method==='POST')!
  await u.click(screen.getByRole('button',{name:'Refresh access'}));await creation();await screen.findByText(/Creation result is uncertain/)
  await act(async()=>finish(response(order({orderNumber:'NEW-900'}),201)))
  expect(sending.signal?.aborted).toBe(true);expect(f.router.state.location.pathname).toBe('/customer/orders/new');expect(f.requests.filter(r=>r.method==='POST')).toHaveLength(1)
})
it.each(['duplicate Trip','impossible history'])('rejects a contradictory successful progress response: %s',async defect=>{
  const f=orderFixture(`/customer/orders/${orderId}`)
  f.handlers.set(f.root+`/orders/${orderId}/progress`,async()=>response({orderId,customerId,orderVersion:0,requestEditable:true,items:defect==='duplicate Trip'?[trip(),trip()]:[],nextCursor:null,changes:defect==='impossible history'?[{version:1,changeType:'ORDER_EDITED',recordedAt:'2026-10-15T18:00:00Z',reason:null,resolution:null}]:[]}))
  await detail();await screen.findByRole('alert');expect(screen.queryByRole('button',{name:'Decide alternate Trailer'})).not.toBeInTheDocument()
  expect(f.requests.some(r=>r.method==='POST')).toBe(false)
})
it('rejects foreign Trailer choices even in an HTTP 200 response',async()=>{
  const f=orderFixture('/customer/orders/new')
  f.handlers.set(f.root+'/trailers',async()=>response({items:[{id:alternateId,operationalNumber:'PRIVATE-EQUIPMENT',designatedCustomerId:customerB,active:true,serviceabilityState:'IN_SERVICE'}]}))
  await creation();await screen.findByText(/Trailer selection is unavailable/);expect(screen.queryByRole('option',{name:'PRIVATE-EQUIPMENT'})).not.toBeInTheDocument()
})
it('requires a new cancellation review when its reason changes',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`)
  await detail();await u.click(await screen.findByRole('button',{name:'Cancel Order'}));await u.type(screen.getByLabelText('Cancellation reason'),'First reason')
  await u.click(screen.getByLabelText('I reviewed this cancellation and its consequences.'));expect(screen.getByRole('button',{name:'Confirm cancellation'})).toBeEnabled()
  await u.type(screen.getByLabelText('Cancellation reason'),' changed');expect(screen.getByRole('button',{name:'Confirm cancellation'})).toBeDisabled();expect(f.requests.some(r=>r.method==='POST')).toBe(false)
})
it('closes operational actions if a continuation page belongs to another Order version',async()=>{
  const u=userEvent.setup(),f=orderFixture(`/customer/orders/${orderId}`),next='00000000-0000-4000-8000-000000000999'
  f.handlers.set(f.root+`/orders/${orderId}/progress`,async(_options,q)=>response({orderId,customerId,orderVersion:q?.get('cursor')?1:0,requestEditable:false,items:q?.get('cursor')?[]:[trip()],nextCursor:q?.get('cursor')?null:next,changes:[]}))
  await detail();await screen.findByRole('button',{name:'Decide alternate Trailer'});await u.click(screen.getByRole('button',{name:'Load more linked work'}))
  await screen.findByText(/Progress and Order versions need refreshing/);expect(screen.queryByRole('button',{name:'Decide alternate Trailer'})).not.toBeInTheDocument()
})
it('resolves normalized requested Site and Location labels only through authorized Customer reads',async()=>{
  const f=orderFixture(`/customer/orders/${orderId}`),locationId='00000000-0000-4000-8000-000000000888'
  f.state.orders[0]=order({requestedStops:[{...order().requestedStops[0]!,siteId,locationId,siteSnapshot:null}]})
  f.handlers.set(f.root+`/sites/${siteId}/locations`,async()=>response({items:[{id:locationId,siteId,customerId,name:'Receiving Dock 4',active:true}]}))
  await detail();await screen.findByText(/Authorized receiving.*current authorized Site label/);await screen.findByText(/Requested Location.*Receiving Dock 4/)
  expect(f.requests.filter(r=>r.path.endsWith('/sites')||r.path.endsWith('/locations')).every(r=>r.query.get('customerId')===customerId)).toBe(true)
})
