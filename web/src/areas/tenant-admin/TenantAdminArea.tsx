import { Link } from 'react-router'
import { AdminPageHeader, AdminPanel } from './AdminPresentation'
import { useAdminTenant } from './tenant'
import { WorkspaceIcon } from '../../shared/ui/WorkspaceIcon'

export function TenantAdminArea() {
  const tenant = useAdminTenant()
  const catalog = [
    ['Customers', '/admin/customers', 'Customer records and Physical Site associations.'],
    ['Locations', '/admin/locations', 'Facilities, addresses and Operational Locations.'],
    ['Drivers', '/admin/drivers', 'Driver identity, certificates and account connections.'],
    ['Tractors', '/admin/tractors', 'Operational identifiers and active equipment records.'],
    ['Trailers', '/admin/trailers', 'Equipment metadata, observed state and QR identities.'],
  ]
  const controls = [
    ['Settings', '/admin/settings', 'Stale Trailer threshold and default timezone.'],
    ...(tenant.capabilities?.includes('ALERT_VIEW') ? [['Alerts', '/admin/alerts', 'Review recorded conditions and authorized follow-up actions.']] : []),
    ...(tenant.tenantActive !== false && tenant.capabilities?.includes('AUDIT_VIEW') ? [['Audit', '/admin/audit', 'Read-only administrative changes and recorded actors.']] : []),
  ]
  const links = (items: string[][]) => <div className="admin-overview-links">{items.map(([title, to, description]) => <Link key={to} to={to!} aria-label={`Manage ${title}`}><i className="admin-module-icon"><WorkspaceIcon to={to!} /></i><strong>{title}</strong><span aria-hidden="true">↗</span><p>{description}</p></Link>)}</div>
  return <div className="admin-page"><AdminPageHeader title="Tenant Admin" eyebrow="Overview" description="Manage the records and configuration that keep your workspace organized." />
    <div className="admin-overview-grid"><AdminPanel title="Master data" description="Keep Customer, facility and equipment records consistent. Select a section to get started.">{links(catalog)}</AdminPanel>
    <AdminPanel title="Tenant controls" description="Configuration and authorized review tools for the current workspace.">{links(controls)}</AdminPanel></div>
  </div>
}
