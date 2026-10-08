import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './e2e',
  outputDir: process.env.INERTIA_E2E_OUTPUT ?? '/tmp/inertia-java-e2e',
  use: { baseURL: process.env.INERTIA_BASE_URL ?? 'http://127.0.0.1:18080', channel: 'chrome', viewport: { width: 1280, height: 900 } },
})
