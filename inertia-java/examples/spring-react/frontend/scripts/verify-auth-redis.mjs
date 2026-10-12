import assert from 'node:assert/strict'
import { createHash, randomBytes, randomUUID } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer as tcpServer, connect } from 'node:net'
import { createServer as httpServer, request as httpRequest } from 'node:http'
import { setTimeout as delay } from 'node:timers/promises'
import { chromium, expect } from '@playwright/test'
import { rehearsal, freePort } from '../../../../docs/scripts/processes.mjs'

const framework = process.env.INERTIA_AUTH_FRAMEWORK ?? 'react'
assert.ok(['react', 'vue'].includes(framework))
const frontend = fileURLToPath(new URL(`../../../spring-${framework}/frontend/`, import.meta.url))
const jar = process.env.INERTIA_AUTH_CONSUMER_JAR
const executable = process.env.INERTIA_REDIS_SERVER
assert.ok(jar && executable, 'Supply independent auth consumer JAR and real Redis executable')
const check = rehearsal(`inertia-${framework}-redis-auth`)
const sha = bytes => createHash('sha256').update(bytes).digest('hex')
check.evidence.jarSha256 = sha(readFileSync(jar))
check.evidence.driverSha256 = sha(readFileSync(fileURLToPath(import.meta.url)))
check.evidence.redisExecutableSha256 = sha(readFileSync(executable))
check.evidence.buildId = JSON.parse(readFileSync(resolve(frontend, 'dist/build.json'))).buildId
check.evidence.scope = `${framework}; actual Spring Session Redis; two JVMs; standalone TCP; no failover claim`
let browser, error, gateway, transport
const sockets = new Set()
let blocked = false
try {
  const redisPort = await freePort(), ssrPort = await freePort()
  const redisArgs = ['--bind', '127.0.0.1', '--port', String(redisPort), '--protected-mode', 'yes', '--save', '', '--appendonly', 'no', '--maxmemory', '64mb', '--maxmemory-policy', 'noeviction']
  let redis = check.start(executable, redisArgs, check.output, 'redis')
  await check.wait('Redis ready', () => redis.transcript.includes('Ready to accept connections'))
  transport = tcpServer(client => {
    if (blocked) { client.destroy(); return }
    const upstream = connect(redisPort, '127.0.0.1')
    for (const socket of [client, upstream]) {
      sockets.add(socket); socket.on('close', () => sockets.delete(socket))
      socket.on('error', () => { client.destroy(); upstream.destroy() })
    }
    client.pipe(upstream); upstream.pipe(client)
  })
  await new Promise(done => transport.listen(0, '127.0.0.1', done))
  const deliveryPort = transport.address().port
  check.start(process.execPath, ['dist/ssr/ssr.js'], frontend, 'ssr', { SSR_PORT: String(ssrPort) })
  await check.wait('SSR ready', async () => {
    try { return (await fetch(`http://127.0.0.1:${ssrPort}/health`, { signal: AbortSignal.timeout(500) })).ok }
    catch { return false }
  })
  browser = await chromium.launch({ channel: process.env.INERTIA_BROWSER_CHANNEL ?? 'chromium' })
  check.evidence.browser = browser.version()
  for (const fixation of ['new-session', 'change-session-id']) {
    const password = randomBytes(24).toString('hex'), namespace = 'auth-' + randomUUID()
    const instances = []
    for (const node of ['A', 'B']) {
      const child = check.start('java', ['-Dinertia.frontend=' + frontend,
        '-Dinertia.ssr=' + `http://127.0.0.1:${ssrPort}/render`, '-jar', jar,
        '--server.address=127.0.0.1', '--server.port=0', '--spring.main.banner-mode=off',
        '--inertia.demo-auth=true', '--inertia.demo-fixation=' + fixation,
        '--qualification.auth=true', '--spring.session.redis.namespace=' + namespace,
        '--spring.data.redis.port=' + redisPort, '--spring.session.timeout=120s',
        '--inertia.session.store=redis', '--inertia.session-namespace=auth-delivery',
        '--inertia.session.redis.port=' + deliveryPort, '--inertia.session.redis.command-timeout=500ms',
        '--inertia.session.redis.idle-ttl=5m', '--inertia.session.redis.lease=60s'], resolve(frontend, '..'), fixation + '-' + node,
        { INERTIA_DEMO_PASSWORD: password })
      let port
      await check.wait('JVM ' + node, () => {
        if (child.closed) throw new Error('JVM exited; see owned log: ' + fixation + '-' + node)
        port = child.transcript.match(/Tomcat started on port (\d+)/)?.[1]
        return port
      })
      instances.push({ child, node, base: `http://127.0.0.1:${port}` })
    }
    const [a, b] = instances
    let cursor = 0
    gateway = httpServer((req, res) => {
      const path = req.url.split('?')[0]
      const target = path.startsWith('/probe/') || (path === '/login' && req.method === 'POST') ? a
        : path === '/account' || path === '/logout' || path === '/login' ? b : instances[cursor++ % 2]
      const outgoing = httpRequest(new URL(req.url, target.base), { method: req.method, headers: req.headers }, incoming => {
        res.writeHead(incoming.statusCode, { ...incoming.headers, 'X-Qualification-Node': target.node }); incoming.pipe(res)
      })
      outgoing.on('error', () => { res.writeHead(502); res.end('Owned upstream unavailable') }); req.pipe(outgoing)
    })
    await new Promise(done => gateway.listen(0, '127.0.0.1', done))
    const base = `http://127.0.0.1:${gateway.address().port}`
    const context = await browser.newContext(), page = await context.newPage(), errors = []
    const requests = []
    check.evidence.lastRequests = requests
    page.on('response', response => {
      requests.push({ method: response.request().method(), path: new URL(response.url()).pathname, status: response.status(), node: response.headers()['x-qualification-node'] })
      if (requests.length > 30) requests.shift()
    })
    page.on('pageerror', failure => errors.push(failure.message))
    const post = async (path, form = {}) => {
      const csrf = (await context.cookies(base)).find(cookie => cookie.name === 'XSRF-TOKEN')?.value
      return context.request.post(base + path, { form, headers: { 'X-Inertia': 'true', 'X-XSRF-TOKEN': decodeURIComponent(csrf ?? '') }, maxRedirects: 0 })
    }
    const account = () => context.request.get(base + '/account', { headers: { 'X-Inertia': 'true', 'X-Inertia-Version': check.evidence.buildId }, maxRedirects: 0 })
    await page.goto(base + '/login')
    await page.getByLabel('Username', { exact: true }).fill('demo')
    await page.getByLabel('Password', { exact: true }).fill('incorrect-password')
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page.getByRole('alert')).toHaveText('Sign-in failed. Check your credentials and try again.')
    await page.reload()
    await expect(page.getByRole('alert')).toHaveCount(0)
    assert.equal((await account()).status(), 409)
    check.evidence.phases.push({ name: fixation + '-cross-node-login-failure-consumed', success: true })
    assert.equal((await post('/probe/seed')).status(), 200)
    const held = await (await post('/probe/hold')).json()
    const oldCookies = (await context.cookies(base)).map(c => c.name + '=' + c.value).join('; ')
    await page.getByLabel('Username', { exact: true }).fill('demo')
    await page.getByLabel('Password', { exact: true }).fill(password)
    const accountReply = page.waitForResponse(response => response.url().endsWith('/account') && response.headers()['content-type']?.includes('application/json'))
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    const envelope = await (await accountReply).json()
    await expect(page.getByTestId('identity')).toHaveText('Signed in as demo')
    assert.equal(envelope.clearHistory, true); assert.equal(envelope.encryptHistory, true); assert.ok(!envelope.flash)
    const state = await (await context.request.get(base + '/probe/state')).json()
    assert.equal(state.authenticated, true); assert.equal(state.marker, fixation === 'change-session-id')
    const stale = await fetch(base + '/account', { redirect: 'manual', headers: { 'X-Inertia': 'true', Cookie: oldCookies } })
    assert.equal(stale.status, 409)
    assert.equal((await (await post('/probe/late?handle=' + held.handle)).json()).rejected, true)
    assert.ok(!(await (await account()).json()).flash)
    check.evidence.phases.push({ name: fixation + '-cross-node-success-rotation-and-old-reservation-rejected', success: true })
    await post('/probe/seed'); const logoutHeld = await (await post('/probe/hold')).json()
    const other = await context.newPage(); await other.goto(base + '/account')
    await expect(other.getByTestId('identity')).toBeVisible()
    await page.getByRole('button', { name: 'Sign out', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
    await expect(other.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
    assert.equal((await account()).status(), 409)
    assert.equal((await (await post('/probe/late?handle=' + logoutHeld.handle)).json()).rejected, true)
    check.evidence.phases.push({ name: fixation + '-cross-node-logout-tabs-and-late-writer-rejected', success: true })
    // Keep the host repository healthy while delivery Redis becomes unreachable.
    blocked = true; for (const socket of sockets) socket.destroy()
    const failed = await post('/login', { username: 'demo', password })
    assert.equal(failed.status(), 500); assert.notEqual(failed.headers().location, '/account')
    blocked = false
    assert.equal((await account()).status(), 409)
    // Reconciliation is explicit: a subsequent login is a new request, not mutation replay.
    const recovered = await post('/login', { username: 'demo', password })
    assert.equal(recovered.status(), 303)
    assert.equal(recovered.headers().location, '/account')
    assert.equal((await account()).status(), 200)
    check.evidence.phases.push({ name: fixation + '-delivery-disconnect-no-false-login-and-recovery', success: true })
    await page.goto(base + '/account')
    await post('/probe/seed')
    blocked = true; for (const socket of sockets) socket.destroy()
    const failedLogout = await post('/logout')
    assert.equal(failedLogout.status(), 500); assert.notEqual(failedLogout.headers().location, '/login')
    blocked = false
    await context.request.get(base + '/login') // obtain a current token after security failure
    const retriedLogout = await post('/logout')
    assert.equal(retriedLogout.status(), 303)
    assert.equal((await account()).status(), 409)
    check.evidence.phases.push({ name: fixation + '-failed-revocation-not-reported-as-logout-and-reconciliation', success: true })
    await context.request.get(base + '/login')
    assert.equal((await post('/login', { username: 'demo', password })).status(), 303)
    const raceCookies = (await context.cookies(base)).map(c => c.name + '=' + c.value).join('; ')
    const raceCsrf = (await context.cookies(base)).find(c => c.name === 'XSRF-TOKEN').value
    const concurrentPost = (path, form) => fetch(base + path, {
      method: 'POST', redirect: 'manual', body: new URLSearchParams(form),
      headers: { Cookie: raceCookies, 'X-Inertia': 'true', 'X-XSRF-TOKEN': decodeURIComponent(raceCsrf), 'Content-Type': 'application/x-www-form-urlencoded' },
      signal: AbortSignal.timeout(10000)
    })
    const race = await Promise.all([concurrentPost('/login', { username: 'demo', password }), concurrentPost('/logout', {})])
    assert.ok(race.every(response => [303, 500].includes(response.status)))
    const oldAfterRace = await fetch(base + '/account', { redirect: 'manual', headers: { Cookie: raceCookies, 'X-Inertia': 'true' } })
    assert.equal(oldAfterRace.status, 409)
    check.evidence.phases.push({ name: fixation + '-concurrent-login-logout-never-revives-old-identity', success: true })
    await context.request.get(base + '/login')
    assert.equal((await post('/login', { username: 'demo', password })).status(), 303)
    const expiryReply = await post('/probe/expire')
    assert.equal(expiryReply.status(), 200)
    const expiring = await expiryReply.json()
    await delay(3100)
    assert.equal((await account()).status(), 409)
    await context.request.get(base + '/login')
    assert.equal((await (await post('/probe/late?handle=' + expiring.handle)).json()).rejected, true)
    check.evidence.phases.push({ name: fixation + '-actual-host-expiry-and-late-delivery-rejected', success: true })
    // Real Redis restart loses both repositories; old cookies must not recover authentication.
    await context.request.get(base + '/login')
    assert.equal((await post('/login', { username: 'demo', password })).status(), 303)
    assert.equal((await account()).status(), 200)
    await check.stop(redis)
    redis = check.start(executable, redisArgs, check.output, 'redis-restart-' + fixation)
    await check.wait('Redis restart', () => redis.transcript.includes('Ready to accept connections'))
    await check.wait('host reconnect and lost-session denial', async () => {
      try { return (await account()).status() === 409 } catch { return false }
    })
    check.evidence.phases.push({ name: fixation + '-real-redis-restart-old-authentication-denied', success: true })
    assert.deepEqual(errors, [])
    assert.ok(instances.every(i => !i.child.transcript.includes(password)))
    await context.close()
    await new Promise(done => gateway.close(done)); gateway = undefined
    for (const i of instances) await check.stop(i.child)
  }
  check.evidence.success = true
} catch (caught) { error = caught }
finally {
  if (browser) await browser.close()
  if (gateway) await new Promise(done => gateway.close(done))
  for (const socket of sockets) socket.destroy()
  if (transport) await new Promise(done => transport.close(done))
  await check.finish(error)
}
