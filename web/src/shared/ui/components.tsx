import { useEffect, useId, useRef, useState } from 'react'
import type { ButtonHTMLAttributes, PropsWithChildren, ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { ApiProblem } from '../api/client'
import './foundation.css'

export function PageHeader({ title, description, children }: PropsWithChildren<{ title: string; description?: string }>) {
  return <div className="page-header"><div><h1>{title}</h1>{description && <p>{description}</p>}</div>{children}</div>
}
export function LoadingState() { return <p className="ui-loading" role="status"><span className="ui-spinner" aria-hidden="true" />Loading…</p> }
export function EmptyState({ children }: PropsWithChildren) { return <div className="empty-state">{children}</div> }
export function StatusBadge({ children, tone = 'neutral' }: PropsWithChildren<{ tone?: 'neutral' | 'success' | 'attention' | 'danger' | 'unknown' | 'pending' }>) { return <span className="status-badge" data-tone={tone}>{children}</span> }
export function DataTable({ headings, children, caption, density = 'comfortable' }: PropsWithChildren<{ headings: string[]; caption: string; density?: 'comfortable' | 'compact' }>) {
  const viewport = useRef<HTMLDivElement>(null), hint = useId()
  const [overflowing, setOverflowing] = useState(false)
  useEffect(() => {
    const element = viewport.current
    if (!element) return
    const measure = () => setOverflowing(element.scrollWidth > element.clientWidth + 1)
    const observer = typeof ResizeObserver === 'undefined' ? undefined : new ResizeObserver(measure)
    observer?.observe(element)
    const table = element.querySelector('table')
    if (table) observer?.observe(table)
    window.addEventListener('resize', measure)
    measure()
    return () => { observer?.disconnect(); window.removeEventListener('resize', measure) }
  }, [])
  return <div ref={viewport} className="table-scroll" data-density={density} role="region" aria-label={caption} aria-describedby={overflowing ? hint : undefined} tabIndex={0}>
    {overflowing && <p className="table-scroll-hint" id={hint}><span aria-hidden="true">↔</span> Scroll horizontally to see all columns. Keyboard: focus this table and use the arrow keys.</p>}
    <table><caption className="sr-only">{caption}</caption><thead><tr>{headings.map(h => <th key={h} scope="col">{h}</th>)}</tr></thead><tbody>{children}</tbody></table></div>
}
export function ErrorBanner({ error, retry, reload, staleMessage, title }: { error: unknown; retry?: () => void; reload?: () => void; staleMessage?: string; title?: string }) {
  const titleId = useId()
  const problem = error instanceof ApiProblem ? error.problem : undefined
  const stale = problem?.code === 'STALE_VERSION'
  let message = 'The request could not be completed. Please try again.'
  if (stale) message = staleMessage ?? 'This record changed while you were editing it. Review the latest version before saving again.'
  else if (problem?.code === 'INVALID_STATE_TRANSITION') message = 'This action is not available in the current state. Reload the latest state before continuing.'
  else if (problem?.status === 401) message = 'Your session needs to be verified. Sign in or refresh your session.'
  else if (problem?.status === 403) message = 'You do not have access to this action.'
  else if (problem?.status === 404) message = 'The resource was not found.'
  else if (problem?.code === 'RESOURCE_CONFLICT') message = 'This change conflicts with existing data. Review the values before trying again.'
  else if (problem?.code === 'VALIDATION_FAILED') message = 'Please check the highlighted fields.'
  else if (problem && problem.status >= 500) message = 'The service is temporarily unavailable. Please try again.'
  return <div className="error-banner" role="alert" aria-labelledby={title ? titleId : undefined}>{title && <h2 id={titleId}>{title}</h2>}<p>{message}</p>
    {problem?.traceId && <p className="support-reference">Support reference: {problem.traceId}</p>}
    {stale && reload ? <button type="button" onClick={reload}>Reload latest</button> : retry && <button type="button" onClick={retry}>Retry</button>}
  </div>
}
export function FormField({ id, label, error, children, hint }: PropsWithChildren<{ id: string; label: string; error?: string; hint?: string }>) {
  return <div className="form-field"><label htmlFor={id}>{label}</label>{children}
    {hint && <small id={`${id}-hint`}>{hint}</small>}{error && <span id={`${id}-error`} className="field-error" role="alert">{error}</span>}
  </div>
}
export function ConfirmDialog({ title, children, confirmLabel = 'Confirm', onConfirm, onCancel, busy = false, confirmDisabled = false, className }: PropsWithChildren<{
  title: string; confirmLabel?: string; onConfirm(): void; onCancel(): void; busy?: boolean; confirmDisabled?: boolean; className?: string
}>) {
  const dialog = useRef<HTMLDialogElement>(null)
  const cancel = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const element = dialog.current
    if (element?.showModal) element.showModal()
    else element?.setAttribute('open', '')
    cancel.current?.focus()
    return () => {
      // Release native modal inertness before restoring focus to the trigger.
      if (element?.open && element.close) element.close()
      previous?.focus()
    }
  }, [])
  return createPortal(<dialog ref={dialog} className={className} aria-labelledby="confirmation-title" onKeyDown={event => { if (event.key === 'Escape' && !busy) { event.preventDefault(); onCancel() } }} onCancel={event => { event.preventDefault(); if (!busy) onCancel() }}>
    <h2 id="confirmation-title">{title}</h2>{children}<div className="actions">
      <button ref={cancel} type="button" onClick={onCancel} disabled={busy}>Cancel</button>
      <button type="button" className="danger" onClick={onConfirm} disabled={busy || confirmDisabled}>{confirmLabel}</button>
    </div></dialog>, document.body)
}
export function DetailList({ items }: { items: [string, ReactNode][] }) {
  return <dl className="detail-list">{items.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? 'Not provided'}</dd></div>)}</dl>
}

