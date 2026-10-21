import type { ComponentProps, PropsWithChildren, ReactNode } from 'react'
import { displayTime } from '../../shared/ui/dateTime'
import { Card, DataTable, PageHeader, StatusBadge } from '../../shared/ui/components'
import type { CargoInput } from './orderContracts'

export function CustomerHeader({ title, description, children }: PropsWithChildren<{title:string;description?:string}>) {
  return <header className="customer-heading"><p className="customer-eyebrow">Customer workspace</p><PageHeader title={title} description={description}>{children && <div className="customer-heading-actions">{children}</div>}</PageHeader></header>
}

export function CustomerPanel({ title, description, actions, children, className = '' }: PropsWithChildren<{title:string;description?:string;actions?:ReactNode;className?:string}>) {
  return <Card title={title} description={description} actions={actions} className={`customer-panel ${className}`}>{children}</Card>
}

export function CustomerTime({ value }: {value:string|null}) {
  return value ? <time dateTime={value} title={`${value} · Displayed in your browser's local time`}>{displayTime(value)}</time> : <>Not recorded</>
}

export function CustomerCargo({ lines }: {lines:CargoInput[]}) {
  return <ul className="customer-cargo">{lines.map((line,index)=><li key={index}><strong>{line.description}</strong><dl className="customer-cargo-facts"><div><dt>Quantity</dt><dd>{line.quantity??'Unspecified'}</dd></div><div><dt>Pallets</dt><dd>{line.palletCount??'Unspecified'}</dd></div><div><dt>Weight</dt><dd>{line.weight??'Unspecified'} {line.weightUnit}</dd></div></dl></li>)}</ul>
}

export function CustomerTable(props:ComponentProps<typeof DataTable>) {
  return <DataTable {...props}/>
}

/** Presentation only. Domain values and authorization predicates remain unchanged. */
export function CustomerStatus({ value }: {value:string}) {
  const tone = ['UNKNOWN','LOCATION_UNKNOWN'].includes(value) ? 'unknown'
    : ['CANCELLING','EXCEPTION','OUT_OF_SERVICE'].includes(value) ? 'attention'
      : ['IN_PROGRESS','PARTIALLY_DELIVERED','IN_TRANSIT','EN_ROUTE','ARRIVED','IN_SERVICE'].includes(value) ? 'pending'
        : ['COMPLETED','ACTIVE'].includes(value) ? 'success' : 'neutral'
  return <StatusBadge tone={tone}>{value}</StatusBadge>
}

