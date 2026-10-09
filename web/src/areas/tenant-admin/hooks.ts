import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../shared/auth/context'
import { useAdminTenant } from './tenant'
import type { Items, Customer } from './contracts'

export function useTenantQuery<T>(path: string, enabled = true) {
  const { api } = useAuth()
  const { tenantId } = useAdminTenant()
  return useQuery({ queryKey: ['tenant', tenantId, path], enabled,
    queryFn: ({ signal }) => api.request<T>(`/tenants/${tenantId}${path}`, { signal }) })
}
export function useCustomers(enabled = true) { return useTenantQuery<Items<Customer>>('/customers', enabled) }
