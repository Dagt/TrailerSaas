import { ErrorBanner, FormField } from '../../shared/ui/components'
import { useOrderSites, useOrderLocations } from './orderQueries'
import type { CargoInput, StopInput } from './orderContracts'
import { newStop } from './orderContracts'

const optionalNumber=(value:string)=>value===''?null:Number(value)
export function CargoLinesEditor({value,onChange,disabled=false}:{value:CargoInput[];onChange(value:CargoInput[]):void;disabled?:boolean}) {
  const update=(index:number,line:CargoInput)=>onChange(value.map((c,i)=>i===index?line:c))
  return <fieldset disabled={disabled}><legend>Cargo lines (optional)</legend>{value.map((line,index)=><fieldset key={index}><legend>Cargo {index+1}</legend>
    <FormField id={`cargo-${index}-description`} label="Description"><input id={`cargo-${index}-description`} value={line.description} onChange={e=>update(index,{...line,description:e.target.value})}/></FormField>
    {(['quantity','palletCount','weight'] as const).map(field=><FormField key={field} id={`cargo-${index}-${field}`} label={field==='palletCount'?'Pallet count':field==='quantity'?'Quantity':'Weight'}><input id={`cargo-${index}-${field}`} type="number" step={field==='palletCount'?'1':'any'} value={line[field]??''} onChange={e=>update(index,{...line,[field]:optionalNumber(e.target.value)})}/></FormField>)}
    <FormField id={`cargo-${index}-unit`} label="Weight unit (optional)"><input id={`cargo-${index}-unit`} value={line.weightUnit??''} onChange={e=>update(index,{...line,weightUnit:e.target.value||null})}/></FormField><button type="button" onClick={()=>onChange(value.filter((_,i)=>i!==index))}>Remove cargo {index+1}</button>
  </fieldset>)}<button type="button" onClick={()=>onChange([...value,{description:'',quantity:null,palletCount:null,weight:null,weightUnit:null}])}>Add cargo line</button></fieldset>
}
export function RequestedStopsEditor({value,onChange,disabled=false}:{value:StopInput[];onChange(value:StopInput[]):void;disabled?:boolean}) {
  const change=(next:StopInput[])=>onChange(next.map((s,i)=>({...s,sequenceNumber:i+1})))
  const move=(index:number,delta:number)=>{const next=[...value];[next[index],next[index+delta]]=[next[index+delta]!,next[index]!];change(next)}
  return <fieldset disabled={disabled}><legend>Requested Stops</legend><p>Customer requests only; these do not execute a Driver Stop or change a Trailer location.</p>
    {value.map((stop,index)=><fieldset key={index}><legend>Requested Stop {index+1}</legend><DestinationEditor stop={stop} prefix={`stop-${index}`} change={next=>change(value.map((s,i)=>i===index?next:s))}/>
      <FormField id={`stop-${index}-type`} label="Stop type"><input id={`stop-${index}-type`} value={stop.stopType} onChange={e=>change(value.map((s,i)=>i===index?{...s,stopType:e.target.value}:s))}/></FormField>
      <FormField id={`stop-${index}-time`} label="Appointment (ISO 8601 with timezone; optional)"><input id={`stop-${index}-time`} value={stop.scheduledAt??''} placeholder="2026-10-16T15:00:00Z" onChange={e=>change(value.map((s,i)=>i===index?{...s,scheduledAt:e.target.value||null}:s))}/></FormField>
      <FormField id={`stop-${index}-instructions`} label="Instructions (optional)"><textarea id={`stop-${index}-instructions`} value={stop.instructions??''} onChange={e=>change(value.map((s,i)=>i===index?{...s,instructions:e.target.value||null}:s))}/></FormField>
      <div className="actions"><button type="button" disabled={index===0} onClick={()=>move(index,-1)}>Move Stop {index+1} up</button><button type="button" disabled={index===value.length-1} onClick={()=>move(index,1)}>Move Stop {index+1} down</button><button type="button" onClick={()=>change(value.filter((_,i)=>i!==index))}>Remove Stop {index+1}</button></div>
    </fieldset>)}<button type="button" onClick={()=>change([...value,newStop()])}>Add requested Stop</button>
  </fieldset>
}
export function DestinationEditor({stop,change,prefix,normalizedOnly=false}:{stop:StopInput;change(value:StopInput):void;prefix:string;normalizedOnly?:boolean}) {
  const sites=useOrderSites(), locations=useOrderLocations(stop.siteId??'')
  const normalized=normalizedOnly||stop.siteId!==null
  return <>
    {!normalizedOnly&&<label>Destination type <select aria-label={`${prefix} destination type`} value={normalized?'site':'snapshot'} onChange={e=>change({...stop,siteId:e.target.value==='site'?'':null,locationId:null,siteSnapshot:e.target.value==='site'?null:{name:''}})}><option value="site">Authorized Site</option><option value="snapshot">Ad hoc destination snapshot</option></select></label>}
    {normalized?<><FormField id={`${prefix}-site`} label="Site"><select id={`${prefix}-site`} value={stop.siteId??''} onChange={e=>change({...stop,siteId:e.target.value,locationId:null,siteSnapshot:null})}><option value="">Choose an authorized Site</option>{sites.data?.items.filter(s=>s.active).map(s=><option key={s.id} value={s.id}>{'name' in s?s.name:''}</option>)}</select></FormField>
      <FormField id={`${prefix}-location`} label="Location (optional)"><select id={`${prefix}-location`} disabled={!stop.siteId} value={stop.locationId??''} onChange={e=>change({...stop,locationId:e.target.value||null})}><option value="">Site only</option>{locations.data?.items.filter(l=>l.active).map(l=><option key={l.id} value={l.id}>{l.name}</option>)}</select></FormField></>:
      <><FormField id={`${prefix}-name`} label="Destination name"><input id={`${prefix}-name`} value={stop.siteSnapshot?.name??''} onChange={e=>change({...stop,siteSnapshot:{...stop.siteSnapshot,name:e.target.value}})}/></FormField>
        {(['line1','line2','city','stateRegion','postalCode','countryCode'] as const).map(field=><FormField key={field} id={`${prefix}-${field}`} label={`${field} (optional)`}><input id={`${prefix}-${field}`} value={stop.siteSnapshot?.address?.[field]??''} onChange={e=>change({...stop,siteSnapshot:{...stop.siteSnapshot,name:stop.siteSnapshot?.name??'',address:{...stop.siteSnapshot?.address,[field]:e.target.value||null}}})}/></FormField>)}
        <p>Historical destination only. This does not create an authorized physical Site or Location.</p></>}
    {sites.isError&&<ErrorBanner error={sites.error} retry={()=>void sites.refetch()}/>}{stop.siteId&&locations.isError&&<ErrorBanner error={locations.error} retry={()=>void locations.refetch()}/>}
  </>
}
