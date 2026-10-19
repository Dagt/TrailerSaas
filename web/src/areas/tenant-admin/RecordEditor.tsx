import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiProblem } from '../../shared/api/client'
import { useAuth } from '../../shared/auth/context'
import { Button, ConfirmDialog, ErrorBanner, FormField } from '../../shared/ui/components'
import { adminFieldHints, fieldSection } from './presentation'
import { useAdminTenant } from './tenant'
import { definitions, formValues, requestValues, valueAt } from './resources'
import type { ResourceKind } from './resources'
import type { Customer } from './contracts'

export function RecordEditor({ kind, path, record, customers = [], reload, created, reconcile }: {
  kind: ResourceKind; path: string; record?: object; customers?: Customer[]; reload?: () => Promise<object | undefined>; created?: (record: object) => void; reconcile?: () => Promise<unknown>
}) {
  const { api } = useAuth()
  const { tenantId } = useAdminTenant()
  const cache = useQueryClient()
  // Editing snapshot remains fixed until successful save or explicit Reload latest.
  const [baseline, setBaseline] = useState(record)
  const [confirm, setConfirm] = useState(false)
  const [saved, setSaved] = useState(false)
  const [checking, setChecking] = useState(false)
  const [reconcileError, setReconcileError] = useState<unknown>()
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
  const uncertainCreate = !baseline && mutation.error instanceof ApiProblem && mutation.error.problem.status >= 500
  const checkLatest = async () => {
    setChecking(true); setReconcileError(undefined)
    try { await reconcile?.() } catch (error) { setReconcileError(error) } finally { setChecking(false) }
  }
  const reloadLatest = async () => {
    const latest = await reload?.()
    if (latest) { setBaseline(latest); form.reset(formValues(kind, latest)); mutation.reset(); setSaved(false) }
  }
  const submit = form.handleSubmit(values => {
    // A lost creation response may follow a successful commit. Never replay this POST.
    if (uncertainCreate) return
    const result = requestValues(kind, values, baseline)
    for (const [key, message] of Object.entries(result.errors)) form.setError(key.replace('.', '_'), { message })
    if (Object.keys(result.errors).length === 0) { setSaved(false); mutation.mutate(result.body) }
  })
  const active = baseline ? (kind === 'customers' ? valueAt(baseline, 'status') === 'ACTIVE' : valueAt(baseline, 'active')) : false
  const changeActive = () => mutation.mutate({ expectedVersion: valueAt(baseline!, 'version'),
    ...(kind === 'customers' ? { status: active ? 'INACTIVE' : 'ACTIVE' } : { active: !active }) })
  const fields = definitions[kind].fields.filter(field => !(baseline && field.createOnly))
  const sections = [...new Set(fields.map(field => fieldSection(kind, field.key)))]
  return <section className="record-editor">
    <header className="admin-editor-heading"><div><h2>{baseline ? `Edit ${definitions[kind].singular}` : `Create ${definitions[kind].singular}`}</h2>
      <p>{baseline ? 'Review the current values before saving. Existing history will be kept.' : 'Enter the record details. Required fields are identified in their labels.'}</p></div></header>
    {mutation.isError && <ErrorBanner error={mutation.error} reload={() => void reloadLatest()}
      retry={baseline && mutation.error instanceof ApiProblem && mutation.error.problem.status >= 500 && mutation.variables
        ? () => mutation.mutate(mutation.variables!) : undefined} />}
    {uncertainCreate && <div role="status"><p>The creation result is uncertain. Check the latest records below before starting a new create. Your form values have been kept.</p>
      {reconcile && <button type="button" disabled={checking} onClick={() => void checkLatest()}>{checking ? 'Checking…' : 'Check latest records'}</button>}
    </div>}
    {reconcileError !== undefined && <ErrorBanner error={reconcileError} />}
    {saved && <p className="admin-saved" role="status">Saved.</p>}
    <form onSubmit={submit} noValidate aria-label={`${baseline ? 'Edit' : 'Create'} ${definitions[kind].singular}`}>
      {sections.map(section => <fieldset key={section} className="admin-field-section"><legend>{section}</legend><div className="form-grid">{fields.filter(field => fieldSection(kind, field.key) === section).map(field => {
        const id = `${kind}-${field.key.replace('.', '-')}`
        const error = form.formState.errors[field.key.replace('.', '_')]?.message
        const hint = adminFieldHints[field.key]
        const describedBy = [hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ') || undefined
        const props = { id, 'aria-required': Boolean(field.required), 'aria-invalid': Boolean(error), 'aria-describedby': describedBy, ...form.register(field.key.replace('.', '_')) }
        return <FormField key={field.key} id={id} label={field.label} error={error} hint={hint}>
          {field.type === 'select' ? <select {...props}>
            <option value="" disabled={field.required}>{field.required ? field.placeholder ?? `Choose ${field.label.toLowerCase()}` : 'None'}</option>
            {(field.reference ? customers.map(customer => ({ value: customer.id, label: customer.name })) : field.choices?.map(choice => ({ value: choice, label: choice })) ?? []).map(option =>
              <option key={option.value} value={option.value}>{option.label}</option>)}
          </select> : field.type === 'textarea' ? <textarea {...props} rows={3} /> : <input {...props} type={field.type ?? 'text'} step={field.type === 'number' ? 'any' : undefined} />}
        </FormField>
      })}</div></fieldset>)}
      <div className="actions admin-editor-actions"><Button type="submit" variant="primary" loading={mutation.isPending} disabled={mutation.isPending || stale || uncertainCreate}>{mutation.isPending ? 'Saving…' : baseline ? 'Save changes' : 'Create'}</Button>
        {baseline && kind !== 'settings' && <Button variant={active ? 'danger' : 'secondary'} disabled={mutation.isPending || stale}
          onClick={() => active ? setConfirm(true) : changeActive()}>{active ? 'Deactivate' : 'Reactivate'}</Button>}
      </div>
    </form>
    {confirm && <ConfirmDialog className="admin-dialog" title={`Deactivate ${definitions[kind].singular}?`} confirmLabel="Deactivate" busy={mutation.isPending}
      onCancel={() => setConfirm(false)} onConfirm={changeActive}><p>The record and its history will be kept.</p></ConfirmDialog>}
  </section>
}
