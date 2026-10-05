import type { ErrorEvent } from '@sentry/react'
import type { RootOptions } from 'react-dom/client'
import type { readWebEnvironment } from '../config/environment'

function safeUrl(value: string | undefined): string | undefined {
  if (!value) return undefined
  try {
    const url = new URL(value, 'https://relative.example.invalid')
    if (!['https:', 'http:'].includes(url.protocol)) return undefined
    return (/^(https?:)?\/\//.test(value) ? url.origin : '') + url.pathname
  } catch {
    return undefined
  }
}

// Allowlist code diagnostics rather than attempting to redact arbitrary exception text.
export function sanitizeSentryEvent(event: ErrorEvent): ErrorEvent {
  return {
    type: undefined,
    event_id: event.event_id,
    timestamp: event.timestamp,
    platform: event.platform,
    release: event.release,
    dist: event.dist,
    environment: event.environment,
    level: event.level,
    exception: event.exception ? {
      values: event.exception.values?.map((exception) => ({
        type: ['Error', 'TypeError', 'RangeError', 'ReferenceError', 'SyntaxError', 'URIError', 'EvalError']
          .includes(exception.type ?? '') ? exception.type : 'Error',
        value: 'Client exception (message omitted for privacy)',
        stacktrace: exception.stacktrace ? {
          frames: exception.stacktrace.frames?.map((frame) => ({
            filename: safeUrl(frame.filename),
            function: frame.function,
            lineno: frame.lineno,
            colno: frame.colno,
            in_app: frame.in_app,
          })),
        } : undefined,
      })),
    } : undefined,
    request: event.request?.url ? { url: safeUrl(event.request.url) } : undefined,
  }
}

export async function initializeObservability(
  configuration: ReturnType<typeof readWebEnvironment>,
): Promise<RootOptions | undefined> {
  if (!configuration.sentry) return undefined
  const Sentry = await import('@sentry/react')
  Sentry.init({
    dsn: configuration.sentry.dsn,
    environment: configuration.sentry.environment,
    dataCollection: {
      userInfo: false, cookies: false, httpHeaders: false, httpBodies: [],
      urlQueryParams: false, stackFrameVariables: false, frameContextLines: 0,
      databaseQueryData: false, queues: false,
      graphQL: { document: false, variables: false },
      genAI: { inputs: false, outputs: false },
    },
    defaultIntegrations: false,
    integrations: [Sentry.globalHandlersIntegration(), Sentry.browserApiErrorsIntegration(), Sentry.dedupeIntegration()],
    maxBreadcrumbs: 0,
    beforeBreadcrumb: () => null,
    beforeSend: (event, hint) => {
      hint.attachments = []
      return sanitizeSentryEvent(event)
    },
    tracesSampleRate: 0,
    beforeSendLog: () => null,
    beforeSendMetric: () => null,
    sendClientReports: false,
  })
  return { onUncaughtError: Sentry.reactErrorHandler(), onCaughtError: Sentry.reactErrorHandler() }
}
