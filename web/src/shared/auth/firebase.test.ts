import { afterEach, expect, it, vi } from 'vitest'
import { createFirebaseAuth } from './firebase'
import type { FirebaseSdk } from './firebase'

const sdk = vi.hoisted(() => ({
  getIdToken: vi.fn(async () => 'synthetic-secret-id-token'), authStateReady: vi.fn(async () => {}),
  signIn: vi.fn(async () => {}), signOut: vi.fn(async () => {}), observe: vi.fn(), unsubscribe: vi.fn(), initialize: vi.fn(() => ({ name: 'test-app' })),
}))
const loader = async () => ({ app: { getApps: () => [], initializeApp: sdk.initialize }, sdk: {
  getAuth: () => ({ currentUser: { uid: 'user', getIdToken: sdk.getIdToken }, authStateReady: sdk.authStateReady }),
  onAuthStateChanged: sdk.observe, signInWithEmailAndPassword: sdk.signIn, signOut: sdk.signOut,
} }) as unknown as FirebaseSdk
afterEach(() => vi.clearAllMocks())
it('unconfigured auth initializes no SDK and does not accept fabricated identity', async () => {
  const boundary = createFirebaseAuth(undefined, loader); const observer = vi.fn()
  const cleanup = boundary.subscribe(observer); await Promise.resolve()
  expect(observer).toHaveBeenCalledWith(null); expect(sdk.initialize).not.toHaveBeenCalled()
  await expect(boundary.signIn('a@example.test', 'secret')).rejects.toThrow('configuration')
  cleanup()
})
it('uses official SDK sign-in, session observation, ID token retrieval and sign-out without custom claims', async () => {
  sdk.observe.mockImplementation((_auth, callback) => { callback({ uid: 'user', role: 'TENANT_ADMIN', tenantId: 'foreign' }); return sdk.unsubscribe })
  const boundary = createFirebaseAuth({ apiKey: 'public-test-key', authDomain: 'auth.example.test', projectId: 'test-project', appId: 'test-app' }, loader)
  const observe = vi.fn(); const cleanup = boundary.subscribe(observe)
  await boundary.signIn('a@example.test', 'synthetic-password')
  expect(observe).toHaveBeenCalledWith({ uid: 'user' })
  expect(await boundary.token(true)).toBe('synthetic-secret-id-token')
  expect(sdk.getIdToken).toHaveBeenCalledWith(true)
  expect(sdk.signIn).toHaveBeenCalledWith(expect.anything(), 'a@example.test', 'synthetic-password')
  await boundary.signOut(); expect(sdk.signOut).toHaveBeenCalledOnce()
  cleanup(); expect(sdk.unsubscribe).toHaveBeenCalledOnce()
})
