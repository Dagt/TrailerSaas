export interface Membership {
  tenantStatus?: string
  capabilities?: string[]
  tenantId: string; tenantName: string; status: 'ACTIVE' | 'SUSPENDED' | 'DISABLED'
  roles: { roleCode: string; scopeType: 'TENANT' | 'CUSTOMER'; customerId: string | null }[]
}
export interface CurrentUser { userId: string; email: string | null; displayName: string | null; memberships: Membership[] }
export interface Versioned { id: string; version: number; createdAt: string; updatedAt: string }
export interface Customer extends Versioned { name: string; status: 'ACTIVE' | 'INACTIVE' }
export interface Address { line1: string | null; line2: string | null; city: string | null; stateRegion: string | null; postalCode: string | null; countryCode: string | null }
export interface Site extends Versioned {
  name: string; address: Address | null; timezone: string; latitude: number | null; longitude: number | null; geofenceRadiusM: number | null; active: boolean
}
export interface Location extends Versioned { siteId: string; customerId: string | null; name: string; type: string; code: string | null; active: boolean }
export interface Driver extends Versioned { name: string; licenseNumber: string | null; medicalCertificateExpires: string | null; userId: string | null; active: boolean }
export interface Tractor extends Versioned { operationalNumber: string; active: boolean }
export interface Trailer extends Tractor {
  designatedCustomerId: string | null; trailerType: string | null; capabilityNotes: string | null
  locationState: string; currentLocationId: string | null; lastKnownLocationId: string | null; lastSeenAt: string | null; lastDriverId: string | null
  loadState: string; serviceabilityState: string; usagePurpose: string | null
}
export interface QrIdentity { id: string; trailerId: string; publicToken: string; operationalNumber: string; active: boolean; issuedAt: string; revokedAt: string | null }
export interface Settings { staleTrailerThresholdHours: number; defaultTimezone: string | null; version: number }
export interface Items<T> { items: T[] }
export type MasterRecord = Customer | Site | Location | Driver | Tractor | Trailer
export interface CustomerSite { id: string; customerId: string; siteId: string; createdAt: string }
