import { NavLink, Outlet } from 'react-router'

export function AppShell() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <header className="app-header">
        <NavLink className="app-name" to="/">
          TrailerSaaS
        </NavLink>
        <nav aria-label="Application areas">
          <NavLink to="/admin">Tenant Admin</NavLink>
          <NavLink to="/operations">Transport Operations</NavLink>
          <NavLink to="/customer">Customer Portal</NavLink>
        </nav>
      </header>
      <main id="main-content" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
