import { useQueryClient } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { expect, it } from 'vitest'
import { AppProviders } from './AppProviders'

it('provides one query cache that survives application rerenders', () => {
  let firstClient: ReturnType<typeof useQueryClient> | undefined
  let currentClient: ReturnType<typeof useQueryClient> | undefined

  function Probe() {
    currentClient = useQueryClient()
    firstClient ??= currentClient
    return null
  }

  const { rerender } = render(
    <AppProviders>
      <Probe />
    </AppProviders>,
  )
  firstClient?.setQueryData(['scaffold-test'], 'cached')
  rerender(
    <AppProviders>
      <Probe />
    </AppProviders>,
  )

  expect(currentClient).toBe(firstClient)
  expect(currentClient?.getQueryData(['scaffold-test'])).toBe('cached')
})
