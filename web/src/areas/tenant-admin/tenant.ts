import { createContext, useContext } from 'react'
import type { Membership } from './contracts'
export const AdminTenant = createContext<Membership | null>(null)
export const AdminIdentity = createContext<string | null>(null)
export function useAdminIdentity() {
  const user = useContext(AdminIdentity)
  if (!user) throw new Error('An authenticated internal User is required.')
  return user
}
export function useAdminTenant() {
  const tenant = useContext(AdminTenant)
  if (!tenant) throw new Error('An active Admin Tenant is required.')
  return tenant
}
