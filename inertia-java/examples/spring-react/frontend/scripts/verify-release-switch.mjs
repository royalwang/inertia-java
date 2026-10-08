import http from 'node:http'
import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { cpSync, mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync, createWriteStream } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
import { chromium, expect } from '@playwright/test'
import { publishAssets } from './release-assets.mjs'

const frontend = fileURLToPath(new URL('..', import.meta.url))
const workspace = mkdtempSync(resolve(frontend, 'dist/release-switch-'))
const output = process.env.INERTIA_SWITCH_OUTPUT ?? '/tmp/inertia-java-release-switch'
mkdirSync(output, { recursive: true })
const hash = value => createHash('sha256').update(value).digest('hex')
const original = JSON.parse(readFileSync(resolve(frontend, 'dist/build.json'), 'utf8'))
const assets = resolve(workspace, 'assets')
const releases = []
const children = []
let proxy
let browser
function start(command, args, options, name) {
  const log = createWriteStream(resolve(output, name + '.log'))
  const child = spawn(command, args, { ...options, stdio: ['ignore', 'pipe', 'pipe'] })
  children.push(child)
  child.transcript = ''
  child.on('error', error => { child.failure = error })
  child.stdout.on('data', data => { log.write(data); child.transcript += data.toString() })
  child.stderr.on('data', data => log.write(data))
  child.on('exit', () => log.end())
  return child
}
async function wait(label, predicate) {
  const deadline = Date.now() + 20000
  while (Date.now() < deadline) {
    for (const child of children) if (child.failure) throw child.failure
    try { if (await predicate()) return } catch { /* startup/reload */ }
    await delay(100)
  }
  throw new Error('Deadline exceeded: ' + label)
}
async function freePort() {
  const server = http.createServer()
  await new Promise(done => server.listen(0, '127.0.0.1', done))
  const port = server.address().port
  await new Promise(done => server.close(done))
  return port
}
try {
  for (const name of ['A', 'B']) {
    const root = resolve(workspace, name)
    const dist = resolve(root, 'dist')
    mkdirSync(dist, { recursive: true })
    for (const directory of ['client', 'ssr']) cpSync(resolve(frontend, 'dist', directory), resolve(dist, directory), { recursive: true })
    const files = { ...original.files }
    if (name === 'B') {
      const entry = Object.keys(files).find(path => path.startsWith('client/assets/') && path.endsWith('.js'))
      writeFileSync(resolve(dist, entry), readFileSync(resolve(dist, entry), 'utf8') + '\n// controlled release B fixture\n')
      files[entry] = hash(readFileSync(resolve(dist, entry)))
    }
    const canonical = { format: 1, files: Object.fromEntries(Object.entries(files).sort(([a], [b]) => a < b ? -1 : 1)) }
    const buildId = hash(JSON.stringify(canonical))
    writeFileSync(resolve(dist, 'build.json'), JSON.stringify({ ...canonical, buildId }))
    publishAssets(dist, assets)
    const nodePort = await freePort()
    start(process.execPath, [resolve(dist, 'ssr/ssr.js')], { cwd: frontend, env: { ...process.env, SSR_PORT: String(nodePort) } }, 'node-' + name)
    await wait('Node ' + name, async () => (await fetch(`http://127.0.0.1:${nodePort}/health`)).ok)
    const java = start('java', [`-Dinertia.frontend=${root}`, `-Dinertia.asset-store=${assets}`,
      `-Dinertia.ssr=http://127.0.0.1:${nodePort}/render`, '-jar', 'target/spring-react-0.1.0-SNAPSHOT.jar',
      '--server.address=127.0.0.1', '--server.port=0', '--inertia.csp.enabled=true'], { cwd: resolve(frontend, '..') }, 'java-' + name)
    let port
    await wait('Java ' + name, () => { port = java.transcript.match(/Tomcat started on port (\d+)/)?.[1]; return Boolean(port) })
    releases.push({ buildId, port })
  }
  expect(releases[0].buildId).not.toBe(releases[1].buildId)
  let active = 0
  proxy = http.createServer((incoming, outgoing) => {
    // A loopback test router, not a production deployment control endpoint.
    if (incoming.url === '/__switch' && incoming.method === 'POST') { active = 1; outgoing.writeHead(204).end(); return }
    const upstream = http.request({ hostname: '127.0.0.1', port: releases[active].port, path: incoming.url,
      method: incoming.method, headers: incoming.headers }, response => { outgoing.writeHead(response.statusCode, response.headers); response.pipe(outgoing) })
    upstream.on('error', () => { outgoing.writeHead(502).end() })
    incoming.pipe(upstream)
  })
  await new Promise(done => proxy.listen(0, '127.0.0.1', done))
  const base = `http://127.0.0.1:${proxy.address().port}`
  browser = await chromium.launch({ channel: 'chrome' })
  const page = await browser.newPage()
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const first = await page.goto(base + '/users')
  expect(await first.text()).toContain('data-server-rendered')
  await expect(page.getByTestId('stats')).toHaveText('Total: 2')
  const oldUrl = await page.locator('script[type="module"][src]').first().getAttribute('src')
  expect(oldUrl).toContain('/build/' + releases[0].buildId + '/')
  const before = await page.request.get(base + oldUrl)
  expect(before.status()).toBe(200)
  const oldBytes = await before.body()
  await page.request.post(base + '/__switch')
  const retained = await page.request.get(base + oldUrl)
  expect(retained.status()).toBe(200)
  expect(await retained.body()).toEqual(oldBytes)
  const conflict = page.waitForResponse(response => response.status() === 409 && response.url().endsWith('/about'))
  await page.getByRole('link', { name: 'About this app' }).click()
  const refresh = await conflict
  expect(refresh.headers()['x-inertia-version']).toBe(releases[1].buildId)
  expect(refresh.headers()['x-inertia-location']).toBe(base + '/about')
  await expect(page).toHaveTitle('About')
  await expect.poll(() => page.locator('script[type="module"][src]').first().getAttribute('src')).toContain('/build/' + releases[1].buildId + '/')
  await page.getByRole('link', { name: 'Back to users' }).click()
  await expect(page.getByTestId('stats')).toHaveText('Total: 2')
  await page.getByLabel('Name', { exact: true }).fill('Grace')
  await page.getByRole('button', { name: 'Save demo name' }).click()
  await expect(page.getByRole('status')).toHaveText('Saved Grace (demo only)')
  expect(errors).toEqual([])
  console.log('Verified A→B switch: old assets byte-identical, 409 refresh, new assets, SSR/hydration/navigation/CSRF/flash')
} finally {
  await browser?.close()
  if (proxy) { proxy.closeAllConnections(); await new Promise(done => proxy.close(done)) }
  for (const child of children.toReversed()) {
    if (child.exitCode !== null || child.signalCode !== null) continue
    child.kill('SIGTERM')
    const deadline = Date.now() + 5000
    while (child.exitCode === null && child.signalCode === null && Date.now() < deadline) await delay(100)
    if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL')
  }
  rmSync(workspace, { recursive: true, force: true })
}
