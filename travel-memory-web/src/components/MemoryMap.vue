<script setup>
import AMapLoader from '@amap/amap-jsapi-loader'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { isValidWgs84Coordinate, wgs84ToGcj02 } from '../utils/coordinates'

const props = defineProps({
  points: { type: Array, default: () => [] },
  routeSegments: { type: Array, default: () => [] },
  selectedId: { type: [String, Number], default: null },
  activeDate: { type: String, default: '' },
  playbackIndex: { type: Number, default: -1 },
  bottomInset: { type: Number, default: 132 },
  focusSelectedOnReady: { type: Boolean, default: false },
  fallbackLatitude: { type: [Number, String], default: null },
  fallbackLongitude: { type: [Number, String], default: null },
  fallbackLabel: { type: String, default: '' },
})
const emit = defineEmits(['select', 'selection-positioned', 'interaction', 'ready'])

const mapElement = ref(null)
const state = ref('idle')
const errorMessage = ref('')
const routePaths = ref([])
const displayPoints = computed(() => props.points.map((point) => {
  if (!isValidWgs84Coordinate(point.latitude, point.longitude)) return null
  const originalLatitude = Number(point.latitude)
  const originalLongitude = Number(point.longitude)
  const coordinate = wgs84ToGcj02(point.latitude, point.longitude)
  return coordinate
    ? {
      ...point,
      originalLatitude,
      originalLongitude,
      latitude: coordinate.latitude,
      longitude: coordinate.longitude,
    }
    : null
}).filter(Boolean))
const fallbackCoordinate = computed(() => {
  if (!isValidWgs84Coordinate(props.fallbackLatitude, props.fallbackLongitude)) return null
  return wgs84ToGcj02(props.fallbackLatitude, props.fallbackLongitude)
})
const pointSignature = computed(() => displayPoints.value
  .map((point) => [
    point.id,
    point.originalLatitude,
    point.originalLongitude,
    point.sequenceNumber,
    point.mapDate,
    point.isReplayPoint,
    point.memberCount,
    (point.memberIds || []).join(','),
    (point.replayIndexes || []).join(','),
    (point.mapDates || []).join(','),
  ].join(':')).join('|'))
const mapSignature = computed(() => `${pointSignature.value}|${fallbackCoordinate.value?.latitude || ''}:${fallbackCoordinate.value?.longitude || ''}`)
const routeSignature = computed(() => props.routeSegments
  .map((segment) => `${segment.fromId}:${segment.toId}:${segment.fromReplayIndex}:${segment.toReplayIndex}:${segment.fromDate}:${segment.toDate}`).join('|'))

let map = null
let AMap = null
let resizeObserver = null
let routeAnimationFrame = null
let selectionPlacementTimer = null
let fitPlacementTimer = null
let selectionPanId = ''
let positionSelectedAfterFit = false
let initialRenderTimer = null
let initialRenderFrame = null
let initialRenderHandler = null
let destroyed = false
let destinationMarker = null
let programmaticMove = false
let programmaticMoveTimer = null
let selectionPositioningSuspendCount = 0
const markers = new Map()
const OVERLAP_DISTANCE = 42

function idKey(value) {
  return value == null ? '' : String(value)
}

function pointContainsMemory(point, memoryId) {
  const selectedKey = idKey(memoryId)
  if (!selectedKey) return false
  return idKey(point.id) === selectedKey
    || (point.memberIds || []).some(memberId => idKey(memberId) === selectedKey)
}

function markerForMemory(memoryId) {
  return [...markers.values()].find(({ point }) => pointContainsMemory(point, memoryId)) || null
}

function beginProgrammaticMove(timeout = 1200) {
  programmaticMove = true
  if (programmaticMoveTimer != null) window.clearTimeout(programmaticMoveTimer)
  programmaticMoveTimer = window.setTimeout(() => {
    programmaticMove = false
    programmaticMoveTimer = null
  }, timeout)
}

function finishProgrammaticMove() {
  if (programmaticMoveTimer != null) window.clearTimeout(programmaticMoveTimer)
  programmaticMoveTimer = null
  programmaticMove = false
}

function suspendSelectionPositioning() {
  selectionPositioningSuspendCount += 1
}

function resumeSelectionPositioning({ position = false } = {}) {
  selectionPositioningSuspendCount = Math.max(0, selectionPositioningSuspendCount - 1)
  if (position && selectionPositioningSuspendCount === 0) positionSelected()
}

