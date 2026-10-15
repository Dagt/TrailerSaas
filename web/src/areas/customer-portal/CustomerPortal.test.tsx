import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { QueryClient, focusManager } from '@tanstack/react-query'
import { readFileSync } from 'node:fs'
const styles = readFileSync('src/styles.css', 'utf8')
import { customerFixture, dispatcher, response, tenantA, tenantB, customerId, customerB, customerC, resourceId, fakeAuth } from '../../test/customerFixture'
import { customerKey, customerQuery, customerChoices } from './queries'
import { ApiProblem } from '../../shared/api/client'

afterEach(() => { vi.unstubAllGlobals(); focusManager.setFocused(undefined) })
const dashboard = () => screen.findByRole('heading', { name: 'Customer dashboard' })
const path = (tenant=tenantA, id=customerId) => `/tenants/${tenant}/customers/${id}`

it('recognizes a Customer role with capabilities [] and verifies the sole context through real API boundaries', async () => {
  const f = customerFixture(); await dashboard()
  expect(screen.getByRole('navigation',{name:'Customer navigation'})).toBeInTheDocument()
  expect(screen.getByText('Workspace for Acme Packaging in Alpha Logistics.')).toBeInTheDocument()
  expect(screen.queryByRole('link',{name:'Tenant Admin'})).not.toBeInTheDocument()
  expect(screen.queryByRole('link',{name:'Transport Operations'})).not.toBeInTheDocument()
  expect(screen.queryByLabelText('Active Customer')).not.toBeInTheDocument()
  expect(f.requests.every(r=>r.path==='/me'||r.path===path()||r.path===`/tenants/${tenantA}/orders`&&r.query.get('customerId')===customerId)).toBe(true)
  expect(f.requests.every(r=>r.method==='GET')).toBe(true)
  const key=customerKey({uid:'customer-user',userId:resourceId,tenantId:tenantA,membership:dispatcher(),customerId},'context')
  expect(f.cache.getQueryData(key)).toMatchObject({id:customerId})
  expect(localStorage.length).toBe(0)
})
it('uses the shared Firebase sign-in instead of a separate Customer account', async () => {
  const u=userEvent.setup(), auth=fakeAuth(null), f=customerFixture('/customer',undefined,auth)
  expect(await screen.findByRole('heading',{name:'Customer Portal'})).toBeInTheDocument()
  expect(f.requests).toHaveLength(0)
  await u.type(screen.getByLabelText('Email'),'dispatcher@example.test');await u.type(screen.getByLabelText('Password'),'synthetic-password')
  await u.click(screen.getByRole('button',{name:'Sign in'}));await dashboard()
  expect(auth.signIn).toHaveBeenCalledWith('dispatcher@example.test','synthetic-password')
})
it.each(['SUSPENDED','DISABLED','wrong role','wrong scope','no Customer','inactive Tenant','unknown Tenant state'])('does not authorize a protected URL from %s hints',async condition=>{
  const m=dispatcher()
  if(condition==='SUSPENDED'||condition==='DISABLED')m.status=condition
  else if(condition==='wrong role')m.roles[0]!.roleCode='DRIVER'
  else if(condition==='wrong scope')m.roles[0]!.scopeType='TENANT'
  else if(condition==='no Customer')m.roles=[]
  else if(condition==='inactive Tenant')m.tenantActive=false
  else delete m.tenantActive
  const f=customerFixture('/customer/documents',[m])
  expect(await screen.findByRole('heading',{name:'Customer access is not available'})).toBeInTheDocument()
  expect(f.requests.every(r=>r.path==='/me')).toBe(true)
  expect(screen.queryByRole('navigation',{name:'Customer navigation'})).not.toBeInTheDocument()
})
it('holds the protected workspace while the server identity is loading',async()=>{
  let finish!:(r:Response)=>void
  const f=customerFixture();f.handlers.set('/me',()=>new Promise(resolve=>{finish=resolve}))
  expect(await screen.findByRole('status')).toHaveTextContent('Loading')
  expect(screen.queryByRole('navigation',{name:'Customer navigation'})).not.toBeInTheDocument()
  await act(async()=>finish(response(f.me)));await dashboard()
})
it('switches Customers, clears old resources and returns to the dashboard',async()=>{
  const u=userEvent.setup(),f=customerFixture('/customer',[dispatcher([customerId,customerB])])
  expect(await screen.findByRole('heading',{name:'Select a Customer'})).toBeInTheDocument()
  await u.selectOptions(screen.getByLabelText('Active Customer'),customerId);await dashboard()
  const old=customerKey({uid:'customer-user',userId:resourceId,tenantId:tenantA,membership:f.me.memberships[0]!,customerId},'context')
  await u.click(screen.getByRole('link',{name:'Orders'}));await screen.findByRole('heading',{name:'Orders'})
  await u.selectOptions(screen.getByLabelText('Active Customer'),customerB);await dashboard()
  expect(screen.getByText('Workspace for Stanfast in Alpha Logistics.')).toBeInTheDocument()
  expect(f.router.state.location.pathname).toBe('/customer')
  expect(f.cache.getQueryData(old)).toBeUndefined()
})
it('switches Tenants without retaining the old Customer or its cache',async()=>{
  const u=userEvent.setup(),f=customerFixture('/customer',[dispatcher(),dispatcher([customerC],tenantB,'Beta Logistics')])
  expect(await screen.findByRole('heading',{name:'Select a Tenant'})).toBeInTheDocument()
  await u.selectOptions(screen.getByLabelText('Customer portal Tenant'),tenantA);await dashboard()
  await u.click(screen.getByRole('link',{name:'Documents'}));await screen.findByRole('heading',{name:'Documents'})
  await u.selectOptions(screen.getByLabelText('Customer portal Tenant'),tenantB);await dashboard()
  expect(screen.getByText('Workspace for Beta Customer in Beta Logistics.')).toBeInTheDocument()
  expect(f.cache.getQueryCache().getAll().filter(q=>q.queryKey[0]==='customer').every(q=>q.queryKey[3]===tenantB)).toBe(true)
})
it.each(['Orders','Trailers','Deliveries','Documents'])('protects %s and keeps deferred catalogs honest without fake actions',async label=>{
  const f=customerFixture(`/customer/${label.toLowerCase()}`)
  await screen.findByRole('heading',{name:label})
  if(label==='Orders') {
    await screen.findByText('No Orders found in this Customer scope.')
    expect(f.requests.filter(r=>r.path.endsWith('/orders')).every(r=>r.query.get('customerId')===customerId)).toBe(true)
  } else expect(screen.getByText(/will be available in a later phase/)).toBeInTheDocument()
  expect(screen.queryByRole('button',{name:/create|save|cancel order|download/i})).not.toBeInTheDocument()
  expect(f.requests.every(r=>r.path==='/me'||r.path===path()||label==='Orders'&&(r.path===`/tenants/${tenantA}/orders`||r.path.endsWith('/orders/customer-context'))&&r.query.get('customerId')===customerId)).toBe(true)
})
it('shows informative dashboard cards without numeric inventory or operational projections',async()=>{
  const f=customerFixture();await dashboard()
  for(const label of ['Pending Orders','Active Deliveries','Trailer Inventory','Recent Evidence'])expect(screen.getByRole('link',{name:label})).toBeInTheDocument()
  expect(screen.getAllByText('These catalogs remain deferred to #60.')).toHaveLength(3)
  expect(f.requests.every(r=>!r.path.includes('/trailers')&&!r.path.includes('/documents')&&(!r.path.endsWith('/orders')||r.query.get('customerId')===customerId))).toBe(true)
})
it.each([401,403,404,409,503])('handles %s safely without rendering stale content or private server error detail',async status=>{
  const f=customerFixture();f.handlers.set(path(),async()=>response({detail:'Private other Customer, SQL password',code:status===409?'STALE_VERSION':'DENIED'},status))
  if(status===401)expect(await screen.findByRole('button',{name:'Refresh session'})).toBeInTheDocument()
  else if(status===403||status===404)expect(await screen.findByRole('heading',{name:'No Customer available'})).toBeInTheDocument()
  else expect(await screen.findByRole('alert')).toBeInTheDocument()
  expect(screen.queryByRole('heading',{name:'Customer dashboard'})).not.toBeInTheDocument()
  expect(screen.queryByText(/Private other Customer/)).not.toBeInTheDocument()
  const count=f.requests.length;await new Promise(resolve=>setTimeout(resolve,20));expect(f.requests).toHaveLength(count)
})
it('recovers an expired session through the existing explicit refresh',async()=>{
  const u=userEvent.setup(),f=customerFixture();f.handlers.set(path(),async()=>response({},401))
  await screen.findByRole('button',{name:'Refresh session'});f.handlers.delete(path())
  await u.click(screen.getByRole('button',{name:'Refresh session'}));await dashboard()
  expect(f.auth.token).toHaveBeenCalledWith(true)
})
it.each(['inactive','malformed','foreign identity'])('rejects an %s Customer context without showing unrelated metadata',async kind=>{
  const f=customerFixture()
  f.handlers.set(path(),async()=>response(kind==='inactive'?{...(f.rows[customerId] as object),status:'INACTIVE'}:kind==='malformed'?{id:customerId,name:'Private invalid'}:f.rows[customerB]))
  if(kind==='inactive')await screen.findByRole('heading',{name:'No Customer available'})
  else await screen.findByRole('alert')
  expect(screen.queryByRole('heading',{name:'Customer dashboard'})).not.toBeInTheDocument()
  expect(screen.queryByText('Private invalid')).not.toBeInTheDocument();expect(screen.queryByText('Stanfast')).not.toBeInTheDocument()
})
it('removes the protected view/cache when the Customer role is revoked on refresh',async()=>{
  const u=userEvent.setup(),f=customerFixture();await dashboard();f.me.memberships[0]!.roles=[]
  await u.click(screen.getByRole('button',{name:'Refresh access'}));await screen.findByRole('heading',{name:'Customer access is not available'})
  expect(screen.queryByText('Acme Packaging')).not.toBeInTheDocument()
  await waitFor(()=>expect(f.cache.getQueryCache().getAll().filter(q=>q.queryKey[0]==='customer')).toHaveLength(0))
})
it('fresh API denial overrides an unchanged /me assignment hint',async()=>{
  const u=userEvent.setup(),f=customerFixture();await dashboard();f.handlers.set(path(),async()=>response({},403))
  await u.click(screen.getByRole('button',{name:'Refresh access'}));await screen.findByRole('heading',{name:'No Customer available'})
  expect(screen.queryByRole('heading',{name:'Customer dashboard'})).not.toBeInTheDocument()
})
it('retires the protected view immediately during authorization revalidation',async()=>{
  let finish!:(r:Response)=>void
  const u=userEvent.setup(),f=customerFixture();await dashboard();f.handlers.set('/me',()=>new Promise(resolve=>{finish=resolve}))
  await u.click(screen.getByRole('button',{name:'Refresh access'}))
  expect(screen.queryByRole('heading',{name:'Customer dashboard'})).not.toBeInTheDocument()
  await act(async()=>finish(response({...f.me,memberships:[]})))
  await screen.findByRole('heading',{name:'Customer access is not available'})
})
it('does not download a Tenant-wide Customer directory for dual-role users',async()=>{
  const m=dispatcher();m.roles.push({roleCode:'TENANT_ADMIN',scopeType:'TENANT',customerId:null})
  const f=customerFixture('/customer',[m]);await dashboard()
  expect(screen.getByRole('link',{name:'Tenant Admin'})).toBeInTheDocument()
  expect(f.requests.every(r=>r.path==='/me'||r.path===path()||r.path===`/tenants/${tenantA}/orders`&&r.query.get('customerId')===customerId)).toBe(true)
})
it('does not accept arbitrary URL context parameters or resource paths as authority',async()=>{
  const f=customerFixture(`/customer/orders/${customerB}?customerId=${customerB}&tenantId=${tenantB}`)
  await screen.findByRole('alert')
  expect(screen.queryByRole('heading',{name:/Order /})).not.toBeInTheDocument()
  expect(f.requests.every(r=>r.path==='/me'||r.path===path()||r.path.startsWith(`/tenants/${tenantA}/orders/`))).toBe(true)
  expect(f.requests.some(r=>r.path.startsWith(`/tenants/${tenantB}/`))).toBe(false)
})
it('supports accessible keyboard navigation with a scope banner and labelled navigation',async()=>{
  const u=userEvent.setup();customerFixture();await dashboard()
  const orders=within(screen.getByRole('navigation',{name:'Customer navigation'})).getByRole('link',{name:'Orders'})
  orders.focus();await u.keyboard('{Enter}');await screen.findByRole('heading',{name:'Orders'})
  expect(screen.getByRole('link',{name:'Orders'})).toHaveAttribute('aria-current','page')
  expect(screen.getByRole('link',{name:'Skip to content'})).toBeInTheDocument()
})
it('ignores a late Customer A result after switching to Customer B',async()=>{
  let finish!:(r:Response)=>void
  const u=userEvent.setup(),f=customerFixture('/customer',[dispatcher([customerId,customerB])]);await screen.findByRole('heading',{name:'Select a Customer'})
  f.handlers.set(path(),()=>new Promise(resolve=>{finish=resolve}))
  await u.selectOptions(screen.getByLabelText('Active Customer'),customerId)
  await waitFor(()=>expect(f.requests.filter(r=>r.path===path())).toHaveLength(2))
  const old=f.requests.at(-1)!
  await u.selectOptions(screen.getByLabelText('Active Customer'),customerB);await dashboard()
  await act(async()=>finish(response({...(f.rows[customerId] as object),name:'Late private Alpha'})))
  expect(old.signal?.aborted).toBe(true);expect(screen.queryByText(/Late private Alpha/)).not.toBeInTheDocument()
  expect(screen.getByText('Workspace for Stanfast in Alpha Logistics.')).toBeInTheDocument()
})
it('cancels a previous Tenant download and ignores its late result',async()=>{
  let finish!:(r:Response)=>void
  const u=userEvent.setup(),f=customerFixture('/customer',[dispatcher(),dispatcher([customerC],tenantB,'Beta Logistics')])
  f.handlers.set(path(),()=>new Promise(resolve=>{finish=resolve}));await screen.findByRole('heading',{name:'Select a Tenant'})
  await u.selectOptions(screen.getByLabelText('Customer portal Tenant'),tenantA);await waitFor(()=>expect(f.requests.some(r=>r.path===path())).toBe(true))
  const old=f.requests.find(r=>r.path===path())!
  await u.selectOptions(screen.getByLabelText('Customer portal Tenant'),tenantB);await dashboard()
  await act(async()=>finish(response({...(f.rows[customerId] as object),name:'Old Tenant confidential'})))
  expect(old.signal?.aborted).toBe(true);expect(screen.queryByText(/Old Tenant confidential/)).not.toBeInTheDocument()
})
it('does not let an old owner late 401 expire a new account',async()=>{
  let finish!:(r:Response)=>void
  const f=customerFixture();f.handlers.set(path(),()=>new Promise(resolve=>{finish=resolve}))
  await waitFor(()=>expect(f.requests.some(r=>r.path===path())).toBe(true))
  f.me.userId=customerC;f.me.memberships=[dispatcher([customerC],tenantB,'Beta Logistics')]
  await act(async()=>f.auth.emit({uid:'new-owner'}));await dashboard()
  await act(async()=>finish(response({},401)))
  expect(screen.queryByRole('button',{name:'Sign in'})).not.toBeInTheDocument()
  expect(screen.getByText('Workspace for Beta Customer in Beta Logistics.')).toBeInTheDocument()
  expect(f.cache.getQueryCache().getAll().some(q=>q.queryKey[1]==='customer-user')).toBe(false)
})
it('clears private cache on logout while a request is active and ignores its late success',async()=>{
  let finish!:(r:Response)=>void
  const u=userEvent.setup(),f=customerFixture();f.handlers.set(path(),()=>new Promise(resolve=>{finish=resolve}))
  await waitFor(()=>expect(f.requests.some(r=>r.path===path())).toBe(true));const old=f.requests.find(r=>r.path===path())!
  await u.click(screen.getByRole('button',{name:'Sign out'}));await screen.findByRole('button',{name:'Sign in'})
  await act(async()=>finish(response(f.rows[customerId])))
  expect(old.signal?.aborted).toBe(true);expect(f.cache.getQueryCache().getAll()).toHaveLength(0)
  expect(screen.queryByText('Acme Packaging')).not.toBeInTheDocument()
})
it('fences an unmounted portal download and releases its namespace',async()=>{
  let finish!:(r:Response)=>void
  const f=customerFixture();f.handlers.set(path(),()=>new Promise(resolve=>{finish=resolve}))
  await waitFor(()=>expect(f.requests.some(r=>r.path===path())).toBe(true));f.unmount()
  await act(async()=>finish(response(f.rows[customerId])))
  await waitFor(()=>expect(f.cache.getQueryCache().getAll().filter(q=>q.queryKey[0]==='customer')).toHaveLength(0))
})
it('query-layer validation rejects manipulated IDs without treating the hint as an authorization grant',async()=>{
  const api={request:vi.fn()},membership=dispatcher()
  const owner={uid:'owner',userId:resourceId,tenantId:tenantA,membership,customerId:customerB}
  const options=customerQuery(api,owner)
  await expect(new QueryClient().fetchQuery(options)).rejects.toBeInstanceOf(ApiProblem)
  expect(api.request).not.toHaveBeenCalled()
})
it('does not claim a complete choice list from a partial malformed response',async()=>{
  const api={request:vi.fn().mockResolvedValueOnce({id:customerId}).mockResolvedValueOnce({id:customerB})}
  await expect(customerChoices(api,{uid:'owner',userId:resourceId,tenantId:tenantA,membership:dispatcher([customerId,customerB])},new AbortController().signal)).rejects.toThrow()
})

