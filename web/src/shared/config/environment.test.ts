import { describe, expect, it } from 'vitest'
import { readWebEnvironment } from './environment'

describe('explicit environment configuration', () => {
  it('enables only complete public Firebase configuration and disables unconfigured sign-in', () => {
    expect(readWebEnvironment({}).firebase).toBeUndefined()
    expect(() => readWebEnvironment({ VITE_FIREBASE_API_KEY: 'only-one-field' })).toThrow(/All four/)
    expect(readWebEnvironment({ VITE_FIREBASE_API_KEY: 'public-key', VITE_FIREBASE_AUTH_DOMAIN: 'auth.example.test', VITE_FIREBASE_PROJECT_ID: 'test-project', VITE_FIREBASE_APP_ID: 'test-app' }).firebase)
      .toEqual({ apiKey: 'public-key', authDomain: 'auth.example.test', projectId: 'test-project', appId: 'test-app' })
  })
  it('public QR URLs are optional, HTTPS outside Development, and loopback-only for local HTTP', () => {
    expect(readWebEnvironment({}).qrPublicBaseUrl).toBeUndefined()
    expect(readWebEnvironment({ VITE_QR_PUBLIC_BASE_URL: 'http://localhost:5173/' }).qrPublicBaseUrl).toBe('http://localhost:5173')
    expect(readWebEnvironment({ VITE_APP_ENV: 'production', VITE_API_BASE_URL: 'https://api.example.test', VITE_QR_PUBLIC_BASE_URL: 'https://labels.example.test' }).qrPublicBaseUrl).toBe('https://labels.example.test')
    for (const url of ['http://remote.example.test', 'javascript:secret', 'https://user:secret@example.test', 'https://labels.example.test?token=secret']) {
      expect(() => readWebEnvironment({ VITE_QR_PUBLIC_BASE_URL: url })).toThrow(/VITE_QR_PUBLIC_BASE_URL/)
    }
    expect(() => readWebEnvironment({ VITE_APP_ENV: 'staging', VITE_API_BASE_URL: 'https://api.example.test', VITE_QR_PUBLIC_BASE_URL: 'http://localhost:5173' })).toThrow(/VITE_QR_PUBLIC_BASE_URL/)
  })
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
