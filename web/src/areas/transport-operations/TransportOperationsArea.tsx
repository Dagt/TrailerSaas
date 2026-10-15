import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../shared/auth/context'
import type { CurrentUser } from '../tenant-admin/contracts'
import { SignIn } from '../tenant-admin/AdminLayout'
import { EmptyState, ErrorBanner, LoadingState } from '../../shared/ui/components'
import { OperationsTenant } from './queries'
export function TransportOperationsArea() {
  const { identity, expired } = useAuth()
  if (identity === undefined) return <LoadingState />
  if (!identity || expired) return <SignIn operations />
  return <OperationsDirectory key={identity.uid} />
}
function OperationsDirectory() {
  const { api, identity, signOut } = useAuth(), cache = useQueryClient(), navigate = useNavigate()
  const [selected, select] = useState('')
  const me = useQuery({ queryKey: ['me', identity?.uid], queryFn: ({ signal }) => api.request<CurrentUser>('/me', { signal }) })
  if (me.isPending) return <LoadingState />
  if (me.isError) return <ErrorBanner error={me.error} retry={() => void me.refetch()} />
  const eligible = me.data.memberships.filter(m => m.status === 'ACTIVE' && m.roles.some(r => r.scopeType === 'TENANT' && ['TENANT_ADMIN', 'TRANSPORT_OPERATIONS'].includes(r.roleCode)))
  const current = eligible.find(m => m.tenantId === selected) ?? (eligible.length === 1 ? eligible[0] : undefined)
  const change = (id: string) => {
    void cache.cancelQueries({ queryKey: ['operations'] }); cache.removeQueries({ queryKey: ['operations'] })
    select(id); navigate('/operations')
  }
  return <section className="operations-area"><div className="admin-topbar"><strong>Dispatch workspace</strong>
    {eligible.length > 1 ? <label>Active Tenant <select aria-label="Active Tenant" value={current?.tenantId ?? ''} onChange={e => change(e.target.value)}><option value="">Choose a Tenant</option>{eligible.map(m => <option key={m.tenantId} value={m.tenantId}>{m.tenantName}</option>)}</select></label> : <span>{current?.tenantName}</span>}
    <button onClick={() => void signOut()}>Sign out</button></div>
    {!eligible.length ? <EmptyState><h1>Operations access is not available</h1><p>Your account has no active Operations membership.</p></EmptyState> : !current ? <EmptyState><h1>Select a Tenant</h1><p>Choose the dispatch workspace.</p></EmptyState> :
      <OperationsTenant.Provider value={current}><div className="operations-workspace" key={current.tenantId}><nav aria-label="Operations navigation"><NavLink end to="/operations">Dashboard</NavLink><NavLink to="/operations/orders">Orders</NavLink><NavLink to="/operations/trips">Trips</NavLink><NavLink to="/operations/trailers">Trailers</NavLink>{current.capabilities?.includes('ALERT_VIEW') && <NavLink to="/operations/alerts">Alerts</NavLink>}</nav><div className="operations-content"><Outlet /></div></div></OperationsTenant.Provider>}
  </section>
}
