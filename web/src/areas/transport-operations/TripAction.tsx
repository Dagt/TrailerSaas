import { MasterDataStatus } from './MasterDataStatus'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { ApiProblem } from '../../shared/api/client'
import { ConfirmDialog, ErrorBanner, FormField } from '../../shared/ui/components'
import { useLocations, useNames, useOperations } from './queries'
import { useTripCommand } from './mutations'
import type { TripDetail, TripStop } from './contracts'
import { human } from './display'
import { instant } from './planning'
export type ActionKind = 'driver' | 'tractor' | 'trailer' | 'substitution' | 'reroute' | 'cancellation'
interface Fields { resourceId: string; reason: string; siteId: string; locationId: string; scheduledAt: string; instructions: string; resolutionType: string }
export function TripAction({ kind, trip, stop, onClose, reload }: { kind: ActionKind; trip: TripDetail; stop?: TripStop; onClose(): void; reload(): void }) {
  const { api } = useOperations(), names = useNames()
  const active = ['IN_TRANSIT', 'AT_STOP'].includes(trip.status)
  const form = useForm<Fields>({ defaultValues: { resourceId: '', reason: '', siteId: stop?.siteId ?? '', locationId: stop?.locationId ?? '', scheduledAt: stop?.scheduledAt ?? '', instructions: stop?.instructions ?? '', resolutionType: '' } })
  const values = useWatch({ control: form.control }) as Fields, locations = useLocations(values.siteId || null)
  const originalLocations = useLocations(stop?.siteId ?? null)
  const [decision, decide] = useState<'AUTHORIZE' | 'REJECT'>('AUTHORIZE'), [confirmed, confirm] = useState(false)
  const command = useTripCommand(async (v: Fields) => {
    if (kind === 'driver' || kind === 'tractor' || kind === 'trailer') return api.assign(trip.id, kind, trip.version, v.resourceId, v.reason)
    if (kind === 'substitution') return api.substitute(trip.id, { expectedTrailerId: trip.plannedInitialTrailerId!, requestedTrailerId: v.resourceId, decision, reason: v.reason })
    if (kind === 'reroute') return api.reroute(trip.id, { expectedVersion: trip.version, affectedStopId: stop!.id, newSiteId: v.siteId, newLocationId: v.locationId || null, scheduledAt: v.scheduledAt || null, reason: v.reason, instructions: v.instructions })
    return api.cancel(trip.id, { expectedVersion: trip.version, reason: v.reason, ...(active ? { resolutionType: v.resolutionType, destinationSiteId: v.resolutionType === 'HOLD_CONTACT_DISPATCH' ? null : v.siteId || null, instructions: v.instructions } : {}) })
  })
  const submit = form.handleSubmit(async value => {
    form.clearErrors()
    const nonblank = z.string().trim().min(1, 'Required.'), id = z.uuid('Choose a resource.')
    const checks: [keyof Fields, z.ZodType][] = [['reason', nonblank]]
    if (['driver', 'tractor', 'trailer', 'substitution'].includes(kind)) checks.push(['resourceId', id])
    if (kind === 'reroute') checks.push(['siteId', id], ['instructions', nonblank], ['scheduledAt', instant.or(z.literal(''))])
    if (kind === 'cancellation' && active) {
      checks.push(['resolutionType', z.enum(['RETURN_TO_BASE', 'REROUTE', 'HOLD_CONTACT_DISPATCH', 'OTHER_AUTHORIZED'])], ['instructions', nonblank])
      if (['RETURN_TO_BASE', 'REROUTE'].includes(value.resolutionType)) checks.push(['siteId', id])
    }
    let invalid = false
    for (const [field, schema] of checks) {
      const result = schema.safeParse(value[field]); if (!result.success) { invalid = true; form.setError(field, { message: result.error.issues[0]?.message }) }
    }
    if (kind === 'substitution' && value.resourceId === trip.plannedInitialTrailerId) { invalid = true; form.setError('resourceId', { message: 'Choose a different Trailer.' }) }
    if (invalid) return
    try { await command.mutateAsync(value); onClose() } catch (error) {
      if (error instanceof ApiProblem) for (const e of error.problem.errors ?? []) {
        const aliases: Record<string, keyof Fields> = { driverId: 'resourceId', tractorId: 'resourceId', trailerId: 'resourceId', requestedTrailerId: 'resourceId', newSiteId: 'siteId', destinationSiteId: 'siteId', newLocationId: 'locationId' }
        const field = aliases[e.field] ?? e.field
        if (field in value) form.setError(field as keyof Fields, { message: e.message })
      }
    }
  })
  const fieldProps = (field: keyof Fields) => ({ id: `action-${field}`, 'aria-invalid': !!form.formState.errors[field], 'aria-describedby': form.formState.errors[field] ? `action-${field}-error` : undefined })
  const text = (field: keyof Fields, label: string, textarea = false) => <FormField id={`action-${field}`} label={label} error={form.formState.errors[field]?.message}>{textarea ? <textarea {...fieldProps(field)} {...form.register(field)} /> : <input {...fieldProps(field)} {...form.register(field)} />}</FormField>
  const resourceOptions = kind === 'driver' ? names.drivers.data?.items.filter(x => x.active).map(x => ({ id: x.id, label: x.name })) : kind === 'tractor' ? names.tractors.data?.items.filter(x => x.active).map(x => ({ id: x.id, label: x.operationalNumber })) : names.trailers.data?.items.filter(x => kind === 'substitution' && decision === 'REJECT' || x.active && x.serviceabilityState === 'IN_SERVICE').map(x => ({ id: x.id, label: x.operationalNumber }))
  const problem = command.error instanceof ApiProblem ? command.error.problem : undefined
  const blocked = command.isPending || problem?.code === 'STALE_VERSION' || problem?.code === 'INVALID_STATE_TRANSITION' || (problem?.status ?? 0) >= 500
  const title = kind === 'cancellation' ? active ? 'Start cancellation' : 'Cancel Trip before execution' : kind === 'substitution' ? 'Resolve alternate Trailer' : kind === 'reroute' ? `Reroute Stop ${stop!.sequenceNumber}` : `Assign / reassign ${human(kind)}`
  const needsConfirm = ['substitution', 'cancellation', 'reroute'].includes(kind)
  return <ConfirmDialog className={`ops-dialog${kind === 'cancellation' ? ' ops-dialog--danger' : ''}`} title={title} confirmLabel={kind === 'substitution' ? decision === 'AUTHORIZE' ? 'Authorize alternate Trailer' : 'Reject and keep expected Trailer' : kind === 'cancellation' ? active ? 'Start cancellation' : 'Cancel Trip before execution' : kind === 'reroute' ? 'Confirm reroute' : 'Save assignment'} onConfirm={() => void submit()} onCancel={onClose} busy={command.isPending} confirmDisabled={blocked || needsConfirm && !confirmed}>
    <form onSubmit={e => e.preventDefault()} noValidate>
      <MasterDataStatus queries={[names.drivers, names.tractors, names.trailers, names.sites, ...(values.siteId ? [locations] : [])]} /><p>Trip state: {human(trip.status)} · Version {trip.version}</p>
      {kind === 'substitution' && <><p>Expected Trailer: <strong>{names.trailer(trip.plannedInitialTrailerId)}</strong></p><p>Manual dispatcher decision for a known discrepancy. This is not a Driver scan request and does not authorize physical execution.</p>
        <label>Decision <select aria-label="Substitution decision" value={decision} onChange={e => { decide(e.target.value as 'AUTHORIZE' | 'REJECT'); form.setValue('resourceId', ''); confirm(false) }}><option value="AUTHORIZE">Authorize alternate Trailer</option><option value="REJECT">Reject and keep expected Trailer</option></select></label></>}
      {['driver', 'tractor', 'trailer', 'substitution'].includes(kind) && <FormField id="action-resourceId" label={kind === 'substitution' ? 'Requested Trailer' : human(kind)} error={form.formState.errors.resourceId?.message}><select {...fieldProps('resourceId')} {...form.register('resourceId')}><option value="">Choose {kind === 'substitution' ? 'a Trailer' : `a ${kind}`}</option>{resourceOptions?.map(x => <option key={x.id} value={x.id}>{x.label}</option>)}</select></FormField>}
      {kind === 'cancellation' && <><p>{active ? 'Physical resolution is still required. This starts cancellation; it does not mark execution complete.' : 'This will cancel the Trip before execution. Historical planning and assignments will remain recorded.'}</p>
        {active && <><p>Driver: {names.driver(trip.currentDriverId)} · Expected Trailer: {names.trailer(trip.plannedInitialTrailerId)}</p><p>{trip.stops.filter(s => s.status === 'COMPLETED').length} completed Stops · {trip.stops.filter(s => !['COMPLETED', 'CANCELLED'].includes(s.status)).length} pending Stops</p>
          <FormField id="action-resolutionType" label="Resolution" error={form.formState.errors.resolutionType?.message}><select {...fieldProps('resolutionType')} {...form.register('resolutionType')} onChange={e => { form.setValue('resolutionType', e.target.value); if (e.target.value === 'HOLD_CONTACT_DISPATCH') form.setValue('siteId', '') }}><option value="">Choose a resolution</option>{['RETURN_TO_BASE', 'REROUTE', 'HOLD_CONTACT_DISPATCH', 'OTHER_AUTHORIZED'].map(r => <option key={r}>{r}</option>)}</select></FormField></>}
      </>}
      {kind === 'reroute' && <><p>Current destination: {names.site(stop!.siteId, stop!.siteSnapshot?.name)}{stop!.locationId && <> · {originalLocations.data?.items.find(l => l.id === stop!.locationId)?.name ?? 'Location unavailable'}</>}</p><p>This changes the Driver’s future instructions. The previous destination will remain in history.</p></>}
      {(kind === 'reroute' || kind === 'cancellation' && active && values.resolutionType !== 'HOLD_CONTACT_DISPATCH') && <FormField id="action-siteId" label={kind === 'reroute' ? 'New Site' : 'Destination Site'} error={form.formState.errors.siteId?.message}><select {...fieldProps('siteId')} {...form.register('siteId')} onChange={e => { form.setValue('siteId', e.target.value); form.setValue('locationId', '') }}><option value="">Choose a Site</option>{names.sites.data?.items.filter(s => s.active).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></FormField>}
      {kind === 'reroute' && <><FormField id="action-locationId" label="New Location (optional)"><select {...fieldProps('locationId')} {...form.register('locationId')} disabled={!values.siteId}><option value="">Site only</option>{locations.data?.items.filter(l => l.active).map(l => <option key={l.id} value={l.id}>{l.name}</option>)}</select></FormField>{text('scheduledAt', 'Appointment (ISO 8601 with timezone; clear to remove)')}</>}
      {text('reason', 'Reason', true)}{(kind === 'reroute' || kind === 'cancellation' && active) && text('instructions', 'Instructions', true)}
      {needsConfirm && <label><input type="checkbox" checked={confirmed} onChange={e => confirm(e.target.checked)} />I confirm these operational instructions.</label>}
      {command.isError && <><ErrorBanner error={command.error} reload={reload} staleMessage="This Trip changed while you were viewing it. Reload the latest state before continuing." />{problem?.code !== 'STALE_VERSION' && <button type="button" onClick={reload}>Reload latest</button>}</>}
    </form>
  </ConfirmDialog>
}
