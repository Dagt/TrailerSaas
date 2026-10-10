import type { TrailerListItem } from './contracts'
export const readable = (value: string) => value.toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase())
export const observedAt = (value: string | null) => value ? new Date(value).toLocaleString() : 'Not observed'
export function currentLocationLabel(t: Pick<TrailerListItem, 'locationState' | 'currentLocationName'>) {
  if (t.locationState === 'LOCATION_UNKNOWN') return 'Unknown'
  if (t.locationState === 'IN_TRANSIT') return 'In transit'
  return t.currentLocationName ?? 'Location unavailable'
}
