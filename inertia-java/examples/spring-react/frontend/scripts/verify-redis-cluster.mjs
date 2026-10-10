// Independent Maven consumer + real owned Redis, two JVMs, shared host session and existing React SSR.
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createServer as httpServer, request as httpRequest } from 'node:http'
import { createServer as tcpServer } from 'node:net'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, createWriteStream } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash, randomUUID } from 'node:crypto'
import { setTimeout as delay } from 'node:timers/promises'
import { chromium } from '@playwright/test'
const frontend = fileURLToPath(new URL('..', import.meta.url))
const javaRoot = resolve(frontend, '../../..')
const consumer = resolve(javaRoot, 'qualification/redis-cluster')
const redisExecutable = process.env.INERTIA_REDIS_SERVER
assert.ok(redisExecutable, 'INERTIA_REDIS_SERVER must name a real executable; no shared server/mock fallback')
const parent = process.env.INERTIA_REDIS_CLUSTER_OUTPUT ?? tmpdir(); mkdirSync(parent, { recursive: true })
const output = mkdtempSync(resolve(parent, 'inertia-redis-cluster-'))
const receipt = JSON.parse(readFileSync(resolve(frontend, 'dist/build.json')))
const jar = process.env.INERTIA_REDIS_CONSUMER_JAR ? resolve(process.env.INERTIA_REDIS_CONSUMER_JAR) : resolve(consumer, 'target/inertia-redis-qualification-0.1.0-SNAPSHOT.jar')
const summary = { success: false, startedAt: new Date().toISOString(), output, frontendBuild: receipt.buildId,
  consumerJarSha256: createHash('sha256').update(readFileSync(jar)).digest('hex'), phases: [], limits: ['standalone Redis only; no failover durability or browser exactly-once claim', 'fixture control routes belong only to this local qualification consumer'] }
