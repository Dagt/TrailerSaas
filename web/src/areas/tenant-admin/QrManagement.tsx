import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toDataURL } from 'qrcode'
import { useAuth } from '../../shared/auth/context'
import { readWebEnvironment } from '../../shared/config/environment'
import { ConfirmDialog, DataTable, EmptyState, ErrorBanner, LoadingState } from '../../shared/ui/components'
import { useAdminTenant } from './tenant'
import { useTenantQuery } from './hooks'
import type { Items, QrIdentity } from './contracts'

export function QrManagement({ trailerId, publicBase = readWebEnvironment(import.meta.env).qrPublicBaseUrl }: { trailerId: string; publicBase?: string }) {
  const { api } = useAuth(); const { tenantId } = useAdminTenant(); const cache = useQueryClient()
  const path = `/trailers/${trailerId}/qr-identities`
  const query = useTenantQuery<Items<QrIdentity>>(path)
  const [revoke, setRevoke] = useState<QrIdentity | null>(null)
  const [print, setPrint] = useState<QrIdentity | null>(null)
  const mutation = useMutation({ mutationFn: (qr?: QrIdentity) => api.request<QrIdentity | void>(`/tenants/${tenantId}${path}${qr ? `/${qr.id}/revocation` : ''}`, { method: 'POST' }),
    onSuccess() { setRevoke(null); void cache.invalidateQueries({ queryKey: ['tenant', tenantId, path] }) }, onError() { setRevoke(null) } })
  const active = query.data?.items.some(qr => qr.active)
  return <section><h2>QR identities</h2><p>Replacement requires revoking the current QR before issuing another. QR identifies the Trailer; it does not grant access.</p>
    <button type="button" disabled={query.isPending || query.isError || active || mutation.isPending} onClick={() => mutation.mutate(undefined)}>Issue QR</button>
    {mutation.error && <ErrorBanner error={mutation.error} retry={() => { mutation.reset(); void query.refetch() }} />}
    {!publicBase && <p role="status">Printing is unavailable: a valid public QR base URL has not been configured.</p>}
    {query.isPending ? <LoadingState /> : query.isError ? <ErrorBanner error={query.error} retry={() => void query.refetch()} /> : !query.data.items.length ? <EmptyState>No QR identities issued.</EmptyState>
      : <DataTable caption="QR history" headings={['Status', 'Issued', 'Revoked', 'Actions']}>
        {query.data.items.map(qr => <tr key={qr.id}><td>{qr.active ? 'Active' : 'Revoked'}</td><td>{qr.issuedAt}</td><td>{qr.revokedAt ?? 'Not revoked'}</td><td>
          {qr.active ? <><button type="button" disabled={mutation.isPending} onClick={() => setRevoke(qr)}>Revoke QR</button>
            <button type="button" disabled={!publicBase || mutation.isPending} onClick={() => setPrint(qr)}>Print active QR</button></> : 'Historical'}</td></tr>)}
      </DataTable>}
    {revoke && <ConfirmDialog title="Revoke QR?" confirmLabel="Revoke QR" busy={mutation.isPending} onCancel={() => setRevoke(null)} onConfirm={() => mutation.mutate(revoke)}><p>The printed label will stop resolving. Its history will be kept.</p></ConfirmDialog>}
    {print && publicBase && <QrPrintPreview qr={print} base={publicBase} close={() => setPrint(null)} />}
  </section>
}
export function QrPrintPreview({ qr, base, close }: { qr: QrIdentity; base: string; close(): void }) {
  const [image, setImage] = useState<string>()
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    let live = true
    void toDataURL(qrPayload(base, qr.publicToken)!, { errorCorrectionLevel: 'M', margin: 4, width: 320 })
      .then(url => { if (live) setImage(url) }).catch(() => { if (live) setFailed(true) })
    return () => { live = false }
  }, [base, qr.publicToken])
  return <ConfirmDialog title="QR print preview" confirmLabel="Print" confirmDisabled={!image} onCancel={close} onConfirm={() => window.print()}>
    {failed ? <p role="alert">The label could not be generated.</p> : !image ? <LoadingState /> : <div className="qr-label" aria-label="Trailer label">
      <img src={image} width={320} height={320} alt="Trailer QR code" /><strong>{qr.operationalNumber}</strong>
    </div>}
  </ConfirmDialog>
}
import { qrPayload } from './qrPayload'
