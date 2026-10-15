import { Link, useSearchParams } from 'react-router'
import { PageHeader, LoadingState, ErrorBanner, DataTable } from '../../shared/ui/components'
import { orderStatuses } from '../transport-operations/contracts'
import { useOrderActions, useOrders } from './orderQueries'
import type { OrderRow } from './orderContracts'

export function OrderRows({rows}:{rows:OrderRow[]}) {
  if(!rows.length) return <p>No Orders found in this Customer scope.</p>
  if(new Set(rows.map(r=>r.id)).size!==rows.length) return <p role="alert">The page changed. Refresh the list before continuing.</p>
  return <DataTable caption="Customer Orders" headings={['Order','PO','Shipment','BOL','State','Planning / progress','Created','Updated']}>
    {rows.map(r=><tr key={r.id}><td><Link to={`/customer/orders/${r.id}`}>{r.orderNumber}</Link></td><td>{r.poNumber??'—'}</td><td>{r.shipmentNumber??'—'}</td><td>{r.bolReference??'—'}</td><td>{r.status}</td><td><Link to={`/customer/orders/${r.id}`}>View actual work</Link></td><td>{r.createdAt}</td><td>{r.updatedAt}</td></tr>)}
  </DataTable>
}
export function OrdersList() {
  const [search,setSearch]=useSearchParams(), actions=useOrderActions()
  const status=orderStatuses.find(s=>s===search.get('status'))??'', unplanned=['true','false'].includes(search.get('unplanned')??'')?search.get('unplanned')!:''
  const filters={limit:'25',...(status?{status}:{}),...(unplanned?{unplanned}:{})}, orders=useOrders(filters)
  const change=(key:string,value:string)=>{const next=new URLSearchParams();if(status)next.set('status',status);if(unplanned)next.set('unplanned',unplanned);if(value)next.set(key,value);else next.delete(key);setSearch(next)}
  return <section><PageHeader title="Orders" description="Requests for the active Customer. Physical delivery and administrative completion are separate.">
    {actions.data?.capabilities.includes('ORDER_CREATE')&&<Link className="button-link" to="/customer/orders/new">Create Order</Link>}
  </PageHeader><div className="filters"><label>State <select aria-label="Order state" value={status} onChange={e=>change('status',e.target.value)}><option value="">All states</option>{orderStatuses.map(s=><option key={s}>{s}</option>)}</select></label>
    <label>Planning <select aria-label="Order planning" value={unplanned} onChange={e=>change('unplanned',e.target.value)}><option value="">All</option><option value="true">No active Trip</option><option value="false">Other planning states</option></select></label><button onClick={()=>void orders.refetch()}>Refresh Orders</button></div>
    {actions.isError&&<ErrorBanner error={actions.error} retry={()=>void actions.refetch()}/>}
    {orders.isPending?<LoadingState/>:orders.isError?<ErrorBanner error={orders.error} retry={()=>void orders.refetch()}/>:<><OrderRows rows={orders.data.pages.flatMap(p=>p.items)}/>
      {orders.hasNextPage&&<button disabled={orders.isFetchingNextPage} onClick={()=>void orders.fetchNextPage()}>Load more Orders</button>}</>}
  </section>
}
export function CustomerOrderDashboard() {
  return <div className="customer-cards">{[
    ['Pending Orders','OPEN'],['Planned Orders','PLANNED'],['Orders in execution','IN_PROGRESS'],['Partially delivered Orders','PARTIALLY_DELIVERED'],['Delivered — awaiting closure','DELIVERED'],['Orders in cancellation','CANCELLING'],
  ].map(([title,status])=><DashboardPreview key={status} title={title!} status={status!}/>)}<DashboardPreview title="Recent Order requests"/></div>
}
function DashboardPreview({title,status}:{title:string;status?:string}) {
  const query=useOrders({limit:'5',...(status?{status}:{})})
  return <article><h2><Link to={`/customer/orders${status?`?status=${status}`:''}`}>{title}</Link></h2><p>Recent requests by creation time. Preview, not a total count.</p>
    {query.isPending?<LoadingState/>:query.isError?<ErrorBanner error={query.error} retry={()=>void query.refetch()}/>:<ul>{query.data.pages[0]?.items.length?query.data.pages[0].items.map(r=><li key={r.id}><Link to={`/customer/orders/${r.id}`}>{r.orderNumber}</Link> — {r.status} · Updated {r.updatedAt}</li>):<li>No matching Orders reported.</li>}</ul>}
  </article>
}
