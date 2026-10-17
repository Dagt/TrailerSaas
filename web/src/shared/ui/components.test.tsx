import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiProblem } from '../api/client'
import { AccessDeniedState, Button, Card, ConfirmDialog, DataTable, DetailList, EmptyState, ErrorBanner, FilterPanel, FormField, LoadingState, OfflineState, StatusBadge, Timeline, TimelineEvent } from './components'

describe('Fleet Simple compatible primitives', () => {
  it('retains native button props, explicit submission and keyboard action', async () => {
    const click = vi.fn(), user = userEvent.setup()
    render(<Button variant="primary" type="submit" onClick={click} aria-label="Save Customer">Save</Button>)
    const button = screen.getByRole('button', { name: 'Save Customer' })
    expect(button).toHaveAttribute('type', 'submit')
    button.focus(); await user.keyboard('{Enter}')
    expect(click).toHaveBeenCalledOnce()
  })
  it.each(['primary', 'secondary', 'subtle', 'danger'] as const)('supports %s without inferring business state', variant => {
    render(<Button variant={variant}>Confirm arrival</Button>)
    expect(screen.getByRole('button')).toHaveClass(`ui-button--${variant}`)
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button')
  })
  it('busy and disabled controls cannot invoke callbacks and keep their accessible name', async () => {
    const click = vi.fn(), user = userEvent.setup()
    render(<><Button loading onClick={click}>Save</Button><Button disabled onClick={click}>Delete</Button></>)
    expect(screen.getByRole('button', { name: 'Save' })).toHaveAttribute('aria-busy', 'true')
    await user.click(screen.getByText('Save')); await user.click(screen.getByText('Delete'))
    expect(click).not.toHaveBeenCalled()
  })
  it('preserves table caption, column headings, row headings and keyboard overflow access', () => {
    render(<DataTable headings={['Trailer', 'Location']} caption="Authorized inventory" density="compact"><tr><th scope="row">T-1</th><td>Location unknown</td></tr></DataTable>)
    expect(screen.getByRole('table', { name: 'Authorized inventory' })).toBeInTheDocument()
    expect(screen.getAllByRole('columnheader')).toHaveLength(2)
    expect(screen.getByRole('rowheader')).toHaveTextContent('T-1')
    expect(screen.getByRole('region', { name: 'Authorized inventory' })).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('region')).toHaveAttribute('data-density', 'compact')
  })
  it('old consumers default to neutral badges and comfortable tables', () => {
    render(<><StatusBadge>LOCATION_UNKNOWN</StatusBadge><DataTable headings={['Status']} caption="States"><tr><td>Pending</td></tr></DataTable></>)
    expect(screen.getByText('LOCATION_UNKNOWN')).toHaveAttribute('data-tone', 'neutral')
    expect(screen.getByRole('region')).toHaveAttribute('data-density', 'comfortable')
  })
  it.each(['success', 'attention', 'danger', 'unknown', 'pending'] as const)('renders %s with explicit text', tone => {
    render(<StatusBadge tone={tone}>Original status</StatusBadge>)
    expect(screen.getByText('Original status')).toHaveAttribute('data-tone', tone)
  })
  it('retains label, hint and validation references for input, select and textarea', () => {
    render(<FilterPanel><FormField id="name" label="Name" hint="Business name"><input id="name" aria-describedby="name-hint" /></FormField><FormField id="reason" label="Reason" error="Reason required"><textarea id="reason" aria-invalid="true" aria-describedby="reason-error" /></FormField><FormField id="state" label="State"><select id="state"><option>Active</option></select></FormField></FilterPanel>)
    expect(screen.getByRole('group', { name: 'Filters' })).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveAccessibleDescription('Business name')
    expect(screen.getByLabelText('Reason')).toHaveAccessibleDescription('Reason required')
    expect(screen.getByLabelText('Reason')).toBeInvalid()
    expect(screen.getByRole('combobox', { name: 'State' })).toBeInTheDocument()
  })
  it('keeps unknown, offline and denied states separate from success', () => {
    render(<><LoadingState /><EmptyState>No inventory</EmptyState><AccessDeniedState /><OfflineState /><DetailList items={[["Current location", null], ['Last known', 'Yard A']]} /></>)
    expect(screen.getAllByRole('status').map(x => x.textContent)).toEqual(['Loading…', 'Pending synchronizationSaved on device. Waiting to sync.'])
    expect(screen.getByText('Not provided')).toBeInTheDocument()
    expect(screen.getByText('Yard A')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Access not available' })).toBeInTheDocument()
  })
  it('retains safe stale-version reload semantics instead of reissuing a command', async () => {
    const reload = vi.fn(), retry = vi.fn()
    render(<ErrorBanner error={new ApiProblem({ code: 'STALE_VERSION', status: 409 })} reload={reload} retry={retry} />)
    await userEvent.click(screen.getByRole('button', { name: 'Reload latest' }))
    expect(reload).toHaveBeenCalledOnce(); expect(retry).not.toHaveBeenCalled()
  })
  it('renders authorized timeline text without reconstructing events', () => {
    render(<Card title="Operational history"><Timeline label="Trailer events"><TimelineEvent title="Yard move" dateTime="2026-10-16T14:00:00Z" timeLabel="Oct 16 · 2:00 PM UTC" source="Human user · User action"><p>Yard A → Dock 2</p></TimelineEvent></Timeline></Card>)
    expect(screen.getByRole('list', { name: 'Trailer events' })).toBeInTheDocument()
    expect(screen.getByText('Oct 16 · 2:00 PM UTC')).toHaveAttribute('datetime', '2026-10-16T14:00:00Z')
    expect(screen.getByText('Human user · User action')).toBeInTheDocument()
  })
})

function DialogHarness({ busy = false, disabled = false }: { busy?: boolean; disabled?: boolean }) {
  const [open, setOpen] = useState(false)
  return <><button onClick={() => setOpen(true)}>Open confirmation</button>{open && <ConfirmDialog title="Confirm sample" confirmLabel="Confirm preview" busy={busy} confirmDisabled={disabled} onConfirm={() => setOpen(false)} onCancel={() => setOpen(false)}><p>Read the consequence.</p></ConfirmDialog>}</>
}
describe('confirmation presentation preserves lifecycle', () => {
  it.each(['Cancel', 'Confirm preview', 'Escape'])('restores trigger focus after %s', async action => {
    const user = userEvent.setup(); render(<DialogHarness />)
    const trigger = screen.getByRole('button', { name: 'Open confirmation' })
    await user.click(trigger)
    expect(screen.getByRole('dialog', { name: 'Confirm sample' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
    if (action === 'Escape') await user.keyboard('{Escape}')
    else await user.click(screen.getByRole('button', { name: action }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument(); expect(trigger).toHaveFocus()
  })
  it('busy confirmation blocks cancellation, Escape and repeat submission', async () => {
    const user = userEvent.setup(); render(<DialogHarness busy />)
    await user.click(screen.getByText('Open confirmation'))
    expect(screen.getByText('Cancel')).toBeDisabled(); expect(screen.getByText('Confirm preview')).toBeDisabled()
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    fireEvent(screen.getByRole('dialog'), new Event('cancel', { bubbles: true, cancelable: true }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
  it('disabled confirmation still permits a safe Cancel', async () => {
    const user = userEvent.setup(); render(<DialogHarness disabled />)
    await user.click(screen.getByText('Open confirmation'))
    expect(screen.getByText('Confirm preview')).toBeDisabled()
    await user.click(screen.getByText('Cancel'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
