import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../shared/auth/context'
import { useCurrentUser } from '../shared/auth/currentUser'
import { portalMemberships } from '../areas/customer-portal/queries'

export function AppShell() {
  const { identity, expired } = useAuth()
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <header className="app-header">
        <NavLink className="app-name" to="/">
          TrailerSaaS
        </NavLink>
        <nav aria-label="Application areas">
          {identity && !expired ? <AuthenticatedAreas /> : <PublicAreas />}
        </nav>
      </header>
      <main id="main-content" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}

// Mount identity queries only for a signed-in account: logout must leave no disabled-query cache row.
function AuthenticatedAreas() {
  const me = useCurrentUser()
  const active = me.isError || me.isFetching ? [] : me.data?.memberships.filter(m => m.status === 'ACTIVE') ?? []
  return <>
    {active.some(m => m.roles.some(r => r.roleCode === 'TENANT_ADMIN' && r.scopeType === 'TENANT')) && <NavLink to="/admin">Tenant Admin</NavLink>}
    {active.some(m => m.roles.some(r => ['TENANT_ADMIN','TRANSPORT_OPERATIONS'].includes(r.roleCode) && r.scopeType === 'TENANT')) && <NavLink to="/operations">Transport Operations</NavLink>}
    {!!me.data && !me.isError && !me.isFetching && portalMemberships(me.data).length > 0 && <NavLink to="/customer">Customer Portal</NavLink>}
  </>
}
function PublicAreas() {
  return <><NavLink to="/admin">Tenant Admin</NavLink><NavLink to="/operations">Transport Operations</NavLink><NavLink to="/customer">Customer Portal</NavLink></>
}
