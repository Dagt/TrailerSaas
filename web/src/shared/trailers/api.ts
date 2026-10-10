import type { ApiClient } from '../api/client'
import type { TrailerDetail, TrailerListItem, TrailerTimelinePage } from './contracts'
export function trailerReadApi(api: ApiClient, tenantId: string) {
  const base = `/tenants/${tenantId}/trailers`
  return {
    search: (number: string, signal?: AbortSignal) => api.request<{ items: TrailerListItem[] }>(`${base}?${new URLSearchParams({ operationalNumber: number })}`, { signal }),
    detail: (id: string, signal?: AbortSignal) => api.request<TrailerDetail>(`${base}/${id}`, { signal }),
    timeline: (id: string, cursor?: string, signal?: AbortSignal) => api.request<TrailerTimelinePage>(`${base}/${id}/timeline${cursor ? `?${new URLSearchParams({ cursor })}` : ''}`, { signal }),
  }
}
