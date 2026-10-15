import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../shared/auth/context'
import { useCurrentUser } from '../../shared/auth/currentUser'
import { SignIn } from '../tenant-admin/AdminLayout'
import { EmptyState, ErrorBanner, LoadingState } from '../../shared/ui/components'
import type { CurrentUser, Membership } from '../tenant-admin/contracts'
import { assignedCustomers, customerChoices, customerQuery, customerRoot, portalMemberships } from './queries'
import type { CustomerOwner } from './queries'
import { PortalContext } from './context'
import { WorkspaceMemory } from './workspaceMemory'

export function CustomerPortalArea() {
  const { identity, expired } = useAuth()
  if (identity === undefined) return <LoadingState />
  if (!identity || expired) return <SignIn customer />
  return <CustomerDirectory key={identity.uid} uid={identity.uid} />
}
function CustomerDirectory({ uid }: { uid: string }) {
  const me = useCurrentUser(30_000), cache = useQueryClient(), { pathname } = useLocation(), { signOut } = useAuth()
  const { refetch } = me
  useEffect(() => { void refetch() }, [pathname, refetch])
  useEffect(() => () => {
    void cache.cancelQueries({ queryKey: customerRoot(uid) }); cache.removeQueries({ queryKey: customerRoot(uid) })
  }, [cache, uid])
  if (me.isPending) return <section><h1>Customer Portal</h1><LoadingState /><button onClick={() => void signOut()}>Sign out</button></section>
  if (me.isError) return <section><h1>Customer Portal</h1><ErrorBanner error={me.error} retry={() => void me.refetch()} /><button onClick={() => void signOut()}>Sign out</button></section>
  const memberships = portalMemberships(me.data)
  const assignmentVersion = JSON.stringify([me.data.userId, memberships.map(m => [m.tenantId, assignedCustomers(m)])])
  return <ContextPicker key={assignmentVersion} uid={uid} me={me.data} memberships={memberships} readRevision={me.data.clientReadSequence ?? me.dataUpdatedAt} verifying={me.isFetching || me.fetchStatus === 'paused'} refresh={() => void me.refetch()} />
}
function ContextPicker({ uid, me, memberships, readRevision, verifying, refresh }: { uid: string; me: CurrentUser; memberships: Membership[]; readRevision: number; verifying: boolean; refresh(): void }) {
  const [tenantId, setTenant] = useState(''), [customerId, setCustomer] = useState('')
  const [workspace,setWorkspace]=useState(()=>new Map<string,unknown>())
  const cache = useQueryClient(), navigate = useNavigate(), { signOut } = useAuth()
  const current = memberships.find(m => m.tenantId === tenantId) ?? (memberships.length === 1 ? memberships[0] : undefined)
  const changeTenant = (id: string) => {
    if (!memberships.some(m => m.tenantId === id)) return
    void cache.cancelQueries({ queryKey: customerRoot(uid) }); cache.removeQueries({ queryKey: customerRoot(uid) })
    setCustomer(''); setTenant(id); void navigate('/customer')
    setWorkspace(new Map())
  }
  const changeCustomer = (id: string) => {
    if (!current || !assignedCustomers(current).includes(id)) return
    const oldScopes = { predicate: (q: { queryKey: readonly unknown[] }) => q.queryKey[0] === 'customer' && q.queryKey[1] === uid && q.queryKey[4] !== 'directory' }
    void cache.cancelQueries(oldScopes); cache.removeQueries(oldScopes)
    setCustomer(id); void navigate('/customer')
    setWorkspace(new Map())
  }
  return <WorkspaceMemory.Provider value={workspace}><section className="customer-area">
    <header className="admin-topbar"><strong>Customer Portal</strong><span>{me.displayName ?? me.email ?? 'Signed in'}</span>
      {memberships.length > 1 ? <label>Tenant <select aria-label="Customer portal Tenant" value={current?.tenantId ?? ''} onChange={e => changeTenant(e.target.value)}><option value="">Choose a Tenant</option>{memberships.map(m => <option key={m.tenantId} value={m.tenantId}>{m.tenantName}</option>)}</select></label> : current && <span>Tenant: {current.tenantName}</span>}
      <button onClick={refresh}>Refresh access</button><button onClick={() => void signOut()}>Sign out</button>
    </header>
    {verifying ? <LoadingState /> : !memberships.length ? <EmptyState><h1>Customer access is not available</h1><p>Your account has no active Customer Dispatcher context. Refresh access after your administrator updates it.</p></EmptyState> : !current ?
      <EmptyState><h1>Select a Tenant</h1><p>Choose one of your Customer workspaces.</p></EmptyState> :
      <CustomerPicker key={`${current.tenantId}:${readRevision}`} owner={{ uid, userId: me.userId, tenantId: current.tenantId, membership: current }} selected={customerId} select={changeCustomer} />}
  </section></WorkspaceMemory.Provider>
}
function CustomerPicker({ owner, selected, select }: { owner: Omit<CustomerOwner,'customerId'>; selected: string; select(id: string): void }) {
  const { api } = useAuth()
  const directory = useQuery({ queryKey: [...customerRoot(owner.uid),owner.userId,owner.membership.tenantId,'directory',assignedCustomers(owner.membership)],
    queryFn: ({ signal }) => customerChoices(api, owner, signal), retry: false, staleTime: 0, gcTime: 0,
  })
  if (directory.isPending || directory.isFetching) return <LoadingState />
  if (directory.isError) return <ErrorBanner error={directory.error} retry={() => void directory.refetch()} />
  const rows = directory.data.available, current = rows.find(c => c.id === selected) ?? (rows.length === 1 ? rows[0] : undefined)
  return <>
    {rows.length > 1 && <label className="customer-selector">Customer <select aria-label="Active Customer" value={current?.id ?? ''} onChange={e => { if (rows.some(c => c.id === e.target.value)) select(e.target.value) }}><option value="">Choose a Customer</option>{rows.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>}
    {!rows.length ? <EmptyState><h1>No Customer available</h1><p>Assigned Customer access is unavailable or inactive. Refresh access to verify the current context.</p></EmptyState> : !current ? <EmptyState><h1>Select a Customer</h1><p>Each Customer has a separate workspace.</p></EmptyState> :
      <VerifiedCustomer key={current.id} owner={{ ...owner, customerId: current.id }} />}
    {directory.data.unavailable > 0 && rows.length > 0 && <p role="status">Some assigned contexts are unavailable. Refresh access to review your current assignments.</p>}
  </>
}
function VerifiedCustomer({ owner }: { owner: CustomerOwner }) {
  const { api } = useAuth(), cache = useQueryClient(), current = useQuery(customerQuery(api, owner))
  useEffect(() => () => {
    const key = [...customerRoot(owner.uid),owner.userId,owner.membership.tenantId,owner.customerId]
    void cache.cancelQueries({ queryKey: key }); cache.removeQueries({ queryKey: key })
  }, [cache, owner.uid, owner.userId, owner.membership.tenantId, owner.customerId])
  if (current.isPending || current.isFetching) return <LoadingState />
  if (current.isError) return <ErrorBanner error={current.error} retry={() => void current.refetch()} />
  if (current.data.status !== 'ACTIVE') return <EmptyState><h1>Customer unavailable</h1><p>This Customer is inactive. Refresh access or select another authorized context.</p></EmptyState>
  return <PortalContext.Provider value={{ ...owner, customer: current.data }}>
    <p className="customer-scope" role="status">Tenant: <strong>{owner.membership.tenantName}</strong> · Customer: <strong>{current.data.name}</strong> · Access verified</p>
    <div className="customer-workspace"><nav aria-label="Customer navigation">{['Dashboard','Orders','Trailers','Deliveries','Documents'].map((label,index) => <NavLink key={label} end={index === 0} to={index === 0 ? '/customer' : `/customer/${label.toLowerCase()}`}>{label}</NavLink>)}</nav>
      <div className="customer-content"><Outlet /></div></div>
  </PortalContext.Provider>
}
