import type { AuthBoundary } from './boundary'
import type { readWebEnvironment } from '../config/environment'

/** SDK is lazy: unconfigured builds and injected tests never initialize/contact Firebase. */
export type FirebaseSdk = { app: typeof import('firebase/app'); sdk: typeof import('firebase/auth') }
async function loadSdk(): Promise<FirebaseSdk> {
  const [app, sdk] = await Promise.all([import('firebase/app'), import('firebase/auth')])
  return { app, sdk }
}
export function createFirebaseAuth(configuration: ReturnType<typeof readWebEnvironment>['firebase'], loader = loadSdk): AuthBoundary {
  let instance: Promise<{ current: import('firebase/auth').Auth; sdk: FirebaseSdk['sdk'] }> | undefined
  const auth = async () => {
    if (!configuration) throw new Error('Web sign-in configuration is unavailable.')
    instance ??= loader().then(({ app, sdk }) => {
      const firebaseApp = app.getApps().find(existing => existing.name === 'trailersaas-web') ?? app.initializeApp(configuration, 'trailersaas-web')
      return { current: sdk.getAuth(firebaseApp), sdk }
    })
    return instance
  }
  return {
    configured: Boolean(configuration),
    subscribe(listener) {
      let live = true
      let unsubscribe: (() => void) | undefined
      if (!configuration) { queueMicrotask(() => { if (live) listener(null) }) }
      else void auth().then(({ current, sdk }) => {
        if (live) unsubscribe = sdk.onAuthStateChanged(current, user => listener(user ? { uid: user.uid } : null), () => listener(null))
      }).catch(() => { if (live) listener(null) })
      return () => { live = false; unsubscribe?.() }
    },
    async signIn(email, password) {
      const { current, sdk } = await auth()
      await sdk.signInWithEmailAndPassword(current, email, password)
    },
    async token(forceRefresh = false) {
      const { current } = await auth()
      await current.authStateReady()
      return current.currentUser?.getIdToken(forceRefresh) ?? null
    },
    async signOut() {
      if (!configuration) return
      const { current, sdk } = await auth()
      await sdk.signOut(current)
    },
  }
}
