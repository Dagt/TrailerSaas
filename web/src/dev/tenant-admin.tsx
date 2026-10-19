// Separate development entry: actual route components with controlled fixtures.
// No AuthProvider, Firebase initialization, tokens, global fetch replacement or Backend calls.
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AuthContext } from '../shared/auth/context'
import type { AuthSession } from '../shared/auth/context'
import { appRoutes } from '../app/routes'
import { createAdminReviewApi } from './tenant-admin-fixtures'
import '../styles.css'
import '../shared/ui/workspace.css'
import '../areas/tenant-admin/admin.css'
import './tenant-admin-review.css'

if (import.meta.env.DEV) {
  const params = new URLSearchParams(location.search)
  const route = params.get('route') ?? '/admin'
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } })
  const session: AuthSession = { identity: { uid: 'isolated-admin-layout-review' }, expired: false, api: createAdminReviewApi(params.get('state') ?? ''),
    auth: { configured: false, subscribe: () => () => undefined, token: async () => null, signIn: async () => { throw new Error('No sign-in in isolated review.') }, signOut: async () => undefined },
    signOut: async () => { location.assign('/dev/tenant-admin.html?route=/') }, refresh: async () => { cache.clear() },
  }
  const router = createMemoryRouter(appRoutes, { initialEntries: [route] })
  createRoot(document.getElementById('root')!).render(<>
    <aside className="admin-review-notice">DEMO DATA · Isolated Tenant Admin UI fixture · No Firebase session or Backend authorization · Writes are rejected</aside>
    <QueryClientProvider client={cache}><AuthContext.Provider value={session}><RouterProvider router={router} /></AuthContext.Provider></QueryClientProvider>
  </>)
}
