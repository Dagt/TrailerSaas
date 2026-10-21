import type { AuditEvent } from './contracts'
import { displayTime } from '../../../shared/ui/dateTime'

export const missing = Symbol('missing')
export type DiffRow = { field: string; before: unknown; after: unknown; state: 'Added' | 'Removed' | 'Changed' | 'Unchanged' | 'Withheld' }
const object = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value)
export function canonical(value: unknown): string {
  if (value === missing) return 'Not recorded (field absent)'
  if (value === null) return 'null'
  if (object(value)) return `{${Object.keys(value).sort().map(k => `${JSON.stringify(k)}:${canonical(value[k])}`).join(',')}}`
  if (Array.isArray(value)) return `[${value.map(canonical).join(', ')}]`
  return JSON.stringify(value)
}
export function auditDiff(event: Pick<AuditEvent, 'beforeData' | 'afterData'>): DiffRow[] {
  const rows: DiffRow[] = []
  const visit = (field: string, before: unknown, after: unknown) => {
    if ((object(before) || before === missing) && (object(after) || after === missing) && (object(before) || object(after))) {
      const keys = [...new Set([...Object.keys(object(before) ? before : {}), ...Object.keys(object(after) ? after : {})])].sort()
      if (keys.length) { keys.forEach(key => visit(field ? `${field}.${key}` : key, object(before) && Object.hasOwn(before, key) ? before[key] : missing, object(after) && Object.hasOwn(after, key) ? after[key] : missing)); return }
    }
    rows.push({ field, before, after, state: before === missing ? 'Added' : after === missing ? 'Removed' : canonical(before) === canonical(after) ? 'Unchanged' : 'Changed' })
  }
  const before = { ...event.beforeData }, after = { ...event.afterData }
  const withheld = new Set([...(Array.isArray(before.redactedChanges) ? before.redactedChanges : []), ...(Array.isArray(after.redactedChanges) ? after.redactedChanges : [])] as string[])
  delete before.redactedChanges; delete after.redactedChanges
  visit('', before, after)
  withheld.forEach(field => rows.push({ field, before: missing, after: missing, state: 'Withheld' }))
  return rows.filter(r => r.field).sort((a, b) => a.field < b.field ? -1 : a.field > b.field ? 1 : 0)
}
export const label = (value: string) => value.replace(/([a-z])([A-Z])/g, '$1 $2').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
export const localTime = displayTime
