import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AppProviders } from './AppProviders'
import { appRoutes } from './routes'

function renderRoute(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  )
  return router
}

describe('routing shell', () => {
  it.each([
    ['/', 'TrailerSaaS'],
    ['/admin', 'Tenant Admin'],
    ['/operations', 'Transport Operations'],
    ['/customer', 'Customer Portal'],
  ])('supports direct entry to %s', async (path, heading) => {
    renderRoute(path)
    expect(
      await screen.findByRole('heading', { name: heading }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('navigation', { name: 'Application areas' }),
    ).toBeInTheDocument()
  })

  it('navigates between areas and marks the active link', async () => {
    const user = userEvent.setup()
    const router = renderRoute('/admin')
    await user.click(screen.getByRole('link', { name: 'Transport Operations' }))
    expect(
      await screen.findByRole('heading', { name: 'Transport Operations' }),
    ).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/operations')
    expect(
      screen.getByRole('link', { name: 'Transport Operations' }),
    ).toHaveAttribute('aria-current', 'page')
    expect(
      screen.getByRole('link', { name: 'Tenant Admin' }),
    ).not.toHaveAttribute('aria-current')

    await user.click(screen.getByRole('link', { name: 'Customer Portal' }))
    expect(
      await screen.findByRole('heading', { name: 'Customer Portal' }),
    ).toBeInTheDocument()
  })

  it('keeps unknown routes inside the shell with a way home', async () => {
    const user = userEvent.setup()
    renderRoute('/unknown/path')
    expect(
      await screen.findByRole('heading', { name: 'Page not found' }),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Return to home' }))
    expect(
      await screen.findByRole('heading', { name: 'TrailerSaaS' }),
    ).toBeInTheDocument()
  })
})
