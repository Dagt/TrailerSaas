import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import type { PropsWithChildren } from 'react'
import type { AuthBoundary } from '../shared/auth/boundary'
import { AuthProvider } from '../shared/auth/AuthProvider'
import { createFirebaseAuth } from '../shared/auth/firebase'
import { readWebEnvironment } from '../shared/config/environment'

const configuration = readWebEnvironment(import.meta.env)
const firebase = createFirebaseAuth(configuration.firebase)

export function AppProviders({ children, auth = firebase, client, baseUrl = configuration.apiBaseUrl }: PropsWithChildren<{
  auth?: AuthBoundary; client?: QueryClient; baseUrl?: string
}>) {
  const [queryClient] = useState(() => client ?? new QueryClient({ defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false },
  } }))

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider auth={auth} baseUrl={baseUrl}>{children}</AuthProvider>
    </QueryClientProvider>
  )
}
