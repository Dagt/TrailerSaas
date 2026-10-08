import { useEffect, useRef } from 'react'
import type { PropsWithChildren, ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { ApiProblem } from '../api/client'

export function PageHeader({ title, description, children }: PropsWithChildren<{ title: string; description?: string }>) {
  return <div className="page-header"><div><h1>{title}</h1>{description && <p>{description}</p>}</div>{children}</div>
}
export function LoadingState() { return <p role="status">Loading…</p> }
export function EmptyState({ children }: PropsWithChildren) { return <div className="empty-state">{children}</div> }
export function StatusBadge({ children }: PropsWithChildren) { return <span className="status-badge">{children}</span> }
export function DataTable({ headings, children, caption }: PropsWithChildren<{ headings: string[]; caption: string }>) {
  return <div className="table-scroll"><table><caption className="sr-only">{caption}</caption><thead><tr>{headings.map(h => <th key={h} scope="col">{h}</th>)}</tr></thead><tbody>{children}</tbody></table></div>
}
export function ErrorBanner({ error, retry, reload }: { error: unknown; retry?: () => void; reload?: () => void }) {
  const problem = error instanceof ApiProblem ? error.problem : undefined
  const stale = problem?.code === 'STALE_VERSION'
  let message = 'The request could not be completed. Please try again.'
  if (stale) message = 'This record changed while you were editing it. Review the latest version before saving again.'
  else if (problem?.status === 401) message = 'Your session needs to be verified. Sign in or refresh your session.'
  else if (problem?.status === 403) message = 'You do not have access to this action.'
  else if (problem?.status === 404) message = 'The resource was not found.'
  else if (problem?.code === 'RESOURCE_CONFLICT') message = 'This change conflicts with existing data. Review the values before trying again.'
  else if (problem?.code === 'VALIDATION_FAILED') message = 'Please check the highlighted fields.'
  else if (problem && problem.status >= 500) message = 'The service is temporarily unavailable. Please try again.'
  return <div className="error-banner" role="alert"><p>{message}</p>
    {problem?.traceId && <p className="support-reference">Support reference: {problem.traceId}</p>}
    {stale && reload ? <button type="button" onClick={reload}>Reload latest</button> : retry && <button type="button" onClick={retry}>Retry</button>}
  </div>
}
export function FormField({ id, label, error, children, hint }: PropsWithChildren<{ id: string; label: string; error?: string; hint?: string }>) {
  return <div className="form-field"><label htmlFor={id}>{label}</label>{children}
    {hint && <small id={`${id}-hint`}>{hint}</small>}{error && <span id={`${id}-error`} className="field-error" role="alert">{error}</span>}
  </div>
}
export function ConfirmDialog({ title, children, confirmLabel = 'Confirm', onConfirm, onCancel, busy = false, confirmDisabled = false }: PropsWithChildren<{
  title: string; confirmLabel?: string; onConfirm(): void; onCancel(): void; busy?: boolean; confirmDisabled?: boolean
}>) {
  const dialog = useRef<HTMLDialogElement>(null)
  const cancel = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    if (dialog.current?.showModal) dialog.current.showModal()
    else dialog.current?.setAttribute('open', '')
    cancel.current?.focus()
    return () => { previous?.focus() }
  }, [])
  return createPortal(<dialog ref={dialog} aria-labelledby="confirmation-title" onKeyDown={event => { if (event.key === 'Escape' && !busy) { event.preventDefault(); onCancel() } }} onCancel={event => { event.preventDefault(); if (!busy) onCancel() }}>
    <h2 id="confirmation-title">{title}</h2>{children}<div className="actions">
      <button ref={cancel} type="button" onClick={onCancel} disabled={busy}>Cancel</button>
      <button type="button" className="danger" onClick={onConfirm} disabled={busy || confirmDisabled}>{confirmLabel}</button>
    </div></dialog>, document.body)
}
export function DetailList({ items }: { items: [string, ReactNode][] }) {
  return <dl className="detail-list">{items.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? 'Not provided'}</dd></div>)}</dl>
}
