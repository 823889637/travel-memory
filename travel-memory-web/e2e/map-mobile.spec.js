import { expect, test } from '@playwright/test'
import { discoverMapFixtures } from './support/map-fixtures.js'

const username = process.env.E2E_USERNAME
const password = process.env.E2E_PASSWORD
const preferredTripId = process.env.E2E_TRIP_ID

let fixtures

function requireTestEnvironment() {
  const missing = [
    ['E2E_USERNAME', username],
    ['E2E_PASSWORD', password],
  ].filter(([, value]) => !value).map(([name]) => name)

  if (missing.length) {
    throw new Error(`Missing required E2E environment variables: ${missing.join(', ')}`)
  }
}

async function login(page) {
  await page.goto('/login?redirect=/trips')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/trips')
  await expect.poll(async () => page.evaluate(async () => {
    const response = await fetch('/api/auth/me', { credentials: 'include' })
    return response.status
  })).toBe(200)
}

async function openMap(page, fixture) {
  const memoryQuery = fixture.memoryId ? `?memoryId=${fixture.memoryId}` : ''
  await page.goto(`/trips/${fixture.tripId}/map${memoryQuery}`)
  await expect(page.getByRole('region', { name: '旅行记忆地图' })).toBeVisible()
  await expect(page.locator('.memory-map-state')).toHaveCount(0, { timeout: 12_000 })
}

async function restoreFullRoute(page) {
  const fitAllButton = page.getByRole('button', { name: '查看完整旅行路线', exact: true })
  if (await fitAllButton.count()) await fitAllButton.click()
  else await page.getByRole('button', { name: '全部 完整路线', exact: true }).click()
  await expect(page.getByRole('button', { name: '全部 完整路线', exact: true })).toBeVisible()
}

function annotateFixture(testInfo, fixture) {
  testInfo.annotations.push({
    type: 'fixture',
    description: `${fixture.tripTitle} (Trip ${fixture.tripId})`,
  })
}

test.beforeAll(async ({ browser }) => {
  requireTestEnvironment()

  const page = await browser.newPage()
  try {
    await login(page)
    fixtures = await discoverMapFixtures(page, preferredTripId)
  } finally {
    await page.close()
  }

  if (!fixtures.route) {
    throw new Error('No owned Trip contains at least two Memories with valid coordinates and record time.')
  }
})

test.beforeEach(async ({ page }) => {
  await login(page)
})

test('mobile map opens on a discovered route without an automatic memory card', async ({ page }, testInfo) => {
  const fixture = fixtures.route
  annotateFixture(testInfo, fixture)
  await openMap(page, fixture)

  await expect(page.locator('.memory-map-marker.is-replay-point')).not.toHaveCount(0)
  await expect(page.locator('.map-memory-card')).toHaveCount(0)
  await expect(page.getByText(`路径回放 · ${fixture.replayCount} 站`, { exact: true })).toBeVisible()

  const viewportState = await page.evaluate(() => ({
    hasHorizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    stageHeight: document.querySelector('.trip-map-stage')?.getBoundingClientRect().height || 0,
  }))

  expect(viewportState.hasHorizontalOverflow).toBe(false)
  expect(viewportState.stageHeight).toBeGreaterThan(500)
})

test('a long-distance trip opens on the full overview and keeps continuous route focus available', async ({ page }, testInfo) => {
  const fixture = fixtures.longRoute
  test.skip(!fixture, 'No owned Trip currently contains a long-distance route break.')
  annotateFixture(testInfo, fixture)
  await openMap(page, fixture)

  await expect(page.getByRole('button', { name: '全部 完整路线', exact: true }))
    .toHaveAttribute('aria-current', 'date')
  await expect(page.getByText(
    `完整路线包含 ${fixture.longBreakCount} 段远距离虚线，仅表示先后顺序。`,
    { exact: true },
  )).toBeVisible()

  const continuousRoute = page.getByRole('button', {
    name: `连续路线 ${fixture.defaultContinuousCount} 站`,
    exact: true,
  })
  await continuousRoute.click()
  await expect(continuousRoute).toHaveAttribute('aria-current', 'date')
  await expect(page.getByText(
    `当前先展示最早的连续路线，另有 ${fixture.replayCount - fixture.defaultContinuousCount} 个站点位于其他路线段。`,
    { exact: true },
  )).toBeVisible()

  await page.getByRole('button', { name: '全部 完整路线', exact: true }).click()
  await expect(page.getByRole('button', { name: '全部 完整路线', exact: true }))
    .toHaveAttribute('aria-current', 'date')
})

