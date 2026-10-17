import { useState } from 'react'
import { AccessDeniedState, Button, Card, ConfirmDialog, DataTable, DetailList, EmptyState, ErrorBanner, FilterPanel, FormField, LoadingState, OfflineState, PageHeader, StatusBadge, Timeline, TimelineEvent } from '../shared/ui/components'

// Isolated component fixtures. No API, authentication, business command or app route.
export function FleetSimpleGallery() {
  const [dialog, setDialog] = useState(false)
  return <div className="gallery-shell">
    <a className="skip-link" href="#gallery-content">Skip to component examples</a>
    <aside className="gallery-sidebar"><div className="gallery-brand"><span className="gallery-mark" aria-hidden="true">F</span>TrailerSaaS</div><p className="gallery-eyebrow">Component review</p>
      <nav aria-label="Component examples">
        <a href="#admin" aria-current="page"><span aria-hidden="true">▤</span> Tenant Admin</a>
        <a href="#operations"><span aria-hidden="true">⇄</span> Operations</a>
        <a href="#customer"><span aria-hidden="true">▦</span> Customer Portal</a>
        <a href="#controls"><span aria-hidden="true">⊞</span> Controls & states</a>
      </nav><p className="gallery-sidebar-note">Fleet Simple<br />Approved Option D</p>
    </aside>
    <div className="gallery-body"><header className="gallery-topbar"><span>Design system / Foundation</span><StatusBadge>DEMO DATA · Development only</StatusBadge></header>
      <main id="gallery-content" className="gallery-content">
        <PageHeader title="A clearer view of the work." description="Fleet Simple · Shared components for everyday fleet operations. Static examples only; no operational actions or live records." />
        <section id="admin" className="gallery-section" aria-labelledby="admin-heading"><div className="gallery-section-heading"><div><p className="gallery-eyebrow">Tenant Admin example</p><h2 id="admin-heading">Trailer inventory</h2></div><StatusBadge>Read-only sample</StatusBadge></div>
          <FilterPanel><FormField id="sample-search" label="Trailer number"><input id="sample-search" placeholder="Search sample inventory" /></FormField><FormField id="sample-status" label="Location state"><select id="sample-status"><option>All states</option><option>At location</option><option>Location unknown</option></select></FormField><FormField id="sample-customer" label="Customer"><select id="sample-customer"><option>All sample Customers</option><option>Example Packaging</option></select></FormField></FilterPanel>
          <DataTable headings={['Trailer', 'Customer', 'Current location', 'Serviceability']} caption="Sample Trailer inventory">
            <tr><th scope="row">DEMO-1042</th><td>Example Packaging</td><td>North Yard · A12</td><td><StatusBadge tone="success">IN_SERVICE</StatusBadge></td></tr>
            <tr><th scope="row">DEMO-1086</th><td>Sample Distribution</td><td><StatusBadge tone="unknown">LOCATION_UNKNOWN</StatusBadge><small className="gallery-cell-note">Last known: North Yard · B04</small></td><td><StatusBadge tone="danger">OOS</StatusBadge></td></tr>
            <tr><th scope="row">DEMO-1091</th><td>Example Packaging</td><td><StatusBadge tone="neutral">IN_TRANSIT</StatusBadge></td><td><StatusBadge tone="success">IN_SERVICE</StatusBadge></td></tr>
          </DataTable>
        </section>
        <section id="operations" className="gallery-section" aria-labelledby="operations-heading"><div className="gallery-section-heading"><div><p className="gallery-eyebrow">Transport Operations example</p><h2 id="operations-heading">Trips & operational history</h2></div></div>
          <div className="gallery-split"><Card title="Trip overview" description="Sample assignment context, not physical coupling."><DetailList items={[["Trip", 'DEMO-TRIP-204'], ['Expected Trailer', 'DEMO-1042'], ['Physical coupling', 'Not confirmed']]} /><StatusBadge tone="attention">READY</StatusBadge></Card>
            <Card title="Operational history" description="Recorded events — not Administrative Audit."><Timeline label="Sample operational events"><TimelineEvent title="Assignment recorded" dateTime="2026-10-16T14:00:00Z" timeLabel="Oct 16, 2026 · 2:00 PM UTC" source="Human user · User action"><p>Expected Trailer: DEMO-1042. Coupling requires a physical event.</p></TimelineEvent><TimelineEvent title="Instructions updated" dateTime="2026-10-16T14:15:00Z" timeLabel="Oct 16, 2026 · 2:15 PM UTC" source="Human user · User action"><p>Sample instruction: check in at receiving.</p></TimelineEvent></Timeline></Card></div>
          <DataTable density="compact" caption="Sample Trips" headings={['Trip', 'Expected Trailer', 'Status', 'Instruction']}><tr><th scope="row">DEMO-TRIP-204</th><td>DEMO-1042</td><td><StatusBadge tone="attention">READY</StatusBadge></td><td>Check in at receiving</td></tr></DataTable>
        </section>
        <section id="customer" className="gallery-section" aria-labelledby="customer-heading"><div className="gallery-section-heading"><div><p className="gallery-eyebrow">Customer Portal example</p><h2 id="customer-heading">Orders & deliveries</h2></div></div><div className="gallery-split"><Card title="Order DEMO-3001" description="Sample Customer-visible status"><StatusBadge tone="attention">DELIVERED</StatusBadge><p>Delivered and Completed remain separate states.</p></Card><Card title="Delivery evidence" description="Sample document state"><StatusBadge tone="pending">POD uploaded; acceptance pending</StatusBadge><p>Upload alone does not confirm accepted evidence.</p></Card></div></section>
        <section id="controls" className="gallery-section" aria-labelledby="controls-heading"><div className="gallery-section-heading"><div><p className="gallery-eyebrow">Component library</p><h2 id="controls-heading">Controls, feedback & typography</h2></div></div>
          <Card title="Action hierarchy" description="Interactive controls below demonstrate component states only."><div className="gallery-actions"><Button variant="primary">Primary action</Button><Button>Secondary</Button><Button variant="subtle">Subtle</Button><Button variant="danger">Destructive action</Button><Button disabled>Disabled</Button><Button loading>Saving…</Button><Button density="compact">Compact</Button><Button onClick={() => setDialog(true)}>Preview confirmation</Button></div></Card>
          <div className="gallery-split"><Card title="Forms & validation"><FormField id="sample-name" label="Display name" hint="Use a recognizable operational name."><input id="sample-name" defaultValue="North Yard" aria-describedby="sample-name-hint" /></FormField><FormField id="sample-reason" label="Reason" error="Enter a reason before continuing."><textarea id="sample-reason" aria-invalid="true" aria-describedby="sample-reason-error" /></FormField><FormField id="sample-disabled" label="Read-only context"><input id="sample-disabled" value="Example Tenant" disabled /></FormField></Card><Card title="Statuses & type"><h3>Section heading</h3><p>Readable text for busy dispatchers and Customer teams.</p><p className="muted">Supporting copy stays legible without competing with the primary task.</p><div className="gallery-actions"><StatusBadge>Neutral</StatusBadge><StatusBadge tone="success">Server confirmed</StatusBadge><StatusBadge tone="attention">Review required</StatusBadge><StatusBadge tone="danger">OOS</StatusBadge><StatusBadge tone="unknown">Unknown</StatusBadge><StatusBadge tone="pending">Pending offline</StatusBadge></div></Card></div>
          <LoadingState /><EmptyState><h2>No records to display</h2><p>Adjust your filters or refresh the query.</p></EmptyState><AccessDeniedState /><OfflineState /><ErrorBanner error={new Error('sample')} />
          <Card title="Flutter foundation mapping" description="Web reference only — no Flutter screen or theme changed."><DetailList items={[["ColorScheme.primary", 'Blue · #2563EB'], ['ColorScheme.surface', 'White · #FFFFFF'], ['onSurface', 'Slate · #1E293B'], ['Supporting accent', 'Teal · #0F766E'], ['Touch target', '44 logical px or greater'], ['Field state', 'Saved on device. Waiting to sync.']]} /></Card>
        </section>
        <footer className="gallery-footer">Static component demonstrations · No API calls · No real Customer data · No live tracking</footer>
      </main>
    </div>
    {dialog && <ConfirmDialog title="Preview confirmation" confirmLabel="Confirm preview" onConfirm={() => setDialog(false)} onCancel={() => setDialog(false)}><p>This is a component demonstration. No Trailer, Order or other record will change.</p></ConfirmDialog>}
  </div>
}
