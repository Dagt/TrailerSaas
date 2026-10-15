import { Link } from 'react-router'
import { PageHeader } from '../../shared/ui/components'
import { useCustomerContext } from './context'

const sections = [
  { label: 'Pending Orders', path: 'orders', description: 'Order management will be available in a later phase.' },
  { label: 'Active Deliveries', path: 'deliveries', description: 'Customer delivery views will be available in a later phase.' },
  { label: 'Trailer Inventory', path: 'trailers', description: 'Customer Trailer inventory will be available in a later phase.' },
  { label: 'Recent Evidence', path: 'documents', description: 'Authorized delivery evidence will be available in a later phase.' },
]
export function CustomerDashboard() {
  const { membership, customer } = useCustomerContext()
  return <section><PageHeader title="Customer dashboard" description={`Workspace for ${customer.name} in ${membership.tenantName}.`} />
    <p>Your Customer context was verified by the server. Navigation does not grant access to commercial actions.</p>
    <div className="customer-cards">{sections.map(s => <article key={s.path}><h2><Link to={`/customer/${s.path}`}>{s.label}</Link></h2><p>{s.description}</p><p>Operational data is not loaded in this phase.</p></article>)}</div>
  </section>
}
export function CustomerSection({ section }: { section: string }) {
  const { customer } = useCustomerContext(), item = sections.find(s => s.path === section)!
  return <section><PageHeader title={section.charAt(0).toUpperCase()+section.slice(1)} description={`${customer.name} Customer workspace`} /><p>{item.description}</p><Link to="/customer">Return to dashboard</Link></section>
}
