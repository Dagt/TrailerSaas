import { expect, it } from 'vitest'
import { operationalNumber, requestValues, formValues, timezone } from './resources'
import { sample } from '../../test/adminFixture'

it.each([' t114 ', 'bh  t114', 'T-114', 'T0114', 'äéß', '\u00a0T114\u00a0'])('preserves display number exactly: %s', value => {
  expect(operationalNumber.parse(value)).toBe(value)
  expect(requestValues('tractors', { operationalNumber: value }).body.operationalNumber).toBe(value)
})
it.each(['', '   ', 'T\t114', 'T\n114', 'T\r114', 'T\u0001', 'T\u007f', 'T\u0085'])('rejects controls or empty ASCII-space number: %s', value => {
  expect(operationalNumber.safeParse(value).success).toBe(false)
})
it('projects only Trailer metadata into PATCH even when extra values include designation and state', () => {
  const record = sample('trailers')
  const result = requestValues('trailers', { ...formValues('trailers', record), designatedCustomerId: 'foreign', locationState: 'AT_LOCATION', active: 'false', usagePurpose: 'DELIVERY' }, record)
  expect(result.body).toEqual({ expectedVersion: 0, usagePurpose: 'DELIVERY' })
})
it('clears nullable Driver metadata without adding account IDs', () => {
  const record = { ...sample('drivers'), licenseNumber: 'old', medicalCertificateExpires: '2000-01-01' }
  const result = requestValues('drivers', { ...formValues('drivers', record), licenseNumber: '', medicalCertificateExpires: '', userId: 'unexpected' }, record)
  expect(result.body).toEqual({ expectedVersion: 0, licenseNumber: null, medicalCertificateExpires: null })
})
it.each(['America/Chicago', 'UTC', 'Europe/Madrid'])('accepts IANA timezone %s', zone => expect(timezone.parse(zone)).toBe(zone))
it.each(['', '+02:00', 'Unknown/Zone'])('rejects unsupported timezone %s', zone => expect(timezone.safeParse(zone).success).toBe(false))
it('validates Site coordinate/radius/country values independently and permits past certificate dates', () => {
  expect(requestValues('sites', { ...formValues('sites'), name: 'Site', latitude: '40', longitude: '' }).errors).toEqual({})
  expect(requestValues('sites', { ...formValues('sites'), name: 'Site', latitude: '91', geofenceRadiusM: '0', address_countryCode: 'USA' }).errors).toHaveProperty('latitude')
  expect(requestValues('drivers', { name: 'Driver', medicalCertificateExpires: '2000-01-01', licenseNumber: '' }).errors).toEqual({})
  expect(requestValues('drivers', { name: 'Driver', medicalCertificateExpires: '2026-02-30' }).errors).toHaveProperty('medicalCertificateExpires')
})
it('requires positive integral Settings threshold and permits timezone clearing', () => {
  for (const threshold of ['0', '-1', '1.5', '']) expect(requestValues('settings', { staleTrailerThresholdHours: threshold, defaultTimezone: '' }).errors).toHaveProperty('staleTrailerThresholdHours')
  expect(requestValues('settings', { staleTrailerThresholdHours: '72', defaultTimezone: '' }).body).toEqual({ staleTrailerThresholdHours: 72, defaultTimezone: null })
})
