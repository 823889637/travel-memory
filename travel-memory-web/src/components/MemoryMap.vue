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
  .map((segment) => [
    segment.fromId,
    segment.toId,
    segment.fromReplayIndex,
    segment.toReplayIndex,
    segment.fromDate,
    segment.toDate,
    segment.isLongDistance ? 'long' : 'local',
  ].join(':')).join('|'))

let map = null
let AMap = null
let resizeObserver = null
let routeAnimationFrame = null
let markerLayoutFrame = null
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
const routeOverlays = new Map()
const longDistanceOverlays = new Set()
let renderedRouteState = ''
const OVERLAP_DISTANCE = 42
const FIT_EDGE_PADDING = 16
const FIT_BOTTOM_PADDING = 64
const LONG_DISTANCE_MAX_VISIBLE_ZOOM = 11

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

function markerMemberSuffix(members, count) {
  const visibleCount = Number(count) || 1
  if (visibleCount <= 1) return ''
  const sequences = [...new Set((members || [])
    .map(member => Number(member.sequenceNumber))
    .filter(Number.isInteger))]
    .sort((left, right) => left - right)
  const isConsecutive = sequences.length === visibleCount
    && sequences.every((sequence, index) => index === 0 || sequence === sequences[index - 1] + 1)
  return isConsecutive ? `–${sequences.at(-1)}` : `×${visibleCount}`
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
    count.textContent = markerMemberSuffix(activeMembers, visibleMemberCount)
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
    count.textContent = markerMemberSuffix(point.members, point.memberCount)
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
  routeOverlays.forEach(overlay => overlay.setMap?.(null))
  routeOverlays.clear()
  longDistanceOverlays.clear()
  renderedRouteState = ''
}

function updateLongDistanceOverlayVisibility() {
  const zoom = Number(map?.getZoom?.())
  const shouldShow = !Number.isFinite(zoom) || zoom <= LONG_DISTANCE_MAX_VISIBLE_ZOOM
  longDistanceOverlays.forEach((overlay) => {
    if (shouldShow) overlay.show?.()
    else overlay.hide?.()
  })
}

function longDistanceArcPath(from, to) {
  const start = [Number(from.point.longitude), Number(from.point.latitude)]
  const end = [Number(to.point.longitude), Number(to.point.latitude)]
  const deltaX = end[0] - start[0]
  const deltaY = end[1] - start[1]
  const length = Math.hypot(deltaX, deltaY)
  if (!Number.isFinite(length) || length <= 0) return [start, end]

  // A fixed side relative to travel direction makes reciprocal journeys
  // curve to opposite sides because their perpendicular vectors are reversed.
  const curveOffset = Math.min(1.2, length * 0.12)
  const perpendicularX = -deltaY / length
  const perpendicularY = deltaX / length
  return Array.from({ length: 17 }, (_, step) => {
    const progress = step / 16
    const curve = 4 * progress * (1 - progress)
    return [
      start[0] + deltaX * progress + perpendicularX * curveOffset * curve,
      start[1] + deltaY * progress + perpendicularY * curveOffset * curve,
    ]
  })
}

function updateRouteOverlay() {
  if (!map || !AMap || !props.routeSegments.length) {
    clearRouteLines()
    return
  }

  const nextState = [
    routeSignature.value,
    props.activeDate || '',
    Number(props.playbackIndex),
    pointSignature.value,
  ].join('|')
  if (nextState === renderedRouteState) return

  clearRouteLines()
  props.routeSegments
    .filter((segment) => !props.activeDate
      || (segment.fromDate === props.activeDate && segment.toDate === props.activeDate))
    .forEach((segment) => {
      const from = markers.get(idKey(segment.fromId))
      const to = markers.get(idKey(segment.toId))
      if (!from || !to) return

      const isLongDistance = segment.isLongDistance === true
      const isPlayed = props.playbackIndex >= 0
        && segment.toReplayIndex <= props.playbackIndex
      const isCurrent = props.playbackIndex >= 0
        && segment.toReplayIndex === props.playbackIndex
      const key = [
        segment.fromId,
        segment.toId,
        segment.fromReplayIndex,
        segment.toReplayIndex,
      ].join('-')
      const path = isLongDistance
        ? longDistanceArcPath(from, to)
        : [
            [from.point.longitude, from.point.latitude],
            [to.point.longitude, to.point.latitude],
          ]
      const polyline = new AMap.Polyline({
        path,
        strokeColor: isLongDistance
          ? isCurrent ? '#a94726' : '#a98270'
          : isCurrent ? '#a94726' : '#b55f3c',
        strokeOpacity: isLongDistance
          ? isCurrent ? 0.82 : isPlayed ? 0.58 : 0.38
          : isCurrent ? 1 : isPlayed ? 0.9 : 0.62,
        strokeWeight: isLongDistance
          ? isCurrent ? 3 : 2
          : isCurrent ? 5 : isPlayed ? 4 : 3,
        strokeStyle: isLongDistance || (!isCurrent && !isPlayed) ? 'dashed' : 'solid',
        strokeDasharray: isLongDistance ? [6, 10] : [9, 7],
        lineJoin: 'round',
        lineCap: 'round',
        zIndex: isCurrent ? 72 : isPlayed ? 68 : 62,
        bubble: true,
      })
      polyline.setMap(map)
      routeOverlays.set(key, polyline)
      if (isLongDistance) {
        longDistanceOverlays.add(polyline)
      }
    })
  updateLongDistanceOverlayVisibility()
  renderedRouteState = nextState
}

