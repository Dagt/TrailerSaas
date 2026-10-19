import type { PropsWithChildren } from 'react'
import { PageHeader, StatusBadge } from '../../shared/ui/components'
import { human } from './display'

export function OperationsHeader({ title, description, eyebrow = 'Transport operations', children }: PropsWithChildren<{ title: string; description?: string; eyebrow?: string }>) {
  return <header className="ops-heading"><p className="ops-eyebrow">{eyebrow}</p><PageHeader title={title} description={description}>{children && <div className="ops-heading-actions">{children}</div>}</PageHeader></header>
}

/** Display only. Raw domain values remain authoritative for all actions. */
export function OperationsStatus({ value, label = human(value) }: { value: string; label?: string }) {
  const tone = ['UNKNOWN', 'LOCATION_UNKNOWN'].includes(value) ? 'unknown'
    : ['CANCELLING', 'INCOMPLETE_ASSIGNMENT', 'OUT_OF_SERVICE', 'WARNING', 'OPEN'].includes(value) ? 'attention'
      : ['IN_TRANSIT', 'AT_STOP', 'EN_ROUTE', 'IN_PROGRESS', 'PARTIALLY_DELIVERED', 'ACKNOWLEDGED'].includes(value) ? 'pending'
        : ['COMPLETED', 'RESOLVED', 'IN_SERVICE', 'ACTIVE'].includes(value) ? 'success' : 'neutral'
  return <StatusBadge tone={tone}>{label}</StatusBadge>
}