it.each(['SUSPENDED','DISABLED','Tenant disabled'])('retires a previously open context after %s is observed',async state=>{
  const u=userEvent.setup(),f=customerFixture();await dashboard()
  if(state==='SUSPENDED'||state==='DISABLED')f.me.memberships[0]!.status=state
  else f.me.memberships[0]!.tenantActive=false
  await u.click(screen.getByRole('button',{name:'Refresh access'}));await screen.findByRole('heading',{name:'Customer access is not available'})
  expect(screen.queryByText('Acme Packaging')).not.toBeInTheDocument()
})
it.each(['/admin','/operations'])('a Customer-only account cannot enter protected %s content',async entry=>{
  const f=customerFixture(entry)
  await screen.findByRole('heading',{name:entry==='/admin'?'Admin access is not available':'Operations access is not available'})
  expect(screen.queryByRole('navigation',{name:'Admin navigation'})).not.toBeInTheDocument()
  expect(screen.queryByRole('navigation',{name:'Operations navigation'})).not.toBeInTheDocument()
  expect(f.requests.every(r=>r.path==='/me')).toBe(true)
})
it('does not open the scope if Customer deactivates between choices and detail',async()=>{
  let reads=0
  const f=customerFixture();f.handlers.set(path(),async()=>response({...(f.rows[customerId] as object),status:++reads===1?'ACTIVE':'INACTIVE'}))
  await screen.findByRole('heading',{name:'Customer unavailable'})
  expect(screen.queryByRole('navigation',{name:'Customer navigation'})).not.toBeInTheDocument()
})
it('handles malformed identity data without blank views or reading a Customer',async()=>{
  const f=customerFixture();f.handlers.set('/me',async()=>response({userId:'invalid',memberships:'not-an-array'}))
  await screen.findByRole('alert');expect(f.requests.every(r=>r.path==='/me')).toBe(true)
})
it('an account without any memberships receives a safe empty access state',async()=>{
  const f=customerFixture('/customer',[]);await screen.findByRole('heading',{name:'Customer access is not available'})
  expect(f.requests.every(r=>r.path==='/me')).toBe(true)
})