function scheduleRouteOverlayUpdate() {
  if (routeAnimationFrame != null) return
  routeAnimationFrame = window.requestAnimationFrame(() => {
    routeAnimationFrame = null
    updateRouteOverlay()
  })
}

function scheduleMarkerLayout() {
  if (markerLayoutFrame != null) return
  markerLayoutFrame = window.requestAnimationFrame(() => {
    markerLayoutFrame = null
    updateMarkerScales()
    spreadOverlappingMarkers()
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

function stagePadding(edgeExtra = 0, bottomExtra = edgeExtra) {
  const mobile = window.matchMedia('(max-width: 640px)').matches
  const mapHeight = mapElement.value?.getBoundingClientRect().height || 0
  const minimumVisibleMapHeight = mobile ? 140 : 180
  const maximumBottom = mapHeight > 0
    ? Math.max(90, mapHeight - minimumVisibleMapHeight)
    : Number.POSITIVE_INFINITY
  const bottom = Math.min(
    Math.max(90, Number(props.bottomInset) || 132) + bottomExtra,
    maximumBottom,
  )
  if (mobile) return [42 + edgeExtra, 28 + edgeExtra, bottom, 28 + edgeExtra]
  return props.selectedId == null
    ? [48 + edgeExtra, 54 + edgeExtra, Math.max(112, bottom), 54 + edgeExtra]
    : [48 + edgeExtra, 54 + edgeExtra, Math.max(112, bottom), 342 + edgeExtra]
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
          stagePadding(FIT_EDGE_PADDING, FIT_BOTTOM_PADDING),
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
  const horizontalSafe = mobile ? 30 : 46
  const verticalSafe = mobile ? 26 : 34
  const safeLeft = horizontalSafe
  const safeRight = Math.max(safeLeft, mapRect.width - horizontalSafe)
  const safeTop = topSafe
  const safeBottom = Math.max(safeTop, availableBottom - verticalSafe)
  const targetX = Math.min(safeRight, Math.max(safeLeft, currentX))
  const targetY = Math.min(safeBottom, Math.max(safeTop, currentY))
  return {
    currentX,
    currentY,
    targetX,
    targetY,
    deltaX: targetX - currentX,
    deltaY: targetY - currentY,
    insideSafeViewport: currentX >= safeLeft
      && currentX <= safeRight
      && currentY >= safeTop
      && currentY <= safeBottom,
  }
}

function positionSelected() {
  if (selectionPositioningSuspendCount > 0) return
  const selected = markerForMemory(props.selectedId)
  const placement = markerPlacement(selected)
  if (!placement) return

  const { deltaX, deltaY, insideSafeViewport } = placement
  const expectedId = idKey(props.selectedId)
  if (selectionPanId === expectedId) {
    positionSelectedAfterFit = true
    return
  }
  positionSelectedAfterFit = false
  if (fitPlacementTimer != null) window.clearTimeout(fitPlacementTimer)
  fitPlacementTimer = null
  // Opening or resizing the bottom dock must not recenter a Marker that is
  // already visible. Only compensate when the card would actually cover it.
  if (insideSafeViewport || (Math.abs(deltaX) < 4 && Math.abs(deltaY) < 4)) {
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
    return fitEntries(allEntries, 13)
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
  scheduleRouteOverlayUpdate()
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
  if (markerLayoutFrame != null) window.cancelAnimationFrame(markerLayoutFrame)
  if (selectionPlacementTimer != null) window.clearTimeout(selectionPlacementTimer)
  if (fitPlacementTimer != null) window.clearTimeout(fitPlacementTimer)
  routeAnimationFrame = null
  markerLayoutFrame = null
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
      scheduleMarkerLayout()
    })
    resizeObserver.observe(mapElement.value)
    map.on('zoomend', () => {
      scheduleMarkerLayout()
      updateLongDistanceOverlayVisibility()
    })
    map.on('dragstart', emitUserInteraction)
    map.on('zoomstart', emitUserInteraction)
    map.on('click', emitUserInteraction)
    map.on('moveend', () => {
      scheduleMarkerLayout()
      if (selectionPanId) {
        finishSelectedPlacement(selectionPanId)
        return
      }
      if (positionSelectedAfterFit) {
        positionSelectedAfterFit = false
        positionSelected()
      }
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
