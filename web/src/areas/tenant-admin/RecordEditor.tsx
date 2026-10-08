import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiProblem } from '../../shared/api/client'
import { useAuth } from '../../shared/auth/context'
import { ConfirmDialog, ErrorBanner, FormField } from '../../shared/ui/components'
import { useAdminTenant } from './tenant'
import { definitions, formValues, requestValues, valueAt } from './resources'
import type { ResourceKind } from './resources'
import type { Customer } from './contracts'

export function RecordEditor({ kind, path, record, customers = [], reload, created }: {
  kind: ResourceKind; path: string; record?: object; customers?: Customer[]; reload?: () => Promise<object | undefined>; created?: (record: object) => void
}) {
  const { api } = useAuth()
  const { tenantId } = useAdminTenant()
  const cache = useQueryClient()
  // Editing snapshot remains fixed until successful save or explicit Reload latest.
  const [baseline, setBaseline] = useState(record)
  const [confirm, setConfirm] = useState(false)
  const [saved, setSaved] = useState(false)
  const form = useForm<Record<string, string>>({ defaultValues: formValues(kind, baseline) })
  const mutation = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.request<object>(`/tenants/${tenantId}${path}`, { method: baseline ? 'PATCH' : 'POST', body }),
    onSuccess(next) {
      setConfirm(false); setSaved(true)
      if (baseline) { setBaseline(next); form.reset(formValues(kind, next)) } else { form.reset(formValues(kind)); created?.(next) }
      void cache.invalidateQueries({ queryKey: ['tenant', tenantId] })
    },
    onError(error) {
      setConfirm(false)
      if (error instanceof ApiProblem && error.problem.code === 'VALIDATION_FAILED') {
        for (const field of error.problem.errors ?? []) {
          if (definitions[kind].fields.some(f => f.key === field.field)) form.setError(field.field.replace('.', '_'), { message: field.message })
        }
      }
    },
  })
  const stale = mutation.error instanceof ApiProblem && mutation.error.problem.code === 'STALE_VERSION'
  const reloadLatest = async () => {
    const latest = await reload?.()
    if (latest) { setBaseline(latest); form.reset(formValues(kind, latest)); mutation.reset(); setSaved(false) }
  }
  const submit = form.handleSubmit(values => {
    const result = requestValues(kind, values, baseline)
    for (const [key, message] of Object.entries(result.errors)) form.setError(key.replace('.', '_'), { message })
    if (Object.keys(result.errors).length === 0) { setSaved(false); mutation.mutate(result.body) }
  })
  const active = baseline ? (kind === 'customers' ? valueAt(baseline, 'status') === 'ACTIVE' : valueAt(baseline, 'active')) : false
  const changeActive = () => mutation.mutate({ expectedVersion: valueAt(baseline!, 'version'),
    ...(kind === 'customers' ? { status: active ? 'INACTIVE' : 'ACTIVE' } : { active: !active }) })
  return <section className="record-editor">
    <h2>{baseline ? `Edit ${definitions[kind].singular}` : `Create ${definitions[kind].singular}`}</h2>
    {mutation.isError && <ErrorBanner error={mutation.error} reload={() => void reloadLatest()}
      retry={mutation.error instanceof ApiProblem && mutation.error.problem.status >= 500 && mutation.variables
        ? () => mutation.mutate(mutation.variables!) : undefined} />}
    {saved && <p role="status">Saved.</p>}
    <form onSubmit={submit} noValidate aria-label={`${baseline ? 'Edit' : 'Create'} ${definitions[kind].singular}`}>
      <div className="form-grid">{definitions[kind].fields.filter(field => !(baseline && field.createOnly)).map(field => {
        const id = `${kind}-${field.key.replace('.', '-')}`
        const error = form.formState.errors[field.key.replace('.', '_')]?.message
        const props = { id, 'aria-required': Boolean(field.required), 'aria-invalid': Boolean(error), 'aria-describedby': error ? `${id}-error` : undefined, ...form.register(field.key.replace('.', '_')) }
        return <FormField key={field.key} id={id} label={field.label} error={error}>
          {field.type === 'select' ? <select {...props}>
            {!field.required && <option value="">None</option>}
            {(field.reference ? customers.map(customer => ({ value: customer.id, label: customer.name })) : field.choices?.map(choice => ({ value: choice, label: choice })) ?? []).map(option =>
              <option key={option.value} value={option.value}>{option.label}</option>)}
          </select> : field.type === 'textarea' ? <textarea {...props} rows={3} /> : <input {...props} type={field.type ?? 'text'} step={field.type === 'number' ? 'any' : undefined} />}
        </FormField>
      })}</div>
      <div className="actions"><button disabled={mutation.isPending || stale}>{mutation.isPending ? 'Saving…' : baseline ? 'Save changes' : 'Create'}</button>
        {baseline && kind !== 'settings' && <button type="button" disabled={mutation.isPending || stale}
          onClick={() => active ? setConfirm(true) : changeActive()}>{active ? 'Deactivate' : 'Reactivate'}</button>}
      </div>
    </form>
    {confirm && <ConfirmDialog title={`Deactivate ${definitions[kind].singular}?`} confirmLabel="Deactivate" busy={mutation.isPending}
      onCancel={() => setConfirm(false)} onConfirm={changeActive}><p>The record and its history will be kept.</p></ConfirmDialog>}
  </section>
}
