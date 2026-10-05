import { z } from 'zod'

const environmentSchema = z.object({
  VITE_APP_ENV: z.enum(['development', 'staging', 'production']).default('development'),
  VITE_API_BASE_URL: z.url({ protocol: /^https?$/ }).optional(),
}).superRefine((value, context) => {
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

export function readWebEnvironment(raw: { VITE_APP_ENV?: unknown; VITE_API_BASE_URL?: unknown }) {
  const environment = environmentSchema.parse(raw)

  return {
    environment: environment.VITE_APP_ENV,
    apiBaseUrl: environment.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080/api/v1',
  }
}
