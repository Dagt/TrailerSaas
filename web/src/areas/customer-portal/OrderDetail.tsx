import { CustomerHeader, CustomerPanel, CustomerStatus, CustomerTime, CustomerCargo } from './CustomerPresentation'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { z } from 'zod'
import { ErrorBanner, LoadingState, DetailList } from '../../shared/ui/components'
import { useOrder, useOrderActions, useProgress, useOrderSites, useOrderLocations } from './orderQueries'
import { EditOrder, OrderCancellation, OrderCompletion, RerouteAction, TrailerSubstitutionDecision } from './OrderActions'
import type { CustomerOrder, ProgressTrip, ProgressStop } from './orderContracts'

export function CustomerOrderDetail() {
  const {orderId=''}=useParams()
  if(!z.uuid().safeParse(orderId).success)return <p role="alert">Order unavailable.</p>
  return <OrderDetail key={orderId} id={orderId}/>
}
function OrderDetail({id}:{id:string}) {
  const order=useOrder(id), actions=useOrderActions(), progress=useProgress(id)
  const sites=useOrderSites(order.data?.requestedStops.some(stop=>!!stop.siteId&&!stop.siteSnapshot)??false)
  const [action,setAction]=useState<'edit'|'cancel'|'complete'|null>(null), [tripAction,setTripAction]=useState<{trip:ProgressTrip;stop?:ProgressStop}|null>(null)
  const close=()=>{setAction(null);setTripAction(null)}
  const reload=()=>{close();void order.refetch();void progress.refetch();void actions.refetch()}
  if(order.isPending||order.isFetching)return <LoadingState/>
  if(order.isError)return <ErrorBanner error={order.error} retry={()=>void order.refetch()}/>
  const row=order.data, capabilities=actions.data?.capabilities??[], fresh=!actions.isFetching&&!actions.isError
  const p=progress.data?.pages[0], coherent=!!p&&progress.data!.pages.every(page=>page.orderVersion===row.version)&&!progress.isFetching&&!progress.isError
  const trips=progress.data?.pages.flatMap(page=>page.items)??[]
  const unique=new Set(trips.map(t=>t.id)).size===trips.length
  const currentActionTrip=tripAction&&trips.find(t=>t.id===tripAction.trip.id&&t.version===tripAction.trip.version&&t.status===tripAction.trip.status)
  const validTripAction=!!currentActionTrip&&(tripAction?.stop?currentActionTrip.stops.some(s=>s.id===tripAction.stop!.id&&s.canReroute):currentActionTrip.canSubstitute)
  return <section><CustomerHeader title={`Order ${row.orderNumber}`} description={`State ${row.status} · Version ${row.version}`}><Link to="/customer/orders">Back to Orders</Link><button onClick={reload}>Reload latest</button></CustomerHeader>
    <p className="customer-note">DELIVERED: required operational delivery and evidence satisfied. COMPLETED: administratively closed. CANCELLING: physical resolution remains pending.</p>
    <p className="customer-status-line"><CustomerStatus value={row.status}/><span>Version {row.version}</span></p><CustomerPanel title="Order information" description="References and recorded lifecycle timestamps."><DetailList items={Object.entries({PO:row.poNumber,Shipment:row.shipmentNumber,BOL:row.bolReference,Created:<CustomerTime value={row.createdAt}/>,Updated:<CustomerTime value={row.updatedAt}/>,Delivered:<CustomerTime value={row.deliveredAt}/>,Completed:<CustomerTime value={row.completedAt}/>,Cancelled:<CustomerTime value={row.cancelledAt}/>})}/></CustomerPanel>
    <div className="customer-detail-grid"><CustomerPanel title="Cargo" description="Requested quantities and handling details.">{row.cargoLines.length?<CustomerCargo lines={row.cargoLines}/>:<p>No cargo details provided.</p>}
    </CustomerPanel><CustomerPanel title="Requested itinerary" description="Requested destinations; execution is shown separately below.">{sites.isError&&<ErrorBanner error={sites.error} retry={()=>void sites.refetch()}/>}<ol className="customer-itinerary">{row.requestedStops.map(s=><li key={s.id}>{s.stopType} · {s.siteSnapshot?.name??sites.data?.items.find(site=>site.id===s.siteId)?.name??'Recorded Site label unavailable'}{s.siteId&&!s.siteSnapshot&&<> (current authorized Site label)</>} · <CustomerTime value={s.scheduledAt}/> · {s.instructions??'No instructions'}{s.siteId&&s.locationId&&<RequestedLocation siteId={s.siteId} locationId={s.locationId}/>}</li>)}</ol></CustomerPanel></div>
    {fresh&&<div className="actions" role="group" aria-label="Available Order actions">
      {capabilities.includes('ORDER_EDIT')&&['OPEN','PLANNED'].includes(row.status)&&<button onClick={()=>setAction('edit')}>Edit before execution</button>}
      {capabilities.includes('ORDER_CANCEL')&&['OPEN','PLANNED','IN_PROGRESS','PARTIALLY_DELIVERED'].includes(row.status)&&<button className="ui-button ui-button--danger" onClick={()=>setAction('cancel')}>{['OPEN','PLANNED'].includes(row.status)?'Cancel Order':'Start active cancellation'}</button>}
      {capabilities.includes('ORDER_COMPLETE')&&row.status==='DELIVERED'&&<button onClick={()=>setAction('complete')}>Complete delivered Order</button>}
    </div>}{actions.isError&&<ErrorBanner error={actions.error} retry={()=>void actions.refetch()}/>}
    <CustomerPanel title="Delivery progress" description="Actual mapped work and Customer-relevant decisions."><p>Only actual work mapped to this Order is shown. A POD alone does not determine DELIVERED.</p>
    {!!p?.changes.length&&<><h3>Recent Order decisions</h3><p>Up to 20 recorded Customer request changes; physical fulfillment and generic audit are separate.</p><ul className="customer-event-list">{p.changes.map(change=><li key={change.version}>{change.changeType} · Version {change.version} · <CustomerTime value={change.recordedAt}/> · {change.reason}{change.resolution&&<> · {change.resolution.type}: {change.resolution.instructions}</>}</li>)}</ul></>}
    {progress.isPending?<LoadingState/>:progress.isError?<ErrorBanner error={progress.error} retry={()=>void progress.refetch()}/>:!unique?<p role="alert">Progress pages changed. Reload before continuing.</p>:<>
      {!coherent&&<p role="status">Progress and Order versions need refreshing before operational changes.</p>}
      {!trips.length&&<p>No linked Trip reported for this Order.</p>}{trips.map(trip=><article key={trip.id} className="progress-trip"><h3>Trip {trip.id}</h3><p><CustomerStatus value={trip.status}/> · Version {trip.version} · Expected Trailer: {trip.expectedTrailer?<Link to={`/customer/trailers/${trip.expectedTrailer.id}`}>{trip.expectedTrailer.operationalNumber}</Link>:'Not available in this context'}</p>
        <ul>{trip.stops.map(stop=><li key={stop.id}><strong>{stop.destination??'Recorded destination unavailable'}</strong> · {stop.stopType} · <CustomerStatus value={stop.status}/><p>Appointment: <CustomerTime value={stop.scheduledAt}/> · Arrival: <CustomerTime value={stop.arrivedAt}/> · Completion: <CustomerTime value={stop.completedAt}/></p>
          {stop.stopType==='DELIVERY'&&<Link to={`/customer/deliveries/${row.id}/${stop.id}`}>Delivery and authorized evidence</Link>}
          {coherent&&stop.canReroute&&<button onClick={()=>setTripAction({trip,stop})}>Change destination: {stop.destination??stop.stopType}</button>}</li>)}</ul>
        {coherent&&trip.canSubstitute&&<button onClick={()=>setTripAction({trip})}>Decide alternate Trailer</button>}
        <h4>Recent recorded operational changes</h4><p>Up to 100 own reroutes and 100 substitutions.</p>{trip.history.length?<ul>{trip.history.map(h=><li key={h.id}>{h.eventType} · <CustomerTime value={h.occurredAt}/> · {h.reason}{h.previousDestination&&<> · Previous: {h.previousDestination}</>}{h.newDestination&&<> · New: {h.newDestination}</>}{h.decision&&<> · {h.decision}</>}</li>)}</ul>:<p>No Customer-specific operational history returned. Shared/internal history is not exposed.</p>}
      </article>)}{progress.hasNextPage&&<button disabled={progress.isFetchingNextPage} onClick={()=>void progress.fetchNextPage()}>Load more linked work</button>}</>}</CustomerPanel>
    {action&&fresh&&<OrderAction key={`${row.version}:${action}`} action={action} order={row} requestEditable={coherent&&!!p?.requestEditable} close={close} reload={reload}/>}
    {tripAction&&!validTripAction&&<p role="status">Operational context changed. Review the latest work and reopen the action.</p>}
    {tripAction&&validTripAction&&coherent&&unique&&(tripAction.stop?<RerouteAction key={`${tripAction.trip.id}:${tripAction.trip.version}`} trip={tripAction.trip} stop={tripAction.stop} close={close} reload={reload}/>:<TrailerSubstitutionDecision key={`${tripAction.trip.id}:${tripAction.trip.version}`} trip={tripAction.trip} close={close} reload={reload}/>)}
    <p><Link to={`/customer/deliveries?orderId=${row.id}`}>All deliveries for this Order</Link> · <Link to={`/customer/documents?orderId=${row.id}`}>Authorized documents for this Order</Link></p>
    <Link to="/customer/orders">Return to Orders</Link>
  </section>
}
function RequestedLocation({siteId,locationId}:{siteId:string;locationId:string}) {
  const query=useOrderLocations(siteId)
  return <div><p>Requested Location (current authorized label): {query.data?.items.find(location=>location.id===locationId)?.name??'Unavailable'}</p>{query.isError&&<ErrorBanner error={query.error} retry={()=>void query.refetch()}/>}</div>
}
function OrderAction({action,order,requestEditable,close,reload}:{action:string;order:CustomerOrder;requestEditable:boolean;close():void;reload():void}) {
  if(action==='edit')return <EditOrder order={order} requestEditable={requestEditable} close={close} reload={reload}/>
  if(action==='cancel')return <OrderCancellation order={order} close={close} reload={reload}/>
  return <OrderCompletion order={order} close={close} reload={reload}/>
}
