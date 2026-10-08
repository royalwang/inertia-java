import { createInertiaApp } from '@inertiajs/react'
import createServer from '@inertiajs/react/server'
import { renderToString } from 'react-dom/server'
import { resolve } from './pages'
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'

const rootId = process.env.SSR_ROOT_ID ?? 'app'
if (!/^[A-Za-z][A-Za-z0-9_-]*$/.test(rootId)) throw new Error('Invalid SSR root id')
const hash = (bytes: string | Buffer) => createHash('sha256').update(bytes).digest('hex')
let buildId: string | undefined
if (import.meta.env.PROD) {
  const receipt = JSON.parse(readFileSync(new URL('../build.json', import.meta.url), 'utf8'))
  const files = receipt.files as Record<string, string>
  if (receipt.format !== 1 || !files || typeof files !== 'object' || Array.isArray(files)) throw new Error('Invalid SSR build receipt')
  const canonical = { format: 1, files: Object.fromEntries(Object.entries(files).sort(([a], [b]) => a < b ? -1 : 1)) }
  if (receipt.buildId !== hash(JSON.stringify(canonical))
      || files['ssr/ssr.js'] !== hash(readFileSync(new URL(import.meta.url)))) throw new Error('SSR build content mismatch')
  buildId = receipt.buildId
  console.info('SSR build verified: ' + buildId)
}
const renderer = createInertiaApp({ id: rootId, resolve })
createServer(async page => {
  if (buildId && page.version !== buildId) return { head: [], body: '', buildId, rootId }
  const render = await renderer
  if (typeof render !== "function") throw new Error("SSR renderer unavailable")
  const rendered = await render(page, renderToString)
  return { ...rendered, buildId, rootId }
}, {
  port: Number(process.env.SSR_PORT ?? 13714), host: '127.0.0.1',
})
