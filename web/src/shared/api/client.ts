export interface Problem {
  status: number; code?: string; detail?: string; traceId?: string
  errors?: { field: string; message: string }[]
}

export class ApiProblem extends Error {
  readonly problem: Problem
  constructor(problem: Problem) { super('The API request could not be completed.'); this.name = 'ApiProblem'; this.problem = problem }
}

export interface ApiClient {
  request<T>(path: string, options?: { method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'; body?: unknown; signal?: AbortSignal }): Promise<T>
}

/** No response/request logging, token storage, mutation retries, or authorization decisions. */
export function createApiClient(baseUrl: string, getToken: () => Promise<string | null>,
  unauthorized: () => void = () => {}, transport: typeof fetch = globalThis.fetch): ApiClient {
  return {
    async request<T>(path: string, options: { method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'; body?: unknown; signal?: AbortSignal } = {}): Promise<T> {
      const active = () => { if (options.signal?.aborted) throw new DOMException('The request was cancelled.', 'AbortError') }
      active()
      let token: string | null
      try { token = await getToken() } catch {
        active(); unauthorized(); throw new ApiProblem({ status: 401, code: 'AUTHENTICATION_REQUIRED' })
      }
      active()
      if (!token) { unauthorized(); throw new ApiProblem({ status: 401, code: 'AUTHENTICATION_REQUIRED' }) }
      const headers: Record<string, string> = { Accept: 'application/json', Authorization: `Bearer ${token}` }
      if (typeof globalThis.crypto?.randomUUID === 'function') headers['X-Request-Id'] = crypto.randomUUID()
      if (options.body !== undefined) headers['Content-Type'] = 'application/json'
      let response: Response
      try {
        response = await transport(`${baseUrl.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`, {
          method: options.method ?? 'GET', headers, signal: options.signal,
          ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}), credentials: 'omit',
        })
      } catch (error) {
        if (options.signal?.aborted) throw error
        throw new ApiProblem({ status: 503 })
      }
      active()
      if (!response.ok) {
        let body: Partial<Problem> = {}
        if (response.headers.get('Content-Type')?.includes('application/problem+json')) {
          try { body = await response.json() as Partial<Problem> } catch { /* Generic safe error. */ }
        }
        active()
        if (response.status === 401) unauthorized()
        throw new ApiProblem({ status: response.status, code: body.code, detail: body.detail, traceId: body.traceId, errors: body.errors })
      }
      if (response.status === 204) return undefined as T
      const data = await response.json() as T
      active()
      return data
    },
  }
}
