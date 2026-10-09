import { chromium } from '@playwright/test'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { rehearsal, freePort } from './processes.mjs'

const docs = fileURLToPath(new URL('..', import.meta.url))
const java = resolve(docs, '..')
const check = rehearsal('inertia-first-app')
let browser, error
try {
  const app = resolve(check.output, 'application')
  check.evidence.application = app
  await check.run(process.execPath, ['scripts/create-first-application.mjs', app], docs, 'create')
  await check.run(resolve(java, 'mvnw'), ['-f', resolve(app, 'pom.xml'), 'package'], app, 'maven')
  const frontend = resolve(app, 'frontend')
  await check.run('npm', ['ci', '--no-audit', '--no-fund'], frontend, 'npm')
  await check.run('npm', ['run', 'typecheck'], frontend, 'typecheck')
  await check.run('npm', ['run', 'build'], frontend, 'frontend-build')
  const ssrPort = await freePort(), appPort = await freePort()
  const renderer = check.start(process.execPath, ['dist/ssr/ssr.js'], frontend, 'renderer', { SSR_PORT: String(ssrPort) })
  const server = check.start('java', [`-Dinertia.ssr=http://127.0.0.1:${ssrPort}/render`, '-jar', 'target/first-inertia-app-1.0.0-SNAPSHOT.jar', '--server.address=127.0.0.1', `--server.port=${appPort}`], app, 'java')
  const base = `http://127.0.0.1:${appPort}`
  await check.wait('external application', async () => {
    if (renderer.closed || server.closed) throw new Error('Application/renderer exited before readiness')
    try { return (await fetch(base + '/hello')).ok } catch { return false }
  })
  browser = await chromium.launch({ channel: process.env.INERTIA_BROWSER_CHANNEL === 'chromium' ? undefined : (process.env.INERTIA_BROWSER_CHANNEL ?? 'chrome') })
  const noJs = await browser.newContext({ javaScriptEnabled: false })
  const staticPage = await noJs.newPage()
  await staticPage.goto(base + '/hello')
  await staticPage.getByRole('heading', { name: 'Hello from Java' }).waitFor()
  await noJs.close()
  check.evidence.phases.push({ name: 'javascript-disabled-ssr', success: true })
  const buildId = JSON.parse(readFileSync(resolve(frontend, 'dist/build.json'), 'utf8')).buildId
  const json = await fetch(base + '/hello', { headers: { 'X-Inertia': 'true', 'X-Inertia-Version': buildId } })
  assert.equal(json.headers.get('X-Inertia'), 'true')
  assert.equal((await json.json()).component, 'Hello')
  const page = await browser.newPage()
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(base + '/hello')
  await page.getByRole('button', { name: 'Say hello' }).click()
  await page.getByRole('alert').filter({ hasText: 'Enter a name between 1 and 100 characters.' }).waitFor()
  await page.getByLabel('Name', { exact: true }).fill('Ada')
  await page.getByRole('button', { name: 'Say hello' }).click()
  await page.getByRole('status').filter({ hasText: 'Hello, Ada' }).waitFor()
  await page.reload()
  assert.equal(await page.getByRole('status').count(), 0)
  await page.getByRole('link', { name: 'About this application' }).click()
  await page.waitForURL(base + '/about')
  await page.goBack()
  await page.waitForURL(base + '/hello')
  await page.getByRole('button', { name: 'Say hello' }).waitFor()
  await page.screenshot({ path: resolve(check.output, 'hello-ssr.png'), fullPage: true })
  check.evidence.phases.push({ name: 'json-hydration-validation-flash-navigation', errors })
  assert.deepEqual(errors, [])
  await check.stop(renderer)
  const fallback = await fetch(base + '/hello')
  const body = await fallback.text()
  assert.equal(fallback.status, 200)
  assert.ok(body.includes('<div id="app"></div>'))
  await page.reload()
  await page.getByRole('heading', { name: 'Hello from Java' }).waitFor()
  check.evidence.phases.push({ name: 'renderer-down-csr', success: true })
  check.evidence.success = true
} catch (caught) { error = caught }
finally { await browser?.close(); await check.finish(error) }