function emitUserInteraction(event) {
  if (!programmaticMove || event?.originEvent) emit('interaction')
}

function markerLabel(point) {
  const excerpt = String(point.content || '').trim().replace(/\s+/g, ' ').slice(0, 24)
  const label = point.locationName || excerpt || '旅行记忆'
  const countLabel = Number(point.memberCount) > 1 ? `，共 ${point.memberCount} 段记忆` : ''
  return point.isReplayPoint
    ? `第 ${point.sequenceNumber || '?'} 站，${label}${countLabel}`
    : `未纳入路径回放的位置，${label}${countLabel}`
}

function markerScaleForZoom() {
  const zoom = Number(map?.getZoom?.())
  if (!Number.isFinite(zoom)) return 1
  if (zoom <= 6) return 0.68
  if (zoom >= 12) return 1
  return 0.68 + ((zoom - 6) / 6) * 0.32
}

function updateMarkerScale(element, selected) {
  const visual = element.querySelector('.memory-map-marker-visual')
  if (!visual) return
  const selectionBoost = selected ? 1.12 : 1
  const scale = Math.min(1.12, markerScaleForZoom() * selectionBoost)
  visual.style.setProperty('--memory-map-marker-scale', scale.toFixed(3))
}

function updateMarkerScales() {
  markers.forEach(({ element, point }) => {
    updateMarkerScale(element, pointContainsMemory(point, props.selectedId))
  })
}

function applyMarkerState(element, point, selected) {
  const isReplayPoint = point.isReplayPoint !== false
  const replayIndexes = Array.isArray(point.replayIndexes)
    ? point.replayIndexes
    : [point.replayIndex].filter(index => index != null)
  const mapDates = Array.isArray(point.mapDates)
    ? point.mapDates
    : [point.mapDate].filter(Boolean)
  const isCurrentStation = isReplayPoint
    && props.playbackIndex >= 0
    && replayIndexes.includes(props.playbackIndex)
  const isPastStation = isReplayPoint
    && props.playbackIndex >= 0
    && replayIndexes.length > 0
    && replayIndexes.every(index => index < props.playbackIndex)
  const selectedMember = (point.members || []).find(
    member => idKey(member.id) === idKey(props.selectedId),
  )
  const displayedSequence = isCurrentStation
    ? props.playbackIndex + 1
    : selected && selectedMember?.sequenceNumber
      ? selectedMember.sequenceNumber
      : point.sequenceNumber
  const number = element.querySelector('.memory-map-marker-number')
  if (number) {
    number.textContent = Number.isInteger(Number(displayedSequence))
      ? String(displayedSequence)
      : '•'
  }
  const activeMembers = props.activeDate
    ? (point.members || []).filter(member => member.mapDate === props.activeDate)
    : point.members || []
  const visibleMemberCount = activeMembers.length || Number(point.memberCount) || 1
  const count = element.querySelector('.memory-map-marker-count')
  if (count) {
    count.textContent = `×${visibleMemberCount}`
    count.hidden = visibleMemberCount <= 1
  }
  element.classList.toggle('is-selected', selected)
  element.classList.toggle('is-replay-point', isReplayPoint)
  element.classList.toggle('is-browse-only', !isReplayPoint)
  element.classList.toggle('is-playback-current', isCurrentStation)
  element.classList.toggle('is-playback-past', isPastStation)
  element.classList.toggle(
    'is-day-muted',
    Boolean(props.activeDate && (!isReplayPoint || !mapDates.includes(props.activeDate))),
  )
  updateMarkerScale(element, selected)
}

function pointVisibleInScope(point) {
  if (!props.activeDate) return true
  return point.isReplayPoint !== false
}

function markerElement(point, selected) {
  const element = document.createElement('button')
  element.type = 'button'
  element.className = 'memory-map-marker'
  const visual = document.createElement('span')
  visual.className = 'memory-map-marker-visual'
  const number = document.createElement('span')
  number.className = 'memory-map-marker-number'
  number.textContent = Number.isInteger(Number(point.sequenceNumber)) ? String(point.sequenceNumber) : '•'
  visual.appendChild(number)
  if (Number(point.memberCount) > 1) {
    const count = document.createElement('span')
    count.className = 'memory-map-marker-count'
    count.textContent = `×${point.memberCount}`
    visual.appendChild(count)
  }
  element.appendChild(visual)
  applyMarkerState(element, point, selected)
  element.setAttribute('aria-label', markerLabel(point))
  element.title = markerLabel(point)
  element.addEventListener('click', (event) => {
    event.stopPropagation()
    emit('select', point.id)
  })
  return element
}

