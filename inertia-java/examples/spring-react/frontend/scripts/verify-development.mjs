import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync, createWriteStream } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'

const frontend = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.INERTIA_HEALTH_OUTPUT ?? mkdtempSync(resolve(tmpdir(), 'inertia-development-'))
mkdirSync(output, { recursive: true })
const hot = resolve(frontend, '.inertia/hot')
if (existsSync(hot)) throw new Error('Existing Vite hot file: stop its owner before running the development verifier')
const children = []
const stages = []
let failure
let ownedHot
let interrupted = false
async function port() {
  const server = createServer()
  await new Promise(done => server.listen(0, '127.0.0.1', done))
  const value = server.address().port
  await new Promise(done => server.close(done))
  return value
}
function start(name, command, args, env) {
  const log = createWriteStream(resolve(output, name + '.log'))
  const child = spawn(command, args, { cwd: frontend, env: { ...process.env, ...env },
    detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'pipe'] })
  child.transcript = ''
  child.stdout.on('data', bytes => { log.write(bytes); child.transcript = (child.transcript + bytes).slice(-64000) })
  child.stderr.on('data', bytes => log.write(bytes))
  child.on('error', error => { child.failure = error })
  child.done = new Promise(done => child.on('close', (code, signal) => { log.end(); done({ code, signal }) }))
  children.push(child)
  return child
}
async function stop(child) {
  if (child.pid == null) { await child.done; return }
  if (child.exitCode !== null || child.signalCode !== null) return
  const signal = value => {
    try { process.platform === 'win32' ? child.kill(value) : process.kill(-child.pid, value) }
    catch (error) { if (error.code !== 'ESRCH') throw error }
  }
  signal('SIGTERM')
  const deadline = Date.now() + 5000
  while (child.exitCode === null && child.signalCode === null && Date.now() < deadline) await delay(50)
  if (child.exitCode === null && child.signalCode === null) signal('SIGKILL')
  await child.done
}
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => {
  interrupted = true
  void Promise.all(children.map(stop))
})
async function wait(label, predicate) {
  const deadline = Date.now() + 30000
  while (Date.now() < deadline) {
    if (interrupted) throw new Error('Development verification interrupted')
    for (const child of children) {
      if (child.failure) throw child.failure
      if (child.exitCode !== null || child.signalCode !== null) throw new Error('Owned process stopped before ' + label)
    }
    if (await predicate()) return
    await delay(100)
  }
  throw new Error('Deadline exceeded: ' + label)
}
try {
  const appPort = await port()
  const vitePort = await port()
  const base = `http://127.0.0.1:${appPort}`
  ownedHot = `http://127.0.0.1:${vitePort}`
  const vite = start('vite', process.execPath, ['node_modules/vite/bin/vite.js', '--port', String(vitePort)], {
    INERTIA_DEV_APP_ORIGIN: base, SSR_ROOT_ID: 'app',
  })
  await wait('owned Vite hot file', async () => existsSync(hot) && readFileSync(hot, 'utf8') === ownedHot)
  const java = start('java', 'java', ['-Dinertia.development=true', '-Dinertia.frontend=' + frontend, '-jar',
    '../target/spring-react-0.1.0-SNAPSHOT.jar', '--server.address=127.0.0.1', '--server.port=' + appPort], {})
  await wait('Java startup', async () => java.transcript.includes('Tomcat started on port ' + appPort))
  await wait('real development SSR', async () => {
    const response = await fetch(base + '/users', { signal: AbortSignal.timeout(3000) })
    const body = await response.text()
    return response.ok && body.includes('data-server-rendered="true"') && body.includes('<li>Ada</li>')
      && body.includes(ownedHot + '/@vite/client')
  })
  stages.push({ name: 'vite-ssr-html', success: true })
  const tests = start('browser', process.execPath, ['node_modules/@playwright/test/cli.js', 'test',
    'e2e/flows.spec.ts', 'e2e/advanced.spec.ts'], {
    INERTIA_BASE_URL: base, INERTIA_E2E_OUTPUT: resolve(output, 'e2e'),
    INERTIA_EXPECT_CSR: 'false', INERTIA_EXPECT_CSP: 'false', INERTIA_EXPECT_HISTORY: 'false',
    INERTIA_EXPECT_ALL_ERRORS: 'false', INERTIA_EXPECT_FAILURES: 'false',
    INERTIA_EXPECT_AUTH: 'false', INERTIA_EXPECT_AUTH_EXPIRY: 'false',
  })
  const result = await tests.done
  stages.push({ name: 'development-browser', ...result })
  if (result.code !== 0) throw new Error('Development browser failed; see ' + output)
  console.log('Verified actual Vite development SSR, hydration, forms and advanced client state')
} catch (error) {
  failure = error.message
  process.exitCode = 1
} finally {
  for (const child of children.toReversed()) await stop(child)
  if (ownedHot && existsSync(hot) && readFileSync(hot, 'utf8') === ownedHot) rmSync(hot)
  writeFileSync(resolve(output, 'summary.json'), JSON.stringify({ format: 1, success: !failure,
    failure, stages, finishedAt: new Date().toISOString() }, null, 2) + '\n')
  console.log('Development evidence: ' + output)
  if (failure) console.error(failure)
}
