import { MasterDataStatus } from './MasterDataStatus'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useQueries } from '@tanstack/react-query'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { ApiProblem } from '../../shared/api/client'
import { DataTable, EmptyState, ErrorBanner, FormField, LoadingState, PageHeader } from '../../shared/ui/components'
import type { CreateTrip, OrderDetail, StopInput } from './contracts'
import { stopTypes } from './contracts'
import { key, useLocations, useNames, useOperations, useOrderPages } from './queries'
import { useTripCommand } from './mutations'
import { createSchema, draftStops } from './planning'
import { human, when } from './display'
export function PlanTrip() {
  const { api, tenantId } = useOperations(), names = useNames(), navigate = useNavigate(), [params] = useSearchParams()
  const [selected, select] = useState<string[]>([...new Set(params.getAll('orderId'))])
  const [step, setStep] = useState(0), [signature, prepared] = useState(''), [uncertain, uncertainty] = useState(false)
  const orders = useOrderPages({ unplanned: 'true' })
  const details = useQueries({ queries: selected.map(id => ({ queryKey: key(tenantId, 'order', id), queryFn: ({ signal }: { signal: AbortSignal }) => api.order(id, signal) })) })
  const loaded = details.flatMap(q => q.data ? [q.data] : [])
  const form = useForm<CreateTrip>({ defaultValues: { orderIds: [], stops: [], driverId: null, tractorId: null, plannedInitialTrailerId: null } })
  const stops = useFieldArray({ control: form.control, name: 'stops' })
  const command = useTripCommand(api.create)
  const values = useWatch({ control: form.control }) as CreateTrip
  const ready = selected.length > 0 && loaded.length === selected.length && loaded.every(o => ['OPEN', 'PLANNED'].includes(o.status))
  const advance = () => {
    if (step === 0) {
      if (!ready) return
      form.setValue('orderIds', selected)
      if (signature !== selected.join('|')) { stops.replace(draftStops(loaded)); prepared(selected.join('|')) }
    }
    setStep(Math.min(3, step + 1))
  }
  const submit = form.handleSubmit(async value => {
    if (step !== 3 || uncertain || command.isPending) return
    form.clearErrors()
    const request = { ...value, stops: value.stops.map((s, i) => ({ ...s, sequenceNumber: i + 1 })) }
    const result = createSchema.safeParse(request)
    if (!result.success) {
      for (const issue of result.error.issues) form.setError(issue.path.join('.') as Parameters<typeof form.setError>[0], { message: issue.message })
      setStep(1); return
    }
    try { const trip = await command.mutateAsync(request); navigate(`/operations/trips/${trip.id}`) }
    catch (error) {
      if (!(error instanceof ApiProblem) || error.problem.status >= 500) uncertainty(true)
      if (error instanceof ApiProblem) for (const e of error.problem.errors ?? []) form.setError(e.field as Parameters<typeof form.setError>[0], { message: e.message })
    }
  })
  return <><PageHeader title="Plan a Trip" description="Build executable Stops from requested work. Expected resources do not represent physical execution." />
    <MasterDataStatus queries={[names.customers, names.drivers, names.tractors, names.trailers, names.sites]} />
    <nav className="planning-steps" aria-label="Planning steps">{['Orders', 'Stops', 'Resources', 'Review'].map((label, i) => <button type="button" key={label} aria-current={step === i ? 'step' : undefined} disabled={i > step || command.isPending} onClick={() => setStep(i)}>{i + 1}. {label}</button>)}</nav>
    <form onSubmit={submit} noValidate>
      {step === 0 && <section><h2>Choose Orders</h2>{orders.isPending && <LoadingState />}{orders.isError && <ErrorBanner error={orders.error} retry={() => void orders.refetch()} />}
        <DataTable caption="Available planning Orders" headings={['Select', 'Order', 'Customer', 'Status']}>{orders.data?.pages.flatMap(p => p.items).map(o => <tr key={o.id}><td><input type="checkbox" aria-label={`Select ${o.orderNumber}`} checked={selected.includes(o.id)} disabled={!['OPEN', 'PLANNED'].includes(o.status)} onChange={e => select(e.target.checked ? [...selected, o.id] : selected.filter(id => id !== o.id))} /></td><th scope="row">{o.orderNumber}</th><td>{names.customer(o.customerId)}</td><td>{human(o.status)}</td></tr>)}</DataTable>
        {orders.hasNextPage && <button type="button" onClick={() => void orders.fetchNextPage()}>Load more Orders</button>}
        {details.some(q => q.isPending) && <LoadingState />}{details.filter(q => q.isError).map((q, i) => <ErrorBanner key={i} error={q.error} retry={() => void q.refetch()} />)}
        <h3>Selected work</h3>{!loaded.length && <EmptyState>Select one or more Orders.</EmptyState>}{loaded.map(o => <article className="dispatch-card" key={o.id}><h3>{o.orderNumber} · {names.customer(o.customerId)}</h3><p>{human(o.status)} · PO {o.poNumber ?? '—'} · BOL {o.bolReference ?? '—'}</p><ul>{o.requestedStops.map(s => <li key={s.id}>{names.site(s.siteId, s.siteSnapshot?.name)} · {s.stopType} · {when(s.scheduledAt)}</li>)}</ul><p>{o.cargoLines.length} cargo lines</p></article>)}
        {!ready && !!selected.length && <p role="status">Selected Orders must be loaded and eligible for planning.</p>}
      </section>}
      {step === 1 && <section><h2>Executable Stops</h2><p>Each requested Stop starts independently. Reorder or associate work explicitly; return to base is optional.</p>
        {form.formState.errors.stops?.message && <p role="alert">{form.formState.errors.stops.message}</p>}
        {stops.fields.map((field, i) => <StopEditor key={field.id} index={i} stop={values.stops[i] ?? field} orders={loaded} error={form.formState.errors.stops?.[i]?.siteId?.message ?? form.formState.errors.stops?.[i]?.scheduledAt?.message ?? form.formState.errors.stops?.[i]?.stopType?.message} update={value => form.setValue(`stops.${i}`, value)} up={i ? () => stops.move(i, i - 1) : undefined} down={i + 1 < stops.fields.length ? () => stops.move(i, i + 1) : undefined} remove={() => stops.remove(i)} />)}
        <button type="button" onClick={() => stops.append({ sequenceNumber: stops.fields.length + 1, stopType: '', siteId: null, locationId: null, siteSnapshot: null, scheduledAt: null, instructions: null, orderIds: [] })}>Add Stop</button>
        <button type="button" onClick={() => stops.append({ sequenceNumber: stops.fields.length + 1, stopType: 'BASE_RETURN', siteId: null, locationId: null, siteSnapshot: null, scheduledAt: null, instructions: null, orderIds: [] })}>Add return to base</button>
      </section>}
      {step === 2 && <section><h2>Resources</h2><div className="operations-form-grid">
        <FormField id="plan-driver" label="Driver (optional)"><select id="plan-driver" value={values.driverId ?? ''} onChange={e => form.setValue('driverId', e.target.value || null)}><option value="">Not assigned</option>{names.drivers.data?.items.filter(d => d.active).map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></FormField>
        <FormField id="plan-tractor" label="Tractor (optional)"><select id="plan-tractor" value={values.tractorId ?? ''} onChange={e => form.setValue('tractorId', e.target.value || null)}><option value="">Not assigned</option>{names.tractors.data?.items.filter(d => d.active).map(d => <option key={d.id} value={d.id}>{d.operationalNumber}</option>)}</select></FormField>
        <FormField id="plan-trailer" label="Expected Trailer (optional)"><select id="plan-trailer" value={values.plannedInitialTrailerId ?? ''} onChange={e => form.setValue('plannedInitialTrailerId', e.target.value || null)}><option value="">Not planned</option>{names.trailers.data?.items.filter(t => t.active && t.serviceabilityState === 'IN_SERVICE').map(t => <option key={t.id} value={t.id}>{t.operationalNumber}</option>)}</select></FormField></div>
        <p role="status">Expected planning state: {values.driverId && values.tractorId ? 'ASSIGNED' : 'PLANNED'}. The server will validate the final state.</p>
      </section>}
      {step === 3 && <section><h2>Review Trip</h2><ul>{loaded.map(o => <li key={o.id}>{o.orderNumber} · {names.customer(o.customerId)}</li>)}</ul><ol>{values.stops.map((s, i) => <li key={i}>{human(s.stopType)} · {names.site(s.siteId, s.siteSnapshot?.name)} · {when(s.scheduledAt)} · {s.orderIds.map(id => loaded.find(o => o.id === id)?.orderNumber ?? 'Order unavailable').join(', ') || 'Operational visit'}{s.instructions && <small> · {s.instructions}</small>}</li>)}</ol>
        <p>Driver: {names.driver(values.driverId)} · Tractor: {names.tractor(values.tractorId)} · Expected Trailer: {names.trailer(values.plannedInitialTrailerId)}</p><p>Expected planning state: {values.driverId && values.tractorId ? 'ASSIGNED' : 'PLANNED'}</p>
        {command.isError && <ErrorBanner error={command.error} />}
        {uncertain && <div role="alert"><p>The result is uncertain. Your draft has been preserved. Check the Trip list before starting another plan; resubmitting could create another Trip.</p><Link to="/operations/trips">Reconcile in Trip list</Link></div>}
        <button disabled={command.isPending || uncertain}>Create Trip</button>
      </section>}
      <div className="operations-actions">{step > 0 && <button type="button" disabled={command.isPending} onClick={() => setStep(step - 1)}>Back</button>}{step < 3 && <button type="button" disabled={step === 0 && !ready} onClick={advance}>Continue to {['', 'Stops', 'Resources', 'Review'][step + 1]}</button>}</div>
    </form>
  </>
}
function StopEditor({ index, stop, orders, update, up, down, remove, error }: { index: number; stop: StopInput; orders: OrderDetail[]; update(value: StopInput): void; up?: () => void; down?: () => void; remove(): void; error?: string }) {
  const names = useNames(), locations = useLocations(stop.siteId), prefix = `draft-stop-${index}`
  return <fieldset className="dispatch-card"><legend>Stop {index + 1}</legend>{stop.siteId && <MasterDataStatus queries={[locations]} />}{error && <p role="alert" id={`${prefix}-error`}>{error}</p>}<div className="operations-form-grid">
    <label>Stop type <select aria-label={`Stop ${index + 1} type`} value={stop.stopType} onChange={e => update({ ...stop, stopType: e.target.value })}><option value="">Choose a Stop type</option>{stopTypes.map(t => <option key={t}>{t}</option>)}</select></label>
    <label>Site <select aria-label={`Stop ${index + 1} Site`} value={stop.siteId ?? ''} onChange={e => update({ ...stop, siteId: e.target.value || null, locationId: null, siteSnapshot: null })}><option value="">Choose a Site</option>{names.sites.data?.items.filter(s => s.active).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
    {stop.siteSnapshot && <p>Frozen requested destination: {stop.siteSnapshot.name}</p>}
    <label>Location <select aria-label={`Stop ${index + 1} Location`} disabled={!stop.siteId} value={stop.locationId ?? ''} onChange={e => update({ ...stop, locationId: e.target.value || null })}><option value="">Site only</option>{locations.data?.items.filter(l => l.active).map(l => <option key={l.id} value={l.id}>{l.name}</option>)}</select></label>
    <label>Appointment (ISO 8601 with timezone) <input aria-label={`Stop ${index + 1} appointment`} aria-describedby={error ? `${prefix}-error` : undefined} value={stop.scheduledAt ?? ''} placeholder="2026-10-09T15:00:00Z" onChange={e => update({ ...stop, scheduledAt: e.target.value || null })} /></label>
    <label>Instructions <textarea aria-label={`Stop ${index + 1} instructions`} value={stop.instructions ?? ''} onChange={e => update({ ...stop, instructions: e.target.value || null })} /></label></div>
    <fieldset><legend>Orders served at Stop {index + 1}</legend>{orders.map(o => <label key={o.id}><input type="checkbox" checked={stop.orderIds.includes(o.id)} onChange={e => update({ ...stop, orderIds: e.target.checked ? [...stop.orderIds, o.id] : stop.orderIds.filter(id => id !== o.id) })} />{o.orderNumber}</label>)}</fieldset>
    <div className="operations-actions"><button type="button" disabled={!up} onClick={up}>Move Stop {index + 1} up</button><button type="button" disabled={!down} onClick={down}>Move Stop {index + 1} down</button><button type="button" onClick={remove}>Remove Stop {index + 1}</button></div>
  </fieldset>
}
