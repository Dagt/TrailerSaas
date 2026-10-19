/** Development-only controlled read fixtures. No real credentials, network or writes. */
import type { ApiClient } from '../shared/api/client'
import { ApiProblem } from '../shared/api/client'
import type { Customer, Membership, MasterRecord, QrIdentity } from '../areas/tenant-admin/contracts'
import type { TrailerDetail, TrailerTimelineItem } from '../shared/trailers/contracts'
import type { Alert } from '../areas/alerts/AlertsWorkspace'
import type { AuditEvent } from '../areas/tenant-admin/audit/contracts'

export const demoId = (n: number) => `12000000-0000-4000-8000-${String(n).padStart(12, '0')}`
const stamp = '2026-10-16T14:00:00Z'
const stamps = { version: 3, createdAt: '2026-10-10T14:00:00Z', updatedAt: stamp }
export const demoMembership: Membership = { tenantId: demoId(1), tenantName: 'DEMO · Northline Logistics', tenantActive: true, status: 'ACTIVE', capabilities: ['ALERT_VIEW', 'ALERT_ACKNOWLEDGE', 'ALERT_RESOLVE', 'AUDIT_VIEW'], roles: [{ roleCode: 'TENANT_ADMIN', scopeType: 'TENANT', customerId: null }] }
const customers: Customer[] = [
  { ...stamps, id: demoId(10), name: 'DEMO · Packaging Company', status: 'ACTIVE' },
  { ...stamps, id: demoId(11), name: 'DEMO · Regional Manufacturing and Distribution — Western Receiving Facility', status: 'ACTIVE' },
  { ...stamps, id: demoId(12), name: 'DEMO · Archived Customer', status: 'INACTIVE' },
]
const sites = [
  { ...stamps, id: demoId(20), name: 'DEMO · Main Distribution Site', address: { line1: '100 Example Street', line2: 'Building A', city: 'Example City', stateRegion: 'IL', postalCode: '00000', countryCode: 'US' }, timezone: 'America/Chicago', latitude: 41.8, longitude: -87.6, geofenceRadiusM: 100, active: true },
  { ...stamps, id: demoId(21), name: 'DEMO · Secondary facility', address: null, timezone: 'UTC', latitude: null, longitude: null, geofenceRadiusM: null, active: false },
]
const locations = [
  { ...stamps, id: demoId(22), siteId: demoId(20), name: 'DEMO · Receiving Dock 4', type: 'DOCK', customerId: demoId(10), code: 'DEMO-D4', active: true },
  { ...stamps, id: demoId(23), siteId: demoId(20), name: 'DEMO · Neutral Yard', type: 'YARD', customerId: null, code: null, active: true },
]
const drivers = [
  { ...stamps, id: demoId(30), name: 'DEMO · Driver One', licenseNumber: 'DEMO-LIC-104', medicalCertificateExpires: '2027-12-31', userId: demoId(3), active: true },
  { ...stamps, id: demoId(31), name: 'DEMO · Driver Two', licenseNumber: null, medicalCertificateExpires: '2000-01-01', userId: null, active: false },
]
const tractors = [{ ...stamps, id: demoId(40), operationalNumber: 'DEMO-TC-104', active: true }, { ...stamps, id: demoId(41), operationalNumber: 'DEMO-TC-205', active: false }]
const trailer: TrailerDetail = { ...stamps, id: demoId(50), operationalNumber: 'DEMO-TR-104', active: true, designatedCustomerId: demoId(10), designatedCustomerName: customers[0]!.name, trailerType: 'Dry van', capabilityNotes: 'DEMO · Sample equipment metadata only.', usagePurpose: 'DELIVERY',
  locationState: 'LOCATION_UNKNOWN', currentLocationId: null, currentLocationName: null, lastKnownLocationId: demoId(22), lastKnownLocationName: locations[0]!.name, lastSeenAt: stamp, lastDriverId: demoId(30), lastDriverName: drivers[0]!.name, loadState: 'UNKNOWN', serviceabilityState: 'IN_SERVICE', couplingState: 'UNCOUPLED', currentDriverId: null, currentDriverName: null, currentTractorId: null, currentTractorOperationalNumber: null, lastTractorId: demoId(40), lastTractorOperationalNumber: tractors[0]!.operationalNumber,
  designatedCustomer: { id: demoId(10), name: customers[0]!.name }, currentLocation: null, lastKnownLocation: { id: demoId(22), name: locations[0]!.name, siteId: demoId(20), siteName: sites[0]!.name, type: 'DOCK' }, lastDriver: { id: demoId(30), name: drivers[0]!.name }, currentTractor: null, lastTractor: { id: demoId(40), operationalNumber: tractors[0]!.operationalNumber }, currentCoupling: null, lastCoupling: null,
  openManifests: [{ id: demoId(55), status: 'OPEN', version: 0, updatedAt: stamp, ordersTruncated: false, orders: [] }], manifestSummaryTruncated: false,
}
const trailers = [trailer, { ...trailer, id: demoId(51), operationalNumber: 'DEMO-TR-205', locationState: 'AT_LOCATION', currentLocationId: demoId(22), currentLocationName: locations[0]!.name, loadState: 'LOADED', serviceabilityState: 'OUT_OF_SERVICE' }, { ...trailer, id: demoId(52), operationalNumber: 'DEMO-TR-306', locationState: 'IN_TRANSIT', loadState: 'EMPTY' }]
export const demoQr: QrIdentity = { id: demoId(60), trailerId: trailer.id, operationalNumber: trailer.operationalNumber, active: true, publicToken: 'BBBBBBBBBBBBBBBBBBBBBB', issuedAt: stamp, revokedAt: null }
const timeline: TrailerTimelineItem = { id: demoId(56), eventType: 'LOCATION_UNKNOWN_CONFIRMED', actorType: 'HUMAN_USER', sourceType: 'USER_ACTION', occurredAt: stamp, recordedAt: '2026-10-16T14:05:00Z', reason: 'DEMO · Recorded observation for layout review.', locationId: null, locationName: null, driverId: null, driverName: null, tractorId: null, tractorOperationalNumber: null, tripId: null, tripStopId: null, changes: { previousLocationState: 'AT_LOCATION' } }
const alert: Alert = { id: demoId(70), tenantId: demoId(1), alertType: 'TRAILER_MISSING', severity: 'WARNING', status: 'OPEN', version: 1, createdAt: stamp, acknowledgedAt: null, acknowledgedBy: null, resolvedAt: null, resolvedBy: null, resolutionReason: null, relatedEntityType: 'TRAILER', relatedResource: { id: trailer.id, operationalNumber: trailer.operationalNumber, designatedCustomerId: demoId(10), locationState: 'AT_LOCATION', currentLocationId: demoId(22) }, context: {}, pushSummary: { ACCEPTED: 1 } }
const audit: AuditEvent = { id: demoId(80), actor: { type: 'HUMAN_USER', userId: demoId(2), membershipId: demoId(4) }, sourceType: 'USER_ACTION', actionCode: 'CUSTOMER_UPDATED', entityType: 'CUSTOMER', entityId: demoId(10), beforeData: { name: 'DEMO · Original Customer', status: 'ACTIVE', version: 2 }, afterData: { name: customers[0]!.name, status: 'ACTIVE', version: 3 }, reason: null, occurredAt: stamp }
const redacted: AuditEvent = { ...audit, id: demoId(81), actionCode: 'DRIVER_UPDATED', entityType: 'DRIVER', entityId: demoId(30), beforeData: { licenseNumberPresent: true, medicalCertificatePresent: false, redactedChanges: [], version: 2 }, afterData: { licenseNumberPresent: true, medicalCertificatePresent: false, redactedChanges: ['licenseNumber'], version: 3 } }
const catalogs: Record<string, MasterRecord[]> = { customers, sites, locations, drivers, tractors, trailers }