/** Visual variants never infer a business outcome or grant permission. */
export function Button({ variant = 'secondary', density = 'comfortable', loading = false, disabled, className = '', children, type = 'button', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'subtle' | 'danger'; density?: 'comfortable' | 'compact'; loading?: boolean
}) {
  return <button {...props} type={type} disabled={disabled || loading} aria-busy={loading || undefined} className={`ui-button ui-button--${variant} ui-button--${density} ${className}`}>
    {loading && <span className="ui-spinner" aria-hidden="true" />}{children}
  </button>
}
export function Card({ title, description, children, density = 'comfortable', actions, className }: PropsWithChildren<{ title: string; description?: string; density?: 'comfortable' | 'compact'; actions?: ReactNode; className?: string }>) {
  return <section className={`ui-card ui-card--${density}${className ? ` ${className}` : ''}`}><header className="ui-card-header"><div><h2>{title}</h2>{description && <p>{description}</p>}</div>{actions && <div className="ui-card-actions">{actions}</div>}</header>{children}</section>
}
export function FilterPanel({ legend = 'Filters', children, density = 'comfortable' }: PropsWithChildren<{ legend?: string; density?: 'comfortable' | 'compact' }>) {
  return <fieldset className={`ui-filter-panel ui-filter-panel--${density}`}><legend>{legend}</legend><div className="ui-filter-fields">{children}</div></fieldset>
}
export function AccessDeniedState({ children }: PropsWithChildren) {
  return <section className="ui-notice ui-notice--denied"><h2>Access not available</h2><p>{children ?? 'You do not have access to this record.'}</p></section>
}
export function OfflineState({ children }: PropsWithChildren) {
  return <div className="ui-notice ui-notice--offline" role="status"><h2>Pending synchronization</h2><p>{children ?? 'Saved on device. Waiting to sync.'}</p></div>
}
export function Timeline({ children, label }: PropsWithChildren<{ label: string }>) {
  return <ol className="ui-timeline" aria-label={label}>{children}</ol>
}
export function TimelineEvent({ title, dateTime, timeLabel, source, children }: PropsWithChildren<{ title: string; dateTime: string; timeLabel: string; source?: string }>) {
  return <li><time dateTime={dateTime}>{timeLabel}</time><h3>{title}</h3>{source && <span className="ui-timeline-meta">{source}</span>}{children}</li>
}
