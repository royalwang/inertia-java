import { test, expect } from '@playwright/test'

test('SSR hydration, navigation, deferred data, validation and flash', async ({ page, request }) => {
  test.skip(process.env.INERTIA_EXPECT_CSR === 'true', 'SSR test runs only with renderer connected')
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text() + ' ' + message.location().url) })
  const first = await request.get('/users')
  expect(await first.text()).toContain('data-server-rendered="true"')
  expect(await first.text()).toContain('<li>Ada</li>')
  await page.goto('/users')
  await expect(page).toHaveTitle('Users')
  await expect(page.getByTestId('stats')).toHaveText('Total: 2')
  await expect(page.getByTestId('large-id')).toHaveText('Exact ID: 9007199254740993')
  const navigation = page.waitForResponse(r => r.url().endsWith('/about') && r.request().headers()['x-inertia'] === 'true')
  await page.getByRole('link', { name: 'About this app' }).click()
  expect((await navigation).headers()['content-type']).toContain('application/json')
  await expect(page).toHaveTitle('About')
  await page.getByRole('link', { name: 'Back to users' }).click()
  const mutation = page.waitForRequest(r => r.method() === 'POST' && r.url().endsWith('/users'))
  await page.getByRole('button', { name: 'Save demo name' }).click()
  expect((await mutation).headers()['x-xsrf-token']).toBeTruthy()
  await expect(page.getByRole('alert')).toHaveText('Please enter a name.')
  await page.getByLabel('Name', { exact: true }).fill('Grace')
  await page.getByRole('button', { name: 'Save demo name' }).click()
  await expect(page.getByRole('status')).toHaveText('Saved Grace (demo only)')
  await page.screenshot({ path: `${process.env.INERTIA_E2E_OUTPUT ?? '/tmp/inertia-java-e2e'}/desktop.png`, fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: `${process.env.INERTIA_E2E_OUTPUT ?? '/tmp/inertia-java-e2e'}/mobile.png`, fullPage: true })
  expect(errors).toEqual([])
})

test('SSR content and document navigation work with JavaScript disabled', async ({ browser, baseURL }) => {
  test.skip(process.env.INERTIA_EXPECT_CSR === 'true', 'This contract requires server-rendered HTML')
  const context = await browser.newContext({ baseURL, javaScriptEnabled: false })
  try {
    const page = await context.newPage()
    const inertiaRequests: string[] = []
    page.on('request', request => {
      if (request.headers()['x-inertia']) inertiaRequests.push(request.url())
    })
    const response = await page.goto('/users')
    expect(response!.status()).toBe(200)
    await expect(page.getByRole('heading', { name: 'Inertia Java', exact: true })).toBeVisible()
    await expect(page.getByRole('listitem')).toHaveText(['Ada', 'Linus'])
    await expect(page.getByTestId('large-id')).toHaveText('Exact ID: 9007199254740993')
    await expect(page.getByText('Loading statistics…', { exact: true })).toBeVisible()
    await expect(page.getByTestId('stats')).toHaveCount(0)
    await page.screenshot({ path: `${process.env.INERTIA_E2E_OUTPUT ?? '/tmp/inertia-java-e2e'}/no-javascript.png`, fullPage: true })
    const navigation = page.waitForResponse(r => r.url().endsWith('/about') && r.request().isNavigationRequest())
    await page.getByRole('link', { name: 'About this app', exact: true }).click()
    const document = await navigation
    expect(document.status()).toBe(200)
    expect(document.headers()['content-type']).toContain('text/html')
    expect(await document.text()).toContain('data-server-rendered="true"')
    await expect(page).toHaveTitle('About')
    await expect(page.getByRole('link', { name: 'Back to users', exact: true })).toBeVisible()
    expect(inertiaRequests).toEqual([])
  } finally { await context.close() }
})

