<script setup>
import AMapLoader from '@amap/amap-jsapi-loader'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { isValidWgs84Coordinate, wgs84ToGcj02 } from '../utils/coordinates'

const props = defineProps({
  points: { type: Array, default: () => [] },
  selectedId: { type: [String, Number], default: null },
  activeDate: { type: String, default: '' },
  focusSelectedOnReady: { type: Boolean, default: false },
})
const emit = defineEmits(['select'])

const mapElement = ref(null)
const state = ref('idle')
const errorMessage = ref('')
const routePaths = ref([])
const displayPoints = computed(() => props.points.map((point) => {
  if (!isValidWgs84Coordinate(point.latitude, point.longitude)) return null
  const coordinate = wgs84ToGcj02(point.latitude, point.longitude)
  return coordinate ? { ...point, ...coordinate } : null
}).filter(Boolean))
const pointSignature = computed(() => displayPoints.value
  .map((point) => `${point.id}:${point.latitude}:${point.longitude}:${point.sequenceNumber}:${point.mapDate}`).join('|'))

let map = null
let AMap = null
let resizeObserver = null
let routeAnimationFrame = null
let destroyed = false
const markers = new Map()
const MARKER_SIZE = 32
const OVERLAP_DISTANCE = 34

function idKey(value) {
  return value == null ? '' : String(value)
}

function markerLabel(point) {
  const excerpt = String(point.content || '').trim().replace(/\s+/g, ' ').slice(0, 24)
  const label = point.locationName || excerpt || '旅行记忆'
  return `第 ${point.sequenceNumber || '?'} 个地点，${label}`
}

function markerElement(point, selected) {
  const element = document.createElement('button')
  element.type = 'button'
  element.className = 'memory-map-marker'
  element.textContent = Number.isInteger(Number(point.sequenceNumber)) ? String(point.sequenceNumber) : '•'
  element.classList.toggle('is-selected', selected)
  element.classList.toggle('is-day-muted', Boolean(props.activeDate && point.mapDate !== props.activeDate))
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
  if (!map || markers.size < 2) {
    clearRouteLines()
    return
  }

  const orderedEntries = displayPoints.value
    .map((point) => markers.get(idKey(point.id)))
    .filter(Boolean)
  const visualPoints = orderedEntries.map((entry) => {
    const anchor = map.lngLatToContainer([entry.point.longitude, entry.point.latitude])
    return {
      x: anchor.x + (entry.visualDelta?.x || 0),
      y: anchor.y + (entry.visualDelta?.y || 0),
    }
  })

  routePaths.value = visualPoints.slice(0, -1).map((start, index) => ({
    key: `${orderedEntries[index].point.id}-${orderedEntries[index + 1].point.id}`,
    order: index + 1,
    d: routePath(start, visualPoints[index + 1], index),
  })).filter((segment) => segment.d)
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
        entry.marker.setOffset(new AMap.Pixel(-MARKER_SIZE / 2, -MARKER_SIZE / 2))
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
        visualDelta.x - MARKER_SIZE / 2,
        visualDelta.y - MARKER_SIZE / 2,
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
    element.classList.toggle('is-selected', selected)
    element.classList.toggle('is-day-muted', Boolean(props.activeDate && point.mapDate !== props.activeDate))
    marker.setzIndex?.(selected ? 120 : 80)
  })
}

function stagePadding() {
  const mobile = window.matchMedia('(max-width: 640px)').matches
  if (mobile) return [42, 28, 132, 28]
  return props.selectedId == null ? [48, 54, 112, 54] : [48, 54, 112, 342]
}

function fitEntries(entries, maxZoom = 13) {
  if (!map || entries.length === 0) return
  try {
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    map.setFitView(entries.map((entry) => entry.marker), reduceMotion, stagePadding(), maxZoom)
  } catch (error) {
    console.error('Failed to fit map markers.', error)
  }
}

function focusSelected() {
  const selected = markers.get(idKey(props.selectedId))
  if (!map || !selected) return
  map.setCenter([selected.point.longitude, selected.point.latitude])
  scheduleRouteOverlayUpdate()
}

