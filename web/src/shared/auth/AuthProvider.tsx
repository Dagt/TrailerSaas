import { useEffect, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { AuthBoundary, SessionIdentity } from './boundary'
import { createApiClient } from '../api/client'
import { AuthContext } from './context'
import type { AuthSession } from './context'
export function AuthProvider({ auth, baseUrl, children }: PropsWithChildren<{ auth: AuthBoundary; baseUrl: string }>) {
  const cache = useQueryClient()
  const [identity, setIdentity] = useState<SessionIdentity | null>()
  const [expired, setExpired] = useState(false)
  useEffect(() => auth.subscribe(next => {
    void cache.cancelQueries(); cache.clear()
    setIdentity(next); setExpired(false)
  }), [auth, cache])
  const api = useMemo(() => createApiClient(baseUrl, () => auth.token(), () => {
    void cache.cancelQueries(); cache.clear(); setExpired(true)
  }), [auth, baseUrl, cache])
  const value: AuthSession = { identity, expired, auth, api,
    async signOut() {
      setIdentity(null); setExpired(false)
      await cache.cancelQueries(); cache.clear()
      try { await auth.signOut() } catch { setExpired(true) }
    },
    async refresh() { await auth.token(true); await cache.cancelQueries(); cache.clear(); setExpired(false) },
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
