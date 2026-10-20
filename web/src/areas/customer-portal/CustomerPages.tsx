import { Link } from 'react-router'
import { CustomerHeader } from './CustomerPresentation'
import { useCustomerContext } from './context'
import { CustomerOrderDashboard } from './OrdersList'
import { CustomerDashboardSummaries } from './CustomerReadViews'

export function CustomerDashboard() {
  const { membership, customer } = useCustomerContext()
  return <section><CustomerHeader title="Customer dashboard" description={`Workspace for ${customer.name} in ${membership.tenantName}.`} />
    <div className="customer-dashboard-heading"><div><h2>{customer.name}</h2><p>Review your Orders, follow delivery progress and find accepted evidence in one Customer workspace.</p></div><Link className="button-link" to="/customer/orders">View Orders</Link></div>
    <CustomerOrderDashboard /><CustomerDashboardSummaries />
  </section>
}
