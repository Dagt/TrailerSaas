import { useAuth } from '../shared/auth/context'

export function Home() {
  const { identity, expired } = useAuth()
  return <section className="home-page"><p className="workspace-eyebrow">Your logistics workspace</p><h1>TrailerSaaS</h1>
    <p className="home-lead">Keep your team, equipment and customer work connected.</p>
    <div className="home-guidance"><h2>Choose your workspace</h2><p>Use the navigation above to open your workspace.{(!identity || expired) && ' Sign in with your existing account to continue.'}</p><p>Your available areas follow your active memberships and assigned access.</p></div>
  </section>
}
