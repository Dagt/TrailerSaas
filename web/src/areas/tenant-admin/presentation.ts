import type { ResourceKind } from './resources'

export const adminDescriptions: Record<Exclude<ResourceKind, 'locations'>, string> = {
  customers: 'Manage the Customers your team serves and their Physical Site associations.',
  sites: 'Physical Sites describe a facility or street address. Operational Locations belong to a Site.',
  drivers: 'Maintain Driver records, certificate details and existing account connections.',
  tractors: 'Manage operational identifiers and active Tractor records.',
  trailers: 'Manage Trailer records. Current location, last known observations and serviceability remain separate.',
  settings: 'Configure the stale Trailer threshold and optional default timezone.',
}

export function fieldSection(kind: ResourceKind, key: string) {
  if (kind === 'sites') return key.startsWith('address.') ? 'Business address' : ['latitude', 'longitude', 'geofenceRadiusM'].includes(key) ? 'Geofence configuration' : 'Facility details'
  if (kind === 'drivers') return key === 'name' ? 'Driver identity' : 'License and certificate'
  if (kind === 'trailers') return key === 'capabilityNotes' || key === 'usagePurpose' ? 'Capabilities and purpose' : 'Equipment details'
  return kind === 'settings' ? 'Tenant configuration' : kind === 'locations' ? 'Operational context' : 'Record details'
}

export const adminFieldHints: Record<string, string> = {
  timezone: 'Use an IANA timezone, such as America/Chicago.',
  defaultTimezone: 'Optional IANA timezone. Leave blank when no default is configured.',
  'address.countryCode': 'Use the two-character country code.',
  geofenceRadiusM: 'Radius in meters. This configuration does not provide live tracking.',
  customerId: 'Leave blank for a neutral Operational Location.',
  designatedCustomerId: 'Initial designation only. Later designation changes follow the existing audited workflow.',
  staleTrailerThresholdHours: 'Hours without a physical observation before stale detection applies.',
}
