import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AppProviders } from './app/AppProviders'
import { appRoutes } from './app/routes'
import { readWebEnvironment } from './shared/config/environment'
import './styles.css'

readWebEnvironment(import.meta.env)

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('The application root element is missing.')
}

const router = createBrowserRouter(appRoutes)

createRoot(rootElement).render(
  <StrictMode>
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
)
