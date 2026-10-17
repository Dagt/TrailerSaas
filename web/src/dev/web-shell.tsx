// Isolated presentation review. No identity injection, auth provider, API calls or protected Outlet.
import { createRoot } from 'react-dom/client'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { WorkspaceHeader, WorkspaceNavigation } from '../shared/ui/WorkspaceShell'
import { AccessDeniedState, Button, EmptyState, ErrorBanner, LoadingState, PageHeader } from '../shared/ui/components'
import { ApiProblem } from '../shared/api/client'
import '../styles.css'
import '../shared/ui/workspace.css'

const examples = {
  admin: { title: 'Tenant Admin', sections: ['Overview', 'Customers', 'Locations', 'Drivers', 'Tractors', 'Trailers', 'Settings'] },
  operations: { title: 'Dispatch workspace', sections: ['Dashboard', 'Orders', 'Trips', 'Trailers'] },
  customer: { title: 'Customer Portal', sections: ['Dashboard', 'Orders', 'Trailers', 'Deliveries', 'Documents'] },
}
const params = new URLSearchParams(location.search), area = params.get('area')
const portal = area === 'operations' || area === 'customer' ? area : 'admin'
const example = examples[portal]
const links = example.sections.map((label, index) => ({ label, to: index ? `/${portal}/${label.toLowerCase()}` : `/${portal}`, end: index === 0 }))
export function Review() {
  const { pathname } = useLocation()
  const title = links.find(link => link.to === pathname)?.label ?? 'Overview'
  return <>
    <div className="shell-review-notice">DEMO DATA · Isolated component layout · No authenticated session or business data</div>
    <WorkspaceHeader title={example.title} identity="Demo layout reviewer" actions={<><Button disabled>Refresh access</Button><Button disabled>Sign out</Button></>}>
      <label>Tenant<select defaultValue="demo"><option value="demo">DEMO · Logistics workspace</option><option value="other">DEMO · Alternate workspace</option></select></label>
      {portal === 'customer' && <label>Customer<select defaultValue="demo"><option value="demo">DEMO · Assigned customer</option><option value="other">DEMO · Alternate customer</option></select></label>}
    </WorkspaceHeader>
    <div className="workspace-layout"><WorkspaceNavigation label={`${example.title} navigation`} context={portal === 'customer' ? 'DEMO · Assigned customer' : 'DEMO · Logistics workspace'} links={links} /><div className="workspace-content">
      <PageHeader title={title} description="Shell component review only. Actual feature screens remain in their authorized application routes." />
      {params.get('state') === 'loading' ? <LoadingState /> : params.get('state') === 'error' ? <ErrorBanner error={new ApiProblem({ status: 503, code: 'SERVICE_UNAVAILABLE' })} /> : params.get('state') === 'denied' ? <AccessDeniedState /> : <EmptyState><h2>No records in this layout review</h2><p>Connect to an authorized local environment to review actual workspace content.</p></EmptyState>}
    </div></div>
  </>
}
if (import.meta.env.DEV) createRoot(document.getElementById('root')!).render(<RouterProvider router={createMemoryRouter([{ path: '*', element: <Review /> }], { initialEntries: [`/${portal}`] })} />)
