import { Link } from 'react-router'

export function NotFound() {
  return (
    <>
      <h1>Page not found</h1>
      <Link to="/">Return to home</Link>
    </>
  )
}
