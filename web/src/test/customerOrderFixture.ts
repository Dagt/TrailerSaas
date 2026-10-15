import { customerFixture, customerId, tenantA } from './customerFixture'
import { stamps } from './adminFixture'
import { response } from './customerFixture'
import type { CustomerOrder, ProgressTrip } from '../areas/customer-portal/orderContracts'
import { cargoInput, stopInput } from '../areas/customer-portal/orderContracts'

export const orderId='00000000-0000-4000-8000-000000000101',tripId='00000000-0000-4000-8000-000000000102',stopId='00000000-0000-4000-8000-000000000103',siteId='00000000-0000-4000-8000-000000000104',trailerId='00000000-0000-4000-8000-000000000105',alternateId='00000000-0000-4000-8000-000000000106'
export const commandReceipt=(trailer=trailerId)=>({...stamps,id:tripId,status:'IN_TRANSIT',version:13,currentDriverId:null,currentTractorId:null,plannedInitialTrailerId:trailer,startedAt:stamps.createdAt,completedAt:null,cancelledAt:null,stops:[],assignmentHistory:[],operationalHistory:[]})
export const order=(patch:Partial<CustomerOrder>={}):CustomerOrder=>({...stamps,id:orderId,customerId,orderNumber:'ACME-101',poNumber:'PO-1',shipmentNumber:null,bolReference:null,status:'OPEN',deliveredAt:null,completedAt:null,cancelledAt:null,
  cargoLines:[],requestedStops:[{...stamps,id:stopId,sequenceNumber:1,siteId:null,locationId:null,siteSnapshot:{name:'Frozen receiving'},stopType:'DELIVERY',scheduledAt:null,instructions:null}],...patch})
export const trip=(patch:Partial<ProgressTrip>={}):ProgressTrip=>({id:tripId,status:'IN_TRANSIT',version:12,expectedTrailer:{id:trailerId,operationalNumber:'T114'},canSubstitute:true,
  stops:[{id:stopId,stopType:'DELIVERY',status:'EN_ROUTE',siteId,locationId:null,destination:'Receiving',scheduledAt:'2026-10-16T15:00:00Z',arrivedAt:null,completedAt:null,canReroute:true}],history:[],...patch})
export function orderFixture(path='/customer/orders',memberships?:Parameters<typeof customerFixture>[1]) {
  const f=customerFixture(path,memberships),state={orders:[order()],trips:[] as ProgressTrip[],requestEditable:true}
  const root=`/tenants/${tenantA}`
  f.handlers.set(root+'/orders',async(options,query)=>{
    if(options?.method==='POST') {
      const body=JSON.parse(String(options.body)) as Record<string,unknown>
      const createdId='00000000-0000-4000-8000-000000000107'
      const row=order({id:createdId,customerId:String(body.customerId),orderNumber:String(body.orderNumber),poNumber:body.poNumber as string|null,shipmentNumber:body.shipmentNumber as string|null,bolReference:body.bolReference as string|null,
        cargoLines:(body.cargoLines as unknown[]).map((c,index)=>({createdAt:stamps.createdAt,id:`00000000-0000-4000-8000-${String(201+index).padStart(12,'0')}`,...cargoInput.parse(c)})),
        requestedStops:(body.requestedStops as unknown[]).map((s,index)=>({...stamps,id:`00000000-0000-4000-8000-${String(301+index).padStart(12,'0')}`,...stopInput.parse(s)}))})
      state.orders.push(row)
      f.handlers.set(root+`/orders/${createdId}`,async()=>response(row))
      f.handlers.set(root+`/orders/${createdId}/progress`,async()=>response({orderId:createdId,customerId:row.customerId,orderVersion:row.version,requestEditable:true,items:[],nextCursor:null,changes:[]}))
      return response(row,201)
    }
    return response({items:state.orders.filter(o=>(!query?.get('status')||query.get('status')===o.status)&&o.customerId===query?.get('customerId')),nextCursor:null})
  })
  f.handlers.set(root+`/orders/${orderId}`,async options=>{
    if(options?.method==='PATCH') {const body=JSON.parse(String(options.body));state.orders[0]={...state.orders[0]!,...body,version:state.orders[0]!.version+1};delete (state.orders[0] as unknown as Record<string,unknown>).expectedVersion}
    return response(state.orders[0])
  })
  f.handlers.set(root+`/orders/${orderId}/progress`,async()=>response({orderId,customerId,orderVersion:state.orders[0]!.version,requestEditable:state.requestEditable,items:state.trips,nextCursor:null,changes:[]}))
  f.handlers.set(root+'/sites',async()=>response({items:[{...stamps,id:siteId,name:'Authorized receiving',active:true}]}))
  f.handlers.set(root+`/sites/${siteId}/locations`,async()=>response({items:[]}))
  f.handlers.set(root+'/trailers',async()=>response({items:[trailerId,alternateId].map((id,i)=>({...stamps,id,operationalNumber:i?'T116':'T114',designatedCustomerId:customerId,active:true,serviceabilityState:'IN_SERVICE'}))}))
  return {...f,state,root}
}