export function createAdminReviewApi(state = ''): ApiClient {
  return { async request<T>(path: string, options?: Parameters<ApiClient['request']>[1]) {
    if (options?.signal?.aborted) throw new DOMException('Cancelled', 'AbortError')
    if (options?.method && options.method !== 'GET') throw new ApiProblem({ status: state === 'conflict' ? 409 : 503, code: state === 'conflict' ? 'STALE_VERSION' : 'ISOLATED_REVIEW_READ_ONLY' })
    if (path === '/me') return { userId: demoId(2), email: null, displayName: 'DEMO · UI reviewer', memberships: [{ ...demoMembership, ...(state === 'denied' ? { capabilities: [] } : {}) }] } as T
    if (state === 'loading') return new Promise<T>((_resolve, reject) => options?.signal?.addEventListener('abort', () => reject(new DOMException('Cancelled', 'AbortError')), { once: true }))
    if (/^(401|403|404|500)$/.test(state)) throw new ApiProblem({ status: Number(state) })
    const url = new URL(path, 'https://isolated.example.test'), parts = url.pathname.split('/').filter(Boolean).slice(2)
    const kind = parts[0]!, id = parts[1]
    if (kind === 'settings') return { staleTrailerThresholdHours: 72, defaultTimezone: 'America/Chicago', version: 3 } as T
    if (kind === 'alerts') {
      if (id) return { ...alert, id } as T
      return { items: state === 'empty' ? [] : [{ ...alert, id: url.searchParams.has('cursor') ? demoId(71) : alert.id }], nextCursor: state === 'empty' || url.searchParams.has('cursor') ? null : 'demo_next_120' } as T
    }
    if (kind === 'audit-events') {
      if (id) return (id === redacted.id ? redacted : { ...audit, id }) as T
      return { items: state === 'empty' ? [] : url.searchParams.has('cursor') ? [{ ...audit, id: demoId(82) }] : [audit, redacted], nextCursor: state === 'empty' || url.searchParams.has('cursor') ? null : 'demo_next_120' } as T
    }
    if (kind === 'trailers' && parts[2] === 'qr-identities') return { items: [demoQr, { ...demoQr, id: demoId(61), active: false, revokedAt: stamp }] } as T
    if (kind === 'trailers' && parts[2] === 'timeline') return { items: [timeline], nextCursor: null } as T
    if (kind === 'sites' && parts[2] === 'locations') return { items: state === 'empty' ? [] : locations } as T
    if (id) {
      const record = catalogs[kind]?.find(record => record.id === id)
      if (!record) throw new ApiProblem({ status: 404 })
      return record as T
    }
    if (!catalogs[kind]) throw new ApiProblem({ status: 404 })
    const items = state === 'empty' ? [] : catalogs[kind]!.filter(row => [...url.searchParams].every(([key, value]) => !(key in row) || String((row as unknown as Record<string, unknown>)[key]) === value))
    return { items } as T
  } }
}
