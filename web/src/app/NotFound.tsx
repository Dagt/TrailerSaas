import { Link } from 'react-router'
import { WorkspaceState } from '../shared/ui/WorkspaceShell'

export function NotFound() {
  return (
    <WorkspaceState title="Page not found">
      <p>The address may have changed. Return to your workspace to continue.</p>
      <Link to="/">Return to home</Link>
    </WorkspaceState>
  )
}
