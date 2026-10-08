import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync, rmSync, createWriteStream } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'

const frontend = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.INERTIA_HEALTH_OUTPUT ?? '/tmp/inertia-java-ssr-health'
mkdirSync(output, { recursive: true })
const copy = mkdtempSync(resolve(frontend, 'dist/health-watch-'))
const entry = resolve(copy, 'ssr.mjs')
const bundle = readFileSync(resolve(frontend, 'dist/ssr/ssr.js'), 'utf8')
writeFileSync(entry, bundle)
const reserve = createServer()
await new Promise(done => reserve.listen(0, '127.0.0.1', done))
const rendererPort = reserve.address().port
await new Promise(done => reserve.close(done))
const processes = []
const csp = process.env.INERTIA_VERIFY_CSP === 'true'
const rootId = process.env.INERTIA_ROOT_ID ?? 'app'
const history = process.env.INERTIA_VERIFY_HISTORY === 'true'
function start(command, args, options, name) {
  const log = createWriteStream(resolve(output, name + '.log'))
  const child = spawn(command, args, { ...options, detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'pipe'] })
  processes.push(child)
  child.transcript = ''
  child.stdout.on('data', data => { log.write(data); child.transcript = (child.transcript + data.toString()).slice(-32000) })
  child.stderr.on('data', data => log.write(data))
  child.on('error', error => { child.failure = error })
  child.on('exit', () => log.end())
  return child
}
async function wait(label, predicate) {
  const deadline = Date.now() + 20000
  while (Date.now() < deadline) {
    if (processes.some(child => child.failure)) throw processes.find(child => child.failure).failure
    try { if (await predicate()) return } catch { /* transient restart/connect */ }
    await delay(100)
  }
  throw new Error('Deadline exceeded: ' + label)
}
async function stop(child) {
  if (child.exitCode !== null || child.signalCode !== null) return
  if (process.platform === 'win32') child.kill('SIGTERM')
  else process.kill(-child.pid, 'SIGTERM')
  const deadline = Date.now() + 5000
  while (child.exitCode === null && child.signalCode === null && Date.now() < deadline) await delay(100)
  if (child.exitCode === null && child.signalCode === null) {
    if (process.platform === 'win32') child.kill('SIGKILL')
    else process.kill(-child.pid, 'SIGKILL')
  }
}
function startRenderer() {
  return start(process.execPath, ['--watch', '--watch-preserve-output', entry], {
    cwd: frontend, env: { ...process.env, SSR_PORT: String(rendererPort), SSR_ROOT_ID: rootId },
  }, 'node-' + processes.length)
}
async function browserCsp(port, csr) {
  if (!csp) return
  await new Promise((done, reject) => {
    const child = spawn(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--grep', 'content security policy'], {
      cwd: frontend, env: { ...process.env, INERTIA_BASE_URL: `http://127.0.0.1:${port}`,
        INERTIA_EXPECT_CSP: 'true', INERTIA_EXPECT_CSR: String(csr) }, stdio: 'inherit',
    })
    child.on('error', reject)
    child.on('exit', code => code === 0 ? done() : reject(new Error('CSP browser failed')))
  })
}
async function browserHistory(port) {
  if (!history) return
  await new Promise((done, reject) => {
    const child = spawn(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--grep', 'history encryption,'], {
      cwd: frontend, env: { ...process.env, INERTIA_BASE_URL: `http://127.0.0.1:${port}`,
        INERTIA_EXPECT_HISTORY: 'true' }, stdio: 'inherit',
    })
    child.on('error', reject)
    child.on('exit', code => code === 0 ? done() : reject(new Error('History browser failed')))
  })
}
let renderer
let java
try {
  renderer = startRenderer()
  await wait('initial Node health', async () => (await fetch(`http://127.0.0.1:${rendererPort}/health`)).ok)
  java = start('java', [`-Dinertia.ssr=http://127.0.0.1:${rendererPort}/render`, `-Dinertia.root-id=${rootId}`, '-jar',
    'target/spring-react-0.1.0-SNAPSHOT.jar', '--server.address=127.0.0.1', '--server.port=0',
    '--inertia.ssr-health-enabled=true', `--inertia.csp.enabled=${csp}`, `--inertia.demo-history-enabled=${history}`], { cwd: resolve(frontend, '..') }, 'java')
  let port
  await wait('Java startup', () => { port = java.transcript.match(/Tomcat started on port (\d+)/)?.[1]; return Boolean(port) })
  const base = `http://127.0.0.1:${port}`
  const health = async () => (await fetch(base + '/api/ssr-health')).json()
  await wait('UP', async () => (await health()).state === 'UP')
  if (!(await (await fetch(base + '/users')).text()).includes('data-server-rendered')) throw new Error('Initial SSR missing')
  console.log('Verified UP with real SSR')
  await browserCsp(port, false)
  await browserHistory(port)
  const mismatched = await fetch(`http://127.0.0.1:${rendererPort}/render`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ component: 'NotRegistered', props: {}, url: '/', version: 'different-release' }),
  })
  const rejected = await mismatched.json()
  if (rejected.body !== '' || !rejected.buildId || rejected.buildId === 'different-release') throw new Error('Node did not reject mismatched Page before component resolution')
  console.log('Verified Node rejects mismatched Page before rendering')
  writeFileSync(entry, bundle)
  await wait('bundle restart', () => renderer.transcript.split('SSR build verified:').length >= 3)
  await wait('restarted renderer health', async () => (await fetch(`http://127.0.0.1:${rendererPort}/health`)).ok)
  if (!(await (await fetch(base + '/users')).text()).includes('data-server-rendered')) throw new Error('SSR missing after watch restart')
  console.log('Verified Node --watch bundle restart and SSR recovery')
  await stop(renderer)
  await wait('DOWN', async () => (await health()).state === 'DOWN')
  if (!(await fetch(base + '/api/health')).ok) throw new Error('Java liveness failed with Node down')
  const fallback = await fetch(base + '/users')
  if (!fallback.ok || !(await fallback.text()).includes(`<div id="${rootId}"></div>`)) throw new Error('CSR fallback missing')
  console.log('Verified DOWN with Java liveness and CSR fallback')
  await browserCsp(port, true)
  renderer = startRenderer()
  await wait('UP after Node recovery', async () => (await health()).state === 'UP')
  if (!(await (await fetch(base + '/users')).text()).includes('data-server-rendered')) throw new Error('SSR missing after renderer recovery')
  console.log('Verified UP and SSR recovery without Java restart')
} finally {
  for (const child of processes.toReversed()) await stop(child)
  rmSync(copy, { recursive: true, force: true })
}
