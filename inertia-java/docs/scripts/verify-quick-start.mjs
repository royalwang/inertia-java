import { execFileSync } from 'node:child_process'
import { copyFileSync, mkdirSync, readFileSync, lstatSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from '@playwright/test'
import assert from 'node:assert/strict'
import { rehearsal, freePort } from './processes.mjs'

const repository = fileURLToPath(new URL('../../..', import.meta.url))
const check = rehearsal('inertia-quick-start')
let browser, error
try {
  // Copy the source manifest, not target/node_modules/dist from a warm checkout.
  const files = execFileSync('git', ['ls-files', '-co', '--exclude-standard', '-z'], { cwd: repository }).toString().split('\0').filter(Boolean)
  const snapshot = resolve(check.output, 'source')
  for (const path of new Set(files)) {
    const source = resolve(repository, path), destination = resolve(snapshot, path)
    if (!lstatSync(source).isFile()) throw new Error('Source snapshot requires ordinary files: ' + path)
    mkdirSync(dirname(destination), { recursive: true })
    copyFileSync(source, destination)
  }
  check.evidence.sourceFiles = files.length
  check.evidence.sourceHead = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: repository, encoding: 'utf8' }).trim()
  check.evidence.sourceState = 'current tracked and untracked source manifest; generated outputs excluded'
  const java = resolve(snapshot, 'inertia-java')
  const app = resolve(java, 'examples/spring-react')
  const frontend = resolve(app, 'frontend')
  await check.run(resolve(java, 'mvnw'), ['verify'], java, 'maven')
  await check.run('npm', ['ci', '--no-audit', '--no-fund'], frontend, 'npm')
  await check.run('npm', ['run', 'typecheck'], frontend, 'typecheck')
  await check.run('npm', ['run', 'build'], frontend, 'frontend-build')
  const ssrPort = await freePort(), appPort = await freePort()
  const renderer = check.start(process.execPath, ['dist/ssr/ssr.js'], frontend, 'renderer', { SSR_PORT: String(ssrPort) })
  const server = check.start('java', [`-Dinertia.ssr=http://127.0.0.1:${ssrPort}/render`, '-jar', 'target/spring-react-0.1.0-SNAPSHOT.jar', '--server.address=127.0.0.1', `--server.port=${appPort}`], app, 'java')
  const base = `http://127.0.0.1:${appPort}`
  await check.wait('clean-source quick start', async () => {
    if (renderer.closed || server.closed) throw new Error('Example exited before readiness')
    try { return (await fetch(base + '/users')).ok } catch { return false }
  })
  browser = await chromium.launch({ channel: process.env.INERTIA_BROWSER_CHANNEL === 'chromium' ? undefined : (process.env.INERTIA_BROWSER_CHANNEL ?? 'chrome') })
  const context = await browser.newContext({ javaScriptEnabled: false })
  const page = await context.newPage()
  await page.goto(base + '/users')
  await page.getByText('Ada', { exact: true }).waitFor()
  await page.getByText('Linus', { exact: true }).waitFor()
  await context.close()
  const buildId = JSON.parse(readFileSync(resolve(frontend, 'dist/build.json'), 'utf8')).buildId
  const response = await fetch(base + '/users', { headers: { 'X-Inertia': 'true', 'X-Inertia-Version': buildId } })
  assert.equal(response.status, 200)
  assert.equal((await response.json()).component, 'Users/Index')
  check.evidence.phases.push({ name: 'clean-build-html-and-json', success: true })
  await check.run(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--grep', 'SSR hydration, navigation'], frontend, 'browser', {
    INERTIA_BASE_URL: base, INERTIA_EXPECT_CSR: 'false', INERTIA_EXPECT_CSP: 'false',
    INERTIA_EXPECT_HISTORY: 'false', INERTIA_EXPECT_ALL_ERRORS: 'false', INERTIA_EXPECT_FAILURES: 'false',
    INERTIA_E2E_OUTPUT: resolve(check.output, 'browser'),
  })
  check.evidence.success = true
} catch (caught) { error = caught }
finally { await browser?.close(); await check.finish(error) }
