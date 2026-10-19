import type { PropsWithChildren, ReactNode } from 'react'
import { PageHeader, StatusBadge } from '../../shared/ui/components'

export function AdminPageHeader({ title, description, eyebrow = 'Tenant administration', children }: PropsWithChildren<{ title: string; description?: string; eyebrow?: string }>) {
  return <header className="admin-page-heading"><p className="admin-eyebrow">{eyebrow}</p><PageHeader title={title} description={description}>{children && <div className="admin-page-actions">{children}</div>}</PageHeader></header>
}

export function AdminPanel({ title, description, actions, children, className = '' }: PropsWithChildren<{ title: string; description?: string; actions?: ReactNode; className?: string }>) {
  return <section className={`admin-panel ${className}`}><header className="admin-panel-heading"><div><h2>{title}</h2>{description && <p>{description}</p>}</div>{actions && <div className="admin-panel-actions">{actions}</div>}</header>{children}</section>
}

/** Visual semantics only; never used to decide access or a permitted action. */
export function AdminStatus({ value }: { value: string }) {
  const tone = ['Active', 'IN_SERVICE', 'RESOLVED'].includes(value) ? 'success'
    : ['OUT_OF_SERVICE', 'WARNING', 'OPEN', 'Expired', 'Withheld'].includes(value) ? 'attention'
      : ['UNKNOWN', 'LOCATION_UNKNOWN'].includes(value) ? 'unknown'
        : ['IN_TRANSIT', 'ACKNOWLEDGED'].includes(value) ? 'pending' : 'neutral'
  return <StatusBadge tone={tone}>{value}</StatusBadge>
}

