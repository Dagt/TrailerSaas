import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as Sentry from '@sentry/react'
import { createElement } from 'react'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AppProviders } from '../../app/AppProviders'
import { appRoutes } from '../../app/routes'
import { readWebEnvironment } from '../config/environment'
import { initializeObservability, sanitizeSentryEvent } from './sentry'

vi.mock('@sentry/react', () => ({
  init: vi.fn(),
  globalHandlersIntegration: () => ({ name: 'GlobalHandlers' }),
  browserApiErrorsIntegration: () => ({ name: 'BrowserApiErrors' }),
  dedupeIntegration: () => ({ name: 'Dedupe' }),
  reactErrorHandler: () => vi.fn(),
}))

const dsn = 'https://example@sentry.example.test/1'

describe('optional, privacy-preserving Sentry', () => {
  beforeEach(() => vi.clearAllMocks())

  it.each([undefined, ''])('does not initialize without DSN: %s', async (value) => {
    expect(await initializeObservability(readWebEnvironment({ VITE_SENTRY_DSN: value }))).toBeUndefined()
    expect(Sentry.init).not.toHaveBeenCalled()
  })

  it('renders the existing application normally without Sentry or network calls', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
    try {
      expect(await initializeObservability(readWebEnvironment({}))).toBeUndefined()
      render(createElement(AppProviders, {
        children: createElement(RouterProvider, { router: createMemoryRouter(appRoutes) }),
      }))
      expect(await screen.findByRole('heading', { name: 'TrailerSaaS' })).toBeInTheDocument()
      expect(Sentry.init).not.toHaveBeenCalled()
      expect(fetch).not.toHaveBeenCalled()
    } finally {
      fetch.mockRestore()
    }
  })

  it.each(['development', 'staging', 'production'])('initializes %s explicitly', async (environment) => {
    expect(await initializeObservability(readWebEnvironment({
      VITE_APP_ENV: environment, VITE_API_BASE_URL: 'https://api.example.test/api/v1',
      VITE_SENTRY_DSN: dsn, VITE_SENTRY_ENVIRONMENT: environment,
    }))).toEqual({ onCaughtError: expect.any(Function), onUncaughtError: expect.any(Function) })
    expect(Sentry.init).toHaveBeenCalledWith(expect.objectContaining({
      dsn, environment, defaultIntegrations: false, maxBreadcrumbs: 0,
      dataCollection: expect.objectContaining({
        userInfo: false, cookies: false, httpHeaders: false, httpBodies: [], urlQueryParams: false,
      }),
    }))
    const options = vi.mocked(Sentry.init).mock.calls[0]![0]!
    const hint = { attachments: [{ filename: 'document.txt', data: 'synthetic-secret-token' }] }
    options.beforeSend!({ type: undefined, message: 'synthetic-secret-token' }, hint)
    expect(hint.attachments).toEqual([])
    expect(options.beforeBreadcrumb!({ message: 'synthetic-secret-token' })).toBeNull()
  })

  it('inherits development explicitly, not the SDK production default', async () => {
    await initializeObservability(readWebEnvironment({ VITE_SENTRY_DSN: dsn }))
    expect(Sentry.init).toHaveBeenCalledWith(expect.objectContaining({ environment: 'development' }))
  })

  it('rejects environment mismatch even with Sentry disabled', () => {
    expect(() => readWebEnvironment({ VITE_SENTRY_ENVIRONMENT: 'production' })).toThrow(/must match/)
    expect(() => readWebEnvironment({
      VITE_SENTRY_DSN: dsn, VITE_SENTRY_ENVIRONMENT: 'staging',
    })).toThrow(/must match/)
  })

  it.each([
    'invalid', 'http://example@sentry.example.test/1', 'https://sentry.example.test/1',
    'https://example:secret@sentry.example.test/1', 'https://example@sentry.example.test/name',
    dsn + '?signature=secret', dsn + '#secret',
  ])('rejects an invalid ingestion DSN: %s', (value) => {
    expect(() => readWebEnvironment({ VITE_SENTRY_DSN: value })).toThrow(/VITE_SENTRY_DSN/)
    expect(Sentry.init).not.toHaveBeenCalled()
  })

  it('retains code frames but excludes sensitive diagnostics and URL credentials', () => {
    const secret = 'synthetic-secret-token'
    const event: Sentry.ErrorEvent = {
      type: undefined, event_id: 'event', environment: 'development',
      message: secret, user: { email: secret }, extra: { document: secret },
      tags: { tenant: secret }, contexts: { custom: { location: secret } },
      breadcrumbs: [{ message: secret }], logentry: { message: secret },
      request: {
        url: 'https://user:password@api.example.test/path?signature=' + secret + '#secret',
        headers: { Authorization: 'Bearer ' + secret, Cookie: secret }, data: secret,
      },
      exception: { values: [{
        type: 'TypeError', value: secret, stacktrace: { frames: [{
          filename: 'https://web.example.test/assets/app.js?token=' + secret,
          function: 'render', lineno: 12, vars: { token: secret },
          context_line: secret, pre_context: [secret],
        }] },
      }] },
    }
    const sanitized = sanitizeSentryEvent(event)
    expect(JSON.stringify(sanitized)).not.toContain(secret)
    expect(sanitized.request).toEqual({ url: 'https://api.example.test/path' })
    expect(sanitized.exception?.values?.[0]?.stacktrace?.frames?.[0]).toEqual({
      filename: 'https://web.example.test/assets/app.js',
      function: 'render', lineno: 12, colno: undefined, in_app: undefined,
    })
    expect(sanitized.user).toBeUndefined()
    expect(sanitized.contexts).toBeUndefined()
  })
})
