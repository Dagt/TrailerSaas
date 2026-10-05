import { afterEach, expect, it, vi } from 'vitest'

afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
  vi.stubEnv('VITE_APP_ENV', 'staging')
})

it('initializes the placeholder from environment without making requests', async () => {
  vi.resetModules()
  vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test/api/v1')
  const fetchSpy = vi.spyOn(globalThis, 'fetch')
  const { apiClient } = await import('./client')

  expect(apiClient.baseUrl).toBe('https://api.example.test/api/v1')
  expect(fetchSpy).not.toHaveBeenCalled()
})