test('CSR fallback mounts and can navigate when SSR is disconnected', async ({ page }) => {
  test.skip(process.env.INERTIA_EXPECT_CSR !== 'true', 'Run against Java with an unavailable SSR endpoint')
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  const response = await page.goto('/users')
  expect(response!.status()).toBe(200)
  const html = await response!.text()
  expect(html).not.toContain('data-server-rendered')
  expect(html).toContain('<div id="app"></div>')
  await expect(page.getByTestId('stats')).toHaveText('Total: 2')
  await page.getByRole('link', { name: 'About this app' }).click()
  await expect(page).toHaveTitle('About')
  await page.getByRole('link', { name: 'Back to users' }).click()
  await page.getByRole('button', { name: 'Save demo name' }).click()
  await expect(page.getByRole('alert')).toHaveText('Please enter a name.')
  await page.getByLabel('Name', { exact: true }).fill('Grace')
  await page.getByRole('button', { name: 'Save demo name' }).click()
  await expect(page.getByRole('status')).toHaveText('Saved Grace (demo only)')
  expect(errors).toEqual([])
})

test('infinite scroll prepend/append/reset and once reuse/refresh', async ({ page }) => {
  test.skip(process.env.INERTIA_EXPECT_CSR === 'true', 'Advanced flow runs in SSR mode')
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/feed?page=2')
  await expect(page).toHaveTitle('Feed')
  const items = page.getByTestId('feed-items').getByRole('listitem')
  await expect(items).toHaveCount(3)
  const firstCatalog = await page.getByTestId('catalog').textContent()
  await page.getByRole('button', { name: 'Load previous', exact: true }).click()
  await expect(items).toHaveCount(6)
  await expect(items.first()).toHaveText('Item 1')
  await page.getByRole('button', { name: 'Load next', exact: true }).click()
  await expect(items).toHaveCount(9)
  await expect(items.last()).toHaveText('Item 9')
  await page.getByRole('button', { name: 'Reset feed' }).click()
  await expect(items).toHaveCount(3)
  await expect(items.first()).toHaveText('Item 1')
  await page.getByRole('link', { name: 'About this app' }).click()
  await page.getByRole('link', { name: 'Explore feed' }).click()
  await expect(page.getByTestId('catalog')).toHaveText(firstCatalog!)
  await page.getByRole('button', { name: 'Refresh catalog' }).click()
  await expect(page.getByTestId('catalog')).not.toHaveText(firstCatalog!)
  expect(errors).toEqual([])
})


test('all-errors server mode preserves and displays every field message', async ({ page }) => {
  test.skip(process.env.INERTIA_EXPECT_ALL_ERRORS !== 'true', 'Run Java with --inertia.all-errors=true')
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/users')
  await expect(page.getByTestId('stats')).toHaveText('Total: 2')
  await page.getByLabel('Name', { exact: true }).fill('<' + 'x'.repeat(100))
  const redirectedPage = page.waitForResponse(r => r.url().endsWith('/users') && r.headers()['content-type']?.includes('application/json') === true && r.request().redirectedFrom()?.method() === 'POST')
  await page.getByRole('button', { name: 'Save demo name' }).click()
  const payload = await (await redirectedPage).json()
  expect(payload.props.errors.name).toEqual(expect.arrayContaining(['Name must be at most 100 characters.', 'Name must not contain angle brackets.']))
  const messages = page.getByRole('alert').getByRole('listitem')
  await expect(messages).toHaveCount(2)
  await expect(messages).toHaveText(payload.props.errors.name)
  await page.getByLabel('Name', { exact: true }).fill('Grace')
  await page.getByRole('button', { name: 'Save demo name' }).click()
  await expect(page.getByRole('status')).toHaveText('Saved Grace (demo only)')
  await expect(page.getByRole('alert')).toHaveCount(0)
  expect(errors).toEqual([])
})


