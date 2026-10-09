import { createContext, useContext } from 'react'
import type { Membership } from './contracts'
export const AdminTenant = createContext<Membership | null>(null)
export function useAdminTenant() {
  const tenant = useContext(AdminTenant)
  if (!tenant) throw new Error('An active Admin Tenant is required.')
  return tenant
}
