import { useState } from 'react'
import { z } from 'zod'
import type { Customer, Items, Site, Location } from './contracts'
import { useTenantQuery } from './hooks'
import { operationalNumber } from './resources'

const empty = { operationalNumber: '', designatedCustomerId: '', locationState: '', currentLocationId: '', loadState: '', serviceabilityState: '', active: '' }
export function TrailerFilters({ customers, onApply }: { customers: Customer[]; onApply(query: string): void }) {
  const [values, setValues] = useState(empty)
  const [site, setSite] = useState('')
  const [invalid, setInvalid] = useState(false)
  const sites = useTenantQuery<Items<Site>>('/sites')
  const locations = useTenantQuery<Items<Location>>(`/sites/${site}/locations`, Boolean(site))
  const apply = (filters = values) => {
    if (filters.operationalNumber && !operationalNumber.safeParse(filters.operationalNumber).success) { setInvalid(true); return }
    for (const id of [filters.designatedCustomerId, filters.currentLocationId]) {
      if (id && !z.uuid().safeParse(id).success) { setInvalid(true); return }
    }
    const query = new URLSearchParams(Object.entries(filters).filter(([, value]) => value !== ''))
    setInvalid(false); onApply(query.size ? `?${query}` : '')
  }
  const select = (key: keyof typeof empty, label: string, options: { value: string; label: string }[]) => <label key={key} htmlFor={`filter-${key}`}>{label}
    <select id={`filter-${key}`} value={values[key]} onChange={event => setValues({ ...values, [key]: event.target.value })}>
      <option value="">All</option>{options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select></label>
  const choices = (values: string[]) => values.map(value => ({ value, label: value }))
  return <form className="filters" aria-label="Trailer filters" onSubmit={event => { event.preventDefault(); apply() }}>
    <label htmlFor="filter-number">Operational number <input id="filter-number" value={values.operationalNumber} onChange={event => setValues({ ...values, operationalNumber: event.target.value })} /></label>
    {select('designatedCustomerId', 'Designated Customer filter', customers.map(c => ({ value: c.id, label: c.name })))}
    {select('locationState', 'Location state', choices(['AT_LOCATION', 'IN_TRANSIT', 'LOCATION_UNKNOWN']))}
    <label htmlFor="filter-site">Site for location filter <select id="filter-site" value={site} onChange={event => { setSite(event.target.value); setValues({ ...values, currentLocationId: '' }) }}>
      <option value="">Choose a Site</option>{sites.data?.items.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
    </select></label>
    {select('currentLocationId', 'Current location', locations.data?.items.map(l => ({ value: l.id, label: l.name })) ?? [])}
    {(sites.isError || locations.isError) && <p role="status">Location filter choices are currently unavailable.</p>}
    {select('loadState', 'Load state', choices(['EMPTY', 'LOADED', 'UNKNOWN']))}
    {select('serviceabilityState', 'Serviceability', choices(['IN_SERVICE', 'OUT_OF_SERVICE']))}
    {select('active', 'Active filter', [{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }])}
    <div className="actions"><button>Apply filters</button>
      <button type="button" onClick={() => { const next = { ...values, serviceabilityState: 'OUT_OF_SERVICE' }; setValues(next); apply(next) }}>Out of Service</button>
      <button type="button" onClick={() => { const next = { ...values, locationState: 'LOCATION_UNKNOWN' }; setValues(next); apply(next) }}>Location Unknown</button>
      <button type="button" onClick={() => { setValues(empty); setSite(''); apply(empty) }}>Clear filters</button></div>
    {invalid && <p role="alert">Check the filter values.</p>}
  </form>
}
