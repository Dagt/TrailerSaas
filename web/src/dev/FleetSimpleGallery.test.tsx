import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { FleetSimpleGallery } from './FleetSimpleGallery'
import routes from '../app/routes.tsx?raw'
import main from '../main.tsx?raw'
import entry from './fleet-simple.tsx?raw'

describe('development-only Fleet Simple gallery', () => {
  it('labels isolated fixtures and preserves domain distinctions', () => {
    render(<FleetSimpleGallery />)
    expect(screen.getByText('DEMO DATA · Development only')).toBeInTheDocument()
    expect(screen.getByText('Last known: North Yard · B04')).toBeInTheDocument()
    expect(screen.getByText('LOCATION_UNKNOWN')).toBeInTheDocument()
    expect(screen.getByText('Physical coupling')).toBeInTheDocument()
    expect(screen.getByText('Not confirmed')).toBeInTheDocument()
    expect(screen.getByText('POD uploaded; acceptance pending')).toBeInTheDocument()
    expect(screen.getByText('DELIVERED')).toBeInTheDocument()
    expect(screen.queryByText('COMPLETED')).not.toBeInTheDocument()
  })
  it('opens an actual shared dialog and returns focus without a business mutation', async () => {
    render(<FleetSimpleGallery />)
    const button = screen.getByRole('button', { name: 'Preview confirmation' })
    await userEvent.click(button)
    expect(screen.getByRole('dialog')).toHaveTextContent('No Trailer, Order or other record will change.')
    await userEvent.click(screen.getByRole('button', { name: 'Confirm preview' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument(); expect(button).toHaveFocus()
  })
  it('keeps the gallery outside production routing and the production entry graph', () => {
    expect(routes).not.toMatch(/FleetSimpleGallery|fleet-simple|\/dev\//)
    expect(main).not.toMatch(/FleetSimpleGallery|fleet-simple|\/dev\//)
    expect(entry).toContain('import.meta.env.DEV')
  })
})
