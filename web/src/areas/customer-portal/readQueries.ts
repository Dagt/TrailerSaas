import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { z } from 'zod'
import { useAuth } from '../../shared/auth/context'
import { ApiProblem } from '../../shared/api/client'
import type { ApiClient } from '../../shared/api/client'
import { useCustomerContext } from './context'
import { customerKey, validateOwner } from './queries'
import type { CustomerOwner } from './queries'
import * as contract from './readContracts'

const unexpected=()=>new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'})
export const documentIdentity=(d:{id:string;orderId:string;stopId:string})=>`${d.id}:${d.orderId}:${d.stopId}`
export const deliveryIdentity=(d:{orderId:string;stopId:string})=>`${d.orderId}:${d.stopId}`
export function customerReadApi(api:ApiClient,owner:CustomerOwner) {
  validateOwner(owner)
  const base=`/tenants/${owner.tenantId}`, root=`${base}/customers/${owner.customerId}`
  const uuid=(value:string)=>z.uuid().parse(value)
  const read=async<T,>(path:string,schema:z.ZodType<T>,signal?:AbortSignal)=>schema.parse(await api.request<unknown>(path,{signal}))
  const own=<T extends {customerId:string}>(row:T)=>{if(row.customerId!==owner.customerId)throw unexpected();return row}
  const unique=(ids:string[])=>{if(new Set(ids).size!==ids.length)throw unexpected()}
  const page=<T extends {items:unknown[];nextCursor:string|null}>(row:T,cursor?:string)=>{if(cursor&&row.nextCursor===cursor||row.nextCursor&&row.items.length===0)throw unexpected();return row}
  const file=(d:contract.EvidenceDocument)=>own(d)
  const delivery=(d:contract.CustomerDelivery)=>{own(d);if(Boolean(d.evidenceMode)!==Boolean(d.acceptedAt)||d.fileAssociationRecorded&&!d.evidenceMode)throw unexpected();d.documents.forEach(f=>{file(f);if(f.orderId!==d.orderId||f.stopId!==d.stopId||f.evidenceMode!==d.evidenceMode||f.acceptedAt!==d.acceptedAt)throw unexpected()});unique(d.documents.map(documentIdentity));if(d.documents.length&&(!d.fileAssociationRecorded||!d.evidenceMode||!d.acceptedAt||d.status!=='COMPLETED'))throw unexpected();return d}
  return {
    trailers:async(filters:Record<string,string>,signal?:AbortSignal)=>{
      const query=new URLSearchParams({...filters,designatedCustomerId:owner.customerId,limit:filters.limit??'50'})
      const data=page(await read(`${base}/trailers?${query}`,contract.trailerPage,signal),filters.cursor)
      data.items.forEach(t=>{if(t.designatedCustomerId!==owner.customerId)throw unexpected()});unique(data.items.map(t=>t.id));return data
    },
    trailer:async(id:string,signal?:AbortSignal)=>{
      const data=await read(`${base}/trailers/${uuid(id)}?customerId=${owner.customerId}`,contract.customerTrailerDetail,signal)
      if(data.id!==id||data.designatedCustomerId!==owner.customerId||data.designatedCustomer.id!==owner.customerId)throw unexpected()
      data.openManifests.forEach(m=>m.orders.forEach(own));return data
    },
    timeline:async(id:string,cursor?:string,signal?:AbortSignal)=>{
      const query=new URLSearchParams({customerId:owner.customerId,limit:'50',...(cursor?{cursor}:{})})
      const data=page(await read(`${base}/trailers/${uuid(id)}/timeline?${query}`,contract.timelinePage,signal),cursor);unique(data.items.map(e=>e.id));return data
    },
    deliveries:async(filters:Record<string,string>,signal?:AbortSignal)=>{
      const data=page(own(await read(`${root}/deliveries?${new URLSearchParams({limit:'20',...filters})}`,contract.deliveryPage,signal)),filters.cursor)
      data.items.forEach(d=>{delivery(d);if(filters.orderId&&d.orderId!==filters.orderId||filters.status&&d.status!==filters.status)throw unexpected()});unique(data.items.map(deliveryIdentity));return data
    },
    delivery:async(order:string,stop:string,signal?:AbortSignal)=>{
      const d=delivery(await read(`${root}/deliveries/${uuid(order)}/${uuid(stop)}`,contract.delivery,signal));if(d.orderId!==order||d.stopId!==stop)throw unexpected();return d
    },
    documents:async(filters:Record<string,string>,signal?:AbortSignal)=>{
      const data=page(own(await read(`${root}/documents?${new URLSearchParams({limit:'20',...filters})}`,contract.documentPage,signal)),filters.cursor)
      data.items.forEach(d=>{file(d);if(filters.orderId&&d.orderId!==filters.orderId||filters.stopId&&d.stopId!==filters.stopId||filters.documentId&&d.id!==filters.documentId||filters.documentType&&d.documentType!==filters.documentType)throw unexpected()});unique(data.items.map(documentIdentity));return data
    },
    metadata:async(id:string,signal?:AbortSignal)=>{const data=await read(`${base}/documents/${uuid(id)}?customerId=${owner.customerId}`,contract.documentMetadata,signal);if(data.id!==id)throw unexpected();return data},
    download:async(id:string,signal:AbortSignal)=>{
      const parsed=contract.downloadGrant.safeParse(await api.request(`${base}/documents/${uuid(id)}/download-url?customerId=${owner.customerId}`,{method:'POST',body:{},signal}))
      if(!parsed.success)throw unexpected()
      const data=parsed.data
      const url=new URL(data.downloadUrl), expires=Date.parse(data.expiresAt)
      if(url.username||url.password||url.hash||!(url.protocol==='https:'||import.meta.env.DEV&&url.protocol==='http:'&&['localhost','127.0.0.1','[::1]'].includes(url.hostname))||expires<=Date.now()||expires>Date.now()+300_000)throw unexpected()
      return data // Component-local only; never enters QueryClient/mutation cache or workspaceMemory.
    },
  }
}
export function useReadApi(){const owner=useCustomerContext(),{api}=useAuth();return {owner,api:customerReadApi(api,owner)}}
export function useCustomerTrailers(filters:Record<string,string>){const {owner,api}=useReadApi();return useInfiniteQuery({queryKey:customerKey(owner,'inventory',filters),initialPageParam:undefined as string|undefined,queryFn:({signal,pageParam})=>api.trailers({...filters,...(pageParam?{cursor:pageParam}:{})},signal),getNextPageParam:(last,_pages,_param,params)=>last.nextCursor&&!params.includes(last.nextCursor)?last.nextCursor:undefined,retry:false,gcTime:0,staleTime:0})}
export function useCustomerTrailer(id:string){const {owner,api}=useReadApi();return useQuery({queryKey:customerKey(owner,'trailer-detail',id),queryFn:({signal})=>api.trailer(id,signal),retry:false,gcTime:0,staleTime:0})}
export function useCustomerTimeline(id:string){const {owner,api}=useReadApi();return useInfiniteQuery({queryKey:customerKey(owner,'trailer-history',id),initialPageParam:undefined as string|undefined,queryFn:({signal,pageParam})=>api.timeline(id,pageParam,signal),getNextPageParam:(last,_pages,_param,params)=>last.nextCursor&&!params.includes(last.nextCursor)?last.nextCursor:undefined,retry:false,gcTime:0,staleTime:0})}
export function useCustomerDeliveries(filters:Record<string,string>){const {owner,api}=useReadApi();return useInfiniteQuery({queryKey:customerKey(owner,'deliveries',filters),initialPageParam:undefined as string|undefined,queryFn:({signal,pageParam})=>api.deliveries({...filters,...(pageParam?{cursor:pageParam}:{})},signal),getNextPageParam:(last,_pages,_param,params)=>last.nextCursor&&!params.includes(last.nextCursor)?last.nextCursor:undefined,retry:false,gcTime:0,staleTime:0})}
export function useCustomerDelivery(order:string,stop:string){const {owner,api}=useReadApi();return useQuery({queryKey:customerKey(owner,'delivery-detail',{order,stop}),queryFn:({signal})=>api.delivery(order,stop,signal),retry:false,gcTime:0,staleTime:0})}
export function useCustomerDocuments(filters:Record<string,string>){const {owner,api}=useReadApi();return useInfiniteQuery({queryKey:customerKey(owner,'evidence-index',filters),initialPageParam:undefined as string|undefined,queryFn:({signal,pageParam})=>api.documents({...filters,...(pageParam?{cursor:pageParam}:{})},signal),getNextPageParam:(last,_pages,_param,params)=>last.nextCursor&&!params.includes(last.nextCursor)?last.nextCursor:undefined,retry:false,gcTime:0,staleTime:0})}