test('safe error page preserves status, hydrates or mounts, and recovers by navigation', async ({ page, request }) => {
  test.skip(process.env.INERTIA_EXPECT_FAILURES !== 'true', 'Run Java with --inertia.demo-failures=true')
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  const response = await page.goto('/failures/props')
  expect(response!.status()).toBe(500)
  const html = await response!.text()
  expect(html).not.toContain('Demo data source failure')
  if (process.env.INERTIA_EXPECT_CSR !== 'true') expect(html).toContain('data-server-rendered="true"')
  await expect(page.getByRole('heading', { name: 'Error 500' })).toBeVisible()
  await expect(page).toHaveTitle('Error 500')
  await page.getByRole('link', { name: 'Back to users' }).click()
  await expect(page).toHaveTitle('Users')
  await expect(page.getByTestId('stats')).toHaveText('Total: 2')
  const invalidated = await page.goto('/failures/session')
  expect(invalidated!.status()).toBe(500)
  await expect(page.getByRole('heading', { name: 'Error 500' })).toBeVisible()
  await page.getByRole('link', { name: 'Back to users' }).click()
  await expect(page.getByTestId('stats')).toHaveText('Total: 2')
  const initialPage = JSON.parse(html.match(/<script[^>]*data-page=\"app\"[^>]*>([\s\S]*?)<\/script>/)![1])
  const forbidden = await request.get('/failures/forbidden', { headers: { 'X-Inertia': 'true', 'X-Inertia-Version': initialPage.version } })
  expect(forbidden.status()).toBe(403)
  expect((await forbidden.json()).props.status).toBe(403)
  expect(await forbidden.text()).not.toContain('Demo denial')
  expect(errors).toEqual([])
})

for (const scenario of ['missing-cookie', 'stale-header']) {
  test(`CSRF ${scenario} rejects once, preserves input and recovers on explicit resubmit`, async ({ page, context }) => {
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await page.goto('/users')
    await expect(page.getByTestId('stats')).toHaveText('Total: 2')
    await page.getByLabel('Name', { exact: true }).fill('Grace')
    let posts = 0
    page.on('request', request => {
      if (request.method() === 'POST' && request.url().endsWith('/users')) posts++
    })
    if (scenario === 'missing-cookie') await context.clearCookies({ name: 'XSRF-TOKEN' })
    else {
      let rejected = false
      await page.route('**/users', async route => {
        if (!rejected && route.request().method() === 'POST') {
          rejected = true
          await route.continue({ headers: { ...route.request().headers(), 'x-xsrf-token': 'stale-demo-token' } })
        } else await route.continue()
      })
    }
    const rejection = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/users'))
    await page.getByRole('button', { name: 'Save demo name' }).click()
    expect((await rejection).status()).toBe(303)
    await expect(page.getByRole('alert')).toHaveText('Your security token changed. Review your form and submit again.')
    await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Grace')
    await expect(page.getByRole('button', { name: 'Save demo name' })).toBeEnabled()
    await expect(page.getByRole('status')).toHaveCount(0)
    expect(posts).toBe(1)
    expect(Boolean((await context.cookies()).find(cookie => cookie.name === 'XSRF-TOKEN')?.value)).toBe(true)
    await page.getByRole('button', { name: 'Save demo name' }).click()
    await expect(page.getByRole('status')).toHaveText('Saved Grace (demo only)')
    await expect(page.getByRole('alert')).toHaveCount(0)
    expect(posts).toBe(2)
    expect(errors).toEqual([])
  })
}

test('once TTL reuses before expiry and queries at the exact client expiry boundary', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  // Change Date only: normal browser timers, network, deferred work and animations keep running.
  await page.clock.setFixedTime(new Date())
  await page.goto('/feed?page=2')
  await expect(page).toHaveTitle('Feed')
  const firstCatalog = await page.getByTestId('catalog').textContent()
  const initial = JSON.parse((await page.locator('script[data-page="app"]').textContent())!)
  const expiry = initial.onceProps['feed-catalog'].expiresAt
  expect(Number.isSafeInteger(expiry)).toBe(true)
  expect(expiry % 1000).toBe(0)
  expect(expiry).toBeGreaterThan(await page.evaluate(() => Date.now()))
  await page.clock.setFixedTime(new Date(expiry - 1))
  const warm = page.waitForResponse(response => new URL(response.url()).pathname === '/about' && response.request().headers()['x-inertia'] === 'true')
  await page.getByRole('link', { name: 'About this app' }).click()
  const warmResponse = await warm
  expect(warmResponse.request().headers()['x-inertia-except-once-props'].split(',')).toContain('feed-catalog')
  expect((await warmResponse.json()).props).not.toHaveProperty('catalog')
  await page.getByRole('link', { name: 'Explore feed' }).click()
  await expect(page.getByTestId('catalog')).toHaveText(firstCatalog!)
  // The official client retains the original cached expiry, despite metadata in warm responses.
  await page.clock.setFixedTime(new Date(expiry))
  const expired = page.waitForResponse(response => new URL(response.url()).pathname === '/about' && response.request().headers()['x-inertia'] === 'true')
  await page.getByRole('link', { name: 'About this app' }).click()
  const expiredResponse = await expired
  const loadedKeys = expiredResponse.request().headers()['x-inertia-except-once-props']?.split(',') ?? []
  expect(loadedKeys).not.toContain('feed-catalog')
  expect((await expiredResponse.json()).props.catalog.load).toBeGreaterThan(initial.props.catalog.load)
  await page.getByRole('link', { name: 'Explore feed' }).click()
  await expect(page.getByTestId('catalog')).not.toHaveText(firstCatalog!)
  expect(errors).toEqual([])
})


test('required SSR rejects unavailable HTML and recovers through ordinary client navigation', async ({ page }) => {
  test.skip(process.env.INERTIA_EXPECT_FAILURES !== 'true', 'Required SSR route is an opt-in fault demo')
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  const response = await page.goto('/failures/required-ssr')
  const html = await response!.text()
  if (process.env.INERTIA_EXPECT_CSR === 'true') {
    expect(response!.status()).toBe(503)
    expect(response!.headers()['cache-control']).toBe('private, no-store')
    expect(html).not.toContain('data-server-rendered="true"')
    await expect(page.getByRole('heading', { name: 'Error 503' })).toBeVisible()
    await expect(page).toHaveTitle('Error 503')
    await page.getByRole('link', { name: 'Back to users' }).click()
    await expect(page).toHaveTitle('Users')
    await expect(page.getByTestId('stats')).toHaveText('Total: 2')
  } else {
    expect(response!.status()).toBe(200)
    expect(html).toContain('data-server-rendered="true"')
    await expect(page).toHaveTitle('About')
  }
  expect(html).not.toContain('Required server rendering is unavailable')
  expect(errors).toEqual([])
})


test('untrusted Page strings survive SSR or CSR without escaping the JSON script boundary', async ({ page }) => {
  test.skip(process.env.INERTIA_EXPECT_FAILURES !== 'true', 'Opt-in fixed payload probe only')
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  const payload = '</script><script>window.__inertiaPayloadExecuted=true</script>&\u2028\u2029'
  const response = await page.goto('/failures/payload')
  expect(response!.status()).toBe(200)
  // Escaping must stand on its own: CSP must not conceal a script-boundary vulnerability.
  expect(response!.headers()['content-security-policy']).toBeUndefined()
  expect(await response!.text()).not.toContain('<script>window.__inertiaPayloadExecuted=true</script>')
  await expect(page.getByTestId('payload')).toBeVisible()
  expect(await page.getByTestId('payload').textContent()).toBe(payload)
  await expect(page.locator('script[data-page]')).toHaveCount(1)
  expect(await page.evaluate(() => Reflect.get(window, '__inertiaPayloadExecuted'))).toBeUndefined()
  expect(errors).toEqual([])
})
