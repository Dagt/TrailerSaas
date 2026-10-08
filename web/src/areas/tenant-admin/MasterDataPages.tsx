import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../shared/auth/context'
import { ApiProblem } from '../../shared/api/client'
import { useAdminTenant } from './tenant'
import { ConfirmDialog, DataTable, DetailList, EmptyState, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../../shared/ui/components'
import { RecordEditor } from './RecordEditor'
import { useCustomers, useTenantQuery } from './hooks'
import { definitions, isActive, recordName } from './resources'
import type { ResourceKind } from './resources'
import type { MasterRecord, Items, Driver, Site, Location, Trailer, CustomerSite, Settings } from './contracts'
import { TrailerFilters } from './TrailerFilters'
import { QrManagement } from './QrManagement'

const routeFor = (kind: ResourceKind, id: string) => `/admin/${kind}/${id}`
export function MasterList({ kind }: { kind: Exclude<ResourceKind, 'locations' | 'settings'> }) {
  const [create, setCreate] = useState(false)
  const [filters, setFilters] = useState('')
  const query = useTenantQuery<Items<MasterRecord>>(`/${kind}${filters}`)
  const customers = useCustomers(kind === 'trailers')
  return <><PageHeader title={definitions[kind].title} description={kind === 'sites' ? 'Physical Sites describe a facility or street address. Operational Locations belong to a Site.' : undefined}>
    <button type="button" onClick={() => setCreate(!create)}>{create ? 'Close create form' : `New ${definitions[kind].singular}`}</button>
  </PageHeader>
    {kind === 'trailers' && <TrailerFilters customers={customers.data?.items ?? []} onApply={setFilters} />}
    {create && <RecordEditor kind={kind} path={`/${kind}`} customers={customers.data?.items ?? []} created={() => setCreate(false)} reconcile={() => query.refetch({ throwOnError: true })} />}
    {query.isPending ? <LoadingState /> : query.isError ? <ErrorBanner error={query.error} retry={() => void query.refetch()} />
      : !query.data.items.length ? <EmptyState>No {definitions[kind].singular.toLowerCase()} records found.</EmptyState>
        : <DataTable caption={definitions[kind].title} headings={[kind === 'tractors' || kind === 'trailers' ? 'Operational number' : 'Name', 'Status', ...(kind === 'sites' ? ['Address', 'Timezone', 'Geofence configured'] : []), ...(kind === 'drivers' ? ['License number', 'Certificate', 'Account'] : []), ...(kind === 'trailers' ? ['Designated Customer', 'Location', 'Load', 'Serviceability', 'Last seen'] : [])]}>
          {query.data.items.map(record => <tr key={record.id}><td><Link to={routeFor(kind, record.id)}>{recordName(record)}</Link></td>
            <td><StatusBadge>{isActive(record) ? 'Active' : 'Inactive'}</StatusBadge></td>
            {kind === 'sites' && <><td>{addressSummary(record as Site)}</td><td>{(record as Site).timezone}</td><td>{geofenceConfigured(record as Site) ? 'Yes' : 'No'}</td></>}
            {kind === 'drivers' && <><td>{(record as Driver).licenseNumber ?? 'Not provided'}</td><td>{certificate(record as Driver)}</td><td>{(record as Driver).userId ? 'Linked account' : 'No linked account'}</td></>}
            {kind === 'trailers' && <><td>{(record as Trailer).designatedCustomerId ? customers.data?.items.find(c => c.id === (record as Trailer).designatedCustomerId)?.name ?? 'Customer details unavailable' : 'None'}</td><td>{(record as Trailer).locationState}</td><td>{(record as Trailer).loadState}</td><td>{(record as Trailer).serviceabilityState}</td><td>{(record as Trailer).lastSeenAt ?? 'Not provided'}</td></>}
          </tr>)}
        </DataTable>}
  </>
}
function addressSummary(site: Site) {
  const address = site.address
  return address ? [address.line1, address.line2, address.city, address.stateRegion, address.postalCode, address.countryCode].filter(value => value?.trim()).join(', ') || 'Not provided' : 'Not provided'
}
function geofenceConfigured(site: Site) {
  return site.latitude != null && site.longitude != null && site.geofenceRadiusM != null
}
function certificate(driver: Driver) {
  if (!driver.medicalCertificateExpires) return 'Not provided'
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
  return driver.medicalCertificateExpires < today ? `${driver.medicalCertificateExpires} — Expired` : driver.medicalCertificateExpires
}
export function MasterDetail({ kind }: { kind: Exclude<ResourceKind, 'locations' | 'settings'> }) {
  const params = useParams()
  const id = params.customerId ?? params.siteId ?? params.driverId ?? params.tractorId ?? params.trailerId!
  const query = useTenantQuery<MasterRecord>(`/${kind}/${id}`)
  const customers = useCustomers(kind === 'sites' || kind === 'trailers')
  if (query.isPending) return <LoadingState />
  if (query.isError) return <ErrorBanner error={query.error} retry={() => void query.refetch()} />
  const record = query.data
  const reload = async () => (await query.refetch()).data
  return <><PageHeader title={recordName(record)}><Link to={`/admin/${kind === 'sites' ? 'locations' : kind}`}>Back to {definitions[kind].title}</Link></PageHeader>
    <DetailList items={[["Status", isActive(record) ? 'Active' : 'Inactive'], ['Created', record.createdAt], ['Updated', record.updatedAt]]} />
    <RecordEditor key={id} kind={kind} path={`/${kind}/${id}`} record={record} customers={customers.data?.items ?? []} reload={reload} />
    {kind === 'customers' && <CustomerAssociations customerId={id} />}
    {kind === 'sites' && <SiteLocations site={record as Site} />}
    {kind === 'drivers' && <DriverAccount driver={record as Driver} reload={reload} />}
    {kind === 'trailers' && <TrailerDetails trailer={record as Trailer} customers={customers.data?.items ?? []} />}
  </>
}
function CustomerAssociations({ customerId }: { customerId: string }) {
  const { api } = useAuth(); const { tenantId } = useAdminTenant(); const cache = useQueryClient()
  const sites = useTenantQuery<Items<Site>>('/sites')
  const [selected, select] = useState('')
  const [known, setKnown] = useState<CustomerSite[]>([])
  const [remove, setRemove] = useState<CustomerSite | null>(null)
  const add = useMutation({ mutationFn: () => api.request<CustomerSite>(`/tenants/${tenantId}/customers/${customerId}/sites`, { method: 'POST', body: { siteId: selected } }),
    onSuccess(row) { setKnown(previous => [...previous.filter(p => p.siteId !== row.siteId), row]); void cache.invalidateQueries({ queryKey: ['tenant', tenantId] }) } })
  const unlink = useMutation({ mutationFn: (row: CustomerSite) => api.request<void>(`/tenants/${tenantId}/customers/${customerId}/sites/${row.siteId}`, { method: 'DELETE' }),
    onSuccess(_, row) { setKnown(previous => previous.filter(p => p.siteId !== row.siteId)); setRemove(null); void cache.invalidateQueries({ queryKey: ['tenant', tenantId] }) } })
  return <section><h2>Customer ↔ Site associations</h2><p>Associate an existing Physical Site. This view shows associations confirmed during this visit; a complete association directory is not available yet.</p>
    {sites.isPending ? <LoadingState /> : sites.isError ? <ErrorBanner error={sites.error} retry={() => void sites.refetch()} /> : <>
      <label htmlFor="association-site">Physical Site</label><select id="association-site" value={selected} onChange={e => select(e.target.value)}><option value="">Choose a Site</option>{sites.data.items.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
      <button type="button" disabled={!selected || add.isPending} onClick={() => add.mutate()}>Associate Site</button>
      {known.map(row => <p key={row.id}>{sites.data.items.find(s => s.id === row.siteId)?.name ?? 'Physical Site'} <button type="button" onClick={() => setRemove(row)}>Remove association</button></p>)}
    </>}
    {(add.error || unlink.error) && <ErrorBanner error={add.error ?? unlink.error} />}
    {remove && <ConfirmDialog title="Remove association?" confirmLabel="Remove association" busy={unlink.isPending} onCancel={() => setRemove(null)} onConfirm={() => unlink.mutate(remove)}><p>The Site and Customer will remain. Active Customer Locations may prevent removal.</p></ConfirmDialog>}
  </section>
}
function SiteLocations({ site }: { site: Site }) {
  const query = useTenantQuery<Items<Location>>(`/sites/${site.id}/locations`)
  const customers = useCustomers()
  const [create, setCreate] = useState(false)
  const [selected, select] = useState<string | null>(null)
  const location = query.data?.items.find(l => l.id === selected)
  return <section><h2>Operational Locations</h2><p>A neutral Location has no Customer context. Customer areas use the association with this same Site.</p>
    <button type="button" onClick={() => setCreate(!create)}>{create ? 'Close location form' : 'New Operational Location'}</button>
    {create && <RecordEditor kind="locations" path={`/sites/${site.id}/locations`} customers={customers.data?.items ?? []} created={() => setCreate(false)} reconcile={() => query.refetch({ throwOnError: true })} />}
    {query.isPending ? <LoadingState /> : query.isError ? <ErrorBanner error={query.error} retry={() => void query.refetch()} /> : !query.data.items.length ? <EmptyState>No Operational Locations.</EmptyState>
      : <DataTable caption="Operational Locations" headings={['Name', 'Type', 'Customer context', 'Status']}>
        {query.data.items.map(l => <tr key={l.id}><td><button type="button" onClick={() => select(l.id)}>{l.name}</button></td><td>{l.type}</td><td>{l.customerId ? customers.data?.items.find(c => c.id === l.customerId)?.name ?? 'Customer' : 'Neutral'}</td><td>{l.active ? 'Active' : 'Inactive'}</td></tr>)}
      </DataTable>}
    {location && <RecordEditor key={location.id} kind="locations" path={`/locations/${location.id}`} record={location} customers={customers.data?.items ?? []}
      reload={async () => (await query.refetch()).data?.items.find(l => l.id === location.id)} />}
  </section>
}
function DriverAccount({ driver, reload }: { driver: Driver; reload: () => Promise<object | undefined> }) {
  const { api } = useAuth(); const { tenantId } = useAdminTenant(); const cache = useQueryClient()
  const [confirm, setConfirm] = useState(false)
  const unlink = useMutation({ mutationFn: () => api.request<void>(`/tenants/${tenantId}/drivers/${driver.id}/user-link`, { method: 'DELETE', body: { expectedVersion: driver.version } }),
    onSuccess() { setConfirm(false); void cache.invalidateQueries({ queryKey: ['tenant', tenantId] }) }, onError() { setConfirm(false) } })
  return <section><h2>Driver account</h2><p>{driver.userId ? 'Linked account' : 'No linked account'}</p><p>{certificate(driver)}</p>
    <p>New account linking will be available when account selection is supported.</p>
    {driver.userId && <button type="button" disabled={unlink.isPending || (unlink.error instanceof ApiProblem && unlink.error.problem.code === 'STALE_VERSION')} onClick={() => setConfirm(true)}>Unlink account</button>}
    {unlink.error && <ErrorBanner error={unlink.error} reload={() => void reload().then(() => unlink.reset())} />}
    {confirm && <ConfirmDialog title="Unlink Driver account?" confirmLabel="Unlink" busy={unlink.isPending} onCancel={() => setConfirm(false)} onConfirm={() => unlink.mutate()}><p>The User, membership and roles will be kept.</p></ConfirmDialog>}
  </section>
}
function TrailerDetails({ trailer, customers }: { trailer: Trailer; customers: import('./contracts').Customer[] }) {
  const driver = useTenantQuery<Driver>(`/drivers/${trailer.lastDriverId}`, Boolean(trailer.lastDriverId))
  return <>
    <section><h2>Current designated Customer</h2><p>{trailer.designatedCustomerId ? customers.find(c => c.id === trailer.designatedCustomerId)?.name ?? 'Customer' : 'None'}</p>
      <button type="button" disabled aria-describedby="designation-unavailable">Change designated Customer</button>
      <p id="designation-unavailable">Designation changes require the audited reassignment workflow, which is not available yet.</p>
    </section>
    <section><h2>Current state</h2><DetailList items={[
      ['Location', trailer.locationState], ['Current location', trailer.currentLocationId ? 'Recorded location (details unavailable)' : 'Not recorded'],
      ['Last known location', trailer.lastKnownLocationId ? 'Recorded location (details unavailable)' : 'Not recorded'],
      ['Last seen', trailer.lastSeenAt], ['Last Driver', driver.data?.name ?? (trailer.lastDriverId ? 'Not available' : 'Not recorded')],
      ['Load', trailer.loadState], ['Serviceability', trailer.serviceabilityState], ['Usage purpose', trailer.usagePurpose],
    ]} /></section><QrManagement trailerId={trailer.id} />
  </>
}
export function SettingsPage() {
  const query = useTenantQuery<Settings>('/settings')
  return <><PageHeader title="Settings" description="Configure the stale Trailer threshold and optional default timezone." />
    {query.isPending ? <LoadingState /> : query.isError ? <ErrorBanner error={query.error} retry={() => void query.refetch()} />
      : <RecordEditor kind="settings" path="/settings" record={query.data} reload={async () => (await query.refetch()).data} />}
  </>
}
