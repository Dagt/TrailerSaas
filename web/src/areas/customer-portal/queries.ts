import { queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import type { ApiClient } from '../../shared/api/client'
import { ApiProblem } from '../../shared/api/client'
import type { CurrentUser, Membership } from '../tenant-admin/contracts'

const customerSchema = z.object({ id: z.uuid(), name: z.string().min(1), status: z.enum(['ACTIVE','INACTIVE']),
  version: z.number().int().nonnegative(), createdAt: z.iso.datetime({ offset: true }), updatedAt: z.iso.datetime({ offset: true }),
}).strict()
export type PortalCustomer = z.infer<typeof customerSchema>
export type CustomerOwner = { uid: string; userId: string; tenantId: string; membership: Membership; customerId: string }
export type CustomerContext = CustomerOwner & { customer: PortalCustomer }
export const customerRoot = (uid: string) => ['customer', uid] as const
export const customerKey = (owner: CustomerOwner, resource: string, filters: unknown = null) =>
  [...customerRoot(owner.uid), owner.userId, owner.tenantId, owner.customerId, resource, filters] as const
export function assignedCustomers(m: Membership) {
  return [...new Set(m.roles.filter(r => r.roleCode === 'CUSTOMER_DISPATCHER' && r.scopeType === 'CUSTOMER' && z.uuid().safeParse(r.customerId).success).map(r => r.customerId!))].sort()
}
export function portalMemberships(me: CurrentUser) {
  return me.memberships.filter(m => m.status === 'ACTIVE' && m.tenantActive === true && assignedCustomers(m).length > 0)
}
export function validateOwner(owner: CustomerOwner) {
  if (!z.uuid().safeParse(owner.userId).success || !z.uuid().safeParse(owner.tenantId).success || owner.tenantId !== owner.membership.tenantId || !owner.uid ||
    owner.membership.status !== 'ACTIVE' || owner.membership.tenantActive !== true || !assignedCustomers(owner.membership).includes(owner.customerId)) {
    throw new ApiProblem({ status: 403, code: 'CUSTOMER_SCOPE_DENIED' })
  }
}
async function readCustomer(api: ApiClient, owner: CustomerOwner, signal: AbortSignal) {
  validateOwner(owner) // Reduces accidental requests; never replaces server authorization.
  const row = customerSchema.parse(await api.request<unknown>(`/tenants/${owner.tenantId}/customers/${owner.customerId}`, { signal }))
  if (row.id !== owner.customerId) throw new ApiProblem({ status: 502, code: 'UNEXPECTED_RESPONSE' })
  return row
}
export function customerQuery(api: ApiClient, owner: CustomerOwner) {
  return queryOptions({ queryKey: customerKey(owner, 'context'), queryFn: ({ signal }) => readCustomer(api, owner, signal),
    retry: false, staleTime: 0, gcTime: 0,
  })
}
/** Explicit /me assignments only, even for dual-role Admins. Never downloads a Tenant directory. */
export async function customerChoices(api: ApiClient, owner: Omit<CustomerOwner,'customerId'>, signal: AbortSignal) {
  const ids = assignedCustomers(owner.membership), available: PortalCustomer[] = []
  let unavailable = 0
  for (let offset = 0; offset < ids.length; offset += 4) {
    const batch = await Promise.all(ids.slice(offset, offset + 4).map(async customerId => {
      try { return await readCustomer(api, { ...owner, customerId }, signal) }
      catch (error) {
        if (signal.aborted) throw error
        if (error instanceof ApiProblem && [403,404].includes(error.problem.status)) { unavailable++; return null }
        throw error
      }
    }))
    for (const row of batch) { if (row?.status === 'ACTIVE') available.push(row); else if (row) unavailable++ }
  }
  return { available, unavailable }
}