function clearMarkers() {
  markers.forEach(({ marker }) => marker.setMap(null))
  markers.clear()
}

function clearDestinationMarker() {
  destinationMarker?.setMap(null)
  destinationMarker = null
}

function renderDestinationMarker() {
  clearDestinationMarker()
  if (!map || !AMap || !fallbackCoordinate.value || displayPoints.value.length) return
  const element = document.createElement('div')
  element.className = 'memory-map-destination-marker'
  element.textContent = props.fallbackLabel || '目的城市'
  destinationMarker = new AMap.Marker({
    position: [fallbackCoordinate.value.longitude, fallbackCoordinate.value.latitude],
    content: element,
    anchor: 'bottom-center',
    zIndex: 50,
  })
  destinationMarker.setMap(map)
}

function clearRouteLines() {
  routePaths.value = []
}

function routePath(start, end, index) {
  const deltaX = end.x - start.x
  const deltaY = end.y - start.y
  const distance = Math.hypot(deltaX, deltaY)
  if (distance < 1) return ''

  const unitX = deltaX / distance
  const unitY = deltaY / distance
  const endpointInset = Math.min(18, distance * 0.24)
  const startX = start.x + unitX * endpointInset
  const startY = start.y + unitY * endpointInset
  const endX = end.x - unitX * endpointInset
  const endY = end.y - unitY * endpointInset
  const curveDirection = index % 2 === 0 ? -1 : 1
  const curveOffset = Math.min(34, Math.max(8, distance * 0.055)) * curveDirection
  const controlX = (startX + endX) / 2 - unitY * curveOffset
  const controlY = (startY + endY) / 2 + unitX * curveOffset
  return `M ${startX.toFixed(1)} ${startY.toFixed(1)} Q ${controlX.toFixed(1)} ${controlY.toFixed(1)} ${endX.toFixed(1)} ${endY.toFixed(1)}`
}

function updateRouteOverlay() {
  if (!map || !props.routeSegments.length) {
    clearRouteLines()
    return
  }

  routePaths.value = props.routeSegments
    .filter((segment) => !props.activeDate
      || (segment.fromDate === props.activeDate && segment.toDate === props.activeDate))
    .map((segment, index) => {
      const from = markers.get(idKey(segment.fromId))
      const to = markers.get(idKey(segment.toId))
      if (!from || !to) return null

      // Deliberately ignore visualDelta here. Overlap spreading makes pins
      // tappable, but route geometry must stay anchored to real coordinates.
      const start = map.lngLatToContainer([from.point.longitude, from.point.latitude])
      const end = map.lngLatToContainer([to.point.longitude, to.point.latitude])
      const d = routePath(start, end, index)
      if (!d) return null
      return {
        ...segment,
        key: `${segment.fromId}-${segment.toId}`,
        d,
        isPlayed: props.playbackIndex >= 0 && segment.toReplayIndex <= props.playbackIndex,
        isCurrent: props.playbackIndex >= 0 && segment.toReplayIndex === props.playbackIndex,
      }
    })
    .filter(Boolean)
}

function scheduleRouteOverlayUpdate() {
  if (routeAnimationFrame != null) return
  routeAnimationFrame = window.requestAnimationFrame(() => {
    routeAnimationFrame = null
    updateRouteOverlay()
  })
}

function spreadOverlappingMarkers() {
  if (!map || !AMap || markers.size < 2) return

  const markerScale = markerScaleForZoom()
  const overlapDistance = OVERLAP_DISTANCE * markerScale
  const entries = [...markers.values()]
    .filter(entry => entry.visible !== false)
    .map((entry) => ({
      ...entry,
      anchor: map.lngLatToContainer([entry.point.longitude, entry.point.latitude]),
    }))
  const groups = []

  entries.forEach((entry) => {
    const group = groups.find((candidate) => candidate.some((item) => {
      const deltaX = item.anchor.x - entry.anchor.x
      const deltaY = item.anchor.y - entry.anchor.y
      return Math.hypot(deltaX, deltaY) < overlapDistance
    }))
    if (group) group.push(entry)
    else groups.push([entry])
  })

  groups.forEach((group) => {
    group.forEach((entry, index) => {
      if (group.length === 1) {
        entry.marker.setOffset(new AMap.Pixel(0, 0))
        entry.visualDelta = { x: 0, y: 0 }
        return
      }
      const angle = -Math.PI / 2 + (Math.PI * 2 * index) / group.length
      const radius = (group.length <= 3 ? 24 : 30) * markerScale
      const visualDelta = {
        x: Math.round(Math.cos(angle) * radius),
        y: Math.round(Math.sin(angle) * radius),
      }
      entry.marker.setOffset(new AMap.Pixel(
        visualDelta.x,
        visualDelta.y,
      ))
      entry.visualDelta = visualDelta
    })
  })
  updateRouteOverlay()
}