function focusPoints(memoryIds = []) {
  const keys = new Set(memoryIds.map(idKey))
  const entries = [...markers.entries()]
    .filter(([key]) => keys.has(key))
    .map(([, entry]) => entry)
  if (!map || entries.length === 0) return
  const center = entries.reduce((result, entry) => ({
    longitude: result.longitude + entry.point.longitude / entries.length,
    latitude: result.latitude + entry.point.latitude / entries.length,
  }), { longitude: 0, latitude: 0 })
  map.setCenter([center.longitude, center.latitude])
  scheduleRouteOverlayUpdate()
}

function fitAll() {
  fitEntries([...markers.values()], 12)
}

function renderMarkers(shouldFit = false) {
  if (!map || !AMap) return
  clearMarkers()
  displayPoints.value.forEach((point) => {
    try {
      const selected = idKey(point.id) === idKey(props.selectedId)
      const element = markerElement(point, selected)
      const marker = new AMap.Marker({
        position: [point.longitude, point.latitude],
        content: element,
        offset: new AMap.Pixel(-MARKER_SIZE / 2, -MARKER_SIZE / 2),
        anchor: 'center',
        zIndex: selected ? 120 : 80,
      })
      marker.setMap(map)
      markers.set(idKey(point.id), { marker, point, element, visualDelta: { x: 0, y: 0 } })
    } catch (error) {
      console.error('Failed to create an AMap marker.', error)
    }
  })
  spreadOverlappingMarkers()
  if (shouldFit) fitAll()
}

function addMapControls() {
  if (!map || !AMap) return
  try {
    map.addControl(new AMap.ToolBar({ position: 'RB', offset: [18, 126] }))
    map.addControl(new AMap.Geolocation({
      position: 'RB',
      offset: [18, 184],
      enableHighAccuracy: true,
      timeout: 8000,
      showMarker: true,
      showCircle: false,
      panToLocation: false,
      zoomToAccuracy: false,
    }))
  } catch (error) {
    console.warn('AMap controls could not be initialized.', error)
  }
}

function cleanUpMap() {
  if (routeAnimationFrame != null) window.cancelAnimationFrame(routeAnimationFrame)
  routeAnimationFrame = null
  resizeObserver?.disconnect()
  resizeObserver = null
  clearRouteLines()
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
  if (!mapElement.value || displayPoints.value.length === 0) {
    state.value = 'idle'
    return
  }

  state.value = 'loading'
  try {
    const loadedAMap = await AMapLoader.load({
      key,
      version: '2.0',
      plugins: ['AMap.ToolBar', 'AMap.Geolocation'],
    })
    if (destroyed || !mapElement.value) return
    AMap = loadedAMap
    map = new AMap.Map(mapElement.value, {
      viewMode: '2D',
      zoom: 5,
      zoomEnable: true,
      dragEnable: true,
    })
    resizeObserver = new ResizeObserver(() => {
      map?.resize()
      updateRouteOverlay()
    })
    resizeObserver.observe(mapElement.value)
    map.on('zoomend', spreadOverlappingMarkers)
    map.on('moveend', spreadOverlappingMarkers)
    map.on('mapmove', scheduleRouteOverlayUpdate)
    map.on('zoomchange', scheduleRouteOverlayUpdate)
    renderMarkers(!props.focusSelectedOnReady)
    if (props.focusSelectedOnReady) focusSelected()
    addMapControls()
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

watch(pointSignature, (next, previous) => {
  if (state.value === 'ready' && next !== previous) renderMarkers(true)
})
watch(() => props.selectedId, () => {
  updateMarkerSelection()
  if (props.selectedId != null) focusSelected()
})
watch(() => props.activeDate, updateMarkerSelection)
onMounted(initializeMap)
onBeforeUnmount(() => {
  destroyed = true
  cleanUpMap()
})

defineExpose({ focusSelected, focusPoints, fitAll })
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
        class="memory-map-route-segment"
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
