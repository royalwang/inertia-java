// Release-local runtime; no source checkout or dev dependency is required.
import { readFileSync, readdirSync, lstatSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'

const root = fileURLToPath(new URL('.', import.meta.url))
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const safe = path => /^[A-Za-z0-9_./@-]+$/.test(path) && path.split('/').every(p => p && p !== '.' && p !== '..')
const sort = files => Object.fromEntries(Object.entries(files).sort(([a], [b]) => a < b ? -1 : 1))
const [major, minor] = process.versions.node.split('.').map(Number)
if (major < 22 || (major === 22 && minor < 12)) throw new Error('Node >=22.12 is required')
function inventory(prefix = '') {
  const files = {}
  for (const entry of readdirSync(resolve(root, prefix), { withFileTypes: true })) {
    const path = prefix ? prefix + '/' + entry.name : entry.name
    if (path === 'frontend/node_modules' || path === 'release.json') continue
    if (!safe(path)) throw new Error('Invalid release path')
    if (entry.isDirectory()) Object.assign(files, inventory(path))
    else if (entry.isFile()) files[path] = hash(readFileSync(resolve(root, path)))
    else throw new Error('Release contains symlink/special file: ' + path)
  }
  return sort(files)
}
function verify() {
  if (lstatSync(resolve(root, 'release.json')).isSymbolicLink()) throw new Error('Manifest cannot be a symlink')
  const record = JSON.parse(readFileSync(resolve(root, 'release.json'), 'utf8'))
  if (record.format !== 1 || !record.files || Array.isArray(record.files) || typeof record.version !== 'string' || !/^[a-f0-9]{64}$/.test(record.buildId)) throw new Error('Invalid release manifest')
  const canonical = { format: 1, version: record.version, buildId: record.buildId, files: sort(record.files) }
  if (record.releaseId !== hash(JSON.stringify(canonical))) throw new Error('Release ID mismatch')
  if (JSON.stringify(canonical.files) !== JSON.stringify(inventory())) throw new Error('Release inventory mismatch')
  const receipt = JSON.parse(readFileSync(resolve(root, 'frontend/dist/build.json'), 'utf8'))
  if (receipt.buildId !== record.buildId) throw new Error('Release/frontend mismatch')
  return record
}
function port(name, fallback) {
  const value = Number(process.env[name] ?? fallback)
  if (!Number.isInteger(value) || value < 0 || value > 65535) throw new Error('Invalid ' + name)
  return value
}
async function freePort() {
  const server = createServer()
  await new Promise((done, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', done) })
  const value = server.address().port
  await new Promise(done => server.close(done))
  return value
}
const children = []
let stopping = false
let shutdownPromise
function launch(command, args, cwd, env = {}) {
  if (stopping) throw new Error('Interrupted before launch')
  const child = spawn(command, args, { cwd, env: { ...process.env, ...env }, stdio: ['ignore', 'pipe', 'pipe'] })
  children.push(child); child.transcript = ''; child.closed = false
  child.stdout.on('data', data => { process.stdout.write(data); child.transcript = (child.transcript + data.toString()).slice(-16000) })
  child.stderr.on('data', data => process.stderr.write(data))
  child.on('error', error => { child.failure = error })
  child.on('close', () => { child.closed = true })
  return child
}
async function stop(child) {
  if (child.closed) return
  child.kill('SIGTERM')
  const end = Date.now() + 8000
  while (!child.closed && Date.now() < end) await delay(50)
  if (!child.closed) {
    child.kill('SIGKILL')
    const deadline = Date.now() + 5000
    while (!child.closed && Date.now() < deadline) await delay(50)
    if (!child.closed) throw new Error('Child did not exit')
  }
}
function shutdown() {
  stopping = true
  return shutdownPromise ??= Promise.all(children.map(stop))
}
for (const [signal, code] of [['SIGINT', 130], ['SIGTERM', 143]]) process.on(signal, () => {
  process.exitCode = code
  shutdown().catch(error => { console.error(error); process.exitCode = 1 })
})
async function wait(label, predicate) {
  const deadline = Date.now() + 30000
  while (Date.now() < deadline) {
    if (stopping) throw new Error('Interrupted')
    const failed = children.find(child => child.failure || child.closed)
    if (failed) throw failed.failure ?? new Error('Child exited before ' + label)
    if (await predicate()) return
    await delay(100)
  }
  throw new Error('Startup deadline: ' + label)
}
async function completion(child) {
  while (!child.closed) await delay(100)
  if (child.failure) throw child.failure
  if (!stopping) process.exitCode = child.exitCode ?? 1
}
const mode = process.argv[2] ?? 'check'
try {
  const record = verify()
  if (!['check', 'java', 'ssr', 'pair'].includes(mode)) throw new Error('Usage: node runtime.mjs check|java|ssr|pair')
  if (mode === 'check') console.log(JSON.stringify({ verified: true, releaseId: record.releaseId, buildId: record.buildId }))
  else {
    const rootId = process.env.INERTIA_ROOT_ID ?? 'app'
    if (!/^[A-Za-z][A-Za-z0-9_-]*$/.test(rootId)) throw new Error('Invalid root ID')
    let rendererPort = port('SSR_PORT', 13714)
    if (!rendererPort && mode !== 'pair') throw new Error('SSR_PORT=0 requires pair mode; separate services need a shared fixed port')
    if (!rendererPort) rendererPort = await freePort()
    const appPort = port('APP_PORT', 8080)
    const host = process.env.APP_HOST ?? '127.0.0.1'
    if (!['127.0.0.1', '0.0.0.0'].includes(host)) throw new Error('APP_HOST must be 127.0.0.1 or 0.0.0.0')
    let renderer
    if (mode === 'ssr' || mode === 'pair') renderer = launch(process.execPath, ['dist/ssr/ssr.js'], resolve(root, 'frontend'), { SSR_PORT: String(rendererPort), SSR_ROOT_ID: rootId })
    if (mode === 'pair') await wait('renderer health', async () => { try { return (await fetch(`http://127.0.0.1:${rendererPort}/health`, { signal: AbortSignal.timeout(500) })).ok } catch { return false } })
    if (mode === 'ssr') await completion(renderer)
    else {
      const assets = resolve(process.env.INERTIA_ASSET_STORE ?? resolve(root, 'assets'))
      const java = launch('java', [`-Dinertia.frontend=${resolve(root, 'frontend')}`, `-Dinertia.asset-store=${assets}`, `-Dinertia.root-id=${rootId}`, `-Dinertia.ssr=http://127.0.0.1:${rendererPort}/render`, '-jar', resolve(root, 'app.jar'),
        `--server.address=${host}`, `--server.port=${appPort}`, '--inertia.ssr-health-enabled=true', '--spring.lifecycle.timeout-per-shutdown-phase=5s'], root)
      if (mode === 'pair') {
        let listening
        await wait('Java listener', () => { listening = java.transcript.match(/Tomcat started on port (\d+)/)?.[1]; return Boolean(listening) })
        await wait('same-release SSR page', async () => {
          const response = await fetch(`http://127.0.0.1:${listening}/users`, { signal: AbortSignal.timeout(5000) })
          const body = await response.text()
          return response.ok && body.includes('data-server-rendered') && body.includes(record.buildId)
        })
        console.log('INERTIA_READY ' + JSON.stringify({ releaseId: record.releaseId, buildId: record.buildId, javaPort: Number(listening), rendererPort, rootId }))
        renderer.on('close', () => { if (!stopping) console.error('Renderer stopped; Java remains available with CSR fallback. Restart renderer separately.') })
      }
      await completion(java)
    }
  }
} catch (error) {
  console.error(error.stack)
  if (!stopping) process.exitCode = 1
} finally { await shutdown() }
