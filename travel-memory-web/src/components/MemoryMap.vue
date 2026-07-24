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
const emit = defineEmits(['select', 'selection-positioned', 'interaction'])

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
  .map((point) => `${point.id}:${point.originalLatitude}:${point.originalLongitude}:${point.sequenceNumber}:${point.mapDate}:${point.isReplayPoint}`).join('|'))
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
let destroyed = false
let destinationMarker = null
const markers = new Map()
const OVERLAP_DISTANCE = 42

function idKey(value) {
  return value == null ? '' : String(value)
}

function markerLabel(point) {
  const excerpt = String(point.content || '').trim().replace(/\s+/g, ' ').slice(0, 24)
  const label = point.locationName || excerpt || '旅行记忆'
  return point.isReplayPoint
    ? `第 ${point.sequenceNumber || '?'} 站，${label}`
    : `未纳入路径回放的位置，${label}`
}

function applyMarkerState(element, point, selected) {
  const isReplayPoint = point.isReplayPoint !== false
  const isCurrentStation = isReplayPoint && props.playbackIndex >= 0 && point.replayIndex === props.playbackIndex
  const isPastStation = isReplayPoint && props.playbackIndex >= 0 && point.replayIndex < props.playbackIndex
  element.classList.toggle('is-selected', selected)
  element.classList.toggle('is-replay-point', isReplayPoint)
  element.classList.toggle('is-browse-only', !isReplayPoint)
  element.classList.toggle('is-playback-current', isCurrentStation)
  element.classList.toggle('is-playback-past', isPastStation)
  element.classList.toggle(
    'is-day-muted',
    Boolean(props.activeDate && (!isReplayPoint || point.mapDate !== props.activeDate)),
  )
}

