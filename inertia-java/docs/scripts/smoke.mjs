import { chromium, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
import { rehearsal, freePort } from './processes.mjs'

const root = fileURLToPath(new URL('..', import.meta.url))
const catalog = JSON.parse(readFileSync(resolve(root, '../../docs/inertia-java/open-source-docs-catalog.json'), 'utf8'))
const base = process.env.INERTIA_DOCS_BASE ?? catalog.site.defaultBase
const check = rehearsal('inertia-docs-smoke')
let browser, error
try {
  const port = await freePort()
  const server = check.start(process.execPath, ['node_modules/vitepress/bin/vitepress.js', 'preview', '.', '--host', '127.0.0.1', '--port', String(port), '--strictPort'], root, 'preview')
  const url = `http://127.0.0.1:${port}${base}`
  await check.wait('documentation preview', async () => {
    if (server.closed) throw new Error('Preview exited: ' + server.transcript)
    try { return (await fetch(url)).ok } catch { return false }
  })
  browser = await chromium.launch({ channel: process.env.INERTIA_BROWSER_CHANNEL ?? 'chrome' })
  const page = await browser.newPage()
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('response', response => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`) })
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text() + ' ' + JSON.stringify(message.location())) })
  await page.goto(url)
  await page.locator('.vp-doc h1').filter({ hasText: 'Inertia Java' }).waitFor()
  assert.equal(await page.locator('a[href*="/getting-started/overview"]').count() > 0, true)
  const versionMenu = page.getByRole('combobox', { name: 'Documentation version' })
  assert.equal(await versionMenu.inputValue(), base)
  assert.ok((await versionMenu.locator('option').allTextContents()).some(text => text.includes(catalog.site.javadoc.version)))
  const searchButton = page.getByRole('button', { name: /Search/ }).first()
  await searchButton.focus()
  await page.keyboard.press('Enter')
  await expect(page.locator('#localsearch-input')).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(page.locator('.VPLocalSearchBox')).toHaveCount(0)
  await expect(searchButton).toBeFocused()
  await page.keyboard.press('Control+k')
  await expect(page.locator('#localsearch-input')).toBeFocused()
  await page.locator('#localsearch-input').fill('flash')
  await page.locator('.VPLocalSearchBox .result').first().waitFor()
  await page.keyboard.press('Escape')
  await expect(searchButton).toBeFocused()
  check.evidence.phases.push({ name: 'keyboard-search', enterOpens: true, controlKOpens: true, inputReceivesFocus: true, escapeClosesAndRestoresFocus: true })
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write'], { origin: new URL(url).origin })
  const copies = []
  const copyBlock = async (route, language, expected) => {
    await page.goto(url + route)
    const block = page.locator('.vp-doc .language-' + language).first()
    await block.waitFor()
    const rendered = await block.locator('pre').textContent()
    // VitePress removes the terminal newline when highlighting a code block.
    assert.equal(rendered, expected.replace(/\n$/, ''), 'Rendered canonical source: ' + route + ' / ' + language)
    const button = block.locator('button.copy')
    await button.focus()
    // Enter from keyboard modality must reveal a visible focus indicator.
    await page.keyboard.press('Tab')
    await page.keyboard.press('Shift+Tab')
    await expect(button).toBeFocused()
    assert.equal(await button.evaluate(element => element.matches(':focus-visible')), true)
    await expect(button).toHaveCSS('opacity', '1')
    assert.notEqual(await button.evaluate(element => getComputedStyle(element).outlineStyle), 'none')
    await page.keyboard.press('Enter')
    const copied = language === 'sh' ? rendered.replace(/^ *(\$|>) /gm, '').trim() : rendered
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe(copied)
    copies.push({ route, language, canonicalSourceExceptTerminalNewline: language !== 'sh' && language !== 'yaml', keyboardCopy: true, clipboardMatchesRenderedText: true, visibleFocus: true })
  }
  for (const prefix of ['', 'zh/']) {
    for (const [language, filename] of [['xml', 'pom.xml'], ['java', 'HelloController.java'], ['tsx', 'Hello.tsx']]) {
      await copyBlock(prefix + 'getting-started/first-application', language, readFileSync(resolve(root, 'examples/first-application', filename), 'utf8'))
    }
    await copyBlock(prefix + 'reference/configuration', 'yaml', 'inertia:\n  props-timeout: 3s\n  response-timeout: 5s\n  props-concurrency: 8\n')
    await copyBlock(prefix + 'getting-started/first-application', 'sh', './mvnw install\nnode docs/scripts/create-first-application.mjs /tmp/my-inertia-app\n')
  }
  check.evidence.phases.push({ name: 'canonical-code-and-clipboard', copies })
  const available = catalog.pages.filter(item => item.status !== 'planned')
  for (const item of available) {
    const route = item.path === 'index.md' ? '' : item.path.replace(/\.md$/, '')
    const response = await page.goto(url + route)
    assert.equal(response.status(), 200, item.path)
    await page.locator('.vp-doc h1').waitFor()
    if (item.id !== 'home') assert.ok(await page.locator(`a[href="${base + route}"]`).count() > 0, 'Missing navigation: ' + item.path)
  }
  check.evidence.phases.push({ name: 'direct-page-loads', pages: available.length })
  // Local search must find content in prose, not merely a navigation title.
  for (const term of ['flash', 'deferred', 'requireSsr', 'session-namespace']) {
    await page.getByRole('button', { name: /Search/ }).first().click()
    await page.locator('#localsearch-input').fill(term)
    await page.locator('.VPLocalSearchBox .result').first().waitFor()
    await page.keyboard.press('Escape')
  }
  check.evidence.phases.push({ name: 'local-search', terms: ['flash', 'deferred', 'requireSsr', 'session-namespace'] })
  const journeys = [
    ['CSRF', 'CSRF and cookie handling', 'guide/csrf'],
    ['deep merge', 'Append, prepend and deep merge', 'props/merging'],
    ['once', 'Once props, expiry and refresh', 'props/once'],
    ['renderer health', 'Renderer health and recovery', 'ssr/health'],
    ['response-timeout', 'Configuration reference', 'reference/configuration'],
    ['inertia.ssr_http', 'Metrics reference', 'reference/metrics'],
    ['private', 'Reporting security issues', 'community/security'],
  ]
  for (const [term, title, route] of journeys) {
    await page.getByRole('button', { name: /Search/ }).first().click()
    await page.locator('#localsearch-input').fill(term)
    await page.locator('.VPLocalSearchBox .result').filter({ hasText: title }).first().click()
    await page.waitForURL(value => value.pathname === base + route)
    await page.locator('.vp-doc h1').waitFor()
  }
  check.evidence.phases.push({ name: 'application-search-navigation', journeys: journeys.map(([term, , route]) => ({ term, route })) })
  await page.goto(url + 'api-guide')
  for (const name of ['CoreApiExample.java', 'SpringApiExample.java']) {
    const link = page.getByRole('link', { name, exact: true })
    const href = await link.getAttribute('href')
    assert.equal(href, base + 'examples/' + name)
    const response = await fetch(new URL(href, url))
    assert.equal(response.status, 200)
    assert.deepEqual(Buffer.from(await response.arrayBuffer()), readFileSync(resolve(root, 'examples', name)))
  }
  check.evidence.phases.push({ name: 'canonical-source-downloads', byteIdentical: true })
  const apiRoot = 'reference/javadoc/' + catalog.site.javadoc.version + '/'
  const api = JSON.parse(readFileSync(resolve(root, 'public', apiRoot, 'api-index.json')))
  await page.goto(url + 'reference/javadoc')
  for (const module of api.modules) {
    const href = base + apiRoot + module.id + '/index.html'
    const entry = page.locator(`.vp-doc a[href$="${module.id}/index.html"]`)
    assert.equal(await entry.count(), 1, module.id)
    assert.equal(await entry.evaluate(link => new URL(link.href).pathname), href)
    for (const file of ['index.html', 'resources/LICENSE', 'resources/NOTICE']) {
      const response = await fetch(url + apiRoot + module.id + '/' + file)
      assert.equal(response.status, 200)
      assert.deepEqual(Buffer.from(await response.arrayBuffer()), readFileSync(resolve(root, 'public', apiRoot, module.id, file)))
    }
    if (module.members.length) {
      const member = module.members.find(item => item.l.startsWith('flash(')) ?? module.members[0]
      const anchor = member.u ?? member.l
      const target = url + apiRoot + module.id + '/' + member.p.replaceAll('.', '/') + '/' + member.c + '.html#' + anchor
      await page.goto(target)
      assert.equal(await page.evaluate(id => document.getElementById(id) !== null, decodeURIComponent(anchor)), true, 'Missing rendered method anchor: ' + module.id)
      await page.goto(url + 'reference/javadoc')
    }
  }
  check.evidence.phases.push({ name: 'generated-javadoc', modules: api.modules.length, publicTypes: api.modules.reduce((count, module) => count + module.types.length, 0), memberAnchors: api.modules.reduce((count, module) => count + module.members.length, 0), entryAndAttributionBytes: true, representativeMethodNavigation: true })
  const translations = catalog.translations ?? []
  for (const item of translations) {
    const route = item.path.replace(/index\.md$/, '').replace(/\.md$/, '')
    const response = await page.goto(url + route)
    assert.equal(response.status(), 200, item.path)
    await page.locator('.vp-doc h1').filter({ hasText: item.title }).waitFor()
    assert.equal(await page.locator('html').getAttribute('lang'), 'zh-CN')
    const original = catalog.pages.find(entry => entry.id === item.id)
    const originalRoute = original.path === 'index.md' ? '' : original.path.replace(/\.md$/, '')
    const counterpart = page.locator('.translation-notice a').first()
    assert.equal(await counterpart.evaluate(link => new URL(link.href).pathname), base + originalRoute)
  }
  await page.goto(url + 'getting-started/quick-start')
  await page.locator('.translation-notice').getByRole('link', { name: '阅读本页中文译文' }).click()
  await page.waitForURL(url + 'zh/getting-started/quick-start')
  await page.locator('.translation-notice').getByRole('link', { name: '查看对应英文' }).click()
  await page.waitForURL(url + 'getting-started/quick-start')
  await page.goto(url + 'props/basics')
  await page.locator('.translation-notice').getByText('this page has no Chinese translation.', { exact: false }).waitFor()
  await page.locator('.translation-notice').getByRole('link', { name: '中文优先文档' }).click()
  await page.waitForURL(url + 'zh/')
  await page.locator('.vp-doc h1').filter({ hasText: 'Inertia Java 中文文档' }).waitFor()
  await page.getByRole('heading', { name: '应用指南', exact: true }).click()
  const englishFallback = page.locator('#VPSidebarNav a[href="' + base + 'guide/authentication"]')
  await englishFallback.filter({ hasText: /（英文）/ }).waitFor()
  assert.match(await englishFallback.textContent(), /（英文）/)
  await englishFallback.click()
  await page.waitForURL(url + 'guide/authentication')
  await page.locator('.vp-doc h1').filter({ hasText: 'Authentication and authorization' }).waitFor()
  await page.goto(url + 'zh/')
  for (const [term, title, route] of [
    ['校验', '表单与校验', 'zh/guide/forms-validation'],
    ['预算', '配置参考', 'zh/reference/configuration'],
    ['会话', '请求与响应生命周期', 'zh/concepts/request-lifecycle'],
  ]) {
    await page.getByRole('button', { name: /搜索文档/ }).first().click()
    await page.locator('#localsearch-input').fill(term)
    await page.locator('.VPLocalSearchBox .result').filter({ hasText: title }).first().click()
    await page.waitForURL(value => value.pathname === base + route)
    await page.locator('.vp-doc h1').filter({ hasText: title }).waitFor()
  }
  await page.goto(url + 'zh/concepts/request-lifecycle')
  const chineseDownload = page.getByRole('link', { name: 'CoreApiExample.java', exact: true })
  assert.equal(await chineseDownload.getAttribute('href'), base + 'examples/CoreApiExample.java')
  check.evidence.phases.push({ name: 'chinese-priority-journeys', pages: translations.length, perPageEnglishCounterparts: true, untranslatedEnglishFallback: true, chineseSearchNavigation: ['校验', '预算', '会话'], canonicalSourceDownload: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(url + 'getting-started/quick-start')
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, 'Mobile horizontal overflow')
  const menu = page.getByRole('button', { name: /Menu/ }).first()
  await menu.click()
  await page.locator('#VPSidebarNav').getByRole('link', { name: 'Installation', exact: true }).click()
  await page.waitForURL(url + 'getting-started/installation')
  await menu.click()
  await page.locator('#VPSidebarNav').getByRole('link', { name: 'Run the React example', exact: true }).click()
  await page.waitForURL(url + 'getting-started/quick-start')
  await page.locator('.vp-doc h1').filter({ hasText: 'Run the React example' }).waitFor()
  await page.locator('.VPLocalNav button.menu[aria-expanded="false"]').waitFor()
  await page.screenshot({ path: resolve(check.output, 'mobile.png'), fullPage: true, animations: 'disabled' })
  await page.goto(url + 'zh/getting-started/quick-start')
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, 'Chinese mobile horizontal overflow')
  const chineseMenu = page.getByRole('button', { name: '目录', exact: true }).first()
  await chineseMenu.click()
  await page.locator('#VPSidebarNav').getByRole('link', { name: '安装', exact: true }).click()
  await page.waitForURL(url + 'zh/getting-started/installation')
  await page.locator('.vp-doc h1').filter({ hasText: '安装' }).waitFor()
  await page.locator('.VPLocalNav button.menu[aria-expanded="false"]').waitFor()
  await page.screenshot({ path: resolve(check.output, 'mobile-zh.png'), fullPage: true, animations: 'disabled' })
  check.evidence.phases.push({ name: 'chinese-mobile-navigation', horizontalOverflow: false })

  await page.keyboard.press('Escape')
  assert.deepEqual(errors, [])
  check.evidence.base = base
  check.evidence.phases.push({ name: 'mobile-navigation-and-console', errors })
  check.evidence.success = true
} catch (caught) { error = caught }
finally { await browser?.close(); await check.finish(error) }
