import { z } from 'zod'

const environmentSchema = z.object({
  VITE_APP_ENV: z.enum(['development', 'staging', 'production']).default('development'),
  VITE_API_BASE_URL: z.url({ protocol: /^https?$/ }).optional(),
  VITE_SENTRY_DSN: z.string().optional(),
  VITE_SENTRY_ENVIRONMENT: z.enum(['development', 'staging', 'production']).optional(),
  VITE_FIREBASE_API_KEY: z.string().optional(),
  VITE_FIREBASE_AUTH_DOMAIN: z.string().optional(),
  VITE_FIREBASE_PROJECT_ID: z.string().optional(),
  VITE_FIREBASE_APP_ID: z.string().optional(),
  VITE_QR_PUBLIC_BASE_URL: z.string().optional(),
}).superRefine((value, context) => {
  const firebaseKeys = ['VITE_FIREBASE_API_KEY', 'VITE_FIREBASE_AUTH_DOMAIN', 'VITE_FIREBASE_PROJECT_ID', 'VITE_FIREBASE_APP_ID'] as const
  const configuredKeys = firebaseKeys.filter(key => value[key]?.trim())
  if (configuredKeys.length !== 0 && configuredKeys.length !== firebaseKeys.length) {
    context.addIssue({ code: 'custom', path: ['VITE_FIREBASE_API_KEY'], message: 'All four VITE_FIREBASE_* identity configuration values are required to enable sign-in.' })
  }
  if (value.VITE_QR_PUBLIC_BASE_URL) {
    let url: URL | undefined
    try { url = new URL(value.VITE_QR_PUBLIC_BASE_URL) } catch { /* No value in diagnostics. */ }
    if (!url || url.username || url.password || url.search || url.hash ||
      !(url.protocol === 'https:' || (value.VITE_APP_ENV === 'development' && url.protocol === 'http:' &&
        ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)))) {
      context.addIssue({ code: 'custom', path: ['VITE_QR_PUBLIC_BASE_URL'], message: 'VITE_QR_PUBLIC_BASE_URL must be absolute HTTPS; only Development loopback may use HTTP.' })
    }
  }
  if (value.VITE_SENTRY_ENVIRONMENT !== undefined &&
    value.VITE_SENTRY_ENVIRONMENT !== value.VITE_APP_ENV) {
    context.addIssue({
      code: 'custom', path: ['VITE_SENTRY_ENVIRONMENT'],
      message: 'VITE_SENTRY_ENVIRONMENT must match VITE_APP_ENV.',
    })
  }
  if (value.VITE_SENTRY_DSN) {
    let dsn: URL | undefined
    try { dsn = new URL(value.VITE_SENTRY_DSN) } catch { /* Report only the variable, not its value. */ }
    if (!dsn || dsn.protocol !== 'https:' || !/^[a-zA-Z0-9]+$/.test(dsn.username) ||
      dsn.password || dsn.search || dsn.hash || !/\/[0-9]+$/.test(dsn.pathname)) {
      context.addIssue({
        code: 'custom', path: ['VITE_SENTRY_DSN'],
        message: 'VITE_SENTRY_DSN must be an HTTPS ingestion DSN with a public key and project ID.',
      })
    }
  }
  if (value.VITE_APP_ENV !== 'development' && value.VITE_API_BASE_URL === undefined) {
    context.addIssue({
      code: 'custom', path: ['VITE_API_BASE_URL'],
      message: 'VITE_API_BASE_URL is required for staging and production.',
    })
  }
  if (value.VITE_API_BASE_URL !== undefined) {
    let url: URL
    try {
      url = new URL(value.VITE_API_BASE_URL)
    } catch {
      return // The URL schema already reports malformed input.
    }
    if (url.username || url.password || url.search || url.hash) {
      context.addIssue({
        code: 'custom', path: ['VITE_API_BASE_URL'],
        message: 'VITE_API_BASE_URL must not contain credentials, query strings or fragments.',
      })
    }
    if (value.VITE_APP_ENV !== 'development' && url.protocol !== 'https:') {
      context.addIssue({
        code: 'custom', path: ['VITE_API_BASE_URL'],
        message: 'VITE_API_BASE_URL must use HTTPS for staging and production.',
      })
    }
  }
})

export function readWebEnvironment(raw: {
  VITE_APP_ENV?: unknown; VITE_API_BASE_URL?: unknown;
  VITE_SENTRY_DSN?: unknown; VITE_SENTRY_ENVIRONMENT?: unknown;
  VITE_FIREBASE_API_KEY?: unknown; VITE_FIREBASE_AUTH_DOMAIN?: unknown;
  VITE_FIREBASE_PROJECT_ID?: unknown; VITE_FIREBASE_APP_ID?: unknown;
  VITE_QR_PUBLIC_BASE_URL?: unknown;
}) {
  const environment = environmentSchema.parse(raw)

  return {
    environment: environment.VITE_APP_ENV,
    apiBaseUrl: environment.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080/api/v1',
    ...(environment.VITE_FIREBASE_API_KEY?.trim() ? {
      firebase: { apiKey: environment.VITE_FIREBASE_API_KEY, authDomain: environment.VITE_FIREBASE_AUTH_DOMAIN!,
        projectId: environment.VITE_FIREBASE_PROJECT_ID!, appId: environment.VITE_FIREBASE_APP_ID! },
    } : {}),
    ...(environment.VITE_QR_PUBLIC_BASE_URL ? { qrPublicBaseUrl: environment.VITE_QR_PUBLIC_BASE_URL.replace(/\/+$/, '') } : {}),
    ...(environment.VITE_SENTRY_DSN ? {
      sentry: { dsn: environment.VITE_SENTRY_DSN, environment: environment.VITE_APP_ENV },
    } : {}),
  }
}
