import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { z } from 'zod'
import { useAuth } from '../../shared/auth/context'
import { ApiProblem } from '../../shared/api/client'
import { useCustomerContext } from './context'
import { customerKey, validateOwner } from './queries'
import { orderDetail, orderPage, progressPage, siteChoice, locationChoice, trailerChoice, createOrderInput } from './orderContracts'
import type { CustomerOwner } from './queries'
import type { ApiClient } from '../../shared/api/client'

export function customerOrderApi(api: ApiClient, owner: CustomerOwner) {
  validateOwner(owner)
  const base = `/tenants/${owner.tenantId}`
  const get = <T,>(path: string, schema: z.ZodType<T>, signal?: AbortSignal) => api.request<unknown>(base+path,{signal}).then(value => schema.parse(value))
  const own = <T extends {customerId:string}>(data:T) => { if(data.customerId !== owner.customerId) throw new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'}); return data }
  const uuid = (value:string) => z.uuid().parse(value)
  return {
    orders: async (filters:Record<string,string>,signal?:AbortSignal) => {
      const params = new URLSearchParams({...filters,customerId:owner.customerId})
      const page = await get(`/orders?${params}`,orderPage,signal)
      page.items.forEach(own)
      if(new Set(page.items.map(r=>r.id)).size!==page.items.length || page.nextCursor && page.nextCursor === filters.cursor) throw new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'})
      return page
    },
    order: async (id:string,signal?:AbortSignal) => { const data=own(await get(`/orders/${uuid(id)}`,orderDetail,signal)); if(data.id!==id) throw new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'}); return data },
    progress: async (id:string,cursor:string|undefined,signal?:AbortSignal) => {
      const page=own(await get(`/orders/${uuid(id)}/progress?limit=20${cursor?`&cursor=${uuid(cursor)}`:''}`,progressPage,signal))
      if(page.orderId!==id||cursor&&page.nextCursor===cursor||new Set(page.items.map(t=>t.id)).size!==page.items.length||new Set(page.changes.map(c=>c.version)).size!==page.changes.length||page.changes.some(c=>c.version>page.orderVersion))throw new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'})
      return page
    },
    actions: (signal?:AbortSignal) => get(`/orders/customer-context?customerId=${owner.customerId}`,z.object({customerId:z.literal(owner.customerId),capabilities:z.array(z.string())}).strict(),signal),
    sites: (signal?:AbortSignal) => get(`/sites?customerId=${owner.customerId}`,z.object({items:z.array(siteChoice)}),signal),
    locations: async (id:string,signal?:AbortSignal) => {
      const data=await get(`/sites/${uuid(id)}/locations?customerId=${owner.customerId}`,z.object({items:z.array(locationChoice)}),signal)
      if(data.items.some(l=>l.siteId!==id||l.customerId!==owner.customerId))throw new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'});return data
    },
    trailers: async (signal?:AbortSignal) => {
      const data=await get(`/trailers?designatedCustomerId=${owner.customerId}`,z.object({items:z.array(trailerChoice)}),signal)
      if(data.items.some(t=>t.designatedCustomerId!==owner.customerId))throw new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'});return data
    },
    create: async (body:unknown,signal:AbortSignal) => {
      const request=createOrderInput.parse(body);own(request)
      const data=own(orderDetail.parse(await api.request(base+'/orders',{method:'POST',body:request,signal})))
      if(data.status!=='OPEN'||data.orderNumber!==request.orderNumber)throw new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'});return data
    },
    command: async (id:string,path:string,body:unknown,signal:AbortSignal,status:z.infer<typeof orderDetail>['status']) => {
      const expected=z.object({expectedVersion:z.number().int().nonnegative()}).parse(body).expectedVersion
      const data=own(orderDetail.parse(await api.request(`${base}/orders/${uuid(id)}${path}`,{method:path?'POST':'PATCH',body,signal})))
      if(data.id!==id||data.status!==status||!(data.version===expected+1||!path&&data.version===expected))throw new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'});return data
    },
    tripCommand: async (id:string,path:'reroutes'|'trailer-substitution-decisions',body:unknown,signal:AbortSignal,expected:{version:number;status:string;trailerId?:string}) => {
      const data=z.object({id:z.literal(uuid(id)),status:z.string(),version:z.number().int().nonnegative(),plannedInitialTrailerId:z.uuid().nullable()}).parse(await api.request(`${base}/trips/${id}/${path}`,{method:'POST',body,signal}))
      if(data.status!==expected.status||data.version!==expected.version+1||expected.trailerId!==undefined&&data.plannedInitialTrailerId!==expected.trailerId)throw new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'})
      return data
    },
  }
}
export function useOrderApi() { const owner=useCustomerContext(), {api}=useAuth(); return {owner,api:customerOrderApi(api,owner)} }
export function useOrderActions() {
  const {owner,api}=useOrderApi()
  return useQuery({queryKey:customerKey(owner,'order-actions'),queryFn:({signal})=>api.actions(signal),retry:false,gcTime:0,staleTime:0})
}
export function useOrders(filters:Record<string,string>) {
  const {owner,api}=useOrderApi()
  return useInfiniteQuery({queryKey:customerKey(owner,'orders',filters),initialPageParam:undefined as string|undefined,
    queryFn:({signal,pageParam})=>api.orders({...filters,...(pageParam?{cursor:pageParam}:{})},signal),
    getNextPageParam:(last,_pages,_lastParam,params)=>last.nextCursor && !params.includes(last.nextCursor)?last.nextCursor:undefined,
    retry:false,gcTime:0,staleTime:0,
  })
}
export function useOrder(id:string) {
  const {owner,api}=useOrderApi()
  return useQuery({queryKey:customerKey(owner,'order',id),queryFn:({signal})=>api.order(id,signal),retry:false,gcTime:0,staleTime:0})
}
export function useProgress(id:string) {
  const {owner,api}=useOrderApi()
  return useInfiniteQuery({queryKey:customerKey(owner,'order-progress',id),initialPageParam:undefined as string|undefined,
    queryFn:({signal,pageParam})=>api.progress(id,pageParam,signal),getNextPageParam:(last,_pages,_last,params)=>last.nextCursor&&!params.includes(last.nextCursor)?last.nextCursor:undefined,retry:false,gcTime:0,staleTime:0})
}
export function useOrderSites(enabled=true) {
  const {owner,api}=useOrderApi()
  return useQuery({queryKey:customerKey(owner,'order-sites'),queryFn:({signal})=>api.sites(signal),enabled,retry:false,gcTime:0})
}
export function useOrderTrailers(enabled=true) {
  const {owner,api}=useOrderApi()
  return useQuery({queryKey:customerKey(owner,'order-trailers'),queryFn:({signal})=>api.trailers(signal),enabled,retry:false,gcTime:0})
}
export function useOrderLocations(site:string) {
  const {owner,api}=useOrderApi()
  return useQuery({queryKey:customerKey(owner,'order-locations',site),queryFn:({signal})=>api.locations(site,signal),enabled:!!site,retry:false,gcTime:0})
}