function updateMarkerSelection() {
  markers.forEach((entry) => {
    const { element, marker, point } = entry
    const selected = pointContainsMemory(point, props.selectedId)
    const visible = pointVisibleInScope(point)
    entry.visible = visible
    if (visible) marker.show?.()
    else marker.hide?.()
    applyMarkerState(element, point, selected)
    marker.setzIndex?.(selected ? 140 : point.isReplayPoint === false ? 50 : 80)
  })
  spreadOverlappingMarkers()
}

function stagePadding(extra = 0) {
  const mobile = window.matchMedia('(max-width: 640px)').matches
  const mapHeight = mapElement.value?.getBoundingClientRect().height || 0
  const minimumVisibleMapHeight = mobile ? 140 : 180
  const maximumBottom = mapHeight > 0
    ? Math.max(90, mapHeight - minimumVisibleMapHeight)
    : Number.POSITIVE_INFINITY
  const bottom = Math.min(
    Math.max(90, Number(props.bottomInset) || 132),
    maximumBottom,
  )
  if (mobile) return [42 + extra, 28 + extra, bottom + extra, 28 + extra]
  return props.selectedId == null
    ? [48 + extra, 54 + extra, Math.max(112, bottom) + extra, 54 + extra]
    : [48 + extra, 54 + extra, Math.max(112, bottom) + extra, 342 + extra]
}

function panMapContentBy(deltaX, deltaY, duration = 0) {
  if (!map || !AMap || !mapElement.value) return
  const mapRect = mapElement.value.getBoundingClientRect()
  const targetCenter = map.containerToLngLat(new AMap.Pixel(
    mapRect.width / 2 - deltaX,
    mapRect.height / 2 - deltaY,
  ))
  map.panTo(targetCenter, duration)
}

function runProgrammaticViewChange(action, timeout = 900) {
  if (!map) return Promise.resolve(false)
  beginProgrammaticMove(timeout + 300)
  return new Promise((resolve) => {
    let settled = false
    let timer = null
    const finish = () => {
      if (settled) return
      settled = true
      if (timer != null) window.clearTimeout(timer)
      map?.off?.('moveend', finish)
      finishProgrammaticMove()
      resolve(true)
    }
    map.on?.('moveend', finish)
    timer = window.setTimeout(finish, timeout)
    try {
      action()
    } catch (error) {
      finish()
      throw error
    }
  })
}

async function fitEntries(entries, maxZoom = 13, { focusSelection = false } = {}) {
  if (!map || entries.length === 0) return false
  try {
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (entries.length === 1) {
      const point = entries[0].point
      await runProgrammaticViewChange(() => {
        map.setZoomAndCenter(
          Math.min(13, maxZoom),
          [point.longitude, point.latitude],
          reduceMotion,
          reduceMotion ? 0 : 260,
        )
      }, reduceMotion ? 100 : 720)
    } else {
      await runProgrammaticViewChange(() => {
        map.setFitView(
          entries.map((entry) => entry.marker),
          reduceMotion,
          stagePadding(OVERLAP_DISTANCE),
          maxZoom,
        )
      }, reduceMotion ? 100 : 900)
    }
    if (focusSelection && props.selectedId != null) positionSelected()
    return true
  } catch (error) {
    console.error('Failed to fit map markers.', error)
    return false
  }
}

function finishSelectedPlacement(expectedId) {
  if (selectionPlacementTimer != null) window.clearTimeout(selectionPlacementTimer)
  selectionPlacementTimer = null
  selectionPanId = ''
  finishProgrammaticMove()
  if (positionSelectedAfterFit && expectedId === idKey(props.selectedId)) {
    positionSelectedAfterFit = false
    window.requestAnimationFrame(positionSelected)
    return
  }
  if (expectedId && expectedId === idKey(props.selectedId)) {
    emit('selection-positioned', props.selectedId)
  }
}

