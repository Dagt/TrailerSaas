import { ErrorBanner } from '../../shared/ui/components'
interface ReadState { isPending: boolean; isError: boolean; error: unknown; refetch(): Promise<unknown> }
export function MasterDataStatus({ queries }: { queries: ReadState[] }) {
  return <div className="ops-resource-feedback">{queries.some(q => q.isPending) && <p role="status">Loading resource names…</p>}{queries.filter(q => q.isError).map((q, i) => <div key={i}><p>Resource options could not be loaded.</p><ErrorBanner error={q.error} retry={() => void q.refetch()} /></div>)}</div>
}
