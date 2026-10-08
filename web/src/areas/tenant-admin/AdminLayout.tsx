import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '../../shared/auth/context'
import { EmptyState, ErrorBanner, FormField, LoadingState, PageHeader } from '../../shared/ui/components'
import type { CurrentUser } from './contracts'
import { AdminTenant } from './tenant'
export function AdminLayout() {
  const session = useAuth()
  if (session.identity === undefined) return <LoadingState />
  if (!session.identity || session.expired) return <SignIn />
  return <AdminDirectory key={session.identity.uid} />
}
function AdminDirectory() {
  const { api, identity, signOut } = useAuth()
  const cache = useQueryClient()
  const navigate = useNavigate()
  const [selected, select] = useState<string | null>(null)
  const me = useQuery({ queryKey: ['me', identity?.uid], queryFn: ({ signal }) => api.request<CurrentUser>('/me', { signal }) })
  if (me.isPending) return <LoadingState />
  if (me.isError) return <ErrorBanner error={me.error} retry={() => void me.refetch()} />
  const eligible = me.data.memberships.filter(m => m.status === 'ACTIVE' && m.roles.some(r => r.roleCode === 'TENANT_ADMIN' && r.scopeType === 'TENANT'))
  const current = eligible.find(m => m.tenantId === selected) ?? (eligible.length === 1 ? eligible[0] : undefined)
  const changeTenant = (id: string) => {
    void cache.cancelQueries({ queryKey: ['tenant'] })
    cache.removeQueries({ queryKey: ['tenant'] })
    select(id); navigate('/admin')
  }
  return <section className="admin-area">
    <div className="admin-topbar"><span>Tenant Admin</span>
      {eligible.length > 1 && <label>Active Tenant <select aria-label="Active Tenant" value={current?.tenantId ?? ''} onChange={e => changeTenant(e.target.value)}>
        <option value="">Choose a Tenant</option>{eligible.map(m => <option key={m.tenantId} value={m.tenantId}>{m.tenantName}</option>)}
      </select></label>}
      {eligible.length === 1 && <strong>{current?.tenantName}</strong>}
      <button type="button" onClick={() => void signOut()}>Sign out</button>
    </div>
    {!eligible.length ? <EmptyState><h1>Admin access is not available</h1><p>Your account has no active Tenant Admin membership.</p></EmptyState>
      : !current ? <EmptyState><h1>Select a Tenant</h1><p>Choose the workspace you want to manage.</p></EmptyState>
        : <AdminTenant.Provider value={current}><div className="admin-workspace" key={current.tenantId}>
          <nav aria-label="Admin navigation">{['Customers', 'Locations', 'Drivers', 'Tractors', 'Trailers', 'Settings'].map(label =>
            <NavLink key={label} to={`/admin/${label.toLowerCase()}`}>{label}</NavLink>)}</nav>
          <div className="admin-content"><Outlet /></div>
        </div></AdminTenant.Provider>}
  </section>
}
export function AdminHome() { return <PageHeader title="Tenant Admin" description="Manage master data and Tenant settings. Choose a section from the navigation." /> }
function SignIn() {
  const { auth, refresh, expired } = useAuth()
  const [failed, setFailed] = useState(false)
  const form = useForm<{ email: string; password: string }>({ defaultValues: { email: '', password: '' } })
  const submit = form.handleSubmit(async fields => {
    if (!z.object({ email: z.email(), password: z.string().min(1) }).safeParse(fields).success) {
      form.setError('email', { message: 'Enter an email and password.' }); return
    }
    setFailed(false)
    try { await auth.signIn(fields.email, fields.password); form.reset() } catch { setFailed(true); form.resetField('password') }
  })
  return <section className="sign-in"><PageHeader title="Tenant Admin" description="Sign in to manage your Tenant." />
    {!auth.configured ? <EmptyState>Web sign-in is not configured. Contact your administrator.</EmptyState>
      : <form onSubmit={submit} noValidate>
        <FormField id="signin-email" label="Email" error={form.formState.errors.email?.message}>
          <input id="signin-email" type="email" autoComplete="username" {...form.register('email')} />
        </FormField>
        <FormField id="signin-password" label="Password">
          <input id="signin-password" type="password" autoComplete="current-password" {...form.register('password')} />
        </FormField>
        {failed && <p role="alert">Sign-in could not be completed. Check your account and try again.</p>}
        <button disabled={form.formState.isSubmitting}>Sign in</button>
        {expired && <button type="button" onClick={() => void refresh().catch(() => setFailed(true))}>Refresh session</button>}
      </form>}
  </section>
}