function markerPlacement(entry) {
  if (!map || !entry || !mapElement.value) return null
  const mapRect = mapElement.value.getBoundingClientRect()
  const current = map.lngLatToContainer([entry.point.longitude, entry.point.latitude])
  const currentX = current.x + (entry.visualDelta?.x || 0)
  const currentY = current.y + (entry.visualDelta?.y || 0)
  const mobile = mapRect.width <= 640
  const topSafe = mobile ? 58 : 72
  const minimumVisibleMapHeight = mobile ? 140 : 180
  const safeBottomInset = Math.min(
    Math.max(90, Number(props.bottomInset) || 132),
    Math.max(90, mapRect.height - minimumVisibleMapHeight),
  )
  const availableBottom = mapRect.height - safeBottomInset
  const targetX = Math.min(mapRect.width - 48, mapRect.width * (mobile ? 0.74 : 0.68))
  const targetY = mobile
    ? Math.max(topSafe, Math.min(availableBottom - 24, availableBottom * 0.45))
    : Math.max(topSafe, Math.min(availableBottom - 28, availableBottom * 0.5))
  const horizontalSafe = mobile ? 30 : 46
  const verticalSafe = mobile ? 26 : 34
  return {
    currentX,
    currentY,
    targetX,
    targetY,
    deltaX: targetX - currentX,
    deltaY: targetY - currentY,
    insideSafeViewport: currentX >= horizontalSafe
      && currentX <= mapRect.width - horizontalSafe
      && currentY >= topSafe
      && currentY <= availableBottom - verticalSafe,
  }
}

function positionSelected() {
  if (selectionPositioningSuspendCount > 0) return
  const selected = markerForMemory(props.selectedId)
  const placement = markerPlacement(selected)
  if (!placement) return

  const { deltaX, deltaY } = placement
  const expectedId = idKey(props.selectedId)
  if (selectionPanId === expectedId) {
    positionSelectedAfterFit = true
    return
  }
  positionSelectedAfterFit = false
  if (fitPlacementTimer != null) window.clearTimeout(fitPlacementTimer)
  fitPlacementTimer = null
  if (Math.abs(deltaX) < 4 && Math.abs(deltaY) < 4) {
    finishSelectedPlacement(expectedId)
    return
  }

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const playbackTransition = Number(props.playbackIndex) >= 0
  const panDuration = reduceMotion ? 0 : playbackTransition ? 680 : 260
  const placementDelay = reduceMotion ? 40 : playbackTransition ? 760 : 340
  selectionPanId = expectedId
  beginProgrammaticMove(placementDelay + 280)
  panMapContentBy(deltaX, deltaY, panDuration)
  selectionPlacementTimer = window.setTimeout(
    () => finishSelectedPlacement(expectedId),
    placementDelay,
  )
  scheduleRouteOverlayUpdate()
}

function focusSelected() {
  positionSelected()
}

function overviewZoomForDistance(distanceMeters, currentZoom) {
  const distance = Number(distanceMeters) || 0
  let suggestedZoom = 10
  if (distance >= 1_500_000) suggestedZoom = 4
  else if (distance >= 700_000) suggestedZoom = 5
  else if (distance >= 300_000) suggestedZoom = 6
  else if (distance >= 120_000) suggestedZoom = 7
  else if (distance >= 50_000) suggestedZoom = 8
  else if (distance >= 20_000) suggestedZoom = 9
  return Math.max(4, Math.min(suggestedZoom, Math.max(4, currentZoom - 2)))
}

async function positionEntryForCamera(entry, duration = 220) {
  const placement = markerPlacement(entry)
  if (!placement) return false
  if (Math.abs(placement.deltaX) < 4 && Math.abs(placement.deltaY) < 4) return true
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  await runProgrammaticViewChange(() => {
    panMapContentBy(
      placement.deltaX,
      placement.deltaY,
      reduceMotion ? 0 : duration,
    )
  }, reduceMotion ? 80 : duration + 80)
  return true
}

