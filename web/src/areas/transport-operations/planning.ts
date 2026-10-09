import { z } from 'zod'
import type { OrderDetail, StopInput, TripDetail } from './contracts'
import { stopTypes } from './contracts'
export function draftStops(orders: OrderDetail[]): StopInput[] {
  return orders.flatMap(o => [...o.requestedStops].sort((a, b) => a.sequenceNumber - b.sequenceNumber).map(s => ({
    sequenceNumber: 0, stopType: stopTypes.includes(s.stopType as typeof stopTypes[number]) ? s.stopType : '',
    siteId: s.siteId, locationId: s.locationId, siteSnapshot: s.siteSnapshot,
    scheduledAt: s.scheduledAt, instructions: s.instructions, orderIds: [o.id],
  }))).map((s, i) => ({ ...s, sequenceNumber: i + 1 }))
}
const nullableId = z.uuid().nullable()
export const instant = z.iso.datetime({ offset: true }).nullable()
export const createSchema = z.object({ orderIds: z.array(z.uuid()).min(1), stops: z.array(z.object({
  sequenceNumber: z.number().int().positive(), stopType: z.enum(stopTypes), siteId: nullableId, locationId: nullableId,
  siteSnapshot: z.custom<StopInput['siteSnapshot']>(), scheduledAt: instant, instructions: z.string().nullable(), orderIds: z.array(z.uuid()),
})).min(1), driverId: nullableId, tractorId: nullableId, plannedInitialTrailerId: nullableId }).superRefine((r, ctx) => {
  const selected = new Set(r.orderIds), covered = new Set(r.stops.flatMap(s => s.orderIds))
  if (selected.size !== r.orderIds.length || selected.size !== covered.size || [...covered].some(id => !selected.has(id))) ctx.addIssue({ code: 'custom', path: ['stops'], message: 'Every selected Order must be covered by a Stop, without other Orders.' })
  if (new Set(r.stops.map(s => s.sequenceNumber)).size !== r.stops.length) ctx.addIssue({ code: 'custom', path: ['stops'], message: 'Stop sequences must be unique.' })
  r.stops.forEach((s, i) => {
    if ((!s.siteId && !s.siteSnapshot?.name.trim()) || (!s.siteId && s.locationId)) ctx.addIssue({ code: 'custom', path: ['stops', i, 'siteId'], message: 'Choose a Site or preserve a requested frozen destination.' })
    if (new Set(s.orderIds).size !== s.orderIds.length) ctx.addIssue({ code: 'custom', path: ['stops', i, 'orderIds'], message: 'Duplicate Order association.' })
  })
})
export function actions(trip: Pick<TripDetail, 'status' | 'plannedInitialTrailerId'>) {
  const assignment = !['COMPLETED', 'CANCELLED'].includes(trip.status)
  return { driver: assignment, tractor: assignment,
    trailer: trip.plannedInitialTrailerId ? ['PLANNED', 'ASSIGNED'].includes(trip.status) : ['PLANNED', 'ASSIGNED', 'IN_TRANSIT', 'AT_STOP'].includes(trip.status),
    substitution: !!trip.plannedInitialTrailerId && ['ASSIGNED', 'IN_TRANSIT', 'AT_STOP'].includes(trip.status),
    reroute: ['IN_TRANSIT', 'AT_STOP'].includes(trip.status), cancellation: ['PLANNED', 'ASSIGNED', 'IN_TRANSIT', 'AT_STOP'].includes(trip.status),
  }
}
