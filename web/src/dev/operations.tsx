// Separate development entry. Synthetic React review only; no Firebase token or Backend.
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AuthContext, type AuthSession } from '../shared/auth/context'
import { appRoutes } from '../app/routes'
import { createOperationsReviewApi } from './operations-fixtures'
import '../styles.css'
import '../shared/ui/workspace.css'
import '../areas/tenant-admin/admin.css'
import '../areas/transport-operations/operations.css'
import './operations-review.css'

if (import.meta.env.DEV) {
  const params = new URLSearchParams(location.search)
  const cache = new QueryClient({ defaultOptions: { queries: { retry:false,refetchOnWindowFocus:false }, mutations: { retry:false } } })
  const session: AuthSession = { identity: { uid: 'isolated-operations-review' }, expired:false, api:createOperationsReviewApi(params.get('state') ?? ''),
    auth:{ configured:false,subscribe:()=>()=>undefined,token:async()=>null,signIn:async()=>{throw new Error('No Firebase sign-in in isolated review.')},signOut:async()=>undefined },
    signOut:async()=>{location.assign('/operations')},refresh:async()=>{cache.clear()},
  }
  const router = createMemoryRouter(appRoutes,{initialEntries:[params.get('route') ?? '/operations']})
  createRoot(document.getElementById('root')!).render(<>
    <aside className="ops-review-notice">ISOLATED DEMO · Synthetic read fixtures · No Firebase session or Backend authorization · All writes rejected</aside>
    <QueryClientProvider client={cache}><AuthContext.Provider value={session}><RouterProvider router={router}/></AuthContext.Provider></QueryClientProvider>
  </>)
}