function markerElement(point, selected) {
  const element = document.createElement('button')
  element.type = 'button'
  element.className = 'memory-map-marker'
  const number = document.createElement('span')
  number.className = 'memory-map-marker-number'
  number.textContent = Number.isInteger(Number(point.sequenceNumber)) ? String(point.sequenceNumber) : '•'
  element.appendChild(number)
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

  const entries = [...markers.values()].map((entry) => ({
    ...entry,
    anchor: map.lngLatToContainer([entry.point.longitude, entry.point.latitude]),
  }))
  const groups = []

  entries.forEach((entry) => {
    const group = groups.find((candidate) => candidate.some((item) => {
      const deltaX = item.anchor.x - entry.anchor.x
      const deltaY = item.anchor.y - entry.anchor.y
      return Math.hypot(deltaX, deltaY) < OVERLAP_DISTANCE
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
      const radius = group.length <= 3 ? 24 : 30
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
  const selectedKey = idKey(props.selectedId)
  markers.forEach(({ element, marker, point }) => {
    const selected = idKey(point.id) === selectedKey
    applyMarkerState(element, point, selected)
    marker.setzIndex?.(selected ? 140 : point.isReplayPoint === false ? 50 : 80)
  })
}

function stagePadding() {
  const mobile = window.matchMedia('(max-width: 640px)').matches
  const bottom = Math.max(90, Number(props.bottomInset) || 132)
  if (mobile) return [42, 28, bottom, 28]
  return props.selectedId == null
    ? [48, 54, Math.max(112, bottom), 54]
    : [48, 54, Math.max(112, bottom), 342]
}

function fitEntries(entries, maxZoom = 13) {
  if (!map || entries.length === 0) return
  try {
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    map.setFitView(entries.map((entry) => entry.marker), reduceMotion, stagePadding(), maxZoom)
    if (props.selectedId != null) {
      positionSelectedAfterFit = true
      if (fitPlacementTimer != null) window.clearTimeout(fitPlacementTimer)
      fitPlacementTimer = window.setTimeout(() => {
        if (!positionSelectedAfterFit) return
        positionSelectedAfterFit = false
        positionSelected()
      }, reduceMotion ? 40 : 420)
    }
  } catch (error) {
    console.error('Failed to fit map markers.', error)
  }
}

function finishSelectedPlacement(expectedId) {
  if (selectionPlacementTimer != null) window.clearTimeout(selectionPlacementTimer)
  selectionPlacementTimer = null
  selectionPanId = ''
  if (expectedId && expectedId === idKey(props.selectedId)) {
    emit('selection-positioned', props.selectedId)
  }
}

function positionSelected() {
  const selected = markers.get(idKey(props.selectedId))
  if (!map || !selected || !mapElement.value) return

  const mapRect = mapElement.value.getBoundingClientRect()
  const current = map.lngLatToContainer([selected.point.longitude, selected.point.latitude])
  const currentX = current.x + (selected.visualDelta?.x || 0)
  const currentY = current.y + (selected.visualDelta?.y || 0)
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
  const deltaX = targetX - currentX
  const deltaY = targetY - currentY
  const expectedId = idKey(props.selectedId)
  positionSelectedAfterFit = false
  if (fitPlacementTimer != null) window.clearTimeout(fitPlacementTimer)
  fitPlacementTimer = null
  if (Math.abs(deltaX) < 4 && Math.abs(deltaY) < 4) {
    finishSelectedPlacement(expectedId)
    return
  }

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  selectionPanId = expectedId
  map.panBy(deltaX, deltaY, reduceMotion ? 0 : 180)
  selectionPlacementTimer = window.setTimeout(
    () => finishSelectedPlacement(expectedId),
    reduceMotion ? 40 : 260,
  )
  scheduleRouteOverlayUpdate()
}

function focusSelected() {
  positionSelected()
}

function fitAll() {
  if (markers.size) {
    fitEntries([...markers.values()], 12)
  } else if (map && fallbackCoordinate.value) {
    map.setZoomAndCenter(10, [fallbackCoordinate.value.longitude, fallbackCoordinate.value.latitude])
  }
}

function renderMarkers(shouldFit = false) {
  if (!map || !AMap) return
  clearDestinationMarker()
  clearMarkers()
  displayPoints.value.forEach((point) => {
    try {
      const selected = idKey(point.id) === idKey(props.selectedId)
      const element = markerElement(point, selected)
      const marker = new AMap.Marker({
        position: [point.longitude, point.latitude],
        content: element,
        offset: new AMap.Pixel(0, 0),
        anchor: 'bottom-center',
        zIndex: selected ? 140 : point.isReplayPoint === false ? 50 : 80,
      })
      marker.setMap(map)
      markers.set(idKey(point.id), { marker, point, element, visualDelta: { x: 0, y: 0 } })
    } catch (error) {
      console.error('Failed to create an AMap marker.', error)
    }
  })
  spreadOverlappingMarkers()
  if (displayPoints.value.length === 0) renderDestinationMarker()
  if (shouldFit) fitAll()
}

function cleanUpMap() {
  if (routeAnimationFrame != null) window.cancelAnimationFrame(routeAnimationFrame)
  if (selectionPlacementTimer != null) window.clearTimeout(selectionPlacementTimer)
  if (fitPlacementTimer != null) window.clearTimeout(fitPlacementTimer)
  routeAnimationFrame = null
  selectionPlacementTimer = null
  fitPlacementTimer = null
  selectionPanId = ''
  positionSelectedAfterFit = false
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
    map.on('zoomend', spreadOverlappingMarkers)
    map.on('dragstart', () => emit('interaction'))
    map.on('zoomstart', () => emit('interaction'))
    map.on('click', () => emit('interaction'))
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
    map.on('zoomchange', scheduleRouteOverlayUpdate)
    renderMarkers(!props.focusSelectedOnReady)
    if (props.focusSelectedOnReady) focusSelected()
    state.value = 'ready'
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
    if (props.selectedId != null) positionSelected()
    scheduleRouteOverlayUpdate()
  })
})
watch(routeSignature, scheduleRouteOverlayUpdate)
onMounted(initializeMap)
onBeforeUnmount(() => {
  destroyed = true
  cleanUpMap()
})

defineExpose({ focusSelected, fitAll })
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