test('a discovered grouped marker exposes every Memory at the same location', async ({ page }, testInfo) => {
  const fixture = fixtures.grouped
  test.skip(!fixture, 'No owned Trip currently contains grouped map Memories.')
  annotateFixture(testInfo, fixture)
  await openMap(page, fixture)

  const groupedMarker = page.locator('.memory-map-marker')
    .filter({ has: page.locator('.memory-map-marker-count:not([hidden])') })
    .first()
  await groupedMarker.click()

  const card = page.locator('.map-memory-card')
  await expect(card).toBeVisible()
  await expect(card.locator('.map-memory-group-nav')).toContainText(
    new RegExp(`同一地点\\s*·\\s*1\\s*/\\s*${fixture.groupSize}`),
  )
  await expect(page.getByRole('button', { name: '放大当前记忆地点', exact: true })).toBeVisible()
})

test('a discovered multi-photo map Memory opens an independent full-screen gallery', async ({ page }, testInfo) => {
  const fixture = fixtures.gallery
  test.skip(!fixture, 'No located Memory with multiple photos is available for gallery regression.')
  annotateFixture(testInfo, fixture)
  await openMap(page, fixture)

  const card = page.locator('.map-memory-card')
  await expect(card).toBeVisible()
  await card.locator('.gallery-favorite-preview').click()

  const dialog = page.getByRole('dialog', { name: '照片浏览' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText(`1 / ${fixture.photoCount}`, { exact: true })).toBeVisible()

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
  await expect(dialog.getByText(`2 / ${fixture.photoCount}`, { exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '关闭照片浏览', exact: true }).click()

  await expect(dialog).toHaveCount(0)
  await expect(card).toBeVisible()
  expect(await page.evaluate(() => document.body.style.overflow)).toBe('')
})

test('a discovered single-station date cannot start replay and restoring all enables it again', async ({ page }, testInfo) => {
  const fixture = fixtures.singleStationDay
  test.skip(!fixture, 'No owned Trip currently contains a single-station date within a replayable route.')
  annotateFixture(testInfo, fixture)
  await openMap(page, fixture)

  await page.locator('.map-day-button')
    .filter({ hasText: fixture.displayDate })
    .click()

  await expect(page.getByText('当前日期只有 1 个站点，可浏览但无法回放', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '播放', exact: true })).toHaveCount(0)
  await expect(page.locator('.map-memory-card')).toHaveCount(0)

  await restoreFullRoute(page)
  await expect(page.getByRole('button', { name: '播放', exact: true })).toBeVisible()
})

test('a discovered route replay exposes stable mobile controls and completes all stations', async ({ page }, testInfo) => {
  test.setTimeout(120_000)
  const fixture = fixtures.replay
  annotateFixture(testInfo, fixture)
  await openMap(page, fixture)

  await page.getByRole('button', { name: '播放', exact: true }).click()

  const controls = page.getByRole('region', { name: '路径回放控制' })
  await expect(controls).toBeVisible({ timeout: 12_000 })
  await expect(controls.getByText(`第 ${fixture.replayCount} / ${fixture.replayCount} 站`, { exact: true }))
    .toBeVisible({ timeout: Math.min(100_000, 12_000 + fixture.replayCount * 5_000) })
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