async function transitionToMemory(
  memoryId,
  {
    distanceMeters = 0,
    targetZoom = 14,
    longDistanceMeters = 50_000,
  } = {},
) {
  const target = markerForMemory(memoryId)
  if (!map || !target) return false

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const currentZoom = Number(map.getZoom?.()) || 10
  const nextZoom = Math.min(16, Math.max(11, Number(targetZoom) || 14))
  const placement = markerPlacement(target)
  const requiresOverview = Number(distanceMeters) >= Number(longDistanceMeters)
    || !placement?.insideSafeViewport

  if (selectionPlacementTimer != null) window.clearTimeout(selectionPlacementTimer)
  if (fitPlacementTimer != null) window.clearTimeout(fitPlacementTimer)
  selectionPlacementTimer = null
  fitPlacementTimer = null
  selectionPanId = ''
  positionSelectedAfterFit = false
  finishProgrammaticMove()
  map.stopMove?.()

  if (reduceMotion) {
    await runProgrammaticViewChange(() => {
      map.setZoomAndCenter(
        nextZoom,
        [target.point.longitude, target.point.latitude],
        true,
        0,
      )
    }, 100)
    await positionEntryForCamera(target, 0)
    scheduleRouteOverlayUpdate()
    return true
  }

  if (!requiresOverview) {
    await positionEntryForCamera(target, 520)
    scheduleRouteOverlayUpdate()
    return true
  }

  const overviewZoom = overviewZoomForDistance(distanceMeters, currentZoom)
  if (currentZoom > overviewZoom + 0.25) {
    const currentCenter = map.getCenter?.()
    await runProgrammaticViewChange(() => {
      map.setZoomAndCenter(overviewZoom, currentCenter, false, 220)
    }, 255)
  }

  await runProgrammaticViewChange(() => {
    map.panTo(
      [target.point.longitude, target.point.latitude],
      320,
    )
  }, 355)

  await runProgrammaticViewChange(() => {
    map.setZoomAndCenter(
      nextZoom,
      [target.point.longitude, target.point.latitude],
      false,
      260,
    )
  }, 295)

  spreadOverlappingMarkers()
  await positionEntryForCamera(target, 100)
  scheduleRouteOverlayUpdate()
  return true
}

async function zoomToSelected(targetZoom = 15) {
  const selected = markerForMemory(props.selectedId)
  if (!map || !selected) return false

  const currentZoom = Number(map.getZoom?.()) || 10
  const nextZoom = Math.min(18, Math.max(currentZoom + 2, Number(targetZoom) || 15))
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (selectionPlacementTimer != null) window.clearTimeout(selectionPlacementTimer)
  if (fitPlacementTimer != null) window.clearTimeout(fitPlacementTimer)
  selectionPlacementTimer = null
  fitPlacementTimer = null
  selectionPanId = ''
  positionSelectedAfterFit = false
  finishProgrammaticMove()
  map.stopMove?.()

  suspendSelectionPositioning()
  try {
    await runProgrammaticViewChange(() => {
      map.setZoomAndCenter(
        nextZoom,
        [selected.point.longitude, selected.point.latitude],
        reduceMotion,
        reduceMotion ? 0 : 320,
      )
    }, reduceMotion ? 100 : 820)
  } finally {
    resumeSelectionPositioning()
  }
  scheduleRouteOverlayUpdate()
  emit('selection-positioned', props.selectedId)
  return true
}

function fitAll() {
  if (markers.size) {
    const allEntries = [...markers.values()]
    const replayEntries = allEntries.filter(entry => entry.point.isReplayPoint !== false)
    return fitEntries(replayEntries.length ? replayEntries : allEntries, 13)
  } else if (map && fallbackCoordinate.value) {
    return runProgrammaticViewChange(() => {
      map.setZoomAndCenter(10, [fallbackCoordinate.value.longitude, fallbackCoordinate.value.latitude])
    }, 720)
  }
  return Promise.resolve(false)
}

function fitPointIds(pointIds, maxZoom = 13) {
  const requestedIds = new Set((pointIds || []).map(idKey).filter(Boolean))
  const entries = [...markers.entries()]
    .filter(([pointId]) => requestedIds.has(pointId))
    .map(([, entry]) => entry)
  return fitEntries(entries, maxZoom)
}

