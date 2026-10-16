import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { customerReadFixture, inventoryTrailer, delivered, evidenceFile, documentId } from '../../test/customerReadFixture'
import { orderId, stopId, trailerId, alternateId } from '../../test/customerOrderFixture'
import { customerId, customerB, customerC, dispatcher, response, tenantA, tenantB } from '../../test/customerFixture'
import { stamps } from '../../test/adminFixture'

afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals()})
it('rejects an empty page with a continuation instead of suggesting more inventory',async()=>{
  const f=customerReadFixture();f.handlers.set(f.root+'/trailers',async()=>response({items:[],nextCursor:'inconsistent-next'}))
  await screen.findByRole('alert');expect(screen.queryByRole('button',{name:'Load more Trailers'})).not.toBeInTheDocument()
})
it('rejects a repeated inventory cursor without looping or mixing pages',async()=>{
  const u=userEvent.setup(),f=customerReadFixture();f.handlers.set(f.root+'/trailers',async(_o,q)=>response({items:[inventoryTrailer(q?.has('cursor')?{id:alternateId,operationalNumber:'Invalid second page'}:{})],nextCursor:'repeated'}))
  await screen.findByRole('link',{name:'T114'});await u.click(screen.getByRole('button',{name:'Load more Trailers'}));await screen.findByRole('alert')
  expect(screen.queryByText('Invalid second page')).not.toBeInTheDocument();expect(f.requests.filter(r=>r.path.endsWith('/trailers'))).toHaveLength(2)
})
it('rejects foreign Customer document rows before revealing their metadata',async()=>{
  const f=customerReadFixture('/customer/documents');f.handlers.set(f.readRoot+'/documents',async()=>response({customerId,items:[evidenceFile({customerId:customerB,orderNumber:'Private foreign evidence'})],nextCursor:null}))
  await screen.findByRole('alert');expect(screen.queryByText('Private foreign evidence')).not.toBeInTheDocument()
})
it.each(['missingReceipt','wrongReceiptTime','wrongReceiptMode'] as const)('rejects inconsistent accepted delivery evidence: %s',async variant=>{
  const f=customerReadFixture('/customer/deliveries');f.readState.deliveries=[delivered(variant==='missingReceipt'?{evidenceMode:null,acceptedAt:null}:variant==='wrongReceiptTime'?{documents:[evidenceFile({acceptedAt:'2026-01-01T00:00:00Z'})]}:{documents:[evidenceFile({evidenceMode:'NO_RECEIVER_AVAILABLE'})]})]
  await screen.findByRole('alert');expect(screen.queryByRole('link',{name:'Delivery for ACME-101'})).not.toBeInTheDocument()
})
const viewerPath=`/customer/documents/${documentId}`
const viewer=()=>screen.findByRole('heading',{name:'BOL document'})
const downloadButton=()=>screen.findByRole('button',{name:'Request authorized download'})
it('loads real scoped inventory and sends only actual server filters with forced Customer',async()=>{
  const u=userEvent.setup(),f=customerReadFixture(`/customer/trailers?designatedCustomerId=${customerB}&customerId=${customerB}`)
  await screen.findByRole('link',{name:'T114'});await u.selectOptions(screen.getByLabelText('Serviceability'),'OUT_OF_SERVICE')
  await screen.findByText('No designated Trailers found for these filters.')
  expect(f.requests.filter(r=>r.path.endsWith('/trailers')).every(r=>r.query.get('designatedCustomerId')===customerId&&r.query.has('limit'))).toBe(true)
  expect(f.requests.some(r=>r.query.get('serviceabilityState')==='OUT_OF_SERVICE')).toBe(true)
  expect(f.requests.some(r=>r.path.endsWith('/customers')||r.path.endsWith('/trips')||r.path.includes('/dispatch/board'))).toBe(false)
})
it('searches by exact operational number and current authorized Location using server requests',async()=>{
  const u=userEvent.setup(),f=customerReadFixture();await screen.findByRole('link',{name:'T114'})
  await u.type(screen.getByLabelText('Exact Trailer number'),'T114');await u.click(screen.getByRole('button',{name:'Find Trailer'}))
  await waitFor(()=>expect(f.requests.some(r=>r.query.get('operationalNumber')==='T114')).toBe(true))
  await u.selectOptions(screen.getByLabelText('Location from these results'),'00000000-0000-4000-8000-000000000501')
  await waitFor(()=>expect(f.requests.some(r=>r.query.has('currentLocationId'))).toBe(true))
})
it.each(['LOCATION_UNKNOWN','IN_TRANSIT'] as const)('keeps Last Known separate from Current Location for %s',async state=>{
  const f=customerReadFixture();f.readState.trailers=[inventoryTrailer({locationState:state,currentLocationId:null,currentLocationName:null})]
  const row=await screen.findByRole('row',{name:/T114/}),cells=within(row).getAllByRole('cell')
  expect(cells[1]).toHaveTextContent(state==='LOCATION_UNKNOWN'?'Unknown':'In transit');expect(cells[1]).not.toHaveTextContent('Historical Yard')
  expect(cells[2]).toHaveTextContent('Historical Yard');expect(screen.queryByRole('img',{name:/map/i})).not.toBeInTheDocument()
})
it('shows current Trailer detail and current-period history without physical controls',async()=>{
  const u=userEvent.setup(),f=customerReadFixture();await u.click(await screen.findByRole('link',{name:'T114'}));await screen.findByRole('heading',{name:'Trailer T114'})
  await screen.findByRole('table',{name:'Customer-relevant Trailer movements'})
  expect(screen.getByText(/Earlier and shared-Customer history is withheld/)).toBeInTheDocument()
  expect(screen.getByRole('link',{name:'Trailers currently at Recorded Dock'})).toBeInTheDocument()
  expect(screen.queryByRole('button',{name:/hook|drop|correct|mark oos/i})).not.toBeInTheDocument();expect(f.requests.every(r=>r.method==='GET')).toBe(true)
})
it('paginates actual inventory and retains filters without claiming totals',async()=>{
  const u=userEvent.setup(),f=customerReadFixture();f.handlers.set(f.root+'/trailers',async(_o,q)=>response({items:[inventoryTrailer(q?.has('cursor')?{id:alternateId,operationalNumber:'T116'}:{})],nextCursor:q?.has('cursor')?null:'next-inventory'}))
  await screen.findByRole('link',{name:'T114'});await u.click(screen.getByRole('button',{name:'Load more Trailers'}));await screen.findByRole('link',{name:'T116'})
  expect(f.requests.at(-1)?.query.get('designatedCustomerId')).toBe(customerId)
})
it.each(['foreignCustomer','internalDriver'] as const)('rejects a malformed HTTP 200 inventory projection: %s',async variant=>{
  const f=customerReadFixture();f.handlers.set(f.root+'/trailers',async()=>response({items:[{...inventoryTrailer(),...(variant==='foreignCustomer'?{designatedCustomerId:customerB,operationalNumber:'Private B Trailer'}:{currentDriverName:'Private Driver'})}],nextCursor:null}))
  await screen.findByRole('alert');expect(screen.queryByText('Private B Trailer')).not.toBeInTheDocument();expect(screen.queryByText('Private Driver')).not.toBeInTheDocument();expect(screen.queryByRole('link',{name:'T114'})).not.toBeInTheDocument()
})
it('does not display a foreign Manifest allocation hidden in an otherwise own Trailer detail',async()=>{
  const f=customerReadFixture(`/customer/trailers/${trailerId}`)
  const read=f.handlers.get(f.root+`/trailers/${trailerId}`)!
  f.handlers.set(f.root+`/trailers/${trailerId}`,async(o,q)=>{
    const body=await (await read(o,q)).json() as Record<string,unknown>
    return response({...body,openManifests:[{id:alternateId,status:'OPEN',version:0,updatedAt:stamps.updatedAt,ordersTruncated:false,orders:[{id:alternateId,customerId:customerB,customerName:'Private B Customer',orderNumber:'Private B Order',status:'OPEN'}]}]})
  })
  await screen.findByRole('alert');expect(screen.queryByRole('heading',{name:'Trailer T114'})).not.toBeInTheDocument()
  expect(screen.queryByText('Private B Order')).not.toBeInTheDocument()
})
it('lists multiple actual Stops for one Order and navigates to the correct composite delivery',async()=>{
  const u=userEvent.setup(),f=customerReadFixture('/customer/deliveries');f.readState.deliveries.push(delivered({stopId:alternateId,documents:[evidenceFile({stopId:alternateId})]}))
  await waitFor(()=>expect(screen.getAllByRole('link',{name:'Delivery for ACME-101'})).toHaveLength(2))
  await u.click(screen.getAllByRole('link',{name:'Delivery for ACME-101'})[0]!);await screen.findByRole('heading',{name:'Delivery for ACME-101'})
  expect(screen.getByText(/A completed Stop does not mean the Order/)).toBeInTheDocument()
  expect(screen.getByRole('link',{name:'Open Order ACME-101'})).toHaveAttribute('href',`/customer/orders/${orderId}`)
  expect(screen.getByRole('link',{name:'View BOL'})).toHaveAttribute('href',viewerPath)
})
it('uses server delivery filters rather than downloading every Order or Trip',async()=>{
  const u=userEvent.setup(),f=customerReadFixture('/customer/deliveries');await screen.findByRole('link',{name:'Delivery for ACME-101'})
  await u.selectOptions(screen.getByLabelText('Delivery state'),'EN_ROUTE');await screen.findByText('No authorized deliveries found.')
  expect(f.requests.some(r=>r.path===f.readRoot+'/deliveries'&&r.query.get('status')==='EN_ROUTE')).toBe(true)
  expect(f.requests.some(r=>r.path.endsWith('/trips')||r.path.endsWith('/dispatch/board')||r.path.endsWith('/orders'))).toBe(false)
})
it('historical evidence acceptance does not fabricate a downloadable POD',async()=>{
  const f=customerReadFixture(`/customer/deliveries/${orderId}/${stopId}`);f.readState.deliveries=[delivered({fileAssociationRecorded:false,documents:[]})]
  await screen.findByText(/Historical receipt has no recorded accepted-file identity/)
  expect(screen.queryByRole('link',{name:'View BOL'})).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:/download/i})).not.toBeInTheDocument()
})
it('shared or unavailable evidence is explicitly withheld without declaring it MISSING',async()=>{
  const f=customerReadFixture(`/customer/deliveries/${orderId}/${stopId}`);f.readState.deliveries=[delivered({fileAssociationRecorded:false,documents:[],evidenceMode:null,acceptedAt:null})]
  await screen.findByText(/Shared or unavailable evidence is withheld/);expect(screen.queryByText(/MISSING/)).not.toBeInTheDocument()
})
it('discovers only authorized accepted files and filters type on the server',async()=>{
  const u=userEvent.setup(),f=customerReadFixture('/customer/documents');await screen.findByRole('link',{name:'View BOL'})
  await u.selectOptions(screen.getByLabelText('Document type'),'POD');await screen.findByText('No authorized accepted documents found.')
  expect(f.requests.some(r=>r.path===f.readRoot+'/documents'&&r.query.get('documentType')==='POD')).toBe(true)
  expect(f.requests.some(r=>r.method==='POST')).toBe(false)
})
it('requests a fresh Backend URL only after explicit action and never caches or persists it',async()=>{
  const u=userEvent.setup(),f=customerReadFixture(viewerPath);await viewer();expect(f.requests.some(r=>r.method==='POST')).toBe(false)
  await u.click(await downloadButton());const link=await screen.findByRole('link',{name:'Open authorized file'})
  expect(link).toHaveAttribute('href','https://private.example.test/accepted-file?signature=synthetic');expect(link).toHaveAttribute('rel','noopener noreferrer')
  const request=f.requests.find(r=>r.method==='POST')!;expect(request.path).toBe(f.root+`/documents/${documentId}/download-url`);expect(request.query.get('customerId')).toBe(customerId)
  expect(JSON.stringify(f.cache.getQueryCache().getAll().map(q=>q.state.data))).not.toContain('signature=synthetic');expect(localStorage.length).toBe(0);expect(sessionStorage.length).toBe(0)
  await u.click(screen.getByRole('button',{name:'Discard temporary link'}));expect(screen.queryByRole('link',{name:'Open authorized file'})).not.toBeInTheDocument()
})
it('handles unavailable storage without fabricating download success or disabling the portal',async()=>{
  const u=userEvent.setup(),f=customerReadFixture(viewerPath);f.handlers.set(f.root+`/documents/${documentId}/download-url`,async()=>response({code:'OBJECT_STORAGE_UNAVAILABLE'},503))
  await viewer();await u.click(await downloadButton());await screen.findByText('Document storage is unavailable. No download link was issued.')
  expect(screen.queryByRole('link',{name:'Open authorized file'})).not.toBeInTheDocument();expect(screen.getByRole('link',{name:'Back to documents'})).toBeInTheDocument()
})
it.each(['expired','unsafe'] as const)('rejects an unusable %s URL response without rendering its secret',async variant=>{
  const u=userEvent.setup(),f=customerReadFixture(viewerPath);f.handlers.set(f.root+`/documents/${documentId}/download-url`,async()=>response({downloadUrl:variant==='unsafe'?'javascript:private-secret':'https://private.example.test/file?secret=do-not-render',expiresAt:new Date(Date.now()+(variant==='expired'?-1000:60_000)).toISOString()}))
  await viewer();await u.click(await downloadButton());await screen.findByRole('alert');expect(screen.queryByRole('link',{name:'Open authorized file'})).not.toBeInTheDocument();expect(screen.queryByText(/private-secret|do-not-render/)).not.toBeInTheDocument()
})
it('checks real expiry on click even if the browser suspended its timer',async()=>{
  const u=userEvent.setup();customerReadFixture(viewerPath);await viewer();await u.click(await downloadButton());const link=await screen.findByRole('link',{name:'Open authorized file'})
  vi.useFakeTimers({toFake:['Date']});vi.setSystemTime(Date.now()+61_000);fireEvent.click(link)
  await screen.findByText('The download link expired. Request authorization again.');expect(screen.queryByRole('link',{name:'Open authorized file'})).not.toBeInTheDocument()
})
it('cancels a pending download and fences its late response',async()=>{
  let finish!:(r:Response)=>void;const u=userEvent.setup(),f=customerReadFixture(viewerPath)
  f.handlers.set(f.root+`/documents/${documentId}/download-url`,()=>new Promise(resolve=>{finish=resolve}))
  await viewer();await u.click(await downloadButton());await u.click(await screen.findByRole('button',{name:'Cancel link request'}))
  expect(f.requests.find(r=>r.method==='POST')?.signal?.aborted).toBe(true)
  await act(async()=>finish(response({downloadUrl:'https://private.example.test/late',expiresAt:new Date(Date.now()+60_000).toISOString()})))
  expect(screen.queryByRole('link',{name:'Open authorized file'})).not.toBeInTheDocument()
})
it('removes pending download and caches on logout, ignoring an accepted late authorization',async()=>{
  let finish!:(r:Response)=>void;const u=userEvent.setup(),f=customerReadFixture(viewerPath)
  f.handlers.set(f.root+`/documents/${documentId}/download-url`,()=>new Promise(resolve=>{finish=resolve}));await viewer();await u.click(await downloadButton())
  await u.click(screen.getByRole('button',{name:'Sign out'}));await screen.findByRole('button',{name:'Sign in'})
  await act(async()=>finish(response({downloadUrl:'https://private.example.test/late-secret',expiresAt:new Date(Date.now()+60_000).toISOString()})))
  expect(f.requests.find(r=>r.method==='POST')?.signal?.aborted).toBe(true);expect(screen.queryByRole('link',{name:'Open authorized file'})).not.toBeInTheDocument()
  expect(f.cache.getQueryCache().getAll().filter(q=>q.queryKey[0]==='customer')).toHaveLength(0)
})
it('cannot install a prior Customers signed grant after scope change',async()=>{
  let finish!:(r:Response)=>void;const u=userEvent.setup(),f=customerReadFixture(viewerPath,[dispatcher([customerId,customerB])])
  await screen.findByRole('heading',{name:'Select a Customer'});await u.selectOptions(screen.getByLabelText('Active Customer'),customerId);await screen.findByRole('heading',{name:'Customer dashboard'})
  await act(async()=>{await f.router.navigate(viewerPath)});await viewer();f.handlers.set(f.root+`/documents/${documentId}/download-url`,()=>new Promise(resolve=>{finish=resolve}));await u.click(await downloadButton())
  await u.selectOptions(screen.getByLabelText('Active Customer'),customerB);await screen.findByRole('heading',{name:'Customer dashboard'})
  await act(async()=>finish(response({downloadUrl:'https://private.example.test/old-customer',expiresAt:new Date(Date.now()+60_000).toISOString()})))
  expect(screen.queryByRole('link',{name:'Open authorized file'})).not.toBeInTheDocument();expect(screen.queryByRole('heading',{name:'BOL document'})).not.toBeInTheDocument()
  expect(f.cache.getQueryCache().getAll().some(q=>q.queryKey[0]==='customer'&&q.queryKey[4]===customerId)).toBe(false)
})
it('does not show late inventory under another Tenant',async()=>{
  let finish!:(r:Response)=>void;const u=userEvent.setup(),f=customerReadFixture('/customer',[dispatcher(),dispatcher([customerC],tenantB,'Beta Logistics')])
  await screen.findByRole('heading',{name:'Select a Tenant'});await u.selectOptions(screen.getByLabelText('Customer portal Tenant'),tenantA);await screen.findByRole('heading',{name:'Customer dashboard'})
  f.handlers.set(f.root+'/trailers',()=>new Promise(resolve=>{finish=resolve}));await act(async()=>{await f.router.navigate('/customer/trailers')});await waitFor(()=>expect(f.requests.some(r=>r.path===f.root+'/trailers'&&r.query.get('limit')==='50')).toBe(true))
  await u.selectOptions(screen.getByLabelText('Customer portal Tenant'),tenantB);await screen.findByRole('heading',{name:'Customer dashboard'})
  await act(async()=>finish(response({items:[inventoryTrailer({operationalNumber:'Private previous Tenant'})],nextCursor:null})))
  expect(screen.queryByText('Private previous Tenant')).not.toBeInTheDocument();expect(screen.getByText(/Workspace for Beta Customer in Beta Logistics/)).toBeInTheDocument()
})
it.each([401,403,404,409,503])('handles inventory %s without exposing private errors or stale rows',async status=>{
  const f=customerReadFixture();f.handlers.set(f.root+'/trailers',async()=>response({code:'DENIED',detail:'Private other Customer database exception'},status))
  if(status===401)await screen.findByRole('button',{name:'Refresh session'});else await screen.findByRole('alert')
  expect(screen.queryByText(/Private other Customer/)).not.toBeInTheDocument();expect(screen.queryByRole('link',{name:'T114'})).not.toBeInTheDocument()
})
it('retires accepted-file views and temporary grants after membership access is revoked',async()=>{
  const u=userEvent.setup(),f=customerReadFixture(viewerPath);await viewer();await u.click(await downloadButton());await screen.findByRole('link',{name:'Open authorized file'})
  f.me.memberships=[];await u.click(screen.getByRole('button',{name:'Refresh access'}));await screen.findByRole('heading',{name:'Customer access is not available'})
  expect(screen.queryByRole('link',{name:'Open authorized file'})).not.toBeInTheDocument();expect(screen.queryByRole('heading',{name:'BOL document'})).not.toBeInTheDocument()
})
it('rejects contradictory successful delivery and document responses',async()=>{
  const f=customerReadFixture('/customer/deliveries');f.handlers.set(f.readRoot+'/deliveries',async()=>response({customerId,items:[delivered({documents:[evidenceFile({customerId:customerB})]})],nextCursor:null}))
  await screen.findByRole('alert');expect(screen.queryByRole('link',{name:'Delivery for ACME-101'})).not.toBeInTheDocument()
})
it('does not use arbitrary UUIDs or URL context hints as resource authority',async()=>{
  const f=customerReadFixture(`/customer/documents/${alternateId}?customerId=${customerB}&tenantId=${tenantB}`)
  await screen.findByText(/Document not available in this Customer context/);expect(f.requests.some(r=>r.path.startsWith(`/tenants/${tenantB}`))).toBe(false)
  expect(f.requests.some(r=>r.method==='POST')).toBe(false)
})
it('retains keyboard navigation and semantic tables in the shared responsive layout',async()=>{
  const u=userEvent.setup();customerReadFixture();const link=await screen.findByRole('link',{name:'T114'});link.focus();await u.keyboard('{Enter}')
  await screen.findByRole('heading',{name:'Trailer T114'});expect(await screen.findByRole('table',{name:'Customer-relevant Trailer movements'})).toBeInTheDocument()
  expect(screen.getByRole('navigation',{name:'Customer navigation'})).toBeInTheDocument();expect(screen.getByRole('link',{name:'Skip to content'})).toBeInTheDocument()
})
