import { useState } from 'react'
import { displayTime } from '../../shared/ui/dateTime'
import { Link, useParams } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../shared/auth/context'
import { ApiProblem } from '../../shared/api/client'
import { useAdminTenant } from './tenant'
import { Button, ConfirmDialog, DataTable, DetailList, EmptyState, ErrorBanner, LoadingState, StatusBadge } from '../../shared/ui/components'
import { AdminPageHeader as PageHeader, AdminPanel, AdminStatus } from './AdminPresentation'
import { adminDescriptions } from './presentation'
import { RecordEditor } from './RecordEditor'
import { useCustomers, useTenantQuery } from './hooks'
import { definitions, isActive, recordName } from './resources'
import type { ResourceKind } from './resources'
import type { MasterRecord, Items, Driver, Site, Location, Trailer, CustomerSite, Settings } from './contracts'
import { TrailerFilters } from './TrailerFilters'
import { QrManagement } from './QrManagement'
import type { TrailerDetail, TrailerListItem } from '../../shared/trailers/contracts'
import { TrailerReadView } from '../../shared/trailers/TrailerReadView'
import { currentLocationLabel } from '../../shared/trailers/display'

const routeFor = (kind: ResourceKind, id: string) => `/admin/${kind}/${id}`
export function MasterList({ kind }: { kind: Exclude<ResourceKind, 'locations' | 'settings'> }) {
  const [create, setCreate] = useState(false)
  const [filters, setFilters] = useState('')
  const query = useTenantQuery<Items<MasterRecord>>(`/${kind}${filters}`)
  const customers = useCustomers(kind === 'trailers')
  return <div className="admin-page"><PageHeader title={definitions[kind].title} description={adminDescriptions[kind]}>
    <Button variant={create ? 'secondary' : 'primary'} onClick={() => setCreate(!create)}>{create ? 'Close create form' : `New ${definitions[kind].singular}`}</Button>
  </PageHeader>
    {kind === 'trailers' && <TrailerFilters customers={customers.data?.items ?? []} onApply={setFilters} />}
    {create && <RecordEditor kind={kind} path={`/${kind}`} customers={customers.data?.items ?? []} created={() => setCreate(false)} reconcile={() => query.refetch({ throwOnError: true })} />}
    <AdminPanel title={`${definitions[kind].singular} directory`} description="Records returned for the current Tenant. Open a record to review its details." className="admin-table-panel">
    {query.isPending ? <LoadingState /> : query.isError ? <ErrorBanner error={query.error} retry={() => void query.refetch()} />
      : !query.data.items.length ? <EmptyState>No {definitions[kind].singular.toLowerCase()} records found.</EmptyState>
        : <DataTable caption={definitions[kind].title} headings={[kind === 'tractors' || kind === 'trailers' ? 'Operational number' : 'Name', 'Status', ...(kind === 'sites' ? ['Address', 'Timezone', 'Geofence configured'] : []), ...(kind === 'drivers' ? ['License number', 'Certificate', 'Account'] : []), ...(kind === 'trailers' ? ['Designated Customer', 'Current location', 'Location state', 'Load', 'Coupling', 'Serviceability', 'Last seen', 'Current Driver', 'Last Driver'] : [])]}>
          {query.data.items.map(record => <tr key={record.id}><td><Link to={routeFor(kind, record.id)}>{recordName(record)}</Link></td>
            <td><AdminStatus value={isActive(record) ? 'Active' : 'Inactive'} /></td>
            {kind === 'sites' && <><td>{addressSummary(record as Site)}</td><td>{(record as Site).timezone}</td><td>{geofenceConfigured(record as Site) ? 'Yes' : 'No'}</td></>}
            {kind === 'drivers' && <><td>{(record as Driver).licenseNumber ?? 'Not provided'}</td><td><StatusBadge tone={certificate(record as Driver).endsWith('Expired') ? 'attention' : 'neutral'}>{certificate(record as Driver)}</StatusBadge></td><td>{(record as Driver).userId ? 'Linked account' : 'No linked account'}</td></>}
            {kind === 'trailers' && <><td>{(record as TrailerListItem).designatedCustomerName ?? ((record as Trailer).designatedCustomerId ? customers.data?.items.find(c => c.id === (record as Trailer).designatedCustomerId)?.name ?? 'Customer details unavailable' : 'None')}</td><td>{currentLocationLabel(record as TrailerListItem)}</td><td><AdminStatus value={(record as Trailer).locationState} /></td><td><AdminStatus value={(record as Trailer).loadState} /></td><td>{(record as TrailerListItem).couplingState === 'COUPLED' ? 'Coupled' : 'Uncoupled'}</td><td><AdminStatus value={(record as Trailer).serviceabilityState} /></td><td>{(record as Trailer).lastSeenAt ?? 'Not provided'}</td><td>{(record as TrailerListItem).currentDriverName ?? 'Not coupled'}</td><td>{(record as TrailerListItem).lastDriverName ?? 'Not recorded'}</td></>}
          </tr>)}
        </DataTable>}</AdminPanel>
  </div>
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
  if (query.isPending) return <div className="admin-page"><PageHeader title={`Loading ${definitions[kind].singular}`} description="Reading the record for the current Tenant." /><LoadingState /></div>
  if (query.isError) return <div className="admin-page"><PageHeader title={`${definitions[kind].singular} unavailable`}><Link to={`/admin/${kind === 'sites' ? 'locations' : kind}`}>Back to {definitions[kind].title}</Link></PageHeader><ErrorBanner error={query.error} retry={() => void query.refetch()} /></div>
  const record = query.data
  const reload = async () => (await query.refetch()).data
  return <div className="admin-page"><PageHeader title={recordName(record)} eyebrow={`${definitions[kind].singular} record`} description={adminDescriptions[kind]}><Link to={`/admin/${kind === 'sites' ? 'locations' : kind}`}>Back to {definitions[kind].title}</Link></PageHeader>
    <section className="admin-record-summary" aria-label="Record overview"><DetailList items={[["Status", <AdminStatus value={isActive(record) ? 'Active' : 'Inactive'} />], ['Created', <time dateTime={record.createdAt}>{displayTime(record.createdAt)}</time>], ['Updated', <time dateTime={record.updatedAt}>{displayTime(record.updatedAt)}</time>]]} /></section>
    <RecordEditor key={id} kind={kind} path={`/${kind}/${id}`} record={record} customers={customers.data?.items ?? []} reload={reload} />
    {kind === 'customers' && <CustomerAssociations customerId={id} />}
    {kind === 'sites' && <SiteLocations site={record as Site} />}
    {kind === 'drivers' && <DriverAccount driver={record as Driver} reload={reload} />}
    {kind === 'trailers' && <TrailerDetails trailer={record as TrailerDetail} />}
  </div>
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
  return <AdminPanel title="Customer ↔ Site associations" description="Associate an existing Physical Site. This view shows associations confirmed during this visit; a complete association directory is not available yet.">
    {sites.isPending ? <LoadingState /> : sites.isError ? <ErrorBanner error={sites.error} retry={() => void sites.refetch()} /> : <>
      <div className="admin-context-form"><label htmlFor="association-site">Physical Site</label><select id="association-site" value={selected} onChange={e => select(e.target.value)}><option value="">Choose a Site</option>{sites.data.items.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
      <Button variant="primary" disabled={!selected || add.isPending} loading={add.isPending} onClick={() => add.mutate()}>Associate Site</Button></div>
      {known.map(row => <div className="admin-association-row" key={row.id}>{sites.data.items.find(s => s.id === row.siteId)?.name ?? 'Physical Site'} <Button onClick={() => setRemove(row)}>Remove association</Button></div>)}
    </>}
    {(add.error || unlink.error) && <ErrorBanner error={add.error ?? unlink.error} />}
    {remove && <ConfirmDialog className="admin-dialog" title="Remove association?" confirmLabel="Remove association" busy={unlink.isPending} onCancel={() => setRemove(null)} onConfirm={() => unlink.mutate(remove)}><p>The Site and Customer will remain. Active Customer Locations may prevent removal.</p></ConfirmDialog>}
  </AdminPanel>
}
function SiteLocations({ site }: { site: Site }) {
  const query = useTenantQuery<Items<Location>>(`/sites/${site.id}/locations`)
  const customers = useCustomers()
  const [create, setCreate] = useState(false)
  const [selected, select] = useState<string | null>(null)
  const location = query.data?.items.find(l => l.id === selected)
  return <AdminPanel title="Operational Locations" description="A neutral Location has no Customer context. Customer areas use the association with this same Site." actions={<Button variant={create ? 'secondary' : 'primary'} onClick={() => setCreate(!create)}>{create ? 'Close location form' : 'New Operational Location'}</Button>}>
    {create && <RecordEditor kind="locations" path={`/sites/${site.id}/locations`} customers={customers.data?.items ?? []} created={() => setCreate(false)} reconcile={() => query.refetch({ throwOnError: true })} />}
    {query.isPending ? <LoadingState /> : query.isError ? <ErrorBanner error={query.error} retry={() => void query.refetch()} /> : !query.data.items.length ? <EmptyState>No Operational Locations.</EmptyState>
      : <DataTable caption="Operational Locations" headings={['Name', 'Type', 'Customer context', 'Status']}>
        {query.data.items.map(l => <tr key={l.id}><td><Button variant="subtle" onClick={() => select(l.id)}>{l.name}</Button></td><td>{l.type}</td><td>{l.customerId ? customers.data?.items.find(c => c.id === l.customerId)?.name ?? 'Customer' : 'Neutral'}</td><td><AdminStatus value={l.active ? 'Active' : 'Inactive'} /></td></tr>)}
      </DataTable>}
    {location && <RecordEditor key={location.id} kind="locations" path={`/locations/${location.id}`} record={location} customers={customers.data?.items ?? []}
      reload={async () => (await query.refetch()).data?.items.find(l => l.id === location.id)} />}
  </AdminPanel>
}
function DriverAccount({ driver, reload }: { driver: Driver; reload: () => Promise<object | undefined> }) {
  const { api } = useAuth(); const { tenantId } = useAdminTenant(); const cache = useQueryClient()
  const [confirm, setConfirm] = useState(false)
  const unlink = useMutation({ mutationFn: () => api.request<void>(`/tenants/${tenantId}/drivers/${driver.id}/user-link`, { method: 'DELETE', body: { expectedVersion: driver.version } }),
    onSuccess() { setConfirm(false); void cache.invalidateQueries({ queryKey: ['tenant', tenantId] }) }, onError() { setConfirm(false) } })
  return <AdminPanel title="Driver account"><p>{driver.userId ? 'Linked account' : 'No linked account'}</p><p>{certificate(driver)}</p>
    <p>New account linking will be available when account selection is supported.</p>
    {driver.userId && <Button disabled={unlink.isPending || (unlink.error instanceof ApiProblem && unlink.error.problem.code === 'STALE_VERSION')} onClick={() => setConfirm(true)}>Unlink account</Button>}
    {unlink.error && <ErrorBanner error={unlink.error} reload={() => void reload().then(() => unlink.reset())} />}
    {confirm && <ConfirmDialog className="admin-dialog" title="Unlink Driver account?" confirmLabel="Unlink" busy={unlink.isPending} onCancel={() => setConfirm(false)} onConfirm={() => unlink.mutate()}><p>The User, membership and roles will be kept.</p></ConfirmDialog>}
  </AdminPanel>
}
function TrailerDetails({ trailer }: { trailer: TrailerDetail }) {
  const { tenantId } = useAdminTenant()
  return <>
    <AdminPanel title="Current designated Customer"><p>{trailer.designatedCustomer?.name ?? 'None'}</p>
      <button type="button" disabled aria-describedby="designation-unavailable">Change designated Customer</button>
      <p id="designation-unavailable">Audited designation changes are supported by the backend. This view is read-only; metadata editing does not change designation.</p>
    </AdminPanel>
    <TrailerReadView trailer={trailer} tenantId={tenantId} namespace="tenant" /><QrManagement trailerId={trailer.id} />
  </>
}
export function SettingsPage() {
  const query = useTenantQuery<Settings>('/settings')
  return <div className="admin-page"><PageHeader title="Settings" description="Configure the stale Trailer threshold and optional default timezone." />
    {query.isPending ? <LoadingState /> : query.isError ? <ErrorBanner error={query.error} retry={() => void query.refetch()} />
      : <RecordEditor kind="settings" path="/settings" record={query.data} reload={async () => (await query.refetch()).data} />}
  </div>
}
