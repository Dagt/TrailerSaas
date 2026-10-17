import { createRoot } from 'react-dom/client'
import { SharedComparison } from './SharedComparison'
import '../styles.css'

if (import.meta.env.DEV) createRoot(document.getElementById('root')!).render(<SharedComparison />)
