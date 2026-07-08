<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getTrip } from '../api/trip'
import { getTimeline } from '../api/memory'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const trip = ref(null)
const memories = ref([])
const loading = ref(false)
const error = ref('')
const selectedMemoryId = ref(null)

const points = computed(() => memories.value.filter((item) => hasValidCoordinates(item)))
const memoriesWithoutLocation = computed(() => memories.value.length - points.value.length)
const selectedMemory = computed(() => {
  if (points.value.length === 0) {
    return null
  }
  return points.value.find((item) => item.id === selectedMemoryId.value) || points.value[0]
})

const bounds = computed(() => {
  if (points.value.length === 0) {
    return null
  }
  const lats = points.value.map((item) => Number(item.latitude))
  const lngs = points.value.map((item) => Number(item.longitude))
  return {
    minLat: Math.min(...lats),
    maxLat: Math.max(...lats),
    minLng: Math.min(...lngs),
    maxLng: Math.max(...lngs),
  }
})

function hasValidCoordinates(memory) {
  const lat = Number(memory.latitude)
  const lng = Number(memory.longitude)
  return Number.isFinite(lat) && Number.isFinite(lng)
}

function pointStyle(memory) {
  const box = bounds.value
  if (!box) {
    return {}
  }
  const lat = Number(memory.latitude)
  const lng = Number(memory.longitude)
  const latRange = box.maxLat - box.minLat || 1
  const lngRange = box.maxLng - box.minLng || 1
  const left = 8 + ((lng - box.minLng) / lngRange) * 84
  const top = 92 - ((lat - box.minLat) / latRange) * 84
  return {
    left: `${left}%`,
    top: `${top}%`,
  }
}

function selectMemory(memory) {
  selectedMemoryId.value = memory.id
}

function photoSrc(url) {
  return url || ''
}

function formatDateTime(value) {
  if (!value) {
    return '未知时间'
  }
  const rawValue = String(value)
  if (rawValue.length >= 16) {
    return `${rawValue.slice(0, 10)} ${rawValue.slice(11, 16)}`
  }
  return rawValue
}

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    const [tripData, memoryData] = await Promise.all([
      getTrip(props.id),
      getTimeline(props.id),
    ])
    trip.value = tripData
    memories.value = memoryData
    selectedMemoryId.value = memoryData.find((item) => hasValidCoordinates(item))?.id || null
  } catch (err) {
    error.value = err.message || '地图暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

onMounted(loadPage)
</script>

<template>
  <section class="trip-map-page">
    <header class="trip-map-head">
      <div>
        <p class="trip-list-kicker">Map</p>
        <h1>记忆地图</h1>
        <p>看看这次旅行中，那些瞬间发生在哪里。</p>
        <div class="trip-map-trip">
          <strong>{{ trip?.title || '这次旅行' }}</strong>
          <span v-if="trip?.destination">{{ trip.destination }}</span>
        </div>
      </div>

      <div class="trip-map-actions">
        <RouterLink :to="`/trips/${id}`">返回 Timeline</RouterLink>
        <RouterLink :to="`/trips/${id}/journey`">进入 Journey</RouterLink>
      </div>
    </header>

    <p v-if="loading" class="trip-map-status">正在整理这些记忆的位置...</p>
    <p v-if="error" class="error trip-map-status">{{ error }}</p>

    <div v-if="!loading && points.length === 0" class="trip-map-empty">
      <h2>还没有可以放在地图上的记忆。</h2>
      <p>上传带定位的照片，或为记忆补充位置后，它们会出现在这里。</p>
      <div class="trip-map-empty-actions">
        <RouterLink :to="`/trips/${id}/memories/new`">
          <button>去添加记忆</button>
        </RouterLink>
        <RouterLink :to="`/trips/${id}`">
          <button class="ghost">返回 Timeline</button>
        </RouterLink>
      </div>
    </div>

    <template v-else-if="!loading">
      <div class="trip-map-summary">
        <span>{{ points.length }} 段记忆显示在地图上</span>
        <span v-if="memoriesWithoutLocation > 0">
          有些记忆还没有位置信息，暂时不会显示在地图上。
        </span>
      </div>

      <div class="map-stage">
        <button
          v-for="memory in points"
          :key="memory.id"
          type="button"
          :class="memory.id === selectedMemory?.id ? 'map-point active' : 'map-point'"
          :style="pointStyle(memory)"
          :aria-label="memory.locationName || '未填写地点'"
          @click="selectMemory(memory)"
        ></button>
      </div>

      <article v-if="selectedMemory" class="map-memory-card">
        <img
          v-if="selectedMemory.photoUrl"
          class="map-memory-photo"
          :src="photoSrc(selectedMemory.photoUrl)"
          alt="地图记忆照片"
        />
        <div v-else class="map-memory-photo empty">没有照片</div>

        <div class="map-memory-body">
          <p class="map-memory-quote">“{{ selectedMemory.content || '没有文字记录' }}”</p>
          <div class="map-memory-meta">
            <span>{{ selectedMemory.locationName || '未填写地点' }}</span>
            <span>{{ formatDateTime(selectedMemory.recordTime) }}</span>
          </div>
        </div>
      </article>
    </template>
  </section>
</template>
