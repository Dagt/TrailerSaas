import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { WorkspaceNavigation } from './WorkspaceShell'

const links = [{ label: 'Dashboard', to: '/operations', end: true }, { label: 'Orders', to: '/operations/orders' }]
beforeEach(() => {
  // jsdom has no native dialog implementation; browser focus/inertness is checked separately.
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value(this: HTMLDialogElement) { this.setAttribute('open', ''); this.querySelector('button')?.focus() } })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value(this: HTMLDialogElement) { this.removeAttribute('open') } })
})
afterEach(() => { vi.unstubAllGlobals(); Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal'); Reflect.deleteProperty(HTMLDialogElement.prototype, 'close') })
function fixture(path = '/operations') {
  const router = createMemoryRouter([{ path: '*', element: <WorkspaceNavigation label="Operations navigation" context="Test Tenant" links={links} /> }], { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}
it('renders only supplied links with readable labels and exact root matching', () => {
  fixture('/operations/orders/record')
  const nav = screen.getByRole('navigation', { name: 'Operations navigation' })
  expect(within(nav).getAllByRole('link')).toHaveLength(2)
  expect(within(nav).getByRole('link', { name: 'Orders' })).toHaveAttribute('aria-current', 'page')
  expect(within(nav).getByRole('link', { name: 'Dashboard' })).not.toHaveAttribute('aria-current')
  expect(screen.queryByRole('link', { name: 'Audit' })).not.toBeInTheDocument()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})
it.each(['Escape', 'Close navigation', 'Orders'])('closes the drawer using %s and restores its trigger', async method => {
  const u = userEvent.setup(), router = fixture(), trigger = screen.getByRole('button', { name: 'Open navigation' })
  expect(trigger).toHaveAttribute('aria-haspopup', 'dialog')
  await u.click(trigger)
  const dialog = screen.getByRole('dialog', { name: 'Operations navigation' })
  expect(trigger).toHaveAttribute('aria-controls', dialog.id)
  expect(within(dialog).getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
  if (method === 'Escape') await u.keyboard('{Escape}')
  else if (method === 'Orders') await u.click(within(dialog).getByRole('link', { name: 'Orders' }))
  else await u.click(within(dialog).getByRole('button', { name: method }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(trigger).toHaveFocus()
  expect(router.state.location.pathname).toBe(method === 'Orders' ? '/operations/orders' : '/operations')
})
it('dismisses native cancel without interfering with navigation history', async () => {
  const u = userEvent.setup(), router = fixture()
  await u.click(screen.getByRole('button', { name: 'Open navigation' }))
  fireEvent(screen.getByRole('dialog'), new Event('cancel', { bubbles: true, cancelable: true }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  await u.click(screen.getByRole('link', { name: 'Orders' }))
  await u.click(screen.getByRole('button', { name: 'Open navigation' }))
  await act(async () => router.navigate(-1))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
  await act(async () => router.navigate(1))
  expect(screen.getByRole('link', { name: 'Orders' })).toHaveAttribute('aria-current', 'page')
})
it('supports keyboard entry and closing without submitting a form', async () => {
  const u = userEvent.setup()
  fixture()
  screen.getByRole('button', { name: 'Open navigation' }).focus()
  await u.keyboard('{Enter}')
  const close = screen.getByRole('button', { name: 'Close navigation' })
  expect(close).toHaveAttribute('type', 'button')
  close.focus(); await u.keyboard(' ')
  expect(screen.getByRole('button', { name: 'Open navigation' })).toHaveFocus()
})
it('releases the native modal and media listener when a protected workspace unmounts', async () => {
  const addEventListener = vi.fn(), removeEventListener = vi.fn()
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener, removeEventListener }))
  const router = createMemoryRouter([{ path: '/operations', element: <WorkspaceNavigation label="Operations navigation" context="A" links={links} /> }, { path: '/signed-out', element: <h1>Signed out</h1> }], { initialEntries: ['/operations'] })
  const view = render(<main id="main-content" tabIndex={-1}><RouterProvider router={router} /></main>)
  await userEvent.setup().click(screen.getByRole('button', { name: 'Open navigation' }))
  const dialog = screen.getByRole('dialog')
  await act(async () => router.navigate('/signed-out'))
  expect(dialog).not.toHaveAttribute('open')
  expect(screen.getByRole('main')).toHaveFocus()
  expect(removeEventListener).toHaveBeenCalledWith('change', addEventListener.mock.calls[0]?.[1])
  view.unmount()
})
it('closes on desktop resize and moves focus to the active desktop link', async () => {
  let resize: () => void = () => undefined
  const media = { matches: false, addEventListener: vi.fn((_event, listener: () => void) => { resize = listener }), removeEventListener: vi.fn() }
  vi.stubGlobal('matchMedia', () => media)
  fixture()
  await userEvent.setup().click(screen.getByRole('button', { name: 'Open navigation' }))
  media.matches = true; act(resize)
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveFocus()
})
