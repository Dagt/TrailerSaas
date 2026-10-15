import { vi } from 'vitest'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { QueryClient } from '@tanstack/react-query'
import { AppProviders } from '../app/AppProviders'
import { appRoutes } from '../app/routes'
import { fakeAuth, tenantA, tenantB, customerId, stamps, resourceId } from './adminFixture'
import type { CurrentUser, Membership } from '../areas/tenant-admin/contracts'
export const customerB = '00000000-0000-4000-8000-000000000007'
export const customerC = '00000000-0000-4000-8000-000000000008'
export const dispatcher = (ids = [customerId], tenantId = tenantA, tenantName = 'Alpha Logistics'): Membership => ({ tenantId, tenantName, tenantActive: true, status: 'ACTIVE', capabilities: [], roles: ids.map(customerId => ({ roleCode: 'CUSTOMER_DISPATCHER',scopeType:'CUSTOMER',customerId })) })
export const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } })
export function customerFixture(path = '/customer', memberships = [dispatcher()], auth = fakeAuth({ uid: 'customer-user' })) {
  const me: CurrentUser = { userId: resourceId, displayName: 'Dispatcher', email:'dispatch@example.test', memberships }
  const rows: Record<string,unknown> = {
    [customerId]: { ...stamps,id:customerId,name:'Acme Packaging',status:'ACTIVE' },
    [customerB]: { ...stamps,id:customerB,name:'Stanfast',status:'ACTIVE' },
    [customerC]: { ...stamps,id:customerC,name:'Beta Customer',status:'ACTIVE' },
  }
  const requests: {path:string; signal?:AbortSignal|null; method:string}[] = []
  const handlers = new Map<string,(options?: RequestInit)=>Promise<Response>>()
  const fetcher = vi.fn<typeof fetch>(async (input,options) => {
    const url = new URL(String(input)), path = url.pathname.replace('/api/v1','')
    requests.push({ path,signal:options?.signal,method:options?.method??'GET' })
    const handler = handlers.get(path); if (handler) return handler(options)
    if (path === '/me') return response(me)
    const match = /^\/tenants\/([^/]+)\/customers\/([^/]+)$/.exec(path)
    if (match) {
      const m = me.memberships.find(m=>m.tenantId===match[1]&&m.status==='ACTIVE'&&m.tenantActive===true)
      if (!m?.roles.some(r=>r.scopeType==='CUSTOMER'&&r.roleCode==='CUSTOMER_DISPATCHER'&&r.customerId===match[2])) return response({code:'CUSTOMER_SCOPE_DENIED'},403)
      return rows[match[2]!] ? response(rows[match[2]!]) : response({},404)
    }
    return response({},404)
  })
  vi.stubGlobal('fetch',fetcher)
  const cache = new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false},mutations:{retry:false}}})
  const router = createMemoryRouter(appRoutes,{initialEntries:[path]})
  const view = render(<AppProviders auth={auth} client={cache} baseUrl="https://api.example.test/api/v1"><RouterProvider router={router}/></AppProviders>)
  return {...view,me,rows,requests,handlers,cache,router,auth,fetcher}
}
export { tenantA,tenantB,customerId,resourceId,fakeAuth }
