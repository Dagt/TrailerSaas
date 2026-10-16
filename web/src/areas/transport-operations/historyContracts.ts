import { z } from 'zod'
import { ApiProblem } from '../../shared/api/client'
const uuid = z.string().regex(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)
const common = { id: uuid, actorUserId: uuid.nullable(), occurredAt: z.iso.datetime({ offset: true }), reason: z.string().nullable(), metadata: z.record(z.string(), z.unknown()).nullable() }
const assignment = z.object({ ...common, eventType: z.enum(['DRIVER_ASSIGNED', 'DRIVER_REASSIGNED', 'TRACTOR_ASSIGNED', 'TRACTOR_REASSIGNED', 'TRAILER_PLANNED', 'TRAILER_SUBSTITUTION_AUTHORIZED', 'TRAILER_SUBSTITUTION_REJECTED']), driverId: uuid.nullable(), tractorId: uuid.nullable(), trailerId: uuid.nullable() }).strict()
const operational = z.object({ ...common, eventType: z.enum(['TRIP_REROUTED', 'TRIP_CANCELLED', 'TRIP_CANCELLATION_STARTED', 'TRIP_CANCELLATION_FINISHED']), tripStopId: uuid.nullable(), targetDriverId: uuid.nullable() }).strict()
export const historyPageSchema = z.object({ items: z.array(z.union([assignment, operational])).max(100), nextCursor: z.string().min(1).max(512).regex(/^[A-Za-z0-9_-]+$/).nullable() }).strict()
export type HistoryPosition = { cursor?: string; seen: string[]; ids: string[] }
export const historyIdentity = (event: { id: string }) => `${'driverId' in event ? 'assignment' : 'operational'}:${event.id}`
export function historyPage(value: unknown, position: HistoryPosition) {
  const result = historyPageSchema.safeParse(value)
  if (!result.success) throw new ApiProblem({ status: 502, code: 'UNEXPECTED_RESPONSE' })
  const page = result.data, ids = page.items.map(historyIdentity)
  if (new Set(ids).size !== ids.length || ids.some(id => position.ids.includes(id)) || page.nextCursor &&
    (page.items.length === 0 || page.nextCursor === position.cursor || position.seen.includes(page.nextCursor))) throw new ApiProblem({ status: 502, code: 'UNEXPECTED_RESPONSE' })
  return page
}
