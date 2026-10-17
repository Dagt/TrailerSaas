import { useState } from 'react'
import { ConfirmDialog, DataTable, DetailList, EmptyState, FormField, LoadingState, PageHeader, StatusBadge } from '../shared/ui/components'

/** Uses only the pre-UX-01 API, so baseline and foundation run identical fixtures. */
export function SharedComparison() {
  const [open, setOpen] = useState(false)
  return <main><PageHeader title="Shared component comparison" description="DEMO DATA · Static shared-component harness, not an operational workspace." />
    <section className="dispatch-card"><h2>Trailer inventory</h2><div className="form-grid"><FormField id="comparison-number" label="Trailer number"><input id="comparison-number" defaultValue="DEMO-1042" /></FormField><FormField id="comparison-state" label="Location state"><select id="comparison-state"><option>All states</option></select></FormField></div><div className="actions"><button type="submit">Primary action</button><button type="button" onClick={() => setOpen(true)}>Preview confirmation</button><button type="button" disabled>Disabled</button></div></section>
    <DataTable headings={['Trailer', 'Current location', 'Serviceability']} caption="Sample inventory comparison"><tr><th scope="row">DEMO-1042</th><td>North Yard · A12</td><td><StatusBadge>IN_SERVICE</StatusBadge></td></tr><tr><th scope="row">DEMO-1086</th><td><StatusBadge>LOCATION_UNKNOWN</StatusBadge></td><td><StatusBadge>OOS</StatusBadge></td></tr></DataTable>
    <section className="dispatch-card"><h2>Recorded location</h2><DetailList items={[["Current location", 'Location unknown'], ['Last known location', 'North Yard · B04'], ['Observation', 'Oct 16, 2026 · 2:00 PM UTC']]} /></section>
    <LoadingState /><EmptyState>No additional sample records.</EmptyState>
    {open && <ConfirmDialog title="Preview confirmation" confirmLabel="Confirm preview" onConfirm={() => setOpen(false)} onCancel={() => setOpen(false)}><p>Component example only. No record will change.</p></ConfirmDialog>}
  </main>
}
