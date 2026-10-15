import { createContext, useContext } from 'react'
import type { CustomerContext } from './queries'

export const PortalContext = createContext<CustomerContext | null>(null)
export function useCustomerContext() {
  const value = useContext(PortalContext)
  if (!value) throw new Error('A validated Customer context is required.')
  return value
}
