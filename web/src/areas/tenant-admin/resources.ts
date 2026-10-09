import { z } from 'zod'
import type { MasterRecord } from './contracts'

export type ResourceKind = 'customers' | 'sites' | 'locations' | 'drivers' | 'tractors' | 'trailers' | 'settings'
export interface FieldDefinition {
  key: string; label: string; type?: 'text' | 'textarea' | 'number' | 'date' | 'select'
  required?: boolean; choices?: string[]; reference?: 'customers'; validate?: z.ZodType
  createOnly?: boolean; placeholder?: string
}
const nonblank = z.string().refine(v => v.trim().length > 0, 'Enter a nonblank value.')
export const operationalNumber = z.string().refine(v => /[^ ]/.test(v) && !Array.from(v).some(c => {
  const code = c.codePointAt(0)!
  return code <= 31 || (code >= 127 && code <= 159)
}), 'Enter a number without control characters.')
export const timezone = z.string().refine(value => {
  try { if (/^[+-]/.test(value)) return false; new Intl.DateTimeFormat('en', { timeZone: value }); return true } catch { return false }
}, 'Enter a valid IANA timezone.')
export const usagePurposes = ['DELIVERY', 'SHUTTLE', 'WIP_STORAGE', 'TOOLING_STORAGE', 'OTHER']
const name: FieldDefinition = { key: 'name', label: 'Name', required: true, validate: nonblank }
const number: FieldDefinition = { key: 'operationalNumber', label: 'Operational number', required: true, validate: operationalNumber }
const customer: FieldDefinition = { key: 'customerId', label: 'Customer context', type: 'select', reference: 'customers', validate: z.uuid() }
export const definitions: Record<ResourceKind, { title: string; singular: string; fields: FieldDefinition[] }> = {
  customers: { title: 'Customers', singular: 'Customer', fields: [name] },
  sites: { title: 'Locations', singular: 'Physical Site', fields: [name,
    ...['line1', 'line2', 'city', 'stateRegion', 'postalCode', 'countryCode'].map(key => ({ key: `address.${key}`, label: ({ line1: 'Address line 1', line2: 'Address line 2', city: 'City', stateRegion: 'State / region', postalCode: 'Postal code', countryCode: 'Country code' } as Record<string, string>)[key]!, ...(key === 'countryCode' ? { validate: z.string().length(2, 'Use a two-character country code.') } : {}) })),
    { key: 'timezone', label: 'Timezone', required: true, validate: timezone },
    { key: 'latitude', label: 'Latitude', type: 'number', validate: z.number().min(-90).max(90) },
    { key: 'longitude', label: 'Longitude', type: 'number', validate: z.number().min(-180).max(180) },
    { key: 'geofenceRadiusM', label: 'Geofence radius (m)', type: 'number', validate: z.number().int().positive().max(2147483647) },
  ] },
  locations: { title: 'Operational Locations', singular: 'Operational Location', fields: [name,
    { key: 'type', label: 'Location type', type: 'select', required: true, choices: ['BASE', 'YARD', 'DOCK', 'CUSTOMER_AREA', 'SHOP', 'OTHER'], placeholder: 'Choose a location type' },
    { key: 'code', label: 'Code' }, customer,
  ] },
  drivers: { title: 'Drivers', singular: 'Driver', fields: [name, { key: 'licenseNumber', label: 'License number' },
    { key: 'medicalCertificateExpires', label: 'Medical certificate expires', type: 'date', validate: z.iso.date() }] },
  tractors: { title: 'Tractors', singular: 'Tractor', fields: [number] },
  trailers: { title: 'Trailers', singular: 'Trailer', fields: [number,
    { ...customer, key: 'designatedCustomerId', label: 'Designated Customer', createOnly: true },
    { key: 'trailerType', label: 'Trailer type' }, { key: 'capabilityNotes', label: 'Capability notes', type: 'textarea' },
    { key: 'usagePurpose', label: 'Usage purpose', type: 'select', choices: usagePurposes },
  ] },
  settings: { title: 'Settings', singular: 'Settings', fields: [
    { key: 'staleTrailerThresholdHours', label: 'Stale Trailer threshold (hours)', type: 'number', required: true, validate: z.number().int().positive().max(2147483647) },
    { key: 'defaultTimezone', label: 'Default timezone', validate: timezone },
  ] },
}
export function valueAt(record: object, key: string): unknown {
  return key.split('.').reduce<unknown>((value, part) => value && typeof value === 'object' ? (value as Record<string, unknown>)[part] : undefined, record)
}
export function recordName(record: MasterRecord) { return 'name' in record ? record.name : record.operationalNumber }
export function isActive(record: MasterRecord) { return 'status' in record ? record.status === 'ACTIVE' : record.active }
export function formValues(kind: ResourceKind, record?: object) {
  return Object.fromEntries(definitions[kind].fields.map(field => [field.key.replace('.', '_'), record ? String(valueAt(record, field.key) ?? '') : '']))
}
/** Explicit request projection; never spread a response DTO into PATCH. */
export function requestValues(kind: ResourceKind, values: Record<string, string>, record?: object) {
  const body: Record<string, unknown> = {}
  const errors: Record<string, string> = {}
  for (const field of definitions[kind].fields) {
    if (record && field.createOnly) continue
    const raw = values[field.key.replace('.', '_')] ?? ''
    const value = raw === '' && !field.required ? null : field.type === 'number' ? Number(raw) : raw
    const schema = field.choices ? z.enum(field.choices as [string, ...string[]]) : field.validate ?? z.string()
    const validation = (field.required ? schema : schema.nullable()).safeParse(value)
    if (!validation.success) { errors[field.key] = validation.error.issues[0]?.message ?? 'Enter a valid value.'; continue }
    if (record && Object.is(valueAt(record, field.key) ?? null, value)) continue
    if (field.key.startsWith('address.')) {
      const address = (body.address ??= {}) as Record<string, unknown>
      address[field.key.slice(8)] = value
    } else body[field.key] = value
  }
  if (record) body.expectedVersion = valueAt(record, 'version')
  return { body, errors }
}
