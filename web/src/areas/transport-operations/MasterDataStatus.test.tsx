import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ApiProblem } from '../../shared/api/client'
import { MasterDataStatus } from './MasterDataStatus'
it('distinguishes loading resource options from an empty catalog', () => {
  render(<MasterDataStatus queries={[{ isPending: true, isError: false, error: null, refetch: vi.fn() }]} />)
  expect(screen.getByRole('status')).toHaveTextContent('Loading resource names')
})
it('offers safe read reconciliation for a resource directory failure', async () => {
  const refetch = vi.fn().mockResolvedValue({})
  render(<MasterDataStatus queries={[{ isPending: false, isError: true, error: new ApiProblem({ status: 503, code: 'INTERNAL_ERROR', detail: 'The request could not be completed.', traceId: 'resource-support' }), refetch }]} />)
  expect(screen.getByText('Resource options could not be loaded.')).toBeInTheDocument()
  expect(screen.getByText(/resource-support/)).toBeInTheDocument()
  await userEvent.setup().click(screen.getByRole('button', { name: 'Retry' }))
  expect(refetch).toHaveBeenCalledTimes(1)
})
