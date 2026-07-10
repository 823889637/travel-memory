<script setup>
import AMapLoader from '@amap/amap-jsapi-loader'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { isValidWgs84Coordinate, wgs84ToGcj02 } from '../utils/coordinates'

const props = defineProps({
  points: { type: Array, default: () => [] },
  selectedId: { type: [String, Number], default: null },
})
const emit = defineEmits(['select'])

const mapElement = ref(null)
const state = ref('idle')
const errorMessage = ref('')
const displayPoints = computed(() => props.points.map((point) => {
  if (!isValidWgs84Coordinate(point.latitude, point.longitude)) return null
  const coordinate = wgs84ToGcj02(point.latitude, point.longitude)
  return coordinate ? { ...point, ...coordinate } : null
}).filter(Boolean))
const pointSignature = computed(() => displayPoints.value
  .map((point) => `${point.id}:${point.latitude}:${point.longitude}`).join('|'))

let map = null
let AMap = null
let resizeObserver = null
let destroyed = false
const markers = new Map()

function markerLabel(point) {
  const excerpt = String(point.content || '').trim().replace(/\s+/g, ' ').slice(0, 24)
  return point.locationName || excerpt || '旅行记忆'
}

function markerElement(point, selected) {
  const element = document.createElement('button')
  element.type = 'button'
  element.className = selected ? 'memory-map-marker is-selected' : 'memory-map-marker'
  element.setAttribute('aria-label', markerLabel(point))
  element.title = markerLabel(point)
  element.addEventListener('click', () => emit('select', point.id))
  return element
}

function clearMarkers() {
  markers.forEach(({ marker }) => marker.setMap(null))
  markers.clear()
}

function updateMarkerSelection() {
  markers.forEach(({ element, point }) => element.classList.toggle('is-selected', point.id === props.selectedId))
}

function focusSelected() {
  const selected = markers.get(props.selectedId)
  if (map && selected) map.panTo([selected.point.longitude, selected.point.latitude])
}

function fitMapToMarkers() {
  const entries = [...markers.values()]
  if (!map || entries.length === 0) return
  if (entries.length === 1) {
    map.setZoomAndCenter(13, [entries[0].point.longitude, entries[0].point.latitude])
    return
  }
  map.setFitView(entries.map((entry) => entry.marker), false, [56, 48, 56, 48])
}

function renderMarkers(shouldFit = false) {
  if (!map || !AMap) return
  clearMarkers()
  displayPoints.value.forEach((point) => {
    try {
      const element = markerElement(point, point.id === props.selectedId)
      const marker = new AMap.Marker({
        position: [point.longitude, point.latitude],
        content: element,
        offset: new AMap.Pixel(-9, -9),
        anchor: 'center',
      })
      marker.on('click', () => emit('select', point.id))
      marker.setMap(map)
      markers.set(point.id, { marker, point, element })
    } catch (error) {
      console.error('Failed to create an AMap marker.', error)
    }
  })
  if (shouldFit) fitMapToMarkers()
}

function cleanUpMap() {
  resizeObserver?.disconnect()
  resizeObserver = null
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
    const loadedAMap = await AMapLoader.load({ key, version: '2.0' })
    if (destroyed || !mapElement.value) return
    AMap = loadedAMap
    map = new AMap.Map(mapElement.value, { viewMode: '2D', zoom: 5, zoomEnable: true, dragEnable: true })
    resizeObserver = new ResizeObserver(() => map?.resize())
    resizeObserver.observe(mapElement.value)
    renderMarkers(true)
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
  focusSelected()
})
onMounted(initializeMap)
onBeforeUnmount(() => {
  destroyed = true
  cleanUpMap()
})

defineExpose({ focusSelected })
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
