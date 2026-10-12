import assert from 'node:assert/strict'
import { createHash, randomBytes } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
import { chromium, expect } from '@playwright/test'
import { rehearsal, freePort } from '../../../../docs/scripts/processes.mjs'

const framework = process.env.INERTIA_AUTH_FRAMEWORK ?? 'react'
const csr = process.env.INERTIA_AUTH_CSR === 'true'
assert.ok(['react', 'vue'].includes(framework), 'Unsupported authentication example')
const frontend = fileURLToPath(new URL(`../../../spring-${framework}/frontend/`, import.meta.url))
const example = resolve(frontend, '..')
const check = rehearsal(`inertia-${framework}-auth`)
const jar = process.env.INERTIA_AUTH_CONSUMER_JAR ?? resolve(example, `target/spring-${framework}-0.1.0-SNAPSHOT.jar`)
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
check.evidence.jarSha256 = hash(readFileSync(jar))
check.evidence.driverSha256 = hash(readFileSync(fileURLToPath(import.meta.url)))
check.evidence.buildId = JSON.parse(readFileSync(resolve(frontend, 'dist/build.json'))).buildId
check.evidence.node = process.version
check.evidence.scope = `${framework}, ${csr ? 'CSR authentication; idle expiry qualified separately in SSR' : 'SSR authentication and real idle expiry'}, single JVM, native Servlet session; independent consumer only when explicitly supplied`
const password = randomBytes(24).toString('hex')
let browser, error
try {
  const ssrPort = await freePort()
  const renderer = check.start(process.execPath, ['dist/ssr/ssr.js'], frontend, 'ssr', { SSR_PORT: String(ssrPort) })
  await check.wait('SSR ready', async () => {
    try { return (await fetch(`http://127.0.0.1:${ssrPort}/health`, { signal: AbortSignal.timeout(500) })).ok }
    catch { return false }
  })
  if (csr) await check.stop(renderer)
  const java = check.start('java', ['-Dinertia.frontend=' + frontend,
    '-Dinertia.ssr=' + `http://127.0.0.1:${ssrPort}/render`, '-jar', jar,
    '--server.address=127.0.0.1', '--server.port=0', '--spring.main.banner-mode=off',
    '--inertia.demo-auth=true', '--server.servlet.session.timeout=1m'], example, 'java', { INERTIA_DEMO_PASSWORD: password })
  let port
  await check.wait('Java ready', () => {
    if (java.closed) throw new Error('Java exited; inspect owned process log')
    port = java.transcript.match(/Tomcat started on port (\d+)/)?.[1]
    return port
  })
  const base = `http://127.0.0.1:${port}`
  const html = await (await fetch(base + '/login')).text()
  assert.equal(html.includes('data-server-rendered'), !csr)
  assert.ok(csr ? html.includes('data-page=') && html.includes('id="app"') : html.includes('Sign in'))
  const anonymous = await fetch(base + '/account', { redirect: 'manual', headers: { 'X-Inertia': 'true' } })
  assert.equal(anonymous.status, 409)
  assert.equal(anonymous.headers.get('X-Inertia-Location'), '/login')
  check.evidence.phases.push({ name: (csr ? 'csr' : 'ssr') + '-login-and-anonymous-protection', success: true })
  browser = await chromium.launch({ channel: process.env.INERTIA_BROWSER_CHANNEL ?? 'chromium' })
  check.evidence.browser = browser.version()
  const context = await browser.newContext(), page = await context.newPage(), errors = []
  for (const target of [page]) target.on('pageerror', failure => errors.push(failure.message))
  await page.goto(base + '/login')
  await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
  await page.getByLabel('Username', { exact: true }).fill('demo')
  await page.getByLabel('Password', { exact: true }).fill('incorrect-private-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('Sign-in failed. Check your credentials and try again.')
  await expect(page.getByLabel('Password', { exact: true })).toHaveValue('')
  assert.ok(!(await page.content()).includes('incorrect-private-password'))
  await page.reload()
  await expect(page.getByRole('alert')).toHaveCount(0)
  check.evidence.phases.push({ name: 'failure-error-consumed-and-password-cleared', success: true })
  const cookie = async name => (await context.cookies(base)).find(value => value.name === name)?.value
  const oldSession = await cookie('JSESSIONID'), oldCsrf = await cookie('XSRF-TOKEN')
  assert.ok(oldSession && oldCsrf)
  await page.getByLabel('Username', { exact: true }).fill('demo')
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByTestId('identity')).toHaveText('Signed in as demo')
  assert.notEqual(await cookie('JSESSIONID'), oldSession)
  assert.notEqual(await cookie('XSRF-TOKEN'), oldCsrf)
  const deniedOld = await fetch(base + '/account', { redirect: 'manual', headers: { 'X-Inertia': 'true', Cookie: `JSESSIONID=${oldSession}` } })
  assert.equal(deniedOld.status, 409)
  const forbidden = await context.request.get(base + '/admin', { headers: { 'X-Inertia': 'true' }, maxRedirects: 0 })
  assert.equal(forbidden.status(), 403)
  const rejected = await context.request.post(base + '/logout', { headers: { 'X-Inertia': 'true', 'X-XSRF-TOKEN': oldCsrf }, maxRedirects: 0 })
  assert.equal(rejected.status(), 303)
  assert.equal(rejected.headers().location, '/account')
  await page.reload()
  await expect(page.getByTestId('identity')).toHaveText('Signed in as demo')
  await expect(page.getByRole('alert')).toHaveText('Your security token changed. Review your form and submit again.')
  check.evidence.phases.push({ name: 'identity-rotation-old-session-and-csrf-rejected', success: true })
  const second = await context.newPage()
  second.on('pageerror', failure => errors.push(failure.message))
  await second.goto(base + '/account')
  await expect(second.getByTestId('identity')).toBeVisible()
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
  await expect(second.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
  const after = await context.request.get(base + '/account', { headers: { 'X-Inertia': 'true' }, maxRedirects: 0 })
  assert.equal(after.status(), 409)
  await page.goBack()
  await expect(page.getByTestId('identity')).toHaveCount(0)
  check.evidence.phases.push({ name: 'logout-cross-tab-and-history', success: true })
  let finalPage = page
  if (!csr) {
    const expiryPage = await context.newPage()
    expiryPage.on('pageerror', failure => errors.push(failure.message))
    await expiryPage.goto(base + '/login')
    await expiryPage.getByLabel('Username', { exact: true }).fill('demo')
    await expiryPage.getByLabel('Password', { exact: true }).fill(password)
    await expiryPage.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(expiryPage.getByTestId('identity')).toHaveText('Signed in as demo')
    // No requests during the idle interval: native Tomcat sessions expire at minute granularity.
    await delay(65000)
    await expiryPage.reload()
    await expect(expiryPage.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
    await expect(expiryPage.getByTestId('identity')).toHaveCount(0)
    check.evidence.phases.push({ name: 'actual-idle-session-expiration-and-role-denial', success: true })
    finalPage = expiryPage
  }
  assert.deepEqual(errors, [])
  assert.ok(!java.transcript.includes(password), 'Credentials must not appear in Java log')
  await finalPage.screenshot({ path: resolve(check.output, `${framework}-auth.png`), fullPage: true })
  check.evidence.phases.push({ name: 'browser-errors-privacy-and-screenshot', success: true })
  check.evidence.success = true
} catch (caught) { error = caught }
finally { if (browser) await browser.close(); await check.finish(error) }
