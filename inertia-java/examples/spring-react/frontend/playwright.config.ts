import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './e2e',
  outputDir: process.env.INERTIA_E2E_OUTPUT ?? '/tmp/inertia-java-e2e',
  use: { baseURL: process.env.INERTIA_BASE_URL ?? 'http://127.0.0.1:18080', channel: process.env.INERTIA_BROWSER_CHANNEL ?? 'chrome', viewport: { width: 1280, height: 900 }, trace: 'retain-on-failure' },
})
