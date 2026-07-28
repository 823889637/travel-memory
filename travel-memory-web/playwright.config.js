import { defineConfig } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173'
const hostname = new URL(baseURL).hostname
const useLocalServer = ['127.0.0.1', 'localhost', '::1'].includes(hostname)

export default defineConfig({
  testDir: './e2e',
  timeout: 45_000,
  expect: {
    timeout: 10_000,
  },
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL,
    viewport: {
      width: 390,
      height: 844,
    },
    deviceScaleFactor: 1,
    isMobile: true,
    hasTouch: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: useLocalServer
    ? {
        command: 'npm run dev -- --port 5173',
        url: baseURL,
        reuseExistingServer: true,
        timeout: 30_000,
      }
    : undefined,
})
