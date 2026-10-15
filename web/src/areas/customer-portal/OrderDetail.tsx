import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { z } from 'zod'
import { PageHeader, ErrorBanner, LoadingState, DetailList } from '../../shared/ui/components'
import { useOrder, useOrderActions, useProgress } from './orderQueries'
import { EditOrder, OrderCancellation, OrderCompletion, RerouteAction, TrailerSubstitutionDecision } from './OrderActions'
import type { CustomerOrder, ProgressTrip, ProgressStop } from './orderContracts'

export function CustomerOrderDetail() {
  const {orderId=''}=useParams()
  if(!z.uuid().safeParse(orderId).success)return <p role="alert">Order unavailable.</p>
  return <OrderDetail key={orderId} id={orderId}/>
}
function OrderDetail({id}:{id:string}) {
  const order=useOrder(id), actions=useOrderActions(), progress=useProgress(id)
  const [action,setAction]=useState<'edit'|'cancel'|'complete'|null>(null), [tripAction,setTripAction]=useState<{trip:ProgressTrip;stop?:ProgressStop}|null>(null)
  const close=()=>{setAction(null);setTripAction(null)}
  const reload=()=>{close();void order.refetch();void progress.refetch();void actions.refetch()}
  if(order.isPending||order.isFetching)return <LoadingState/>
  if(order.isError)return <ErrorBanner error={order.error} retry={()=>void order.refetch()}/>
  const row=order.data, capabilities=actions.data?.capabilities??[], fresh=!actions.isFetching&&!actions.isError
  const p=progress.data?.pages[0], coherent=p?.orderVersion===row.version&&!progress.isFetching&&!progress.isError
  const trips=progress.data?.pages.flatMap(page=>page.items)??[]
  const unique=new Set(trips.map(t=>t.id)).size===trips.length
  return <section><PageHeader title={`Order ${row.orderNumber}`} description={`State ${row.status} · Version ${row.version}`}><button onClick={reload}>Reload latest</button></PageHeader>
    <p>DELIVERED: required operational delivery and evidence satisfied. COMPLETED: administratively closed. CANCELLING: physical resolution remains pending.</p>
    <DetailList items={Object.entries({PO:row.poNumber,Shipment:row.shipmentNumber,BOL:row.bolReference,Created:row.createdAt,Updated:row.updatedAt,Delivered:row.deliveredAt,Completed:row.completedAt,Cancelled:row.cancelledAt})}/>
    <h2>Cargo</h2>{row.cargoLines.length?<ul>{row.cargoLines.map(c=><li key={c.id}>{c.description} · Quantity {c.quantity??'unspecified'} · Pallets {c.palletCount??'unspecified'} · Weight {c.weight??'unspecified'} {c.weightUnit}</li>)}</ul>:<p>No cargo details provided.</p>}
    <h2>Requested itinerary</h2><ol>{row.requestedStops.map(s=><li key={s.id}>{s.stopType} · {s.siteSnapshot?.name??'Normalized authorized Site'} · {s.scheduledAt??'No appointment'} · {s.instructions??'No instructions'}</li>)}</ol>
    {fresh&&<div className="actions">
      {capabilities.includes('ORDER_EDIT')&&['OPEN','PLANNED'].includes(row.status)&&<button onClick={()=>setAction('edit')}>Edit before execution</button>}
      {capabilities.includes('ORDER_CANCEL')&&['OPEN','PLANNED','IN_PROGRESS','PARTIALLY_DELIVERED'].includes(row.status)&&<button onClick={()=>setAction('cancel')}>{['OPEN','PLANNED'].includes(row.status)?'Cancel Order':'Start active cancellation'}</button>}
      {capabilities.includes('ORDER_COMPLETE')&&row.status==='DELIVERED'&&<button onClick={()=>setAction('complete')}>Complete delivered Order</button>}
    </div>}{actions.isError&&<ErrorBanner error={actions.error} retry={()=>void actions.refetch()}/>}
    <h2>Delivery progress</h2><p>Only actual work mapped to this Order is shown. A POD alone does not determine DELIVERED.</p>
    {progress.isPending?<LoadingState/>:progress.isError?<ErrorBanner error={progress.error} retry={()=>void progress.refetch()}/>:!unique?<p role="alert">Progress pages changed. Reload before continuing.</p>:<>
      {!coherent&&<p role="status">Progress and Order versions need refreshing before operational changes.</p>}
      {!trips.length&&<p>No linked Trip reported for this Order.</p>}{trips.map(trip=><article key={trip.id} className="progress-trip"><h3>Trip {trip.id}</h3><p>{trip.status} · Version {trip.version} · Expected Trailer: {trip.expectedTrailer?.operationalNumber??'Not available in this context'}</p>
        <ul>{trip.stops.map(stop=><li key={stop.id}><strong>{stop.destination??'Recorded destination unavailable'}</strong> · {stop.stopType} · {stop.status}<p>Appointment: {stop.scheduledAt??'Not scheduled'} · Arrival: {stop.arrivedAt??'Not recorded'} · Completion: {stop.completedAt??'Not recorded'}</p>
          {coherent&&stop.canReroute&&<button onClick={()=>setTripAction({trip,stop})}>Change destination: {stop.destination??stop.stopType}</button>}</li>)}</ul>
        {coherent&&trip.canSubstitute&&<button onClick={()=>setTripAction({trip})}>Decide alternate Trailer</button>}
        <h4>Recorded operational changes</h4>{trip.history.length?<ul>{trip.history.map(h=><li key={h.id}>{h.eventType} · {h.occurredAt} · {h.reason}{h.previousDestination&&<> · Previous: {h.previousDestination}</>}{h.newDestination&&<> · New: {h.newDestination}</>}{h.decision&&<> · {h.decision}</>}</li>)}</ul>:<p>No Customer-specific operational history returned. Shared/internal history is not exposed.</p>}
      </article>)}{progress.hasNextPage&&<button disabled={progress.isFetchingNextPage} onClick={()=>void progress.fetchNextPage()}>Load more linked work</button>}</>}
    {action&&fresh&&<OrderAction key={`${row.version}:${action}`} action={action} order={row} requestEditable={coherent&&!!p?.requestEditable} close={close} reload={reload}/>}
    {tripAction&&coherent&&unique&&(tripAction.stop?<RerouteAction key={`${tripAction.trip.id}:${tripAction.trip.version}`} trip={tripAction.trip} stop={tripAction.stop} close={close} reload={reload}/>:<TrailerSubstitutionDecision key={`${tripAction.trip.id}:${tripAction.trip.version}`} trip={tripAction.trip} close={close} reload={reload}/>)}
    <Link to="/customer/orders">Return to Orders</Link>
  </section>
}
function OrderAction({action,order,requestEditable,close,reload}:{action:string;order:CustomerOrder;requestEditable:boolean;close():void;reload():void}) {
  if(action==='edit')return <EditOrder order={order} requestEditable={requestEditable} close={close} reload={reload}/>
  if(action==='cancel')return <OrderCancellation order={order} close={close} reload={reload}/>
  return <OrderCompletion order={order} close={close} reload={reload}/>
}
