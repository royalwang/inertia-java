import { chromium } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
import { readRegistry } from './versions.mjs'
import { rehearsal, freePort } from './processes.mjs'

const root = fileURLToPath(new URL('..', import.meta.url))
const registry = readRegistry(root)
const catalog = JSON.parse(readFileSync(resolve(root, '../../docs/inertia-java/open-source-docs-catalog.json')))
const check = rehearsal('inertia-version-navigation')
let browser, error
try {
  const port = await freePort(), base = catalog.site.defaultBase
  const server = check.start(process.execPath, ['node_modules/vitepress/bin/vitepress.js', 'preview', '.', '--host', '127.0.0.1', '--port', String(port), '--strictPort'], root, 'preview')
  const origin = `http://127.0.0.1:${port}`, url = origin + base
  await check.wait('assembled documentation preview', async () => {
    if (server.closed) throw new Error('Preview exited')
    try { return (await fetch(url)).ok } catch { return false }
  })
  browser = await chromium.launch({ channel: process.env.INERTIA_BROWSER_CHANNEL ?? 'chrome' })
  const page = await browser.newPage(), errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('response', response => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`) })
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await page.goto(url)
  await page.locator('.vp-doc h1').waitFor()
  for (const entry of registry.releases) {
    const path = base + 'versions/' + entry.version + '/'
    await page.getByRole('combobox', { name: 'Documentation version' }).selectOption(path)
    await page.waitForURL(origin + path)
    await page.locator('.vp-doc h1').waitFor()
    assert.equal(await page.getByRole('combobox', { name: 'Documentation version' }).inputValue(), path)
    const manifest = await fetch(origin + path + 'snapshot.json')
    assert.equal(manifest.status, 200)
    const snapshot = await manifest.json()
    assert.equal(snapshot.commit, entry.commit); assert.equal(snapshot.tag, entry.tag)
    const source = await page.getByRole('link', { name: 'View tagged source', exact: true }).getAttribute('href')
    assert.ok(source.includes('/blob/' + entry.commit + '/'), 'Historical page must link to immutable source')
    await page.setViewportSize({ width: 390, height: 844 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true)
    await page.getByRole('combobox', { name: 'Documentation version' }).selectOption(base)
    await page.waitForURL(url); await page.locator('.vp-doc h1').waitFor()
    await page.setViewportSize({ width: 1280, height: 720 })
  }
  assert.deepEqual(errors, [])
  check.evidence.registeredReleaseJourneys = registry.releases.length
  check.evidence.base = base
  check.evidence.errors = errors
  check.evidence.success = true
} catch (failure) { error = failure }
finally { if (browser) await browser.close(); await check.finish(error) }
