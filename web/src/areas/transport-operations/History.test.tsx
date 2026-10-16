import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { alternateId, operationsFixture, tripId, trip } from '../../test/operationsFixture'
import { resourceId } from '../../test/adminFixture'
import { historyPage } from './historyContracts'
const stamp = '2026-10-16T18:00:00Z'
const assignment = () => ({ id: alternateId, eventType: 'DRIVER_REASSIGNED', actorUserId: resourceId, occurredAt: stamp, reason: 'Next Driver confirmed', driverId: resourceId, tractorId: null, trailerId: null, metadata: { previousDriverId: alternateId } })
const operation = () => ({ id: resourceId, eventType: 'TRIP_CANCELLED', actorUserId: resourceId, occurredAt: stamp, reason: 'Customer instruction retained', tripStopId: null, targetDriverId: null, metadata: null })
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } })
afterEach(() => vi.unstubAllGlobals())
it('requests bounded history once, displays assignments and instructions without duplicate sections', async () => {
  const u = userEvent.setup(), t = trip()
  t.assignmentHistory = [assignment()]; t.operationalHistory = [operation()]
  const f = operationsFixture(`/operations/trips/${tripId}`, { trip: t }), original = f.fetcher.getMockImplementation()!
  f.fetcher.mockImplementation(async (input, init) => {
    const response = await original(input, init), url = new URL(String(input))
    return url.pathname.endsWith('/timeline') ? json({ items: [url.searchParams.has('cursor') ? operation() : assignment()], nextCursor: url.searchParams.has('cursor') ? null : 'next' }) : response
  })
  const history = await screen.findByRole('list', { name: 'Trip operational history' })
  expect(within(history).getByText('Driver reassigned')).toBeInTheDocument()
  expect(f.requests.some(r => r.path.endsWith(`/${tripId}?includeHistory=false`))).toBe(true)
  expect(f.requests.some(r => r.path.endsWith('/timeline?limit=50'))).toBe(true)
  expect(screen.queryByRole('heading', { name: 'Assignment history' })).not.toBeInTheDocument()
  await u.click(screen.getByRole('button', { name: 'Load more Trip events' }))
  await screen.findByText('Customer instruction retained', { exact: false })
  expect(history.querySelectorAll('li')).toHaveLength(2)
  expect(f.requests.some(r => r.path.endsWith('/timeline?limit=50&cursor=next'))).toBe(true)
  expect(screen.queryByRole('button', { name: 'Load more Trip events' })).not.toBeInTheDocument()
})
it.each([403, 500, 200])('handles a failed/incompatible %s next page without exposing fabricated history', async status => {
  const u = userEvent.setup(), f = operationsFixture(`/operations/trips/${tripId}`), original = f.fetcher.getMockImplementation()!
  f.fetcher.mockImplementation(async (input, init) => {
    const response = await original(input, init), url = new URL(String(input))
    return url.pathname.endsWith('/timeline') ? url.searchParams.has('cursor') ? json({ secret: 'foreign-history' }, status) : json({ items: [assignment()], nextCursor: 'next' }) : response
  })
  await u.click(await screen.findByRole('button', { name: 'Load more Trip events' }))
  await screen.findByRole('alert')
  expect(screen.queryByRole('list', { name: 'Trip operational history' })).not.toBeInTheDocument()
  expect(screen.queryByText('foreign-history')).not.toBeInTheDocument()
  expect(f.requests.every(r => r.method === 'GET')).toBe(true)
})
it('presents actual Stop execution timestamps separately from administrative evidence', async () => {
  const t = trip('AT_STOP'); t.stops[0]!.arrivedAt = stamp; operationsFixture(`/operations/trips/${tripId}`, { trip: t })
  await screen.findByRole('heading', { name: 'Recorded Stop execution' })
  expect(screen.getByText(/Stop 1: Arrived/)).toBeInTheDocument()
  expect(screen.getByText(/no missing events are reconstructed/)).toBeInTheDocument()
})
it('revalidates a previously loaded timeline on manual action reload', async () => {
  const f = operationsFixture(`/operations/trips/${tripId}`)
  await screen.findByText('No operational events recorded.')
  await f.cache.invalidateQueries({ queryKey: ['operations'] })
  await waitFor(() => expect(f.requests.filter(r => r.path.includes('/timeline?limit=50')).length).toBeGreaterThan(1))
})
it('keeps identical UUIDs from distinct event tables and rejects same-kind duplicates', () => {
  expect(historyPage({ items: [assignment(), { ...operation(), id: alternateId }], nextCursor: null }, { seen: [], ids: [] }).items).toHaveLength(2)
  expect(() => historyPage({ items: [assignment(), assignment()], nextCursor: null }, { seen: [], ids: [] })).toThrow()
})
it.each([
  { items: [assignment()], nextCursor: 'prior' }, { items: [assignment()], nextCursor: 'bad cursor' },
  { items: [{ ...operation(), actorUserId: 'not-a-uuid' }], nextCursor: null },
  { items: [operation()], nextCursor: null, unexpected: true },
])('rejects malformed, repeated or privileged timeline response %#', page => expect(() => historyPage(page, { cursor: 'prior', seen: ['prior'], ids: [] })).toThrow())
