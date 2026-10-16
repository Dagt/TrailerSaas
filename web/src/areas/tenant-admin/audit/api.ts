import type { ApiClient } from '../../../shared/api/client'
import { ApiProblem } from '../../../shared/api/client'
import { auditEventSchema, auditPageSchema, validated, type AuditFilters } from './contracts'

export type AuditOwner = { firebaseUid: string; userId: string; tenantId: string }
export const auditRoot = (o: AuditOwner) => ['audit', o.firebaseUid, o.userId, o.tenantId] as const
export type AuditPosition = { cursor: string | null; ids: string[]; cursors: string[] }
export const firstPosition: AuditPosition = { cursor: null, ids: [], cursors: [] }
export function auditApi(client: ApiClient, owner: AuditOwner) {
  const root = `/tenants/${owner.tenantId}/audit-events`
  return {
    async list(filters: AuditFilters, position: AuditPosition, signal: AbortSignal) {
      const query = new URLSearchParams({ ...filters, limit: '25', ...(position.cursor ? { cursor: position.cursor } : {}) })
      const page = validated(auditPageSchema, await client.request<unknown>(`${root}?${query}`, { signal }))
      const ids = page.items.map(e => e.id)
      if (new Set(ids).size !== ids.length || ids.some(id => position.ids.includes(id)) || page.nextCursor !== null &&
        (page.items.length === 0 || page.nextCursor === position.cursor || position.cursors.includes(page.nextCursor)))
        throw new ApiProblem({ status: 502, code: 'UNEXPECTED_RESPONSE' })
      return page
    },
    async detail(id: string, signal: AbortSignal) {
      const event = validated(auditEventSchema, await client.request<unknown>(`${root}/${id}`, { signal }))
      if (event.id !== id) throw new ApiProblem({ status: 502, code: 'UNEXPECTED_RESPONSE' })
      return event
    },
  }
}