it('revalidates revoked membership when the workspace regains focus',async()=>{
  const f=customerFixture();await dashboard();focusManager.setFocused(false);f.me.memberships[0]!.status='DISABLED'
  await act(async()=>focusManager.setFocused(true));await screen.findByRole('heading',{name:'Customer access is not available'})
  expect(screen.queryByText('Acme Packaging')).not.toBeInTheDocument()
})
it('keeps usable labelled navigation in tablet/mobile layout rules without inventing device QA',async()=>{
  customerFixture();await dashboard()
  expect(styles).toMatch(/@media \(max-width: 850px\)/)
  expect(styles).toContain('.customer-workspace { grid-template-columns: minmax(0,1fr)')
  expect(screen.getByRole('navigation',{name:'Customer navigation'})).toBeInTheDocument()
})
it('rejects a manipulated Customer option outside the server assignment set',async()=>{
  const u=userEvent.setup(),f=customerFixture('/customer',[dispatcher([customerId,customerB])]);await screen.findByRole('heading',{name:'Select a Customer'})
  const picker=screen.getByLabelText('Active Customer');const option=document.createElement('option');option.value=customerC;option.textContent='Forged choice';picker.append(option)
  await u.selectOptions(picker,customerC)
  expect(f.requests.some(r=>r.path===path(tenantA,customerC))).toBe(false)
  expect(screen.queryByRole('heading',{name:'Customer dashboard'})).not.toBeInTheDocument()
})

it('the canonical Tenant field cannot diverge from the actual membership context',async()=>{
  const api={request:vi.fn()},owner={uid:'owner',userId:resourceId,tenantId:tenantB,membership:dispatcher(),customerId}
  await expect(new QueryClient().fetchQuery(customerQuery(api,owner))).rejects.toBeInstanceOf(ApiProblem)
  expect(api.request).not.toHaveBeenCalled()
})
