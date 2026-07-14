<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import MemoryMap from '../components/MemoryMap.vue'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'
import { getTrip } from '../api/trip'
import { getTimeline } from '../api/memory'
import { isValidWgs84Coordinate } from '../utils/coordinates'
import TripViewNav from '../components/TripViewNav.vue'

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
const memoryMap = ref(null)

const points = computed(() => memories.value.filter((item) => hasValidCoordinates(item)))
const memoriesWithoutLocation = computed(() => memories.value.length - points.value.length)
const selectedMemory = computed(() => {
  if (points.value.length === 0) {
    return null
  }
  const selectedKey = memoryIdKey(selectedMemoryId.value)
  return points.value.find((item) => memoryIdKey(item.id) === selectedKey) || points.value[0]
})

function memoryIdKey(value) {
  return value == null ? '' : String(value)
}

function selectMemory(memoryId) {
  const selected = points.value.find((item) => memoryIdKey(item.id) === memoryIdKey(memoryId))
  selectedMemoryId.value = selected?.id ?? null
}

function hasValidCoordinates(memory) {
  return isValidWgs84Coordinate(memory.latitude, memory.longitude)
}

function focusSelectedMemory() {
  memoryMap.value?.focusSelected()
}

function photoSrc(url) {
  return url || ''
}

function hasMemoryPhoto(memory) {
  return Boolean(memory?.photoUrl || memory?.photos?.some(photo => photo?.photoUrl))
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
    memories.value = Array.isArray(memoryData) ? memoryData : []
    selectedMemoryId.value = memories.value.find((item) => hasValidCoordinates(item))?.id || null
  } catch (err) {
    error.value = err.message || '地图暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

watch(() => props.id, loadPage, { immediate: true })
</script>

<template>
  <section class="trip-map-page">
    <header v-if="!loading && trip" class="trip-map-head">
      <div>
        <p class="trip-list-kicker">地图</p>
        <h1>记忆地图</h1>
        <p>看看这次旅行中，那些瞬间发生在哪里。</p>
        <div class="trip-map-trip">
          <strong>{{ trip?.title || '这次旅行' }}</strong>
          <span v-if="trip?.destination">{{ trip.destination }}</span>
        </div>
      </div>

    </header>

    <TripViewNav v-if="!loading && trip" :trip-id="id" active="map" />

    <p v-if="loading" class="trip-map-status">正在整理这些记忆的位置...</p>
    <p v-if="error" class="error trip-map-status">{{ error }}</p>

    <div v-if="!loading && !error && trip && points.length === 0" class="trip-map-empty">
      <h2>还没有可以放在地图上的记忆。</h2>
      <p>上传带定位的照片，或为记忆补充位置后，它们会出现在这里。</p>
      <div class="trip-map-empty-actions">
        <RouterLink :to="`/trips/${id}/memories/new`">
          <button>去添加记忆</button>
        </RouterLink>
      </div>
    </div>

    <template v-else-if="!loading && !error && trip">
      <div class="trip-map-summary">
        <span>{{ points.length }} 段记忆显示在地图上</span>
        <span v-if="memoriesWithoutLocation > 0">
          有些记忆还没有位置信息，暂时不会显示在地图上。
        </span>
      </div>

      <div class="trip-map-workspace">
        <MemoryMap
          ref="memoryMap"
          :points="points"
          :selected-id="selectedMemory?.id"
          @select="selectMemory"
        />

        <article v-if="selectedMemory" class="map-memory-card">
        <MemoryPhotoGallery
          v-if="hasMemoryPhoto(selectedMemory)"
          class="map-memory-photo"
          :photos="selectedMemory.photos"
          :fallback-url="photoSrc(selectedMemory.photoUrl)"
          layout="favorite"
          alt="地图记忆照片"
        />
        <div v-else class="map-memory-photo empty">没有照片</div>

        <div class="map-memory-body">
          <p class="map-memory-quote">“{{ selectedMemory.content || '没有文字记录' }}”</p>
          <div class="map-memory-meta">
            <span>{{ selectedMemory.locationName || '未填写地点' }}</span>
            <span>{{ formatDateTime(selectedMemory.recordTime) }}</span>
          </div>
          <p v-if="selectedMemory.companions?.length" class="map-memory-companions">
            和 {{ selectedMemory.companions.map(item => item.name).join('、') }} 一起
          </p>
          <button type="button" class="map-memory-focus" @click="focusSelectedMemory">
            在地图上定位
          </button>
        </div>
        </article>
      </div>
    </template>
  </section>
</template>
