import { PageHeader } from '../../shared/ui/components'
import { useCustomerContext } from './context'
import { CustomerOrderDashboard } from './OrdersList'
import { CustomerDashboardSummaries } from './CustomerReadViews'

export function CustomerDashboard() {
  const { membership, customer } = useCustomerContext()
  return <section><PageHeader title="Customer dashboard" description={`Workspace for ${customer.name} in ${membership.tenantName}.`} />
    <p>Your Customer context was verified by the server. Navigation does not grant access to commercial actions.</p>
    <CustomerOrderDashboard /><CustomerDashboardSummaries />
  </section>
}
