import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ErrorBanner, FormField, PageHeader } from '../../shared/ui/components'
import { createOrderInput, newStop } from './orderContracts'
import type { CargoInput, StopInput } from './orderContracts'
import { useOrderActions, useOrderApi, useOrderTrailers, useOrderSites } from './orderQueries'
import { useWorkspaceRecord } from './workspaceMemory'
import { useSubmission } from './useSubmission'
import { CargoLinesEditor, RequestedStopsEditor } from './OrderEditors'

type Draft={orderNumber:string;poNumber:string;shipmentNumber:string;bolReference:string;cargoLines:CargoInput[];requestedStops:StopInput[];plannedTrailerId:string|null}
export function CreateOrder() {
  const {owner,api}=useOrderApi(), actions=useOrderActions(), trailers=useOrderTrailers(actions.data?.capabilities.includes('TRIP_ASSIGN_TRAILER')??false), navigate=useNavigate()
  const draft=useWorkspaceRecord<Draft>('create-order',()=>({orderNumber:'',poNumber:'',shipmentNumber:'',bolReference:'',cargoLines:[],requestedStops:[newStop()],plannedTrailerId:null}))
  const sites=useOrderSites(draft.value.requestedStops.some(s=>s.siteId!==null))
  const submit=useSubmission('create-order'), [step,setStep]=useState(0), [confirmed,confirm]=useState(false), [invalid,setInvalid]=useState<string|null>(null)
  const set=(next:Draft)=>{draft.save(next);confirm(false);setInvalid(null)}
  const body={...draft.value,customerId:owner.customerId,poNumber:draft.value.poNumber||null,shipmentNumber:draft.value.shipmentNumber||null,bolReference:draft.value.bolReference||null}
  const create=()=>{
    const validated=createOrderInput.safeParse(body)
    if(!validated.success){setInvalid('Review required references, cargo descriptions, destination, unique Stop sequences and appointments with an explicit timezone.');return}
    if(draft.value.plannedTrailerId&&!actions.data?.capabilities.includes('TRIP_ASSIGN_TRAILER')){setInvalid('Planned Trailer access changed. Remove the previous selection before reviewing this Order again.');return}
    if(!confirmed||submit.blocked||!actions.data?.capabilities.includes('ORDER_CREATE'))return
    void submit.send(signal=>api.create(validated.data,signal),order=>{draft.memory.delete('create-order');draft.memory.delete('submission:create-order');void navigate(`/customer/orders/${order.id}`)})
  }
  return <section><PageHeader title="Create Order" description="Creates an OPEN request directly in the existing operational queue."/><p>Borrador / Draft: retained in this signed-in Customer workspace during navigation and access refresh. Changing account or scope clears it; browser reload does not retain it.</p>
    {actions.isError&&<ErrorBanner error={actions.error} retry={()=>void actions.refetch()}/>}
    <nav aria-label="Order creation steps">{['Information','Cargo','Requested Stops','Review'].map((label,i)=><button key={label} type="button" disabled={submit.blocked} aria-current={step===i?'step':undefined} onClick={()=>{setStep(i);confirm(false)}}>{label}</button>)}</nav>
    <form onSubmit={e=>e.preventDefault()}><fieldset disabled={submit.blocked}>
      {step===0&&<>{(['orderNumber','poNumber','shipmentNumber','bolReference'] as const).map((field,i)=><FormField key={field} id={field} label={['Order number (required)','PO number (optional)','Shipment number (optional)','BOL reference (optional)'][i]!}><input id={field} value={draft.value[field]} onChange={e=>set({...draft.value,[field]:e.target.value})}/></FormField>)}
        {actions.data?.capabilities.includes('TRIP_ASSIGN_TRAILER')&&<FormField id="planned-trailer" label="Planned Trailer (optional)"><select id="planned-trailer" value={draft.value.plannedTrailerId??''} onChange={e=>set({...draft.value,plannedTrailerId:e.target.value||null})}><option value="">Assigned later by Operations</option>{trailers.data?.items.filter(t=>t.active&&'serviceabilityState' in t&&t.serviceabilityState==='IN_SERVICE').map(t=><option key={t.id} value={t.id}>{'operationalNumber' in t?t.operationalNumber:''}</option>)}</select></FormField>}
        {trailers.isError&&<><p>Trailer selection is unavailable. You may still create without a planned Trailer.</p><ErrorBanner error={trailers.error} retry={()=>void trailers.refetch()}/></>}
        {draft.value.plannedTrailerId&&!actions.data?.capabilities.includes('TRIP_ASSIGN_TRAILER')&&<button type="button" onClick={()=>set({...draft.value,plannedTrailerId:null})}>Remove previous planned Trailer selection</button>}
      </>}
      {step===1&&<CargoLinesEditor value={draft.value.cargoLines} onChange={cargoLines=>set({...draft.value,cargoLines})}/>}
      {step===2&&<RequestedStopsEditor value={draft.value.requestedStops} onChange={requestedStops=>set({...draft.value,requestedStops})}/>}
      {step===3&&<><h2>Review Order</h2><p>Customer: {owner.customer.name} · Order: {draft.value.orderNumber}</p><p>PO: {draft.value.poNumber||'Not provided'} · Shipment: {draft.value.shipmentNumber||'Not provided'} · BOL: {draft.value.bolReference||'Not provided'}</p>
        <ul>{draft.value.cargoLines.map((c,i)=><li key={i}>{c.description} · Quantity {c.quantity??'unspecified'} · Pallets {c.palletCount??'unspecified'} · Weight {c.weight??'unspecified'} {c.weightUnit}</li>)}</ul>
        <ol>{draft.value.requestedStops.map((s,i)=><li key={i}>{s.stopType} · {s.siteSnapshot?.name??sites.data?.items.find(site=>site.id===s.siteId)?.name??'Site unavailable — review authorized choices'} · {s.scheduledAt??'No appointment'} · {s.instructions??'No instructions'}</li>)}</ol>
        <label><input type="checkbox" checked={confirmed} onChange={e=>confirm(e.target.checked)}/>I reviewed this Order and its requested itinerary.</label><button type="button" disabled={!confirmed||!actions.data?.capabilities.includes('ORDER_CREATE')} onClick={create}>Confirm and create Order</button>
      </>}
    </fieldset></form>{invalid&&<p role="alert">{invalid}</p>}{submit.busy&&<p role="status">Creating on server…</p>}
    {!!submit.error&&<ErrorBanner error={submit.error}/>}{submit.status==='uncertain'&&<p role="alert">Creation result is uncertain. The draft is retained and sending again is blocked. Review authorized Orders to find a possible accepted request; no POST is retried automatically.</p>}
    {submit.status==='blocked'&&!submit.receipt&&<p role="alert">Submission stopped. Review server data and the rejected request before starting a new intention.</p>}
    {submit.canRevise&&<button type="button" onClick={()=>{submit.revise();confirm(false);setStep(0)}}>Revise rejected draft</button>}
    <Link to="/customer/orders">Review server Orders / return to list</Link>
  </section>
}
