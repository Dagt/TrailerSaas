export interface SessionIdentity { uid: string }
export interface AuthBoundary {
  configured: boolean
  subscribe(listener: (identity: SessionIdentity | null) => void): () => void
  signIn(email: string, password: string): Promise<void>
  token(forceRefresh?: boolean): Promise<string | null>
  signOut(): Promise<void>
}
