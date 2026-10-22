import { customerLabel } from './customerLabel'
import { useState } from 'react'
import { ConfirmDialog, ErrorBanner, FormField } from '../../shared/ui/components'
import { useOrderApi, useOrderTrailers } from './orderQueries'
import { useSubmission } from './useSubmission'
import { useWorkspaceRecord } from './workspaceMemory'
import { CargoLinesEditor, DestinationEditor, RequestedStopsEditor } from './OrderEditors'
import { cargoInput, createOrderInput, stopInput, newStop } from './orderContracts'
import type { CustomerOrder, ProgressTrip, ProgressStop, StopInput } from './orderContracts'

type Props={order:CustomerOrder;close():void;reload():void}
function Feedback({submission,reload}:{submission:ReturnType<typeof useSubmission>;reload():void}) {
  return <>{submission.error&&<ErrorBanner error={submission.error} reload={reload}/>} {submission.status==='uncertain'&&<p role="alert">The server result is uncertain. Sending again is blocked. Consult the latest authorized state and review before a new intention.</p>}
    {submission.blocked&&!submission.busy&&!submission.receipt&&<button type="button" onClick={reload}>Reload latest</button>}{submission.canRevise&&<button type="button" onClick={submission.revise}>Revise rejected request</button>}</>
}
export function EditOrder({order,requestEditable,close,reload}:Props&{requestEditable:boolean}) {
  const {api,owner}=useOrderApi(), submission=useSubmission(`edit:${order.id}:${order.version}`)
  const draft=useWorkspaceRecord(`edit:${order.id}`,()=>({poNumber:order.poNumber??'',shipmentNumber:order.shipmentNumber??'',bolReference:order.bolReference??'',cargoLines:order.cargoLines.map(c=>cargoInput.parse(c)),requestedStops:order.requestedStops.map(s=>stopInput.parse(s))}))
  const [content,setContent]=useState(false), [confirmed,confirm]=useState(false), [error,setError]=useState<string|null>(null)
  const save=()=>{
    if(!confirmed||submission.blocked)return
    if(content&&!createOrderInput.safeParse({...draft.value,customerId:owner.customerId,orderNumber:order.orderNumber,plannedTrailerId:null}).success){setError('Review cargo descriptions, destinations and appointments with an explicit timezone.');return}
    const body={expectedVersion:order.version,poNumber:draft.value.poNumber||null,shipmentNumber:draft.value.shipmentNumber||null,bolReference:draft.value.bolReference||null,...(content?{cargoLines:draft.value.cargoLines,requestedStops:draft.value.requestedStops}:{})}
    void submission.send(signal=>api.command(order.id,'',body,signal,order.status),close)
  }
  return <ConfirmDialog className="customer-dialog" title="Edit Order before execution" onCancel={close} onConfirm={save} confirmLabel="Save reviewed changes" busy={submission.busy} confirmDisabled={!confirmed||submission.blocked}>
    <p>Order version {order.version}. Ordinary reference edits are available only before execution. Planned itineraries cannot be rewritten here.</p>
    <fieldset disabled={submission.blocked}>{(['poNumber','shipmentNumber','bolReference'] as const).map((field,i)=><FormField key={field} id={`edit-${field}`} label={['PO number','Shipment number','BOL reference'][i]!}><input id={`edit-${field}`} value={draft.value[field]} onChange={e=>{draft.save({...draft.value,[field]:e.target.value});confirm(false)}}/></FormField>)}
      {requestEditable&&<label><input type="checkbox" checked={content} onChange={e=>{setContent(e.target.checked);confirm(false)}}/>Revise unallocated cargo and requested itinerary</label>}
      {content&&requestEditable&&<><CargoLinesEditor value={draft.value.cargoLines} onChange={cargoLines=>{draft.save({...draft.value,cargoLines});confirm(false)}}/><RequestedStopsEditor value={draft.value.requestedStops} onChange={requestedStops=>{draft.save({...draft.value,requestedStops});confirm(false)}}/></>}
      <label><input type="checkbox" checked={confirmed} onChange={e=>confirm(e.target.checked)}/>I reviewed these pre-execution changes.</label>
    </fieldset>{error&&<p role="alert">{error}</p>}<Feedback submission={submission} reload={reload}/>
  </ConfirmDialog>
}
export function OrderCancellation({order,close,reload}:Props) {
  const {api}=useOrderApi(), submission=useSubmission(`cancel:${order.id}:${order.version}`), active=['IN_PROGRESS','PARTIALLY_DELIVERED'].includes(order.status)
  const draft=useWorkspaceRecord(`cancel:${order.id}`,()=>({reason:'',instructions:'',resolutionType:'',destination:newStop()}))
  const [confirmed,confirm]=useState(false), [error,setError]=useState<string|null>(null)
  const change=(next:typeof draft.value)=>{draft.save(next);confirm(false)}
  const requiredSite=['RETURN_TO_BASE','REROUTE'].includes(draft.value.resolutionType)
  const send=()=>{
    if(!confirmed||submission.blocked)return
    if(!draft.value.reason.trim()||active&&(!draft.value.instructions.trim()||!draft.value.resolutionType||requiredSite&&!draft.value.destination.siteId)){setError('A reason and the applicable resolution, destination and instructions are required.');return}
    const body={expectedVersion:order.version,reason:draft.value.reason,...(active?{resolution:{type:draft.value.resolutionType,destinationSiteId:draft.value.resolutionType==='HOLD_CONTACT_DISPATCH'?null:draft.value.destination.siteId||null,instructions:draft.value.instructions}}:{})}
    void submission.send(signal=>api.command(order.id,'/cancellations',body,signal,active?'CANCELLING':'CANCELLED'),close)
  }
  return <ConfirmDialog className="customer-dialog customer-dialog--danger" title={active?'Start active Order cancellation':'Cancel Order before execution'} onCancel={close} onConfirm={send} confirmLabel={active?'Confirm active cancellation':'Confirm cancellation'} busy={submission.busy} confirmDisabled={!confirmed||submission.blocked}>
    <p>{active?'This starts cancellation. Physical resolution remains required. Existing deliveries and evidence remain.':'Historical requests are retained. Only releasable pre-execution allocations are released.'} Cancelling this Order does not cancel a shared Trip.</p>
    <fieldset disabled={submission.blocked}><FormField id="cancel-reason" label="Cancellation reason"><textarea id="cancel-reason" value={draft.value.reason} onChange={e=>change({...draft.value,reason:e.target.value})}/></FormField>
      {active&&<><label>Resolution <select value={draft.value.resolutionType} onChange={e=>change({...draft.value,resolutionType:e.target.value,destination:newStop()})}><option value="">Choose resolution</option>{['RETURN_TO_BASE','REROUTE','HOLD_CONTACT_DISPATCH','OTHER_AUTHORIZED'].map(r=><option key={r} value={r}>{customerLabel(r)}</option>)}</select></label>
        {draft.value.resolutionType!=='HOLD_CONTACT_DISPATCH'&&<DestinationEditor prefix="cancel-destination" normalizedOnly stop={draft.value.destination} change={destination=>change({...draft.value,destination})}/>}
        <FormField id="cancel-instructions" label="Operational instructions"><textarea id="cancel-instructions" value={draft.value.instructions} onChange={e=>change({...draft.value,instructions:e.target.value})}/></FormField></>}
      <label><input type="checkbox" checked={confirmed} onChange={e=>confirm(e.target.checked)}/>I reviewed this cancellation and its consequences.</label>
    </fieldset>{error&&<p role="alert">{error}</p>}<Feedback submission={submission} reload={reload}/>
  </ConfirmDialog>
}
export function OrderCompletion({order,close,reload}:Props) {
  const {api}=useOrderApi(), submission=useSubmission(`complete:${order.id}:${order.version}`), [confirmed,confirm]=useState(false)
  return <ConfirmDialog className="customer-dialog" title="Complete delivered Order" onCancel={close} onConfirm={()=>{if(confirmed&&order.status==='DELIVERED')void submission.send(signal=>api.command(order.id,'/completion',{expectedVersion:order.version},signal,'COMPLETED'),close)}} confirmLabel="Confirm administrative completion" busy={submission.busy} confirmDisabled={!confirmed||submission.blocked||order.status!=='DELIVERED'}>
    <p>Delivered means operational delivery obligations were satisfied. Completed closes the Order administratively. The server validates state and authorization.</p>
    <label><input type="checkbox" checked={confirmed} onChange={e=>confirm(e.target.checked)}/>I confirm administrative closure of this delivered Order.</label><Feedback submission={submission} reload={reload}/>
  </ConfirmDialog>
}
export function RerouteAction({trip,stop,close,reload}:{trip:ProgressTrip;stop:ProgressStop;close():void;reload():void}) {
  const {api}=useOrderApi(), submission=useSubmission(`reroute:${trip.id}:${stop.id}:${trip.version}`)
  const draft=useWorkspaceRecord<{destination:StopInput;appointment:string;reason:string;instructions:string}>(`reroute:${trip.id}:${stop.id}`,()=>({destination:{...newStop(),siteId:stop.siteId??'',siteSnapshot:null,locationId:stop.locationId},appointment:stop.scheduledAt??'',reason:'',instructions:''}))
  const [confirmed,confirm]=useState(false), [error,setError]=useState<string|null>(null)
  const send=()=>{
    if(!confirmed||!stop.canReroute||submission.blocked)return
    if(!draft.value.destination.siteId||!draft.value.reason.trim()||!draft.value.instructions.trim()||!stopInput.safeParse({...draft.value.destination,scheduledAt:draft.value.appointment||null}).success){setError('Choose an authorized destination and enter reason, instructions and a valid appointment.');return}
    void submission.send(signal=>api.tripCommand(trip.id,'reroutes',{expectedVersion:trip.version,affectedStopId:stop.id,newSiteId:draft.value.destination.siteId,newLocationId:draft.value.destination.locationId,scheduledAt:draft.value.appointment||null,reason:draft.value.reason,instructions:draft.value.instructions},signal,{version:trip.version,status:trip.status}),close)
  }
  return <ConfirmDialog className="customer-dialog" title="Operational destination change" onCancel={close} onConfirm={send} confirmLabel="Confirm operational reroute" busy={submission.busy} confirmDisabled={!confirmed||submission.blocked}>
    <p>The Driver has started this work. This records a real reroute and changes future instructions. Previous destination: {stop.destination??'Recorded destination unavailable'}. Trip version {trip.version}.</p>
    <fieldset disabled={submission.blocked}><DestinationEditor prefix="reroute" normalizedOnly stop={draft.value.destination} change={destination=>{draft.save({...draft.value,destination});confirm(false)}}/>
      {(['appointment','reason','instructions'] as const).map(field=><FormField key={field} id={`reroute-${field}`} label={field==='appointment'?'Appointment (ISO 8601 with timezone; clear to remove)':field==='reason'?'Reason':'Instructions'}><input id={`reroute-${field}`} value={draft.value[field]} onChange={e=>{draft.save({...draft.value,[field]:e.target.value});confirm(false)}}/></FormField>)}
      <label><input type="checkbox" checked={confirmed} onChange={e=>confirm(e.target.checked)}/>I reviewed the previous and new operational destination.</label>
    </fieldset>{error&&<p role="alert">{error}</p>}<Feedback submission={submission} reload={reload}/>
  </ConfirmDialog>
}
export function TrailerSubstitutionDecision({trip,close,reload}:{trip:ProgressTrip;close():void;reload():void}) {
  const {api}=useOrderApi(), trailers=useOrderTrailers(), submission=useSubmission(`substitution:${trip.id}:${trip.version}`)
  const draft=useWorkspaceRecord(`substitution:${trip.id}`,()=>({requested:'',decision:'AUTHORIZE' as 'AUTHORIZE'|'REJECT',reason:''})), [confirmed,confirm]=useState(false)
  return <ConfirmDialog className="customer-dialog" title="Decide alternate Trailer" onCancel={close} onConfirm={()=>{
    if(!confirmed||!trip.canSubstitute||!trip.expectedTrailer||!draft.value.reason.trim()||!draft.value.requested||draft.value.requested===trip.expectedTrailer.id)return
    void submission.send(signal=>api.tripCommand(trip.id,'trailer-substitution-decisions',{expectedTrailerId:trip.expectedTrailer!.id,requestedTrailerId:draft.value.requested,decision:draft.value.decision,reason:draft.value.reason},signal,{version:trip.version,status:trip.status,trailerId:draft.value.decision==='AUTHORIZE'?draft.value.requested:trip.expectedTrailer!.id}),close)
  }} confirmLabel={draft.value.decision==='AUTHORIZE'?'Authorize alternate Trailer':'Reject and retain expected Trailer'} busy={submission.busy} confirmDisabled={!confirmed||submission.blocked||!draft.value.requested||!draft.value.reason.trim()}>
    <p>Expected Trailer: {trip.expectedTrailer?.operationalNumber}. Manual decision for a known reported discrepancy; no pending scan request is fabricated. AUTHORIZE changes planning expectation only. REJECT keeps it. Neither performs Hook/Drop.</p>
    <fieldset disabled={submission.blocked}><label>Decision <select value={draft.value.decision} onChange={e=>{draft.save({...draft.value,decision:e.target.value as 'AUTHORIZE'|'REJECT',requested:''});confirm(false)}}><option value="AUTHORIZE">AUTHORIZE</option><option value="REJECT">REJECT</option></select></label>
      <label>Requested Trailer <select value={draft.value.requested} onChange={e=>{draft.save({...draft.value,requested:e.target.value});confirm(false)}}><option value="">Choose a known reported Trailer</option>{trailers.data?.items.filter(t=>'operationalNumber' in t&&t.id!==trip.expectedTrailer?.id&&(draft.value.decision==='REJECT'||t.active&&'serviceabilityState' in t&&t.serviceabilityState==='IN_SERVICE')).map(t=><option key={t.id} value={t.id}>{'operationalNumber' in t?t.operationalNumber:''}</option>)}</select></label>
      <FormField id="decision-reason" label="Decision reason"><textarea id="decision-reason" value={draft.value.reason} onChange={e=>{draft.save({...draft.value,reason:e.target.value});confirm(false)}}/></FormField>
      <label><input type="checkbox" checked={confirmed} onChange={e=>confirm(e.target.checked)}/>I reviewed the expected Trailer, requested Trailer and decision.</label>
    </fieldset>{trailers.isError&&<ErrorBanner error={trailers.error} retry={()=>void trailers.refetch()}/>}<Feedback submission={submission} reload={reload}/>
  </ConfirmDialog>
}
