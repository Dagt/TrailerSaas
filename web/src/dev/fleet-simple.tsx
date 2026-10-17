import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { FleetSimpleGallery } from './FleetSimpleGallery'
import '../styles.css'
import './gallery.css'

// This entry is referenced only by dev/fleet-simple.html, never by index.html.
if (import.meta.env.DEV) {
  createRoot(document.getElementById('root')!).render(<StrictMode><FleetSimpleGallery /></StrictMode>)
}
