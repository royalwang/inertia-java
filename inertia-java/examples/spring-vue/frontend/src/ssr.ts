import { createInertiaApp } from '@inertiajs/vue3'
import createServer from '@inertiajs/vue3/server'
import { renderToString } from '@vue/server-renderer'
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import type { Page } from '@inertiajs/core'
import { resolve } from './pages'

const rootId = process.env.SSR_ROOT_ID ?? 'app'
if (!/^[A-Za-z][A-Za-z0-9_-]*$/.test(rootId)) throw new Error('Invalid SSR root id')
const hash = (bytes: string | Buffer) => createHash('sha256').update(bytes).digest('hex')
let buildId: string | undefined
if (import.meta.env.PROD) {
  const receipt = JSON.parse(readFileSync(new URL('../build.json', import.meta.url), 'utf8'))
  const files = receipt.files as Record<string, string>
  if (receipt.format !== 1 || !files || typeof files !== 'object' || Array.isArray(files)) throw new Error('Invalid SSR build receipt')
  const canonical = { format: 1, files: Object.fromEntries(Object.entries(files).sort(([a], [b]) => a < b ? -1 : 1)) }
  if (receipt.buildId !== hash(JSON.stringify(canonical)) || files['ssr/ssr.js'] !== hash(readFileSync(new URL(import.meta.url)))) throw new Error('SSR build content mismatch')
  buildId = receipt.buildId
  console.info('SSR build verified: ' + buildId)
}
const renderer = createInertiaApp({ id: rootId, resolve })
createServer(async page => {
  const object = (value: unknown) => value !== null && typeof value === 'object' && !Array.isArray(value)
  if (!object(page) || typeof page.component !== 'string' || !object(page.props) || typeof page.url !== 'string' || page.url.length > 8192 || !(page.version === null || typeof page.version === 'string')) return { head: [], body: '', buildId, rootId, invalidPage: true }
  try { resolve(page.component) } catch { return { head: [], body: '', buildId, rootId, invalidPage: true } }
  if (buildId && page.version !== buildId) return { head: [], body: '', buildId, rootId }
  const render = await renderer
  if (typeof render !== 'function') throw new Error('SSR renderer unavailable')
  return { ...await render(page as Page, renderToString), buildId, rootId }
}, { port: Number(process.env.SSR_PORT ?? 13715), host: '127.0.0.1' })
