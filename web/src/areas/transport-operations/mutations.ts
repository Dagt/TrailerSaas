import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { TripDetail } from './contracts'
import { key, useOperations } from './queries'
export function useTripCommand<T>(command: (input: T) => Promise<TripDetail>) {
  const cache = useQueryClient(), { tenantId } = useOperations()
  return useMutation({ mutationFn: command, retry: false, onSuccess: async trip => {
    cache.setQueryData(key(tenantId, 'trip', trip.id), trip)
    await Promise.all(['timeline', 'dashboard', 'trips', 'orders'].map(part => cache.invalidateQueries({ queryKey: key(tenantId, part) })))
  } })
}
