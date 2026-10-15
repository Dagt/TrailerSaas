import { z } from 'zod'
import { orderStatuses } from '../transport-operations/contracts'
const id = z.uuid(), instant = z.iso.datetime({ offset: true }), nullableText = z.string().nullable()
export const orderSummary = z.object({ id, customerId: id, orderNumber: z.string(), poNumber: nullableText, shipmentNumber: nullableText, bolReference: nullableText,
  status: z.enum(orderStatuses), version: z.number().int().nonnegative(), createdAt: instant, updatedAt: instant,
  deliveredAt: instant.nullable(), completedAt: instant.nullable(), cancelledAt: instant.nullable(),
})
export const cargoInput = z.object({ description: z.string().trim().min(1), quantity: z.number().finite().nullable(), palletCount: z.number().int().nullable(), weight: z.number().finite().nullable(), weightUnit: nullableText })
export const snapshot = z.object({ name: z.string().trim().min(1), address: z.object({ line1: nullableText.optional(), line2: nullableText.optional(), city: nullableText.optional(), stateRegion: nullableText.optional(), postalCode: nullableText.optional(), countryCode: z.string().length(2).nullable().optional() }).nullable().optional(), timezone: nullableText.optional(), latitude: z.number().min(-90).max(90).nullable().optional(), longitude: z.number().min(-180).max(180).nullable().optional(), geofenceRadiusM: z.number().int().positive().nullable().optional() })
export const stopInput = z.object({ sequenceNumber: z.number().int().positive(), siteId: id.nullable(), locationId: id.nullable(), siteSnapshot: snapshot.nullable(), stopType: z.string().trim().min(1), scheduledAt: instant.nullable(), instructions: nullableText })
export const orderDetail = orderSummary.extend({ cargoLines: z.array(cargoInput.extend({ id, createdAt: instant })), requestedStops: z.array(stopInput.extend({ id, version: z.number().int().nonnegative(), createdAt: instant, updatedAt: instant })) })
export type CustomerOrder = z.infer<typeof orderDetail>
export type OrderRow = z.infer<typeof orderSummary>
export type CargoInput = z.infer<typeof cargoInput>
export type StopInput = z.infer<typeof stopInput>
export const newStop=():StopInput=>({sequenceNumber:1,siteId:null,locationId:null,siteSnapshot:{name:''},stopType:'DELIVERY',scheduledAt:null,instructions:null})
export const orderPage = z.object({ items: z.array(orderSummary), nextCursor: z.string().min(1).nullable() }).strict()
export const progressTrip = z.object({ id, status: z.enum(['PLANNED','ASSIGNED','IN_TRANSIT','AT_STOP','CANCELLING','COMPLETED','CANCELLED']), version: z.number().int().nonnegative(),
  expectedTrailer: z.object({ id, operationalNumber: z.string() }).nullable(), canSubstitute: z.boolean(),
  stops: z.array(z.object({ id, stopType: z.string(), status: z.enum(['PENDING','EN_ROUTE','ARRIVED','IN_SERVICE','COMPLETED','CANCELLED']), siteId: id.nullable(), locationId: id.nullable(), destination: nullableText, scheduledAt: instant.nullable(), arrivedAt: instant.nullable(), completedAt: instant.nullable(), canReroute: z.boolean() }).strict()),
  history: z.array(z.object({ id, eventType: z.string(), occurredAt: instant, reason: nullableText, previousDestination: nullableText, newDestination: nullableText, decision: z.enum(['AUTHORIZE','REJECT']).nullable() }).strict()),
}).strict()
export type ProgressTrip = z.infer<typeof progressTrip>
export type ProgressStop = ProgressTrip['stops'][number]
export const progressPage = z.object({ orderId:id,customerId:id,orderVersion:z.number().int().nonnegative(),requestEditable:z.boolean(),items: z.array(progressTrip), nextCursor: id.nullable(),
  changes:z.array(z.object({version:z.number().int().positive(),changeType:z.enum(['ORDER_EDITED','ORDER_CANCELLED','ORDER_CANCELLATION_REQUESTED','ORDER_COMPLETED']),recordedAt:instant,reason:nullableText,resolution:z.object({type:z.string(),destinationSiteId:id.nullable(),instructions:nullableText}).nullable()}).strict()),
}).strict()
export const createOrderInput = z.object({ customerId: id, orderNumber: z.string().trim().min(1), poNumber: nullableText, shipmentNumber: nullableText, bolReference: nullableText,
  cargoLines: z.array(cargoInput), requestedStops: z.array(stopInput).min(1), plannedTrailerId: id.nullable(),
}).strict().superRefine((value, context) => {
  const sequences = new Set<number>()
  value.requestedStops.forEach((s, index) => {
    if (sequences.has(s.sequenceNumber) || !s.siteId && (!s.siteSnapshot || s.locationId)) context.addIssue({ code: 'custom', path: ['requestedStops',index], message: 'Each Stop needs a unique sequence and an authorized Site or destination snapshot.' })
    sequences.add(s.sequenceNumber)
  })
})
export const siteChoice = z.object({ id, name: z.string(), active: z.boolean() })
export const locationChoice = siteChoice.extend({ siteId: id, customerId: id.nullable() })
export const trailerChoice = z.object({ id, operationalNumber: z.string(), designatedCustomerId: id.nullable(), active: z.boolean(), serviceabilityState: z.string() })
