import { describe, expect, it } from 'vitest'
import { auditDiff, canonical, missing } from './diff'
import { auditEventSchema, parseFilters, validated } from './contracts'
import { auditApi, firstPosition } from './api'
import { ApiProblem } from '../../../shared/api/client'

export const eventId = '10000000-0000-4000-8000-000000000061'
export const actorId = '10000000-0000-4000-8000-000000000062'
export const event = () => ({ id: eventId, actor: { type: 'HUMAN_USER', userId: actorId, membershipId: actorId }, sourceType: 'USER_ACTION', actionCode: 'CUSTOMER_UPDATED', entityType: 'CUSTOMER', entityId: actorId, beforeData: { name: 'Old Customer', status: 'ACTIVE', version: 2 }, afterData: { name: 'New Customer', status: 'INACTIVE', version: 3 }, reason: null, occurredAt: '2026-10-16T18:00:00Z' })
describe('closed Audit read contract', () => {
  it('validates a real human event without requiring current actor membership', () => expect(auditEventSchema.parse(event()).actor.userId).toBe(actorId))
  it.each(['SYSTEM', 'INTEGRATION'])('accepts %s without fabricated human IDs', type => {
    const e = { ...event(), actor: { type, userId: null, membershipId: null }, sourceType: type === 'SYSTEM' ? 'SYSTEM_PROCESS' : 'EXTERNAL_INTEGRATION' }
    expect(auditEventSchema.safeParse(e).success).toBe(true)
  })
  it.each([
    { id: 'malformed' }, { actionCode: 'UNKNOWN' }, { entityType: 'ORDER' }, { occurredAt: 'not-a-date' }, { reason: 42 },
    { rawToken: 'not-allowed' }, { afterData: { secret: 'not-allowed' } }, { afterData: { name: 3 } }, { afterData: { active: true } },
    { actor: { type: 'SYSTEM', userId: actorId, membershipId: null }, sourceType: 'SYSTEM_PROCESS' },
    { actor: { type: 'HUMAN_USER', userId: null, membershipId: null } }, { sourceType: 'EXTERNAL_INTEGRATION' },
  ])('rejects incompatible HTTP 200 data %#', patch => expect(auditEventSchema.safeParse({ ...event(), ...patch }).success).toBe(false))
  it('sanitizes parse failures instead of propagating sensitive values', () => {
    try { validated(auditEventSchema, { ...event(), afterData: { secret: 'synthetic-private-value' } }) } catch (error) {
      expect(error).toBeInstanceOf(ApiProblem); expect(String(error)).not.toContain('synthetic-private-value')
    }
  })
})
describe('deterministic snapshot comparison', () => {
  it('distinguishes absent, null, string null, deletion, boolean and creation', () => {
    const rows = auditDiff({ beforeData: { value: null, removed: true, string: 'null' }, afterData: { value: null, added: false, string: null } })
    expect(rows.map(r => [r.field, r.state])).toEqual([['added', 'Added'], ['removed', 'Removed'], ['string', 'Changed'], ['value', 'Unchanged']])
    expect(rows[0]?.before).toBe(missing); expect(canonical(missing)).not.toBe(canonical(null))
    expect(auditDiff({ beforeData: null, afterData: { name: 'New' } })[0]?.state).toBe('Added')
  })
  it('compares nested business addresses by field and preserves recorded role arrays', () => {
    const roles = [{ roleCode: 'CUSTOMER_DISPATCHER', scopeType: 'CUSTOMER', customerId: actorId }]
    const rows = auditDiff({ beforeData: { address: { line1: 'Old', line2: null }, roles: [] }, afterData: { roles, address: { line2: null, line1: 'New' } } })
    expect(rows.map(r => [r.field, r.state])).toEqual([['address.line1', 'Changed'], ['address.line2', 'Unchanged'], ['roles', 'Changed']])
    expect(canonical(rows[2]?.after)).toContain('CUSTOMER_DISPATCHER')
  })
  it('identifies sensitive edits even when presence flags remain unchanged', () => {
    const rows = auditDiff({ beforeData: { capabilityNotesPresent: true, redactedChanges: [] }, afterData: { capabilityNotesPresent: true, redactedChanges: ['capabilityNotes'] } })
    expect(rows.find(r => r.field === 'capabilityNotes')?.state).toBe('Withheld')
    expect(rows.some(r => r.field === 'redactedChanges')).toBe(false)
  })
  it('object key order never creates a false difference', () => expect(auditDiff({ beforeData: { address: { a: 1, b: 2 } }, afterData: { address: { b: 2, a: 1 } } }).every(r => r.state === 'Unchanged')).toBe(true))
})
describe('filters and real cursor pages', () => {
  it('preserves exact inclusive offsets rather than rounding to local minutes', () => {
    const from = '2026-10-16T13:00:00.123-05:00', to = '2026-10-16T18:00:00.123Z'
    expect(parseFilters(new URLSearchParams({ from, to }))).toEqual({ from, to })
  })
  it.each(['actorUserId=invalid', 'entityId=invalid', 'from=2026-10-16T18:00:00', 'from=2026-10-17T00:00:00Z&to=2026-10-16T00:00:00Z', 'actionCode=FAKE', 'entityType=ORDER', 'actorUserId=x&actorUserId=y', 'cursor=fabricated', 'from=%2B10000-01-01T00:00:00Z'])('rejects invalid URL filter %s', value => expect(() => parseFilters(new URLSearchParams(value))).toThrow())
  const owner = { firebaseUid: 'firebase', userId: actorId, tenantId: actorId }, signal = new AbortController().signal
  it('sends only the actual read contract and validates the detail identity', async () => {
    let path = ''
    const api = auditApi({ request: async <T,>(p: string) => { path = p; return event() as T } }, owner)
    await api.detail(eventId, signal); expect(path).toBe(`/tenants/${actorId}/audit-events/${eventId}`)
    await expect(api.detail(actorId, signal)).rejects.toBeInstanceOf(ApiProblem)
  })
  it.each([
    { items: [event(), event()], nextCursor: null }, { items: [], nextCursor: 'cursor' },
    { items: [event()], nextCursor: 'bad cursor!' }, { items: [event()], nextCursor: 'seen' },
  ])('rejects duplicate items, empty continuation or invalid/repeated cursors %#', async page => {
    const api = auditApi({ request: async <T,>() => page as T }, owner)
    await expect(api.list({}, { ...firstPosition, cursors: ['seen'] }, signal)).rejects.toBeInstanceOf(ApiProblem)
  })
  it('rejects an item repeated across pages', async () => {
    const api = auditApi({ request: async <T,>() => ({ items: [event()], nextCursor: null }) as T }, owner)
    await expect(api.list({}, { cursor: 'next', ids: [eventId], cursors: ['next'] }, signal)).rejects.toBeInstanceOf(ApiProblem)
  })
})
