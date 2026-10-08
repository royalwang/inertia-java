import { test, expect } from '@playwright/test'

test('content security policy permits the app and rejects untrusted inline scripts', async ({ page, request }) => {
  test.skip(process.env.INERTIA_EXPECT_CSP !== 'true', 'Run the opt-in CSP harness')
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => {
    const state = window as typeof window & { violations: string[], unsafeExecuted?: boolean }
    state.violations = []
    document.addEventListener('securitypolicyviolation', event => state.violations.push(event.effectiveDirective))
  })
  const response = await page.goto('/users')
  const policy = response!.headers()['content-security-policy']
  const nonce = policy.match(/script-src 'nonce-([^']+)'/)?.[1]
  expect(nonce).toBeTruthy()
  expect(policy).not.toContain("script-src 'unsafe-inline'")
  const html = await response!.text()
  const rootId = process.env.INERTIA_ROOT_ID ?? 'app'
  expect(await page.locator('meta[name="inertia-root"]').getAttribute('content')).toBe(rootId)
  await expect(page.locator(`[id="${rootId}"]`)).toHaveCount(1)
  if (process.env.INERTIA_EXPECT_CSR === 'true') expect(html).not.toContain('data-server-rendered')
  else expect(html).toContain('data-server-rendered')
  await expect(page.getByTestId('stats')).toHaveText('Total: 2')
  expect(await page.locator('meta[name="csp-nonce"]').getAttribute('content')).toBe(nonce)
  const modules = await page.locator('script[type="module"]').evaluateAll(nodes => nodes.map(node => (node as HTMLScriptElement).nonce))
  expect(modules.length).toBeGreaterThan(0)
  expect(modules.every(value => value === nonce)).toBe(true)
  await page.getByRole('link', { name: 'About this app' }).click()
  await expect(page).toHaveTitle('About')
  await page.getByRole('link', { name: 'Back to users' }).click()
  await page.getByLabel('Name', { exact: true }).fill('Grace')
  await page.getByRole('button', { name: 'Save demo name' }).click()
  await expect(page.getByRole('status')).toHaveText('Saved Grace (demo only)')
  expect(await page.evaluate(() => (window as typeof window & { violations: string[] }).violations)).toEqual([])
  const another = await request.get('/users')
  expect(another.headers()['content-security-policy']).not.toBe(policy)
  await page.route('**/users', async route => {
    if (!route.request().isNavigationRequest()) return route.continue()
    const original = await route.fetch()
    const probeNonce = original.headers()['content-security-policy'].match(/script-src 'nonce-([^']+)'/)?.[1]
    // A standalone parser probe avoids synthetic-response local-network classification for Vite imports.
    const body = `<html><body><script nonce="${probeNonce}">window.trustedExecuted = true</script><script>window.unsafeExecuted = true</script></body></html>`
    await route.fulfill({ response: original, body })
  })
  await page.reload()
  await expect.poll(() => page.evaluate(() => (window as typeof window & { violations: string[] }).violations.length)).toBeGreaterThan(0)
  expect(await page.evaluate(() => (window as typeof window & { unsafeExecuted?: boolean }).unsafeExecuted)).toBeUndefined()
  expect(await page.evaluate(() => (window as typeof window & { trustedExecuted?: boolean }).trustedExecuted)).toBe(true)
  expect(errors).toEqual([])
})
