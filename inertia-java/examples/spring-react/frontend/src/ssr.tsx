import { createInertiaApp } from '@inertiajs/react'
import createServer from '@inertiajs/react/server'
import { renderToString } from 'react-dom/server'
import { resolve } from './pages'
createServer(page => createInertiaApp({ page, render: renderToString, resolve, setup: ({ App, props }) => <App {...props} /> }), {
  port: Number(process.env.SSR_PORT ?? 13714), host: '127.0.0.1',
})