function renderMarkers(shouldFit = false) {
  if (!map || !AMap) return
  clearDestinationMarker()
  clearMarkers()
  displayPoints.value.forEach((point) => {
    try {
      const selected = pointContainsMemory(point, props.selectedId)
      const element = markerElement(point, selected)
      const marker = new AMap.Marker({
        position: [point.longitude, point.latitude],
        content: element,
        offset: new AMap.Pixel(0, 0),
        anchor: 'bottom-center',
        zIndex: selected ? 140 : point.isReplayPoint === false ? 50 : 80,
      })
      marker.setMap(map)
      const visible = pointVisibleInScope(point)
      if (!visible) marker.hide?.()
      markers.set(idKey(point.id), {
        marker,
        point,
        element,
        visible,
        visualDelta: { x: 0, y: 0 },
      })
    } catch (error) {
      console.error('Failed to create an AMap marker.', error)
    }
  })
  spreadOverlappingMarkers()
  if (displayPoints.value.length === 0) renderDestinationMarker()
  if (shouldFit) fitAll()
}

function cancelInitialRender() {
  if (initialRenderTimer != null) window.clearTimeout(initialRenderTimer)
  if (initialRenderFrame != null) window.cancelAnimationFrame(initialRenderFrame)
  if (map && initialRenderHandler) map.off?.('complete', initialRenderHandler)
  initialRenderTimer = null
  initialRenderFrame = null
  initialRenderHandler = null
}

function cleanUpMap() {
  cancelInitialRender()
  if (routeAnimationFrame != null) window.cancelAnimationFrame(routeAnimationFrame)
  if (selectionPlacementTimer != null) window.clearTimeout(selectionPlacementTimer)
  if (fitPlacementTimer != null) window.clearTimeout(fitPlacementTimer)
  routeAnimationFrame = null
  selectionPlacementTimer = null
  fitPlacementTimer = null
  selectionPanId = ''
  positionSelectedAfterFit = false
  finishProgrammaticMove()
  resizeObserver?.disconnect()
  resizeObserver = null
  clearRouteLines()
  clearDestinationMarker()
  clearMarkers()
  map?.destroy()
  map = null
  AMap = null
}

function configureSecurity() {
  const serviceHost = import.meta.env.VITE_AMAP_SERVICE_HOST?.trim()
  const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_JS_CODE?.trim()
  if (serviceHost) {
    window._AMapSecurityConfig = { ...window._AMapSecurityConfig, serviceHost }
    return true
  }
  if (securityJsCode) {
    window._AMapSecurityConfig = { ...window._AMapSecurityConfig, securityJsCode }
    return true
  }
  state.value = 'missing-security'
  return false
}

async function initializeMap() {
  cleanUpMap()
  errorMessage.value = ''
  const key = import.meta.env.VITE_AMAP_JS_API_KEY?.trim()
  if (!key) {
    state.value = 'missing-key'
    return
  }
  if (!configureSecurity()) return
  if (!mapElement.value || (displayPoints.value.length === 0 && !fallbackCoordinate.value)) {
    state.value = 'idle'
    return
  }

  state.value = 'loading'
  try {
    const loadedAMap = await AMapLoader.load({
      key,
      version: '2.0',
    })
    if (destroyed || !mapElement.value) return
    AMap = loadedAMap
    map = new AMap.Map(mapElement.value, {
      viewMode: '2D',
      zoom: fallbackCoordinate.value && displayPoints.value.length === 0 ? 10 : 5,
      center: fallbackCoordinate.value && displayPoints.value.length === 0
        ? [fallbackCoordinate.value.longitude, fallbackCoordinate.value.latitude]
        : undefined,
      zoomEnable: true,
      dragEnable: true,
    })
    resizeObserver = new ResizeObserver(() => {
      map?.resize()
      updateRouteOverlay()
    })
    resizeObserver.observe(mapElement.value)
    map.on('zoomend', () => {
      updateMarkerScales()
      spreadOverlappingMarkers()
    })
    map.on('dragstart', emitUserInteraction)
    map.on('zoomstart', emitUserInteraction)
    map.on('click', emitUserInteraction)
    map.on('moveend', () => {
      spreadOverlappingMarkers()
      if (selectionPanId) {
        finishSelectedPlacement(selectionPanId)
        return
      }
      if (positionSelectedAfterFit) {
        positionSelectedAfterFit = false
        positionSelected()
      }
    })
    map.on('mapmove', scheduleRouteOverlayUpdate)
    map.on('zoomchange', () => {
      updateMarkerScales()
      scheduleRouteOverlayUpdate()
    })

    const initializedMap = map
    let initialRenderComplete = false
    const finishInitialRender = () => {
      if (initialRenderComplete || destroyed || map !== initializedMap) return
      initialRenderComplete = true
      if (initialRenderTimer != null) window.clearTimeout(initialRenderTimer)
      initialRenderTimer = null
      initializedMap.off?.('complete', finishInitialRender)
      initialRenderHandler = null
      initializedMap.resize()
      initialRenderFrame = window.requestAnimationFrame(() => {
        initialRenderFrame = null
        if (destroyed || map !== initializedMap) return
        renderMarkers(false)
        if (props.focusSelectedOnReady) {
          const selected = markerForMemory(props.selectedId)
          if (selected) {
            fitEntries([selected], 13).then(focusSelected)
          } else {
            fitAll()
          }
        }
        scheduleRouteOverlayUpdate()
        state.value = 'ready'
        emit('ready')
      })
    }
    initialRenderHandler = finishInitialRender
    initializedMap.on('complete', finishInitialRender)
    // Some cached AMap loads do not emit `complete` again. Keep a bounded fallback.
    initialRenderTimer = window.setTimeout(finishInitialRender, 1200)
  } catch (error) {
    cleanUpMap()
    console.error('Failed to load AMap JS API.', error)
    errorMessage.value = '地图服务暂时不可用，请检查 Web JS API Key、域名白名单和网络后重试。'
    state.value = 'error'
  }
}

