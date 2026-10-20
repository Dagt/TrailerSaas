import { CustomerHeader, CustomerPanel, CustomerStatus, CustomerTime, CustomerTable } from './CustomerPresentation'
import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { z } from 'zod'
import { DetailList, EmptyState, ErrorBanner, LoadingState } from '../../shared/ui/components'
import { ApiProblem } from '../../shared/api/client'
import { currentLocationLabel } from '../../shared/trailers/display'
import { customerKey } from './queries'
import { useReadApi, useCustomerTrailers, useCustomerTrailer, useCustomerTimeline, useCustomerDeliveries, useCustomerDelivery, useCustomerDocuments, deliveryIdentity, documentIdentity } from './readQueries'
import { locationStates, loadStates, serviceStates, stopStates, documentTypes } from './readContracts'
import type { CustomerTrailer, CustomerDelivery, EvidenceDocument } from './readContracts'

type Paged<T>={isPending:boolean;isError:boolean;error:unknown;data?:{pages:{items:T[];nextCursor:string|null}[]};refetch():Promise<unknown>;hasNextPage:boolean;isFetchingNextPage:boolean;fetchNextPage():Promise<unknown>}
function Results<T>({query,keyOf,children,empty,more='Load more'}:{query:Paged<T>;keyOf:(row:T)=>string;children:(rows:T[])=>ReactNode;empty:string;more?:string|null}) {
  if(query.isPending)return <LoadingState/>
  if(query.isError)return <ErrorBanner error={query.error} retry={()=>void query.refetch()}/>
  const pages=query.data?.pages??[],rows=pages.flatMap(p=>p.items),cursors=pages.map(p=>p.nextCursor).filter(c=>c!==null)
  if(new Set(rows.map(keyOf)).size!==rows.length||new Set(cursors).size!==cursors.length)return <div role="alert">The results changed or could not be verified. <button onClick={()=>void query.refetch()}>Refresh results</button></div>
  return <>{rows.length?children(rows):<EmptyState>{empty}</EmptyState>}{more&&query.hasNextPage&&<button disabled={query.isFetchingNextPage} onClick={()=>void query.fetchNextPage()}>{more}</button>}</>
}
const byId=(row:{id:string})=>row.id
function SelectFilter({label,value,options,change}:{label:string;value:string;options:readonly string[];change:(value:string)=>void}){
  return <label>{label} <select aria-label={label} value={value} onChange={e=>change(e.target.value)}><option value="">All</option>{options.map(v=><option key={v}>{v}</option>)}</select></label>
}
function EvidenceRows({rows}:{rows:EvidenceDocument[]}) {
  return <CustomerTable caption="Authorized accepted delivery documents" headings={['Document','Order','Delivery','Publication','Accepted evidence']}>
    {rows.map(d=><tr key={documentIdentity(d)}><th scope="row"><Link to={`/customer/documents/${d.id}`}>View {d.documentType}</Link></th><td><Link to={`/customer/orders/${d.orderId}`}>{d.orderNumber}</Link></td><td><Link to={`/customer/deliveries/${d.orderId}/${d.stopId}`}>Delivery detail</Link></td><td><CustomerStatus value={d.uploadStatus}/><time dateTime={d.uploadedAt}>{<CustomerTime value={d.uploadedAt}/>}</time></td><td><strong>{d.evidenceMode}</strong><time dateTime={d.acceptedAt}>{<CustomerTime value={d.acceptedAt}/>}</time></td></tr>)}
  </CustomerTable>
}
function TrailerRows({rows}:{rows:CustomerTrailer[]}){
  return <CustomerTable caption="Designated Customer Trailer inventory" headings={['Trailer','Location state','Current Location','Last Known Location','Last Seen','Load','Serviceability','Coupling','Type / usage']}>
    {rows.map(t=><tr key={t.id}><th scope="row"><Link to={`/customer/trailers/${t.id}`}>{t.operationalNumber}</Link>{!t.active&&<p>Inactive</p>}</th><td><CustomerStatus value={t.locationState}/></td><td>{currentLocationLabel(t)}</td><td>{t.lastKnownLocationName??'Not recorded or not visible'}</td><td>{<CustomerTime value={t.lastSeenAt}/>}</td><td><CustomerStatus value={t.loadState}/></td><td><CustomerStatus value={t.serviceabilityState}/></td><td>{t.couplingState}</td><td>{t.trailerType??'Not recorded'} / {t.usagePurpose??'Not recorded'}</td></tr>)}
  </CustomerTable>
}
export function CustomerTrailerInventory(){
  const [params,setParams]=useSearchParams(),[number,setNumber]=useState(params.get('operationalNumber')??'')
  const keys=['operationalNumber','locationState','loadState','serviceabilityState','active','coupled','currentLocationId']
  const filters=Object.fromEntries(keys.filter(k=>params.get(k)).map(k=>[k,params.get(k)!])),query=useCustomerTrailers(filters)
  const change=(key:string,value:string)=>{const next=new URLSearchParams(filters);if(value)next.set(key,value);else next.delete(key);setParams(next)}
  const locations=new Map(query.data?.pages.flatMap(p=>p.items).filter(t=>t.currentLocationId&&t.currentLocationName).map(t=>[t.currentLocationId!,t.currentLocationName!]))
  return <section><CustomerHeader title="Trailers" description="Designated inventory. Last Known Location is historical; it is not a live position."><button onClick={()=>void query.refetch()}>Refresh inventory</button></CustomerHeader>
    <form className="filters" onSubmit={e=>{e.preventDefault();change('operationalNumber',number)}}><label>Exact Trailer number <input aria-label="Exact Trailer number" value={number} onChange={e=>setNumber(e.target.value)}/></label><button>Find Trailer</button><button type="button" onClick={()=>{setNumber('');setParams({})}}>Clear filters</button></form>
    <div className="filters" role="group" aria-label="Inventory filters"><SelectFilter label="Location state" value={filters.locationState??''} options={locationStates} change={v=>change('locationState',v)}/><SelectFilter label="Load state" value={filters.loadState??''} options={loadStates} change={v=>change('loadState',v)}/><SelectFilter label="Serviceability" value={filters.serviceabilityState??''} options={serviceStates} change={v=>change('serviceabilityState',v)}/><SelectFilter label="Active" value={filters.active??''} options={['true','false']} change={v=>change('active',v)}/><SelectFilter label="Coupled" value={filters.coupled??''} options={['true','false']} change={v=>change('coupled',v)}/>
      <label>Location from these results <select aria-label="Location from these results" value={filters.currentLocationId??''} onChange={e=>change('currentLocationId',e.target.value)}><option value="">All authorized Locations</option>{filters.currentLocationId&&!locations.has(filters.currentLocationId)&&<option value={filters.currentLocationId}>Selected recorded Location</option>}{[...locations].map(([id,name])=><option key={id} value={id}>{name}</option>)}</select></label>
    </div><CustomerPanel title="Designated inventory" description="Current observations, historical context and independent operational states."><Results query={query} keyOf={byId} empty="No designated Trailers found for these filters." more="Load more Trailers">{rows=><TrailerRows rows={rows}/>}</Results></CustomerPanel>
  </section>
}
export function CustomerTrailerDetail(){const {trailerId=''}=useParams();if(!z.uuid().safeParse(trailerId).success)return <p role="alert">Trailer unavailable.</p>;return <TrailerDetail key={trailerId} id={trailerId}/>}
function TrailerDetail({id}:{id:string}){
  const query=useCustomerTrailer(id)
  if(query.isPending)return <LoadingState/>
  if(query.isError)return <ErrorBanner error={query.error} retry={()=>void query.refetch()}/>
  const t=query.data
  return <section><CustomerHeader title={`Trailer ${t.operationalNumber}`} description={t.designatedCustomer.name}><Link to="/customer/trailers">Back to inventory</Link><button onClick={()=>void query.refetch()}>Refresh Trailer</button></CustomerHeader>
    <p className="customer-status-line"><CustomerStatus value={t.locationState}/> <CustomerStatus value={t.loadState}/> <CustomerStatus value={t.serviceabilityState}/> {t.active?'Active':'Inactive'}</p>
    <CustomerPanel title="Location and condition" description="Recorded location, last observation and physical state."><DetailList items={[
      ['Current Location',currentLocationLabel(t)],['Last Known Location',t.lastKnownLocation?.name??'Not recorded or not visible'],['Last Seen',<CustomerTime value={t.lastSeenAt}/>],['Type',t.trailerType],['Usage',t.usagePurpose],['Physical coupling',t.couplingState],
    ]}/></CustomerPanel>
    <p className="customer-note">Last Known Location is a historical observation. No current position is implied when IN_TRANSIT or LOCATION_UNKNOWN. Equipment and staff identities are withheld.</p>
    {t.currentLocation&&t.locationState==='AT_LOCATION'&&<Link to={`/customer/trailers?currentLocationId=${t.currentLocation.id}`}>Trailers currently at {t.currentLocation.name}</Link>}
    <CustomerPanel title="Open planning allocations"><p>A planning Manifest does not prove physically loaded cargo. Only this Customer's allocations are shown.</p>
    {!t.openManifests.length?<EmptyState>No authorized open planning allocations returned.</EmptyState>:t.openManifests.map(m=><article key={m.id}><h3>Open Manifest</h3>{m.orders.length?<ul>{m.orders.map(o=><li key={o.id}><Link to={`/customer/orders/${o.id}`}>{o.orderNumber}</Link> · {o.status}</li>)}</ul>:<p>Allocation preview unavailable.</p>}{m.ordersTruncated&&<p>More authorized allocations exist beyond this preview. Review Orders for current details.</p>}</article>)}
    </CustomerPanel><CustomerTrailerHistory id={id}/>
  </section>
}
export function CustomerTrailerHistory({id}:{id:string}){
  const query=useCustomerTimeline(id)
  return <CustomerPanel title="Recent authorized movements"><p>Only permitted facts from the current designation period. Earlier and shared-Customer history is withheld; this is not a reconstructed route.</p>
    <Results query={query} keyOf={byId} empty="No authorized movements returned for the current designation." more="Load more movements">{rows=><CustomerTable caption="Customer-relevant Trailer movements" headings={['Movement','Occurred','Recorded','Location','Recorded changes']}>
      {rows.map(e=><tr key={e.id}><th scope="row">{e.eventType}</th><td>{<CustomerTime value={e.occurredAt}/>}</td><td>{<CustomerTime value={e.recordedAt}/>}</td><td>{e.locationName??'Not recorded or not visible'}</td><td>{e.changes.previousLoadState&&<>Load: {e.changes.previousLoadState} → {e.changes.newLoadState??'Not recorded'}<br/></>}{e.changes.previousServiceabilityState&&<>Service: {e.changes.previousServiceabilityState} → {e.changes.newServiceabilityState??'Not recorded'}<br/></>}{e.changes.previousLocationName&&<>Previous: {e.changes.previousLocationName}<br/></>}{e.changes.newLocationName&&<>New: {e.changes.newLocationName}</>}</td></tr>)}
    </CustomerTable>}</Results>
  </CustomerPanel>
}
function DeliveryRows({rows}:{rows:CustomerDelivery[]}){
  return <CustomerTable caption="Customer deliveries" headings={['Order / Delivery','Destination','Appointment','Stop state','Arrival','Completion','Trailer','Evidence']}>
    {rows.map(d=><tr key={deliveryIdentity(d)}><th scope="row"><Link to={`/customer/deliveries/${d.orderId}/${d.stopId}`}>Delivery for {d.orderNumber}</Link><p><Link to={`/customer/orders/${d.orderId}`}>Order {d.orderNumber}</Link></p></th><td>{d.destination??'Recorded destination unavailable'}</td><td>{<CustomerTime value={d.scheduledAt}/>}</td><td><CustomerStatus value={d.status}/></td><td>{<CustomerTime value={d.arrivedAt}/>}</td><td>{<CustomerTime value={d.completedAt}/>}</td><td>{d.expectedTrailer?<Link to={`/customer/trailers/${d.expectedTrailer.id}`}>{d.expectedTrailer.operationalNumber}</Link>:'Not available in this scope'}</td><td>{d.documents.length?<Link to={`/customer/deliveries/${d.orderId}/${d.stopId}`}>Accepted files available</Link>:d.evidenceMode?'Acceptance recorded; files unavailable':'No authorized accepted evidence returned'}</td></tr>)}
  </CustomerTable>
}
export function CustomerDeliveriesList(){
  const [params,setParams]=useSearchParams(),status=params.get('status')??'',active=params.get('active')??'',orderId=params.get('orderId')??''
  const filters={...(status?{status}:{}),...(active?{active}:{}),...(orderId?{orderId}:{})},query=useCustomerDeliveries(filters)
  return <section><CustomerHeader title="Deliveries" description="Actual Delivery Stops mapped to this Customer's Orders. One Order may span several Stops and Trips."><button onClick={()=>void query.refetch()}>Refresh deliveries</button></CustomerHeader>
    <div className="filters" role="group" aria-label="Delivery filters"><SelectFilter label="Delivery state" value={status} options={stopStates} change={value=>setParams({...filters,...(value?{status:value}:{status:''})})}/><SelectFilter label="Active deliveries" value={active} options={['true','false']} change={value=>setParams({...filters,...(value?{active:value}:{active:''})})}/>{orderId&&<button onClick={()=>setParams({...filters,orderId:''})}>Clear Order filter</button>}</div>
    <CustomerPanel title="Delivery activity" description="Appointments, recorded progress and authorized evidence."><Results query={query} keyOf={deliveryIdentity} empty="No authorized deliveries found." more="Load more deliveries">{rows=><DeliveryRows rows={rows}/>}</Results></CustomerPanel>
  </section>
}
export function CustomerDeliveryDetail(){const {orderId='',stopId=''}=useParams();if(!z.uuid().safeParse(orderId).success||!z.uuid().safeParse(stopId).success)return <p role="alert">Delivery unavailable.</p>;return <DeliveryDetail key={`${orderId}:${stopId}`} order={orderId} stop={stopId}/>}
function DeliveryDetail({order,stop}:{order:string;stop:string}){
  const query=useCustomerDelivery(order,stop)
  if(query.isPending)return <LoadingState/>
  if(query.isError)return <ErrorBanner error={query.error} retry={()=>void query.refetch()}/>
  const d=query.data
  return <section><CustomerHeader title={`Delivery for ${d.orderNumber}`} description={d.destination??'Recorded destination unavailable'}><Link to="/customer/deliveries">Back to deliveries</Link><button onClick={()=>void query.refetch()}>Refresh delivery</button></CustomerHeader>
    <p className="customer-status-line"><CustomerStatus value={d.status}/><span>Order</span><CustomerStatus value={d.orderStatus}/></p><CustomerPanel title="Recorded delivery progress" description="Only authoritative timestamps are shown; missing events remain unrecorded."><DetailList items={Object.entries({Stop:d.status,Order:d.orderStatus,Appointment:<CustomerTime value={d.scheduledAt}/>,Arrival:<CustomerTime value={d.arrivedAt}/>,'Service started':<CustomerTime value={d.serviceStartedAt}/>,Completion:<CustomerTime value={d.completedAt}/>,Departure:<CustomerTime value={d.departedAt}/>})}/></CustomerPanel>
    <p>A completed Stop does not mean the Order is administratively COMPLETED. An uploaded file alone is not accepted POD.</p><Link to={`/customer/orders/${d.orderId}`}>Open Order {d.orderNumber}</Link>
    {d.expectedTrailer&&<p>Expected Trailer: <Link to={`/customer/trailers/${d.expectedTrailer.id}`}>{d.expectedTrailer.operationalNumber}</Link> · Planning expectation, not proof of physical coupling.</p>}
    <CustomerPanel title="Accepted delivery evidence" description="Acceptance and exact published-file associations are separate facts.">{d.evidenceMode?<p>{d.evidenceMode} · Acceptance recorded {<CustomerTime value={d.acceptedAt}/>}{!d.fileAssociationRecorded&&' · Historical receipt has no recorded accepted-file identity.'}</p>:<p>No authorized acceptance returned. Shared or unavailable evidence is withheld.</p>}
    {d.documents.length?<EvidenceRows rows={d.documents}/>:<EmptyState>No authorized accepted files are available for this delivery.</EmptyState>}</CustomerPanel>
  </section>
}
export function CustomerDocumentsList(){
  const [params,setParams]=useSearchParams(),filters=Object.fromEntries(['documentType','orderId','stopId'].filter(k=>params.get(k)).map(k=>[k,params.get(k)!])),query=useCustomerDocuments(filters)
  return <section><CustomerHeader title="Documents" description="Published files explicitly associated with accepted delivery evidence. A BOL reference is not a file."><button onClick={()=>void query.refetch()}>Refresh documents</button></CustomerHeader>
    <div className="filters" role="group" aria-label="Document filters"><SelectFilter label="Document type" value={filters.documentType??''} options={documentTypes} change={value=>setParams({...filters,documentType:value})}/><button onClick={()=>setParams({})}>Clear document filters</button></div>
    <p>Each row identifies an authorized Order/Delivery association; the same accepted file may relate to more than one delivery.</p>
    <CustomerPanel title="Accepted document library" description="Published files with a verified, authorized delivery association."><Results query={query} keyOf={documentIdentity} empty="No authorized accepted documents found." more="Load more documents">{rows=><EvidenceRows rows={rows}/>}</Results></CustomerPanel>
  </section>
}
export function CustomerDocumentViewer(){const {documentId=''}=useParams();if(!z.uuid().safeParse(documentId).success)return <p role="alert">Document unavailable.</p>;return <DocumentViewer key={documentId} id={documentId}/>}
function DocumentViewer({id}:{id:string}){
  const {owner,api}=useReadApi(),index=useCustomerDocuments({documentId:id}),first=index.data?.pages[0]?.items[0]
  const meta=useQuery({queryKey:customerKey(owner,'document-metadata',id),queryFn:({signal})=>api.metadata(id,signal),enabled:!!first&&!index.isError,retry:false,gcTime:0,staleTime:0})
  const [grant,setGrant]=useState<{downloadUrl:string;expiresAt:string}|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState<unknown>(null),[expired,setExpired]=useState(false)
  const live=useRef(true),request=useRef<AbortController|null>(null),timer=useRef<ReturnType<typeof setTimeout>|null>(null)
  useEffect(()=>{live.current=true;return()=>{live.current=false;request.current?.abort();if(timer.current)clearTimeout(timer.current)}},[])
  const discard=()=>{if(timer.current)clearTimeout(timer.current);setGrant(null)}
  const authorize=async()=>{
    if(request.current)return
    const controller=new AbortController();request.current=controller;setBusy(true);setError(null);setExpired(false);discard()
    try{const next=await api.download(id,controller.signal);if(live.current&&!controller.signal.aborted){setGrant(next);timer.current=setTimeout(()=>{if(live.current){setGrant(null);setExpired(true)}},Math.max(0,Date.parse(next.expiresAt)-Date.now()))}}
    catch(e){if(live.current&&!controller.signal.aborted)setError(e)}
    finally{if(request.current===controller){request.current=null;if(live.current)setBusy(false)}}
  }
  if(index.isPending)return <LoadingState/>
  if(index.isError)return <ErrorBanner error={index.error} retry={()=>void index.refetch()}/>
  if(!first)return <EmptyState>Document not available in this Customer context. <Link to="/customer/documents">Return to documents</Link></EmptyState>
  if(meta.isPending)return <LoadingState/>
  if(meta.isError)return <ErrorBanner error={meta.error} retry={()=>void meta.refetch()}/>
  const d=meta.data
  if(d.documentType!==first.documentType||d.contentType!==first.contentType||d.sizeBytes!==first.sizeBytes||d.uploadedAt!==first.uploadedAt)return <ErrorBanner error={new ApiProblem({status:502,code:'UNEXPECTED_RESPONSE'})}/>
  return <section><CustomerHeader title={`${d.documentType} document`} description="Accepted delivery evidence. No file is opened until fresh server download authorization."><Link to="/customer/documents">Back to documents</Link></CustomerHeader>
    <CustomerPanel title="Document metadata" description="File publication does not independently establish accepted POD."><DetailList items={Object.entries({Type:d.documentType,Publication:d.uploadStatus,'Content type':d.contentType,Bytes:String(d.sizeBytes),Uploaded:<CustomerTime value={d.uploadedAt}/>})}/></CustomerPanel>
    <CustomerPanel title="Related deliveries"><Results query={index} keyOf={documentIdentity} empty="No authorized associations returned." more="Load more related deliveries">{rows=><EvidenceRows rows={rows}/>}</Results></CustomerPanel>
    <CustomerPanel title="Secure file access" description="Request a temporary link after the server checks your current access."><div className="customer-download"><button className="ui-button ui-button--primary" disabled={busy||meta.isFetching||index.isFetching} onClick={()=>void authorize()}>{busy?'Authorizing download…':'Request authorized download'}</button>
    {busy&&<button onClick={()=>{request.current?.abort();request.current=null;setBusy(false)}}>Cancel link request</button>}
    {error!=null&&<><ErrorBanner error={error}/>{error instanceof ApiProblem&&error.problem.code==='OBJECT_STORAGE_UNAVAILABLE'&&<p>Document storage is unavailable. No download link was issued.</p>}</>}
    {expired&&<p role="status">The download link expired. Request authorization again.</p>}
    {grant&&<div><a href={grant.downloadUrl} target="_blank" rel="noopener noreferrer" onClick={e=>{if(Date.parse(grant.expiresAt)<=Date.now()){e.preventDefault();discard();setExpired(true)}}}>Open authorized file</a><p>Link expires {<CustomerTime value={grant.expiresAt}/>}. An already-issued link can remain valid until its expiry.</p><button onClick={discard}>Discard temporary link</button></div>}</div></CustomerPanel>
  </section>
}
export function CustomerDashboardSummaries(){return <section aria-label="Resources and evidence"><h2 className="customer-section-label">Resources and evidence</h2><p className="customer-note">Customer-scoped previews connect delivery work, designated equipment and accepted files.</p><div className="customer-cards"><DeliveryPreview/><InventoryPreview title="Trailer Inventory" filters={{active:'true'}}/><InventoryPreview title="Unknown Trailer locations" filters={{locationState:'LOCATION_UNKNOWN',active:'true'}}/><InventoryPreview title="Out-of-service Trailers" filters={{serviceabilityState:'OUT_OF_SERVICE',active:'true'}}/><DocumentsPreview/></div></section>}
function DeliveryPreview(){const query=useCustomerDeliveries({active:'true',limit:'5'});return <article><h3><Link to="/customer/deliveries?active=true">Active Deliveries</Link></h3><p>Up to five authorized records; this preview is not a total.</p><Results query={query} keyOf={deliveryIdentity} empty="No active deliveries returned." more={null}>{rows=><ul className="customer-preview-list">{rows.map(d=><li key={deliveryIdentity(d)}><Link to={`/customer/deliveries/${d.orderId}/${d.stopId}`}>{d.orderNumber} · {d.destination??'Destination unavailable'}</Link><CustomerStatus value={d.status}/></li>)}</ul>}</Results></article>}
function InventoryPreview({title,filters}:{title:string;filters:Record<string,string>}){const query=useCustomerTrailers({...filters,limit:'5'});return <article><h3><Link to={`/customer/trailers?${new URLSearchParams(filters)}`}>{title}</Link></h3><p>Up to five authorized active records; this preview is not an inventory total.</p><Results query={query} keyOf={byId} empty="No Trailers returned for this preview." more={null}>{rows=><ul className="customer-preview-list">{rows.map(t=><li key={t.id}><Link to={`/customer/trailers/${t.id}`}>{t.operationalNumber}</Link><CustomerStatus value={t.locationState}/><small>{t.serviceabilityState}</small></li>)}</ul>}</Results></article>}
function DocumentsPreview(){const query=useCustomerDocuments({limit:'5'});return <article><h3><Link to="/customer/documents">Recent Evidence</Link></h3><p>Most recently uploaded accepted files, up to five associations. No total is inferred.</p><Results query={query} keyOf={documentIdentity} empty="No authorized recent evidence returned." more={null}>{rows=><ul className="customer-preview-list">{rows.map(d=><li key={documentIdentity(d)}><Link to={`/customer/documents/${d.id}`}>{d.documentType} · {d.orderNumber}</Link></li>)}</ul>}</Results></article>}
