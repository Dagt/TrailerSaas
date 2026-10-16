export type { Customer, Site, Location, Driver, Tractor, Trailer } from '../tenant-admin/contracts'
export interface Page<T> { items: T[]; nextCursor: string | null }
export type TripStatus = 'PLANNED' | 'ASSIGNED' | 'IN_TRANSIT' | 'AT_STOP' | 'CANCELLING' | 'COMPLETED' | 'CANCELLED'
export type OrderStatus = 'OPEN' | 'PLANNED' | 'IN_PROGRESS' | 'PARTIALLY_DELIVERED' | 'DELIVERED' | 'COMPLETED' | 'CANCELLING' | 'CANCELLED'
export const tripStatuses: TripStatus[] = ['PLANNED', 'ASSIGNED', 'IN_TRANSIT', 'AT_STOP', 'CANCELLING', 'COMPLETED', 'CANCELLED']
export const orderStatuses: OrderStatus[] = ['OPEN', 'PLANNED', 'IN_PROGRESS', 'PARTIALLY_DELIVERED', 'DELIVERED', 'COMPLETED', 'CANCELLING', 'CANCELLED']
export const stopTypes = ['BASE_DEPARTURE', 'PICKUP', 'DELIVERY', 'LIVE_LOAD', 'LIVE_UNLOAD', 'DROP_HOOK', 'BASE_RETURN', 'OTHER'] as const
export interface SiteSnapshot {
  name: string; address?: { line1?: string | null; line2?: string | null; city?: string | null; stateRegion?: string | null; postalCode?: string | null; countryCode?: string | null } | null
  timezone?: string | null; latitude?: number | null; longitude?: number | null; geofenceRadiusM?: number | null
}
export interface OrderSummary {
  id: string; customerId: string; orderNumber: string; status: OrderStatus; version: number; createdAt: string; updatedAt: string
  poNumber: string | null; shipmentNumber: string | null; bolReference: string | null
  deliveredAt: string | null; completedAt: string | null; cancelledAt: string | null
}
export interface RequestedStop { id: string; sequenceNumber: number; stopType: string; siteId: string | null; locationId: string | null; siteSnapshot: SiteSnapshot | null; scheduledAt: string | null; instructions: string | null }
export interface OrderDetail extends OrderSummary {
  requestedStops: RequestedStop[]
  cargoLines: { id: string; description: string; quantity: number | null; palletCount: number | null; weight: number | null; weightUnit: string | null; createdAt: string }[]
}
export interface TripSummary {
  id: string; status: TripStatus; currentDriverId: string | null; currentTractorId: string | null; plannedInitialTrailerId: string | null
  version: number; createdAt: string; updatedAt: string; startedAt: string | null; completedAt: string | null; cancelledAt: string | null
}
export interface StopInput {
  sequenceNumber: number; stopType: string; siteId: string | null; locationId: string | null; siteSnapshot: SiteSnapshot | null
  scheduledAt: string | null; orderIds: string[]; instructions: string | null
}
export interface TripStop extends StopInput {
  id: string; status: string; version: number; createdAt: string; updatedAt: string
  arrivedAt: string | null; serviceStartedAt: string | null; completedAt: string | null; departedAt: string | null
}
export interface AssignmentHistory {
  id: string; eventType: string; driverId: string | null; tractorId: string | null; trailerId: string | null
  actorUserId: string | null; occurredAt: string; reason: string | null; metadata: Record<string, unknown> | null
}
export interface OperationalHistory {
  id: string; eventType: string; tripStopId: string | null; actorUserId: string | null; targetDriverId: string | null
  occurredAt: string; reason: string | null; metadata: Record<string, unknown> | null
}
export interface TripDetail extends TripSummary { stops: TripStop[]; assignmentHistory: AssignmentHistory[]; operationalHistory: OperationalHistory[] }
export interface Timeline { items: (AssignmentHistory | OperationalHistory)[] }
export interface NextStop { id: string; sequenceNumber: number; stopType: string; siteId: string | null; locationId: string | null; snapshotName: string | null; scheduledAt: string | null; status: string }
export interface DispatchBoard {
  generatedAt: string
  pendingOrders: Pick<OrderSummary, 'id' | 'customerId' | 'orderNumber' | 'status' | 'version' | 'createdAt' | 'updatedAt'>[]
  activeTrips: (Pick<TripSummary, 'id' | 'status' | 'currentDriverId' | 'currentTractorId' | 'plannedInitialTrailerId' | 'version' | 'createdAt' | 'updatedAt'> & { nextPlannedStop: NextStop | null })[]
  attentionItems: { type: 'INCOMPLETE_ASSIGNMENT' | 'CANCELLING'; tripId: string }[]
}
export interface CreateTrip { orderIds: string[]; stops: StopInput[]; driverId: string | null; tractorId: string | null; plannedInitialTrailerId: string | null }
export interface Reroute { expectedVersion: number; affectedStopId: string; newSiteId: string; newLocationId: string | null; scheduledAt: string | null; reason: string; instructions: string }
export interface Cancellation { expectedVersion: number; reason: string; resolutionType?: string; destinationSiteId?: string | null; instructions?: string }
export interface Substitution { expectedTrailerId: string; requestedTrailerId: string; decision: 'AUTHORIZE' | 'REJECT'; reason: string }