function retry() {
  initializeMap()
}

watch(mapSignature, (next, previous) => {
  if (state.value === 'ready' && next !== previous) renderMarkers(true)
  else if (next !== previous) initializeMap()
})
watch(() => props.selectedId, () => {
  updateMarkerSelection()
  if (props.selectedId != null) {
    focusSelected()
  } else {
    if (selectionPlacementTimer != null) window.clearTimeout(selectionPlacementTimer)
    if (fitPlacementTimer != null) window.clearTimeout(fitPlacementTimer)
    selectionPlacementTimer = null
    fitPlacementTimer = null
    selectionPanId = ''
    positionSelectedAfterFit = false
  }
})
watch(() => props.activeDate, () => {
  updateMarkerSelection()
  scheduleRouteOverlayUpdate()
})
watch(() => props.playbackIndex, () => {
  updateMarkerSelection()
  scheduleRouteOverlayUpdate()
})
watch(() => props.bottomInset, () => {
  if (!map) return
  window.requestAnimationFrame(() => {
    if (props.selectedId != null && selectionPositioningSuspendCount === 0) positionSelected()
    scheduleRouteOverlayUpdate()
  })
})
watch(routeSignature, scheduleRouteOverlayUpdate)
onMounted(initializeMap)
onBeforeUnmount(() => {
  destroyed = true
  cleanUpMap()
})

defineExpose({
  focusSelected,
  fitAll,
  fitPointIds,
  transitionToMemory,
  zoomToSelected,
  suspendSelectionPositioning,
  resumeSelectionPositioning,
})
</script>

<template>
  <section class="memory-map-shell" aria-label="旅行记忆地图">
    <div ref="mapElement" class="memory-map-canvas"></div>
    <svg v-if="routePaths.length" class="memory-map-route-overlay" aria-hidden="true">
      <defs>
        <marker id="memory-route-arrow" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="5" markerHeight="5" orient="auto-start-reverse">
          <path d="M 0 0 L 8 4 L 0 8 z"></path>
        </marker>
      </defs>
      <path
        v-for="segment in routePaths"
        :key="segment.key"
        :class="[
          'memory-map-route-segment',
          { 'is-played': segment.isPlayed, 'is-current': segment.isCurrent },
        ]"
        :d="segment.d"
        marker-end="url(#memory-route-arrow)"
      ></path>
    </svg>
    <div v-if="state !== 'ready'" class="memory-map-state" role="status">
      <p v-if="state === 'loading'">正在加载地图...</p>
      <template v-else-if="state === 'missing-key'">
        <strong>地图尚未配置</strong>
        <p>请配置高德 Web JS API Key 后查看真实地图。</p>
      </template>
      <template v-else-if="state === 'missing-security'">
        <strong>地图安全配置缺失</strong>
        <p>本地可配置安全密钥；生产环境应配置安全代理地址。</p>
      </template>
      <template v-else-if="state === 'error'">
        <strong>地图服务暂不可用</strong>
        <p>{{ errorMessage }}</p>
        <button type="button" class="ghost" @click="retry">重新加载地图</button>
      </template>
    </div>
  </section>
</template>
