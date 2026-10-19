import { useState } from 'react'
import { Outlet, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '../../shared/auth/context'
import { Button, EmptyState, ErrorBanner, FormField, LoadingState, PageHeader } from '../../shared/ui/components'
import { AuthLayout, WorkspaceHeader, WorkspaceNavigation, WorkspaceState } from '../../shared/ui/WorkspaceShell'
import { AdminIdentity, AdminTenant } from './tenant'
import { useCurrentUser } from '../../shared/auth/currentUser'
import { TenantAdminArea } from './TenantAdminArea'
export function AdminLayout() {
  const session = useAuth()
  if (session.identity === undefined) return <WorkspaceState title="Verifying session"><LoadingState /></WorkspaceState>
  if (!session.identity || session.expired) return <SignIn />
  return <AdminDirectory key={session.identity.uid} />
}
function AdminDirectory() {
  const { signOut } = useAuth()
  const cache = useQueryClient()
  const navigate = useNavigate()
  const [selected, select] = useState<string | null>(null)
  const me = useCurrentUser(60_000)
  if (me.isPending) return <WorkspaceState title="Loading Tenant Admin"><LoadingState /></WorkspaceState>
  if (me.isError) return <WorkspaceState title="Tenant Admin unavailable"><ErrorBanner error={me.error} retry={() => void me.refetch()} /></WorkspaceState>
  const eligible = me.data.memberships.filter(m => m.status === 'ACTIVE' && m.roles.some(r => r.roleCode === 'TENANT_ADMIN' && r.scopeType === 'TENANT'))
  const current = eligible.find(m => m.tenantId === selected) ?? (eligible.length === 1 ? eligible[0] : undefined)
  const changeTenant = (id: string) => {
    void cache.cancelQueries({ queryKey: ['tenant'] })
    cache.removeQueries({ queryKey: ['tenant'] })
    void cache.cancelQueries({ queryKey: ['audit'] })
    cache.removeQueries({ queryKey: ['audit'] })
    select(id); navigate('/admin')
  }
  return <section className="admin-area">
    <WorkspaceHeader title="Tenant Admin" identity={me.data.displayName ?? me.data.email ?? 'Signed in'} actions={<Button onClick={() => void signOut()}>Sign out</Button>}>
      {eligible.length > 1 && <label>Active Tenant <select aria-label="Active Tenant" value={current?.tenantId ?? ''} onChange={e => changeTenant(e.target.value)}>
        <option value="">Choose a Tenant</option>{eligible.map(m => <option key={m.tenantId} value={m.tenantId}>{m.tenantName}</option>)}
      </select></label>}
      {eligible.length === 1 && <strong>{current?.tenantName}</strong>}
    </WorkspaceHeader>
    {!eligible.length ? <EmptyState><h1>Admin access is not available</h1><p>Your account has no active Tenant Admin membership.</p></EmptyState>
      : !current ? <EmptyState><h1>Select a Tenant</h1><p>Choose the workspace you want to manage.</p></EmptyState>
        : <AdminIdentity.Provider value={me.data.userId}><AdminTenant.Provider value={current}><div className="workspace-layout" key={`${me.data.userId}:${current.tenantId}`}>
          <WorkspaceNavigation label="Admin navigation" context={current.tenantName} links={[
            { label: 'Overview', to: '/admin', end: true },
            ...['Customers', 'Locations', 'Drivers', 'Tractors', 'Trailers', 'Settings'].map(label => ({ label, to: `/admin/${label.toLowerCase()}` })),
            ...(current.capabilities?.includes('ALERT_VIEW') ? [{ label: 'Alerts', to: '/admin/alerts' }] : []),
            ...(current.tenantActive !== false && current.capabilities?.includes('AUDIT_VIEW') ? [{ label: 'Audit', to: '/admin/audit' }] : []),
          ]} />
          <div className="admin-content workspace-content"><Outlet /></div>
        </div></AdminTenant.Provider></AdminIdentity.Provider>}
  </section>
}
export function AdminHome() { return <TenantAdminArea /> }
export function SignIn({ operations = false, customer = false }: { operations?: boolean; customer?: boolean }) {
  const { auth, refresh, expired } = useAuth()
  const [failed, setFailed] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const form = useForm<{ email: string; password: string }>({ defaultValues: { email: '', password: '' } })
  const submit = form.handleSubmit(async fields => {
    if (!z.object({ email: z.email(), password: z.string().min(1) }).safeParse(fields).success) {
      form.setError('email', { message: 'Enter an email and password.' }); return
    }
    setFailed(false)
    try { await auth.signIn(fields.email, fields.password); form.reset() } catch { setFailed(true); form.resetField('password') }
  })
  return <AuthLayout><PageHeader title={customer ? 'Customer Portal' : operations ? 'Transport Operations' : 'Tenant Admin'} description={customer ? 'Sign in to your authorized Customer workspace.' : operations ? 'Sign in to plan and coordinate dispatch.' : 'Sign in to manage your Tenant.'} />
    {expired && <p className="auth-session-notice" role="status">Your session has expired. Sign in again or refresh your session to continue.</p>}
    {!auth.configured ? <EmptyState>Web sign-in is not configured. Contact your administrator.</EmptyState>
      : <form onSubmit={submit} noValidate>
        <FormField id="signin-email" label="Email" error={form.formState.errors.email?.message}>
          <input id="signin-email" type="email" autoComplete="username" aria-invalid={!!form.formState.errors.email} aria-describedby={form.formState.errors.email ? 'signin-email-error' : undefined} {...form.register('email')} />
        </FormField>
        <FormField id="signin-password" label="Password">
          <input id="signin-password" type="password" autoComplete="current-password" {...form.register('password')} />
        </FormField>
        {failed && <p className="error-banner" role="alert">Sign-in could not be completed. Check your account and try again.</p>}
        <Button type="submit" variant="primary" loading={form.formState.isSubmitting} disabled={refreshing}>Sign in</Button>
        {expired && <Button loading={refreshing} disabled={form.formState.isSubmitting} onClick={() => {
          setRefreshing(true)
          void refresh().catch(() => setFailed(true)).finally(() => setRefreshing(false))
        }}>Refresh session</Button>}
      </form>}
  </AuthLayout>
}
