import type { Trailer } from '../../areas/tenant-admin/contracts'
export interface Named { id: string; name: string }
export interface Numbered { id: string; operationalNumber: string }
export interface LocationSummary extends Named { type: string; siteId: string; siteName: string }
export interface Coupling {
  id: string; driverId: string; driverName: string; tractorId: string; tractorOperationalNumber: string
  tripId: string | null; hookedAt: string; droppedAt: string | null
}
export interface TrailerListItem extends Trailer {
  designatedCustomerName: string | null; currentLocationName: string | null; lastKnownLocationName: string | null
  couplingState: 'COUPLED' | 'UNCOUPLED'; currentDriverId: string | null; currentDriverName: string | null
  currentTractorId: string | null; currentTractorOperationalNumber: string | null; lastDriverName: string | null
  lastTractorId: string | null; lastTractorOperationalNumber: string | null
}
export interface ManifestSummary {
  id: string; status: 'OPEN'; version: number; updatedAt: string
  orders: { id: string; customerId: string; customerName: string; orderNumber: string; status: string }[]
}
export interface TrailerDetail extends TrailerListItem {
  designatedCustomer: Named | null; currentLocation: LocationSummary | null; lastKnownLocation: LocationSummary | null
  lastDriver: Named | null; currentTractor: Numbered | null; lastTractor: Numbered | null
  currentCoupling: Coupling | null; lastCoupling: Coupling | null
  openManifests: ManifestSummary[]; manifestSummaryTruncated: boolean
}
export interface TrailerTimelineItem {
  id: string; eventType: string; actorType: string; sourceType: string; occurredAt: string; recordedAt: string
  reason: string | null; locationId: string | null; locationName: string | null; driverId: string | null; driverName: string | null
  tractorId: string | null; tractorOperationalNumber: string | null; tripId: string | null; tripStopId: string | null
  changes: { previousLocationState?: string; previousLocationId?: string | null; newLocationId?: string | null
    previousLoadState?: string; newLoadState?: string; previousServiceabilityState?: string; newServiceabilityState?: string }
}
export interface TrailerTimelinePage { items: TrailerTimelineItem[]; nextCursor: string | null }
