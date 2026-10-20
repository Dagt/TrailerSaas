// Separate development entry. Actual React routes, synthetic reads, rejected writes.
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AuthContext, type AuthSession } from '../shared/auth/context'
import { appRoutes } from '../app/routes'
import { createCustomerReviewApi } from './customer-fixtures'
import '../styles.css'
import '../shared/ui/workspace.css'
import '../areas/tenant-admin/admin.css'
import '../areas/transport-operations/operations.css'
import '../areas/customer-portal/customer.css'

if(import.meta.env.DEV){
  const params=new URLSearchParams(location.search),cache=new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false},mutations:{retry:false}}})
  const session:AuthSession={identity:{uid:'isolated-customer-review'},expired:false,api:createCustomerReviewApi(params.get('state')??''),auth:{configured:false,subscribe:()=>()=>undefined,token:async()=>null,signIn:async()=>{throw Error('No Firebase session in isolated DEMO')},signOut:async()=>undefined},signOut:async()=>{location.assign('/customer')},refresh:async()=>{cache.clear()}}
  const router=createMemoryRouter(appRoutes,{initialEntries:[params.get('route')??'/customer']})
  createRoot(document.getElementById('root')!).render(<><aside className="shell-review-notice">ISOLATED DEMO · Synthetic Customer read fixtures · No Firebase session or Backend authorization · All writes rejected</aside><QueryClientProvider client={cache}><AuthContext.Provider value={session}><RouterProvider router={router}/></AuthContext.Provider></QueryClientProvider></>)
}
