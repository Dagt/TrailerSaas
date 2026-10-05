import { describe, expect, it } from 'vitest'
import { readWebEnvironment } from './environment'

describe('explicit environment configuration', () => {
  it('defaults to development with a loopback API, without future provider configuration', () => {
    expect(readWebEnvironment({})).toEqual({
      environment: 'development', apiBaseUrl: 'http://127.0.0.1:8080/api/v1',
    })
  })

  it.each(['development', 'staging', 'production'])(
    'accepts explicit %s without changing the selected API', (environment) => {
      expect(readWebEnvironment({
        VITE_APP_ENV: environment, VITE_API_BASE_URL: 'https://api.example.test/api/v1',
      })).toEqual({ environment, apiBaseUrl: 'https://api.example.test/api/v1' })
    },
  )

  it('an optimized Vite production mode cannot implicitly select Production resources', () => {
    const viteBuiltins = { MODE: 'production', PROD: true, VITE_APP_ENV: undefined }
    expect(readWebEnvironment(viteBuiltins)).toEqual({
      environment: 'development', apiBaseUrl: 'http://127.0.0.1:8080/api/v1',
    })
  })

  it.each(['', 'unknown', 'Production'])(
    'rejects an unknown environment rather than falling back: %s', (environment) => {
      expect(() => readWebEnvironment({ VITE_APP_ENV: environment })).toThrow(/VITE_APP_ENV/)
    },
  )

  it.each(['staging', 'production'])(
    '%s requires an explicit URL and never falls back to development', (environment) => {
      expect(() => readWebEnvironment({ VITE_APP_ENV: environment }))
        .toThrow(/VITE_API_BASE_URL is required/)
      expect(() => readWebEnvironment({ VITE_APP_ENV: environment, VITE_API_BASE_URL: '' })).toThrow()
      expect(() => readWebEnvironment({
        VITE_APP_ENV: environment, VITE_API_BASE_URL: 'http://api.example.test/api/v1',
      })).toThrow(/must use HTTPS/)
    },
  )

  it('allows an explicit development API without inferring the environment from its host', () => {
    expect(readWebEnvironment({ VITE_API_BASE_URL: 'http://localhost:8080/api/v1' }))
      .toEqual({ environment: 'development', apiBaseUrl: 'http://localhost:8080/api/v1' })
  })

  it.each([
    '', 'not-a-url', '/api/v1', 'ftp://example.test', 'javascript:alert(1)',
    'https://user:example@example.test/api/v1',
    'https://example.test/api/v1?token=example', 'https://example.test/api/v1#fragment',
  ])('rejects malformed or unsafe API URL: %s', (apiBaseUrl) => {
    expect(() => readWebEnvironment({ VITE_API_BASE_URL: apiBaseUrl })).toThrow()
  })
})
