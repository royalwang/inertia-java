import { createInertiaApp } from '@inertiajs/react'
import { createRoot, hydrateRoot } from 'react-dom/client'
import { resolve } from './pages'
import './style.css'
const nonce = document.querySelector<HTMLMetaElement>('meta[name="csp-nonce"]')?.content
const id = document.querySelector<HTMLMetaElement>('meta[name="inertia-root"]')?.content ?? 'app'
void createInertiaApp({ id, resolve, nonce, setup({ el, App, props }) {
  if (el.hasChildNodes()) hydrateRoot(el, <App {...props} />)
  else createRoot(el).render(<App {...props} />)
} })
