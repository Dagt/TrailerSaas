import { describe, expect, it } from 'vitest'
import { readWebEnvironment } from './environment'

describe('API environment configuration', () => {
  it('defaults to the accepted same-origin API prefix', () => {
    expect(readWebEnvironment({})).toEqual({ apiBaseUrl: '/api/v1' })
  })

  it.each([
    'http://127.0.0.1:8080/api/v1',
    'https://api.example.test/api/v1',
    '/api/v1',
  ])('accepts %s', (apiBaseUrl) => {
    expect(readWebEnvironment({ VITE_API_BASE_URL: apiBaseUrl })).toEqual({
      apiBaseUrl,
    })
  })

  it.each(['', 'not-a-url', 'ftp://example.test', 'javascript:alert(1)'])(
    'rejects invalid configuration %s',
    (apiBaseUrl) => {
      expect(() =>
        readWebEnvironment({ VITE_API_BASE_URL: apiBaseUrl }),
      ).toThrow()
    },
  )
})
