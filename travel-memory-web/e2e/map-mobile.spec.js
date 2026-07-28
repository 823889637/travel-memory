import { expect, test } from '@playwright/test'

const username = process.env.E2E_USERNAME
const password = process.env.E2E_PASSWORD
const tripId = process.env.E2E_TRIP_ID

function requireTestEnvironment() {
  const missing = [
    ['E2E_USERNAME', username],
    ['E2E_PASSWORD', password],
    ['E2E_TRIP_ID', tripId],
  ].filter(([, value]) => !value).map(([name]) => name)

  if (missing.length) {
    throw new Error(`Missing required E2E environment variables: ${missing.join(', ')}`)
  }
}

async function login(page) {
  await page.goto(`/login?redirect=/trips/${tripId}/map`)
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/trips/${tripId}/map`))
  await expect(page.getByRole('region', { name: '旅行记忆地图' })).toBeVisible()
}

async function restoreFullRoute(page) {
  await page.getByRole('button', { name: '恢复完整旅行路线', exact: true }).click()
  await expect(page.getByRole('button', { name: '全部 完整路线', exact: true })).toBeVisible()
}

test.beforeAll(() => {
  requireTestEnvironment()
})

test.beforeEach(async ({ page }) => {
  await login(page)
})

test('mobile map opens on the full route without an automatic memory card', async ({ page }) => {
  await expect(page.locator('.memory-map-marker.is-replay-point')).toHaveCount(3)
  await expect(page.locator('.map-memory-card')).toHaveCount(0)
  await expect(page.getByText('路径回放 · 4 站', { exact: true })).toBeVisible()

  const viewportState = await page.evaluate(() => ({
    hasHorizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    stageHeight: document.querySelector('.trip-map-stage')?.getBoundingClientRect().height || 0,
  }))

  expect(viewportState.hasHorizontalOverflow).toBe(false)
  expect(viewportState.stageHeight).toBeGreaterThan(500)
})

test('marker selection, grouped memories and the full-screen gallery stay independent', async ({ page }) => {
  const groupedMarker = page.getByRole('button', {
    name: '第 1 站，海河边上，共 2 段记忆',
    exact: true,
  })
  await groupedMarker.click()

  const card = page.locator('.map-memory-card')
  await expect(card).toBeVisible()
  await expect(card.getByText('同一地点 · 1 / 2', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '放大当前记忆地点', exact: true })).toBeVisible()

  await card.getByRole('button', { name: '地图记忆照片 共 4 张', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '照片浏览' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText('1 / 4', { exact: true })).toBeVisible()

  const galleryGeometry = await page.evaluate(() => {
    const dialogElement = document.querySelector('.gallery-dialog')
    const thumbnails = document.querySelector('.gallery-dialog-thumbs')
    const dialogRect = dialogElement?.getBoundingClientRect()
    const thumbnailRect = thumbnails?.getBoundingClientRect()
    return {
      bodyOverflow: document.body.style.overflow,
      dialogBottom: dialogRect?.bottom || 0,
      thumbnailBottom: thumbnailRect?.bottom || 0,
      viewportHeight: window.innerHeight,
    }
  })

  expect(galleryGeometry.bodyOverflow).toBe('hidden')
  expect(galleryGeometry.dialogBottom).toBeLessThanOrEqual(galleryGeometry.viewportHeight + 1)
  expect(galleryGeometry.thumbnailBottom).toBeLessThanOrEqual(galleryGeometry.viewportHeight)

  await dialog.getByRole('button', { name: '下一张照片', exact: true }).click()
  await expect(dialog.getByText('2 / 4', { exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '关闭照片浏览', exact: true }).click()

  await expect(dialog).toHaveCount(0)
  await expect(card).toBeVisible()
  expect(await page.evaluate(() => document.body.style.overflow)).toBe('')
})

test('single-station dates cannot start replay and restoring all enables it again', async ({ page }) => {
  await page.getByRole('button', { name: '第 2 天 2026.07.04', exact: true }).click()

  await expect(page.getByText('当前日期只有 1 个站点，可浏览但无法回放', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '播放', exact: true })).toHaveCount(0)
  await expect(page.locator('.map-memory-card')).toHaveCount(0)

  await restoreFullRoute(page)
  await expect(page.getByRole('button', { name: '播放', exact: true })).toBeVisible()
})

test('route replay exposes stable mobile controls and completes all stations', async ({ page }) => {
  await page.getByRole('button', { name: '播放', exact: true }).click()

  const controls = page.getByRole('region', { name: '路径回放控制' })
  await expect(controls).toBeVisible({ timeout: 12_000 })
  await expect(controls.getByText('第 4 / 4 站', { exact: true })).toBeVisible({ timeout: 15_000 })
  await expect(controls.getByRole('button', { name: '重新播放', exact: true })).toBeVisible()

  const layoutState = await page.evaluate(() => ({
    hasHorizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    selectedCardVisible: Boolean(document.querySelector('.map-memory-card')),
    galleryOpen: Boolean(document.querySelector('.gallery-dialog')),
  }))

  expect(layoutState.hasHorizontalOverflow).toBe(false)
  expect(layoutState.selectedCardVisible).toBe(true)
  expect(layoutState.galleryOpen).toBe(false)
})
