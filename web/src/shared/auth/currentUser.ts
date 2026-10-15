import { useQuery } from '@tanstack/react-query'
import { z } from 'zod'
import { useAuth } from './context'

const membership = z.object({
  tenantId: z.string().min(1), tenantName: z.string(), tenantActive: z.boolean().optional(),
  status: z.enum(['ACTIVE', 'SUSPENDED', 'DISABLED']), capabilities: z.array(z.string()).optional(),
  roles: z.array(z.object({ roleCode: z.string(), scopeType: z.enum(['TENANT', 'CUSTOMER']), customerId: z.string().nullable() })),
})
const currentUser = z.object({ userId: z.string().min(1), email: z.string().nullable(), displayName: z.string().nullable(), memberships: z.array(membership) })

// Ephemeral read sequence defeats structural-sharing reuse after an unchanged /me refresh.
// It is neither a server identity nor an authorization token.
let clientReadSequence = 0

/** Navigation hints only. Resource APIs re-authorize the principal on every request. */
export function useCurrentUser(refetchInterval: number | false = false) {
  const { api, identity, expired } = useAuth()
  return useQuery({ queryKey: ['me', identity?.uid], enabled: !!identity && !expired,
    queryFn: async ({ signal }) => ({ ...currentUser.parse(await api.request<unknown>('/me', { signal })), clientReadSequence: ++clientReadSequence }),
    retry: false, staleTime: 0, refetchInterval: query => query.state.status === 'error' ? false : refetchInterval, refetchIntervalInBackground: false, refetchOnWindowFocus: true, refetchOnReconnect: true,
  })
}