const children = []
let proxy, browser, interrupted = false
function start(name, command, args, cwd = consumer, extraEnv = {}) {
  const child = spawn(command, args, { cwd, env: { ...process.env, ...extraEnv }, stdio: ['ignore', 'pipe', 'pipe'] })
  child.transcript = ''; child.fixtureName = name
  const log = createWriteStream(resolve(output, name + '.log'))
  for (const stream of [child.stdout, child.stderr]) stream.on('data', bytes => { log.write(bytes); child.transcript = (child.transcript + bytes.toString()).slice(-1024 * 1024) })
  child.on('error', error => { child.failure = error }); child.on('close', () => log.end()); children.push(child); return child
}
async function wait(name, predicate, timeout = 30000, cleanup = false) {
  const end = Date.now() + timeout
  while (Date.now() < end) {
    if (interrupted && !cleanup) throw new Error('Interrupted')
    if (await predicate()) return
    await delay(25)
  }
  throw new Error(name + ' deadline')
}
async function freePort() { const server = tcpServer(); await new Promise(done => server.listen(0, '127.0.0.1', done)); const port = server.address().port; await new Promise(done => server.close(done)); return port }
async function stop(child, signal = 'SIGTERM') {
  if (child.exitCode !== null || child.signalCode !== null) return
  child.kill(signal)
  try { await wait('owned process cleanup', () => child.exitCode !== null || child.signalCode !== null, 10000, true) }
  catch (error) { child.kill('SIGKILL'); throw error }
}
function alive(child) { if (child.failure) throw child.failure; if (child.exitCode !== null || child.signalCode !== null) throw new Error(child.fixtureName + ' exited before ready') }
const interrupt = () => { interrupted = true }
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, interrupt)
function client() {
  const cookies = new Map()
  return async (base, path, extra = {}) => {
    const response = await fetch(base + path, { redirect: 'manual', signal: AbortSignal.timeout(10000), ...extra,
      headers: { 'X-Inertia': 'true', 'X-Inertia-Version': receipt.buildId,
        Cookie: [...cookies].map(([key, value]) => key + '=' + value).join('; '), ...extra.headers } })
    for (const header of response.headers.getSetCookie()) { const [pair] = header.split(';'); const at = pair.indexOf('='); cookies.set(pair.slice(0, at), pair.slice(at + 1)) }
    const text = await response.text()
    return { status: response.status, node: response.headers.get('X-Qualification-Node'), body: text, json: () => JSON.parse(text) }
  }
}
try {
  const redisPort = await freePort(), rendererPort = await freePort()
  const redis = start('redis', redisExecutable, ['--bind', '127.0.0.1', '--port', String(redisPort), '--protected-mode', 'yes', '--save', '', '--appendonly', 'no', '--maxmemory', '64mb', '--maxmemory-policy', 'noeviction'], output)
  await wait('Redis', () => { alive(redis); return redis.transcript.includes('Ready to accept connections') })
  const ssr = start('ssr', 'node', ['dist/ssr/ssr.js'], frontend, { SSR_PORT: String(rendererPort) })
  await wait('SSR', async () => { alive(ssr); try { return (await fetch(`http://127.0.0.1:${rendererPort}/health`, { signal: AbortSignal.timeout(500) })).ok } catch { return false } })
  const hostNamespace = 'inertia-host-' + randomUUID()
  const instances = []
  for (const name of ['A', 'B']) {
    const child = start('java-' + name, 'java', ['-Xmx192m', '-jar', jar,
      '--server.address=127.0.0.1', '--server.port=0', '--spring.main.banner-mode=off',
      '--qualification.node=' + name, '--qualification.frontend=' + frontend, '--qualification.ssr=' + `http://127.0.0.1:${rendererPort}/render`,
      '--spring.data.redis.host=127.0.0.1', '--spring.data.redis.port=' + redisPort, '--spring.session.redis.namespace=' + hostNamespace,
      '--inertia.session.store=redis', '--inertia.session-namespace=delivery-proof', '--inertia.session.redis.port=' + redisPort,
      '--inertia.session.redis.command-timeout=500ms', '--inertia.session.redis.lease=8s', '--inertia.session.redis.idle-ttl=60s', '--inertia.session.redis.terminal-retention=30s',
      '--inertia.response-timeout=5s', '--inertia.props-timeout=3s'])
    let port
    await wait('JVM ' + name, () => { alive(child); port = child.transcript.match(/Tomcat started on port (\d+)/)?.[1]; return port })
    instances.push({ child, base: `http://127.0.0.1:${port}`, name })
  }
  const [a, b] = instances
  summary.processes = instances.map(i => ({ node: i.name, pid: i.child.pid }))
  const request = client()
  assert.equal((await request(a.base, '/fixture/seed')).status, 302)
  const first = await request(b.base, '/users'); assert.equal(first.status, 200); assert.equal(first.node, 'B')
  assert.equal(first.json().flash.toast, 'cross-node'); assert.equal(first.json().props.errors['form-a'].name, 'first'); assert.equal(first.json().props.errors['form-b'].email, 'required')
  assert.ok(!('flash' in (await request(a.base, '/users')).json()))
  summary.phases.push({ name: 'cross-jvm-redirect-flash-errors', success: true })
  const simultaneous = client(); await simultaneous(a.base, '/fixture/seed?value=one-reservation')
  const overlapping = await Promise.all([simultaneous(a.base, '/users'), simultaneous(b.base, '/users')])
  assert.ok(overlapping.every(r => r.status === 200)); assert.equal(overlapping.filter(r => r.json().flash?.toast === 'one-reservation').length, 1)
  summary.phases.push({ name: 'cross-jvm-overlapping-reservations', success: true })
  await request(a.base, '/fixture/seed?value=expired-lease'); assert.equal((await request(a.base, '/fixture/hold')).status, 200)
  await delay(8200)
  assert.equal((await request(b.base, '/users')).json().flash.toast, 'expired-lease')
  assert.equal((await request(a.base, '/fixture/complete')).status, 500)
  summary.phases.push({ name: 'lease-recovery-rejects-late-original-jvm', success: true })
  for (const operation of ['rotate', 'invalidate']) {
    await request(a.base, '/fixture/seed?value=must-not-cross-identity')
    assert.equal((await request(b.base, '/fixture/' + operation)).status, 200)
    const next = await request(a.base, '/users'); assert.equal(next.status, 200); assert.ok(!('flash' in next.json()))
  }
  summary.phases.push({ name: 'spring-session-identity-rotation-and-destruction', success: true })
  let cursor = 0
  proxy = httpServer((req, res) => {
    const target = req.method === 'POST' && req.url === '/users' ? a : req.method === 'GET' && req.url === '/users' ? b : instances[cursor++ % 2]
    const outgoing = httpRequest(new URL(req.url, target.base), { method: req.method, headers: req.headers }, incoming => {
      res.writeHead(incoming.statusCode, incoming.headers); incoming.pipe(res)
    })
    outgoing.on('error', () => { res.writeHead(502); res.end('Owned qualification upstream unavailable') }); req.pipe(outgoing)
  })
  await new Promise(done => proxy.listen(0, '127.0.0.1', done))
  const publicBase = `http://127.0.0.1:${proxy.address().port}`
  browser = await chromium.launch({ headless: true, channel: process.env.INERTIA_BROWSER_CHANNEL ?? 'chromium' })
  const context = await browser.newContext(), page = await context.newPage(), browserErrors = [], nodeVisits = new Set()
  page.on('pageerror', error => browserErrors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') browserErrors.push(message.text()) })
  page.on('response', response => { const node = response.headers()['x-qualification-node']; if (node) nodeVisits.add(node) })
  const initial = await page.goto(publicBase + '/users'); assert.equal(initial.status(), 200); assert.ok((await initial.text()).includes('data-server-rendered'))
  await page.getByTestId('stats').waitFor(); await page.getByLabel('Name', { exact: true }).fill('Two JVMs')
  await page.getByRole('button', { name: 'Save demo name' }).click(); await page.getByRole('status').filter({ hasText: 'Saved Two JVMs across nodes' }).waitFor()
  await page.getByRole('link', { name: 'About this app' }).click(); await page.getByRole('heading', { name: 'About this app' }).waitFor()
  await page.getByRole('link', { name: 'Back to users' }).click(); await page.getByTestId('stats').waitFor()
  assert.equal(await page.getByRole('status').count(), 0)
  await page.getByLabel('Name', { exact: true }).fill(''); await page.getByRole('button', { name: 'Save demo name' }).click(); await page.getByRole('alert').filter({ hasText: 'Please enter a name.' }).waitFor()
  assert.equal(nodeVisits.size, 2); assert.deepEqual(browserErrors, [])
  await context.close(); await browser.close(); browser = undefined
  await new Promise(done => proxy.close(done)); proxy = undefined
  summary.phases.push({ name: 'round-robin-browser-ssr-hydration-csrf-form-flash-validation-deferred', nodes: [...nodeVisits], success: true })
  await request(a.base, '/fixture/seed?value=crash-recovery'); await request(a.base, '/fixture/hold')
  await stop(a.child, 'SIGKILL'); await delay(8200)
  const afterCrash = await request(b.base, '/users'); assert.equal(afterCrash.status, 200); assert.equal(afterCrash.json().flash.toast, 'crash-recovery')
  assert.ok(!('flash' in (await request(b.base, '/users')).json()))
  summary.phases.push({ name: 'owned-jvm-crash-and-lease-recovery', success: true })
  summary.success = true
} catch (error) { summary.error = error.stack; process.exitCode = 1 }
finally {
  if (browser) await browser.close()
  if (proxy) await new Promise(done => proxy.close(done))
  for (const child of [...children].reverse()) try { await stop(child) } catch (error) { summary.success = false; summary.cleanupError = error.stack; process.exitCode = 1 }
  for (const signal of ['SIGTERM', 'SIGINT']) process.off(signal, interrupt)
  summary.finishedAt = new Date().toISOString(); writeFileSync(resolve(output, 'summary.json'), JSON.stringify(summary, null, 2) + '\n')
  console.log('Redis cluster evidence: ' + resolve(output, 'summary.json'))
}
