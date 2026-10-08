import { expect, it, vi } from 'vitest'
import { ApiProblem, createApiClient } from './client'

it.each(['GET', 'POST', 'PATCH', 'DELETE'] as const)('injects bearer and request correlation for %s, without cookies or token logging', async method => {
  const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ value: 'ok' }), { status: 200 }))
  const consoleLog = vi.spyOn(console, 'log')
  const consoleError = vi.spyOn(console, 'error')
  const client = createApiClient('https://api.example.test/api/v1/', async () => 'synthetic-secret-bearer-token', () => {}, fetcher)
  const body = method === 'GET' ? undefined : { name: 'test' }
  expect(await client.request('/resource', { method, body })).toEqual({ value: 'ok' })
  const options = fetcher.mock.calls[0]![1]!
  const headers = new Headers(options.headers)
  expect(headers.get('Authorization')).toBe('Bearer synthetic-secret-bearer-token')
  expect(headers.get('Accept')).toBe('application/json')
  expect(headers.get('Content-Type')).toBe(body ? 'application/json' : null)
  expect(headers.get('X-Request-Id')).toMatch(/^[0-9a-f-]{36}$/)
  expect(options.credentials).toBe('omit')
  expect(fetcher.mock.calls[0]![0]).toBe('https://api.example.test/api/v1/resource')
  expect(consoleLog).not.toHaveBeenCalled(); expect(consoleError).not.toHaveBeenCalled()
})
it('handles 204 and parses only Problem Details fields, with a safe exception message', async () => {
  const transport = vi.fn<typeof fetch>().mockResolvedValueOnce(new Response(null, { status: 204 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ status: 409, code: 'STALE_VERSION', detail: 'safe detail', traceId: 'trace-1', errors: [{ field: 'name', message: 'must be valid' }], token: 'secret' }), { status: 409, headers: { 'Content-Type': 'application/problem+json' } }))
  const client = createApiClient('https://api.example.test', async () => 'token', () => {}, transport)
  expect(await client.request('/resource', { method: 'DELETE' })).toBeUndefined()
  const error = await client.request('/resource').catch(e => e) as ApiProblem
  expect(error).toBeInstanceOf(ApiProblem)
  expect(error.problem).toEqual({ status: 409, code: 'STALE_VERSION', detail: 'safe detail', traceId: 'trace-1', errors: [{ field: 'name', message: 'must be valid' }] })
  expect(error.message).not.toContain('secret')
  expect(transport).toHaveBeenCalledTimes(2)
})
it('never dispatches without an ID token and revalidates on 401 without replaying a mutation', async () => {
  const unauthorized = vi.fn()
  const transport = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 401 }))
  const absent = createApiClient('https://api.example.test', async () => null, unauthorized, transport)
  await expect(absent.request('/resource')).rejects.toMatchObject({ problem: { status: 401 } })
  expect(transport).not.toHaveBeenCalled()
  const client = createApiClient('https://api.example.test', async () => 'token', unauthorized, transport)
  await expect(client.request('/resource', { method: 'PATCH', body: { expectedVersion: 0 } })).rejects.toMatchObject({ problem: { status: 401 } })
  expect(unauthorized).toHaveBeenCalledTimes(2); expect(transport).toHaveBeenCalledTimes(1)
})
it('does not expose network exception content', async () => {
  const transport = vi.fn<typeof fetch>().mockRejectedValue(new Error('synthetic-secret-qr-token'))
  const client = createApiClient('https://api.example.test', async () => 'token', () => {}, transport)
  const error = await client.request('/resource').catch(e => e) as ApiProblem
  expect(error.problem).toEqual({ status: 503 })
  expect(String(error)).not.toContain('synthetic-secret-qr-token')
})

it('safely revalidates when Firebase token retrieval fails without sending a request', async () => {
  const unauthorized = vi.fn(); const transport = vi.fn<typeof fetch>()
  const client = createApiClient('https://api.example.test', async () => { throw new Error('synthetic-secret-firebase-token') }, unauthorized, transport)
  const error = await client.request('/resource').catch(e => e) as ApiProblem
  expect(error.problem).toEqual({ status: 401, code: 'AUTHENTICATION_REQUIRED' })
  expect(String(error)).not.toContain('synthetic-secret-firebase-token')
  expect(unauthorized).toHaveBeenCalledOnce(); expect(transport).not.toHaveBeenCalled()
})
