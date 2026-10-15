import { createContext, useContext } from 'react'
import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { useAuth } from '../../shared/auth/context'
import { operationsApi, type Filters } from './operationsApi'
import type { Customer, Driver, Location, Site, Tractor, Trailer } from './contracts'
export const OperationsTenant = createContext<{ tenantId: string; tenantName: string; capabilities?: string[] } | null>(null)
export function useOperations() {
  const tenant = useContext(OperationsTenant)
  const { api } = useAuth()
  if (!tenant) throw new Error('Operations Tenant is required.')
  return { ...tenant, api: operationsApi(api, tenant.tenantId) }
}
export const key = (tenant: string, ...parts: unknown[]) => ['operations', tenant, ...parts]
export function useMaster<T>(path: string, enabled = true) {
  const { api, tenantId } = useOperations()
  return useQuery({ queryKey: key(tenantId, 'master', path), queryFn: ({ signal }) => api.master<T>(path, signal), enabled, staleTime: 60_000 })
}
export function useNames() {
  const customers = useMaster<Customer>('/customers'), drivers = useMaster<Driver>('/drivers'), tractors = useMaster<Tractor>('/tractors'), trailers = useMaster<Trailer>('/trailers'), sites = useMaster<Site>('/sites')
  return { customers, drivers, tractors, trailers, sites,
    customer: (id: string) => customers.data?.items.find(x => x.id === id)?.name ?? 'Customer unavailable',
    driver: (id: string | null) => id ? drivers.data?.items.find(x => x.id === id)?.name ?? 'Driver unavailable' : 'Not assigned',
    tractor: (id: string | null) => id ? tractors.data?.items.find(x => x.id === id)?.operationalNumber ?? 'Tractor unavailable' : 'Not assigned',
    trailer: (id: string | null) => id ? trailers.data?.items.find(x => x.id === id)?.operationalNumber ?? 'Trailer unavailable' : 'Not planned',
    site: (id: string | null, snapshotName?: string | null) => id ? sites.data?.items.find(x => x.id === id)?.name ?? snapshotName ?? 'Site unavailable' : snapshotName ?? 'Destination not specified',
  }
}
export function useLocations(siteId: string | null) { return useMaster<Location>(`/sites/${siteId}/locations`, !!siteId) }
export function useOrderPages(filters: Filters) {
  const { api, tenantId } = useOperations()
  return useInfiniteQuery({ queryKey: key(tenantId, 'orders', filters), initialPageParam: undefined as string | undefined,
    queryFn: ({ signal, pageParam }) => api.orders({ ...filters, cursor: pageParam }, signal), getNextPageParam: page => page.nextCursor ?? undefined })
}
export function useTripPages(filters: Filters) {
  const { api, tenantId } = useOperations()
  return useInfiniteQuery({ queryKey: key(tenantId, 'trips', filters), initialPageParam: undefined as string | undefined,
    queryFn: ({ signal, pageParam }) => api.trips({ ...filters, cursor: pageParam }, signal), getNextPageParam: page => page.nextCursor ?? undefined })
}
