import { createContext, useContext } from 'react'
import type { AuthBoundary, SessionIdentity } from './boundary'
import type { ApiClient } from '../api/client'

export interface AuthSession {
  identity: SessionIdentity | null | undefined; expired: boolean; auth: AuthBoundary; api: ApiClient
  signOut(): Promise<void>; refresh(): Promise<void>
}
export const AuthContext = createContext<AuthSession | null>(null)
export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('Authentication provider is required.')
  return value
}
