import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { mkdirSync, createWriteStream } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
import { chromium } from '@playwright/test'

const frontend = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.INERTIA_MATRIX_OUTPUT ?? '/tmp/inertia-java-browser-matrix'
mkdirSync(output, { recursive: true })
const children = []
let interrupted = false
function start(command, args, options, name) {
  const log = createWriteStream(resolve(output, name + '.log'))
  const child = spawn(command, args, { ...options, detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'pipe'] })
  children.push(child)
  child.transcript = ''
  child.stdout.on('data', data => { log.write(data); child.transcript = (child.transcript + data.toString()).slice(-32000) })
  child.stderr.on('data', data => log.write(data))
  child.on('error', error => { child.failure = error })
  child.on('close', () => log.end())
  return child
}
async function wait(label, predicate) {
  const deadline = Date.now() + 30000
  while (Date.now() < deadline) {
    if (interrupted) throw new Error('Browser matrix interrupted')
    for (const child of children) if (child.failure) throw child.failure
    if (await predicate()) return
    await delay(100)
  }
  throw new Error('Deadline exceeded: ' + label)
}
async function stop(child) {
  if (child.exitCode !== null || child.signalCode !== null) return
  const signal = value => {
    try { process.platform === 'win32' ? child.kill(value) : process.kill(-child.pid, value) }
    catch (error) { if (error.code !== 'ESRCH') throw error }
  }
  signal('SIGTERM')
  const deadline = Date.now() + 5000
  while (child.exitCode === null && child.signalCode === null && Date.now() < deadline) await delay(50)
  if (child.exitCode === null && child.signalCode === null) signal('SIGKILL')
}
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => {
  interrupted = true
  void Promise.all(children.map(stop))
})
const reserve = createServer()
await new Promise(done => reserve.listen(0, '127.0.0.1', done))
const port = reserve.address().port
await new Promise(done => reserve.close(done))
let renderer
try {
  const browser = await chromium.launch({ channel: process.env.INERTIA_BROWSER_CHANNEL ?? 'chrome' })
  console.log('Browser matrix runtime: ' + browser.version())
  await browser.close()
  renderer = start(process.execPath, ['dist/ssr/ssr.js'], { cwd: frontend, env: { ...process.env, SSR_PORT: String(port), SSR_ROOT_ID: 'app' } }, 'node')
  await wait('Node health', async () => {
    if (renderer.exitCode !== null) throw new Error('Node exited during startup')
    try { return (await fetch(`http://127.0.0.1:${port}/health`, { signal: AbortSignal.timeout(1000) })).ok } catch { return false }
  })
  for (const scenario of [
    { name: 'ssr', args: [], env: {} },
    { name: 'all-errors', args: ['--inertia.all-errors=true'], env: { INERTIA_EXPECT_ALL_ERRORS: 'true' } },
    { name: 'failures', args: ['--inertia.demo-failures=true'], env: { INERTIA_EXPECT_FAILURES: 'true' } },
    { name: 'namespace', args: ['--inertia.demo-failures=true', '--inertia.session-namespace=portal'], env: { INERTIA_EXPECT_FAILURES: 'true' } },
    { name: 'auth', args: ['--inertia.demo-auth=true', '--inertia.demo-password=local-demo-test-password'], env: { INERTIA_EXPECT_AUTH: 'true' } },
    { name: 'auth-expiry', args: ['--inertia.demo-auth=true', '--inertia.demo-password=local-demo-test-password', '--server.servlet.session.timeout=1m'], env: { INERTIA_EXPECT_AUTH: 'true', INERTIA_EXPECT_AUTH_EXPIRY: 'true' } },
    { name: 'csr-failures', args: ['--inertia.demo-failures=true'], env: { INERTIA_EXPECT_CSR: 'true', INERTIA_EXPECT_FAILURES: 'true' }, csr: true },
    { name: 'auth-csr', args: ['--inertia.demo-auth=true', '--inertia.demo-password=local-demo-test-password'], env: { INERTIA_EXPECT_AUTH: 'true', INERTIA_EXPECT_CSR: 'true' }, csr: true },
  ]) {
    if (scenario.csr) await stop(renderer)
    const java = start('java', [`-Dinertia.ssr=http://127.0.0.1:${port}/render`, '-jar',
      'target/spring-react-0.1.0-SNAPSHOT.jar', '--server.address=127.0.0.1', '--server.port=0', ...scenario.args],
      { cwd: resolve(frontend, '..') }, 'java-' + scenario.name)
    let javaPort
    try {
      await wait('Java ' + scenario.name, () => {
        if (java.exitCode !== null) throw new Error('Java exited during startup: ' + scenario.name)
        javaPort = java.transcript.match(/Tomcat started on port (\d+)/)?.[1]
        return Boolean(javaPort)
      })
      // Explicitly reset mode flags so caller environment cannot silently skip this matrix.
      const tests = start(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', 'e2e/flows.spec.ts', 'e2e/auth.spec.ts', 'e2e/advanced.spec.ts'], {
        cwd: frontend, env: { ...process.env, INERTIA_BASE_URL: `http://127.0.0.1:${javaPort}`,
          INERTIA_EXPECT_AUTH_EXPIRY: 'false', INERTIA_EXPECT_AUTH: 'false', INERTIA_EXPECT_CSR: 'false', INERTIA_EXPECT_ALL_ERRORS: 'false', INERTIA_EXPECT_FAILURES: 'false',
          ...scenario.env, INERTIA_E2E_OUTPUT: resolve(output, scenario.name) },
      }, 'browser-' + scenario.name)
      const code = await new Promise((done, reject) => { tests.on('error', reject); tests.on('close', done) })
      if (code !== 0) throw new Error('Browser matrix failed: ' + scenario.name + '; see ' + output)
      console.log('Verified browser matrix: ' + scenario.name)
    } finally { await stop(java) }
  }
} finally {
  for (const child of children.toReversed()) await stop(child)
}
