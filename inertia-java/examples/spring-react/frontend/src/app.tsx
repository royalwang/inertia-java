import { createInertiaApp } from '@inertiajs/react'
import { createRoot, hydrateRoot } from 'react-dom/client'
import { resolve } from './pages'
import './style.css'
void createInertiaApp({ resolve, setup({ el, App, props }) {
  if (el.hasChildNodes()) hydrateRoot(el, <App {...props} />)
  else createRoot(el).render(<App {...props} />)
} })
