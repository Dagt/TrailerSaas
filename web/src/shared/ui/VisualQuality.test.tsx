import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { DataTable, ErrorBanner } from './components'
import { displayTime } from './dateTime'

afterEach(() => vi.unstubAllGlobals())

it('distinguishes independent action and record failures with named alerts and the existing separate retry callbacks', () => {
  const actions = vi.fn(), records = vi.fn()
  render(<><ErrorBanner title="Order actions unavailable" error={Error('unavailable')} retry={actions} /><ErrorBanner title="Orders could not be loaded" error={Error('unavailable')} retry={records} /></>)
  fireEvent.click(within(screen.getByRole('alert', { name: 'Order actions unavailable' })).getByRole('button', { name: 'Retry' }))
  expect(actions).toHaveBeenCalledOnce()
  expect(records).not.toHaveBeenCalled()
  fireEvent.click(within(screen.getByRole('alert', { name: 'Orders could not be loaded' })).getByRole('button', { name: 'Retry' }))
  expect(records).toHaveBeenCalledOnce()
})

it('offers a named keyboard scrolling hint only for an overflowing table, retires it on reflow, and disconnects observation', () => {
  let resized = () => {}
  const disconnect = vi.fn(), observe = vi.fn()
  vi.stubGlobal('ResizeObserver', class {
    constructor(callback: () => void) { resized = callback }
    observe = observe
    disconnect = disconnect
  })
  const view = render(<DataTable headings={['Trailer', 'Recorded location']} caption="Trailer observations"><tr><td>T114</td><td>Unknown; Last Known is historical</td></tr></DataTable>)
  const region = screen.getByRole('region', { name: 'Trailer observations' })
  expect(region).not.toHaveAttribute('aria-describedby')
  Object.defineProperties(region, { clientWidth: { configurable: true, value: 350 }, scrollWidth: { configurable: true, value: 900 } })
  act(resized)
  expect(region).toHaveAccessibleDescription('Scroll horizontally to see all columns. Keyboard: focus this table and use the arrow keys.')
  region.focus()
  expect(region).toHaveFocus()
  expect(screen.getByRole('table')).toHaveAccessibleName('Trailer observations')
  expect(observe).toHaveBeenCalledWith(region)
  expect(observe).toHaveBeenCalledWith(screen.getByRole('table'))
  Object.defineProperty(region, 'clientWidth', { value: 1000 })
  fireEvent(window, new Event('resize'))
  expect(region).not.toHaveAttribute('aria-describedby')
  expect(screen.queryByText(/Scroll horizontally/)).not.toBeInTheDocument()
  view.unmount()
  expect(disconnect).toHaveBeenCalledOnce()
})

it('keeps table access usable when ResizeObserver is unavailable', () => {
  vi.stubGlobal('ResizeObserver', undefined)
  render(<DataTable headings={['Order']} caption="Orders"><tr><td>ORDER-1</td></tr></DataTable>)
  expect(screen.getByRole('region', { name: 'Orders' })).toHaveAttribute('tabindex', '0')
  fireEvent.resize(window)
  expect(screen.getByRole('cell', { name: 'ORDER-1' })).toBeVisible()
})

it('formats operational timestamps in US English with the actual browser offset without changing the input', () => {
  const instant = '2026-10-19T12:00:00Z', locale = vi.spyOn(Intl, 'DateTimeFormat')
  const text = displayTime(instant)
  expect(locale).toHaveBeenCalledWith('en-US', expect.objectContaining({ timeZoneName: 'shortOffset' }))
  expect(text).toMatch(/Oct 19, 2026/)
  expect(text).toMatch(/(?:AM|PM)/)
  expect(text).toMatch(/GMT/)
  expect(instant).toBe('2026-10-19T12:00:00Z')
  expect(displayTime('source value unavailable')).toBe('source value unavailable')
})
