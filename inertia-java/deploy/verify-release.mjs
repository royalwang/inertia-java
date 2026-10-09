import { spawn, execFileSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync, createWriteStream, existsSync, readdirSync, cpSync } from 'node:fs'
import { resolve } from 'node:path'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
import { packageRelease } from './release.mjs'

const root = fileURLToPath(new URL('..', import.meta.url))
const frontend = resolve(root, 'examples/spring-react/frontend')
const parent = resolve(process.env.INERTIA_DEPLOY_OUTPUT ?? tmpdir())
mkdirSync(parent, { recursive: true })
const output = mkdtempSync(resolve(parent, 'inertia-java-deploy-'))
const phases = []
const children = []
let ready
let interrupted = false
const evidence = { format: 1, success: false, startedAt: new Date().toISOString(), output, phases }
function start(command, args, cwd, name, extra = {}) {
  const log = createWriteStream(resolve(output, name + '.log'))
  const child = spawn(command, args, { cwd, env: { ...process.env, ...extra }, detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'pipe'] })
  child.transcript = ''; child.closed = false; child.log = name + '.log'; children.push(child)
  for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { log.write(data); child.transcript = (child.transcript + data.toString()).slice(-100000) })
  child.on('error', error => { child.failure = error })
  child.on('close', () => { child.closed = true; log.end() })
  return child
}
async function wait(label, predicate, ms = 30000) {
  const end = Date.now() + ms
  while (Date.now() < end) {
    if (interrupted) throw new Error('Interrupted')
    const failed = children.find(c => c.failure)
    if (failed) throw failed.failure
    if (await predicate()) return
    await delay(100)
  }
  throw new Error('Deadline: ' + label)
}
async function stop(child) {
  if (child.closed) return
  child.kill('SIGTERM')
  const end = Date.now() + 15000
  while (!child.closed && Date.now() < end) await delay(100)
  if (!child.closed) {
    if (process.platform === 'win32') child.kill('SIGKILL')
    else { try { process.kill(-child.pid, 'SIGKILL') } catch (error) { if (error.code !== 'ESRCH') throw error } }
    throw new Error('Release runtime did not gracefully exit; inspect child cleanup') }
}
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { interrupted = true })
async function run(name, command, args, cwd, extra = {}, expected = 0) {
  const child = start(command, args, cwd, name, extra)
  await wait(name, () => child.closed, 60000)
  phases.push({ name, exit: child.exitCode, log: child.log })
  if (child.exitCode !== expected) throw new Error(name + ' exit ' + child.exitCode)
  if (name.startsWith('browser-') && !child.transcript.includes('1 passed')) throw new Error(name + ' did not execute the required browser flow')
  return child
}
try {
  // Freeze built inputs. All subsequent publication checks target the same payload even
  // when a developer edits documentation or builds a different release concurrently.
  const inputs = resolve(output, 'inputs')
  const version = readFileSync(resolve(root, 'pom.xml'), 'utf8').match(/<version>([^<]+)<\/version>/)?.[1]
  const paths = ['pom.xml', 'README.md', 'docs', 'deploy/runtime.mjs', 'deploy/README.md', 'deploy/systemd',
    'examples/spring-react/frontend/dist', 'examples/spring-react/frontend/package.json',
    'examples/spring-react/frontend/package-lock.json', `examples/spring-react/target/spring-react-${version}.jar`]
  for (const module of ['inertia-core', 'inertia-ssr-http', 'inertia-vite', 'inertia-spring-webmvc', 'inertia-spring-boot-autoconfigure', 'inertia-spring-boot-starter', 'inertia-testing']) {
    paths.push(`${module}/pom.xml`)
    for (const classifier of ["", "-sources", "-javadoc"]) paths.push(`${module}/target/${module}-${version}${classifier}.jar`)
  }
  for (const path of paths) {
    const dest = resolve(inputs, path)
    mkdirSync(resolve(dest, '..'), { recursive: true })
    cpSync(resolve(root, path), dest, { recursive: true, dereference: false })
  }
  evidence.inputSnapshot = inputs
  const store = resolve(output, 'release store')
  const release = packageRelease(store, inputs)
  evidence.release = release
  evidence.manifest = JSON.parse(readFileSync(resolve(release, 'release.json'), 'utf8'))
  await run('preflight', process.execPath, ['runtime.mjs', 'check'], release)
  await run('production-dependencies', 'npm', ['ci', '--omit=dev', '--ignore-scripts', '--no-audit', '--no-fund'], resolve(release, 'frontend'))
  if (existsSync(resolve(release, 'frontend/node_modules/vite')) || existsSync(resolve(release, 'frontend/node_modules/@playwright/test'))) throw new Error('Dev dependency included in deployment')
  const again = packageRelease(store, inputs)
  if (again !== release || readdirSync(store).some(p => p.startsWith('.staging-'))) throw new Error('Idempotence/staging cleanup failed')
  phases.push({ name: 'idempotent-after-install', success: true })
  const runtime = start(process.execPath, ['runtime.mjs', 'pair'], release, 'runtime', { APP_PORT: '0', SSR_PORT: '0', APP_HOST: '127.0.0.1', INERTIA_ROOT_ID: 'app' })
  await wait('pair readiness', () => {
    if (runtime.closed) throw new Error('Runtime exited before ready; see ' + runtime.log)
    ready = runtime.transcript.match(/INERTIA_READY (\{[^\n]+\})/)?.[1]
    return Boolean(ready)
  })
  ready = JSON.parse(ready)
  evidence.ready = ready
  if (ready.buildId !== evidence.manifest.buildId || ready.releaseId !== evidence.manifest.releaseId) throw new Error('Readiness release mismatch')
  const base = `http://127.0.0.1:${ready.javaPort}`
  const ssr = await fetch(base + '/users', { signal: AbortSignal.timeout(5000) })
  if (!ssr.ok || !(await ssr.text()).includes('data-server-rendered')) throw new Error('SSR missing')
  await run('browser-ssr', process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--grep', 'SSR hydration, navigation'], frontend, {
    INERTIA_EXPECT_CSP: 'false', INERTIA_EXPECT_HISTORY: 'false', INERTIA_EXPECT_ALL_ERRORS: 'false', INERTIA_EXPECT_FAILURES: 'false',
    INERTIA_BASE_URL: base, INERTIA_EXPECT_CSR: 'false', INERTIA_E2E_OUTPUT: resolve(output, 'browser-ssr'),
  })
  // Identify the renderer owned by this runtime, not an unrelated listener.
  const childrenText = execFileSync('ps', ['-axo', 'pid=,ppid=,command='], { encoding: 'utf8' })
  const rendererLine = childrenText.split('\n').find(line => {
    const match = line.trim().match(/^(\d+)\s+(\d+)\s+(.*)$/)
    return match && Number(match[2]) === runtime.pid && match[3].includes('dist/ssr/ssr.js')
  })
  if (!rendererLine) throw new Error('Owned renderer PID missing')
  const rendererPid = Number(rendererLine.trim().split(/\s+/)[0])
  process.kill(rendererPid, 'SIGTERM')
  await wait('renderer down', async () => (await (await fetch(base + '/api/ssr-health', { signal: AbortSignal.timeout(1000) })).json()).state === 'DOWN')
  const csr = await fetch(base + '/users', { signal: AbortSignal.timeout(5000) })
  const body = await csr.text()
  if (!csr.ok || body.includes('data-server-rendered') || !body.includes('<div id="app"></div>')) throw new Error('CSR after renderer death missing')
  await run('browser-csr', process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--grep', 'CSR fallback mounts'], frontend, {
    INERTIA_EXPECT_CSP: 'false', INERTIA_EXPECT_HISTORY: 'false', INERTIA_EXPECT_ALL_ERRORS: 'false', INERTIA_EXPECT_FAILURES: 'false',
    INERTIA_BASE_URL: base, INERTIA_EXPECT_CSR: 'true', INERTIA_E2E_OUTPUT: resolve(output, 'browser-csr'),
  })
  if (!(await fetch(base + '/api/health', { signal: AbortSignal.timeout(1000) })).ok) throw new Error('Java liveness failed')
  phases.push({ name: 'renderer-down-keeps-java', success: true, rendererPid })
  const ownedJava = runtime.transcript.match(/Starting Application.*?PID (\d+)/)?.[1]
  await stop(runtime)
  if (ownedJava && execFileSync('ps', ['-axo', 'pid='], { encoding: 'utf8' }).split(/\s+/).includes(ownedJava)) throw new Error('Owned Java remained after shutdown')
  if (runtime.exitCode !== 143) throw new Error('Runtime shutdown code ' + runtime.exitCode)
  phases.push({ name: 'graceful-shutdown', exit: runtime.exitCode })
  const app = resolve(release, 'app.jar')
  const original = readFileSync(app)
  writeFileSync(app, Buffer.concat([original, Buffer.from('tampered')]))
  try {
    const rejected = await run('tampered-preflight', process.execPath, ['runtime.mjs', 'check'], release, {}, 1)
    if (!rejected.transcript.includes('Release inventory mismatch')) throw new Error('Wrong tamper rejection')
    let rejectedPublish = false
    try { packageRelease(store, inputs) } catch (error) { rejectedPublish = error.message.includes('Artifact inventory mismatch') }
    if (!rejectedPublish) throw new Error('Corrupt existing release was overwritten')
    phases.push({ name: 'corrupt-publish-refused', success: true })
  } finally { writeFileSync(app, original) }
  await run('restored-preflight', process.execPath, ['runtime.mjs', 'check'], release)
  evidence.success = true
} catch (error) { evidence.error = error.stack; process.exitCode = 1 }
finally {
  const cleanup = await Promise.allSettled(children.map(stop))
  const errors = cleanup.filter(r => r.status === 'rejected').map(r => String(r.reason))
  if (errors.length) { evidence.success = false; evidence.cleanupErrors = errors; process.exitCode = 1 }
  evidence.finishedAt = new Date().toISOString()
  writeFileSync(resolve(output, 'summary.json'), JSON.stringify(evidence, null, 2) + '\n')
  console.log('Deployment evidence: ' + resolve(output, 'summary.json'))
  if (!evidence.success) console.error(evidence.error)
}
