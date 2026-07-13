<script setup>
import AMapLoader from '@amap/amap-jsapi-loader'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { isValidWgs84Coordinate, wgs84ToGcj02 } from '../utils/coordinates'

const props = defineProps({
  latitude: { type: [Number, String], default: null },
  longitude: { type: [Number, String], default: null },
})
const emit = defineEmits(['pick'])

const mapElement = ref(null)
const state = ref('loading')
const errorMessage = ref('')
const coordinate = computed(() => (
  isValidWgs84Coordinate(props.latitude, props.longitude)
    ? wgs84ToGcj02(props.latitude, props.longitude)
    : null
))
const coordinateSignature = computed(() => coordinate.value
  ? `${coordinate.value.latitude}:${coordinate.value.longitude}`
  : '')

let map = null
let AMap = null
let marker = null
let resizeObserver = null
let destroyed = false

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

function updateMarker({ pan = false } = {}) {
  if (!map || !AMap || !coordinate.value) return
  const position = [coordinate.value.longitude, coordinate.value.latitude]
  if (!marker) {
    marker = new AMap.Marker({ position, anchor: 'bottom-center' })
    marker.setMap(map)
  } else {
    marker.setPosition(position)
  }
  if (pan) map.setZoomAndCenter(15, position)
}

function pickFromMap(event) {
  const longitude = event?.lnglat?.getLng?.()
  const latitude = event?.lnglat?.getLat?.()
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return
  emit('pick', { latitude, longitude, coordinateSystem: 'GCJ02' })
}

function cleanup() {
  resizeObserver?.disconnect()
  resizeObserver = null
  marker?.setMap(null)
  marker = null
  map?.destroy()
  map = null
  AMap = null
}

async function initializeMap() {
  cleanup()
  errorMessage.value = ''
  const key = import.meta.env.VITE_AMAP_JS_API_KEY?.trim()
  if (!key) {
    state.value = 'missing-key'
    return
  }
  if (!configureSecurity()) return

  state.value = 'loading'
  try {
    AMap = await AMapLoader.load({ key, version: '2.0' })
    if (destroyed || !mapElement.value) return
    map = new AMap.Map(mapElement.value, {
      viewMode: '2D',
      zoom: coordinate.value ? 15 : 5,
      center: coordinate.value ? [coordinate.value.longitude, coordinate.value.latitude] : undefined,
    })
    map.on('click', pickFromMap)
    resizeObserver = new ResizeObserver(() => map?.resize())
    resizeObserver.observe(mapElement.value)
    updateMarker()
    state.value = 'ready'
  } catch (error) {
    cleanup()
    console.error('Failed to load AMap for the location picker.', error)
    errorMessage.value = '地图暂时没有加载成功，仍可以手动填写地点名称。'
    state.value = 'error'
  }
}

watch(coordinateSignature, () => updateMarker({ pan: true }))
onMounted(initializeMap)
onBeforeUnmount(() => {
  destroyed = true
  cleanup()
})
</script>

<template>
  <div class="location-picker-map">
    <div ref="mapElement" class="location-picker-map-canvas"></div>
    <div v-if="state !== 'ready'" class="location-picker-map-state" role="status">
      <p v-if="state === 'loading'">正在加载地图...</p>
      <template v-else-if="state === 'missing-key'">
        <strong>地图尚未配置</strong>
        <p>仍可以手动填写地点名称和坐标。</p>
      </template>
      <template v-else-if="state === 'missing-security'">
        <strong>地图安全配置缺失</strong>
        <p>仍可以手动填写地点名称和坐标。</p>
      </template>
      <template v-else>
        <strong>地图暂时不可用</strong>
        <p>{{ errorMessage }}</p>
        <button type="button" @click="initializeMap">重新加载</button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.location-picker-map { position: relative; min-height: 360px; overflow: hidden; border-radius: 8px; background: #f3eee6; }
.location-picker-map-canvas { position: absolute; inset: 0; }
.location-picker-map-state { position: absolute; inset: 0; display: grid; place-content: center; gap: 8px; padding: 24px; text-align: center; color: #75695f; background: #f8f4ed; }
.location-picker-map-state p { margin: 0; font-size: 14px; }
.location-picker-map-state strong { color: #3c332e; }
.location-picker-map-state button { justify-self: center; padding: 7px 12px; border: 1px solid #dccfc2; border-radius: 6px; background: #fffdfa; color: #74513f; }
@media (max-width: 700px) { .location-picker-map { min-height: min(48vh, 430px); } }
</style>
