<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { CalendarDays, Maximize2, X } from '@lucide/vue'
import MemoryMap from '../components/MemoryMap.vue'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'
import { getTrip } from '../api/trip'
import { getTimeline } from '../api/memory'
import { isValidWgs84Coordinate } from '../utils/coordinates'
import { getTripDayNumber } from '../utils/tripDay'
import TripViewNav from '../components/TripViewNav.vue'
import MobilePageHeader from '../components/MobilePageHeader.vue'
import TripContextCard from '../components/TripContextCard.vue'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})
const route = useRoute()
const router = useRouter()

const trip = ref(null)
const memories = ref([])
const loading = ref(false)
const error = ref('')
const selectedMemoryId = ref(null)
const activeDate = ref('')
const memoryMap = ref(null)

const points = computed(() => {
  const fallbackDays = new Map()
  const stopsByDate = new Map()

  return memories.value
    .filter(hasValidCoordinates)
    .slice()
    .sort(compareMemories)
    .map((memory, sequenceIndex) => {
      const mapDate = recordDate(memory.recordTime) || `unknown-${memoryIdKey(memory.id)}`
      if (!fallbackDays.has(mapDate)) fallbackDays.set(mapDate, fallbackDays.size + 1)
      const stopNumber = (stopsByDate.get(mapDate) || 0) + 1
      stopsByDate.set(mapDate, stopNumber)

      return {
        ...memory,
        mapDate,
        dayNumber: getTripDayNumber(trip.value?.startDate, mapDate, fallbackDays.get(mapDate)),
        stopNumber,
        sequenceNumber: sequenceIndex + 1,
      }
    })
})
const memoriesWithoutLocation = computed(() => Math.max(0, memories.value.length - points.value.length))
const dayOptions = computed(() => {
  const days = new Map()
  points.value.forEach((memory) => {
    if (!days.has(memory.mapDate)) {
      days.set(memory.mapDate, {
        date: memory.mapDate,
        dayNumber: memory.dayNumber,
        memories: [],
      })
    }
    days.get(memory.mapDate).memories.push(memory)
  })
  return [...days.values()]
})
const selectedMemory = computed(() => {
  const selectedKey = memoryIdKey(selectedMemoryId.value)
  if (!selectedKey) return null
  return points.value.find((item) => memoryIdKey(item.id) === selectedKey) || null
})
const selectedPhotoCount = computed(() => photoCount(selectedMemory.value))
const hasDestinationCoordinates = computed(() => isValidWgs84Coordinate(
  trip.value?.destinationLatitude,
  trip.value?.destinationLongitude,
))

function memoryIdKey(value) {
  return value == null ? '' : String(value)
}

function recordDate(value) {
  const match = String(value || '').match(/^(\d{4}-\d{2}-\d{2})/)
  return match?.[1] || ''
}

function sortableTime(value) {
  const normalized = String(value || '').replace(' ', 'T')
  const timestamp = Date.parse(normalized)
  return Number.isFinite(timestamp) ? timestamp : Number.MAX_SAFE_INTEGER
}

function compareMemories(left, right) {
  return sortableTime(left.recordTime) - sortableTime(right.recordTime)
    || sortableTime(left.createTime) - sortableTime(right.createTime)
    || Number(left.id || 0) - Number(right.id || 0)
}

function hasValidCoordinates(memory) {
  return isValidWgs84Coordinate(memory.latitude, memory.longitude)
}

function replaceMemoryQuery(memoryId) {
  const nextQuery = { ...route.query }
  if (memoryId == null) delete nextQuery.memoryId
  else nextQuery.memoryId = memoryIdKey(memoryId)
  router.replace({ query: nextQuery }).catch(() => {})
}

function selectMemory(memoryId, { updateRoute = true } = {}) {
  const selected = points.value.find((item) => memoryIdKey(item.id) === memoryIdKey(memoryId))
  if (!selected) return
  selectedMemoryId.value = selected.id
  activeDate.value = selected.mapDate
  if (updateRoute) replaceMemoryQuery(selected.id)
}

function closeMemoryCard() {
  selectedMemoryId.value = null
  replaceMemoryQuery(null)
}

function selectDay(day) {
  activeDate.value = day.date
  const firstMemory = day.memories[0]
  if (firstMemory) {
    selectedMemoryId.value = firstMemory.id
    replaceMemoryQuery(firstMemory.id)
  }
  nextTick(() => memoryMap.value?.focusPoints(day.memories.map((memory) => memory.id)))
}

function selectDateValue(event) {
  const day = dayOptions.value.find(item => item.date === event.target.value)
  if (day) selectDay(day)
}

function fitAllMemories() {
  memoryMap.value?.fitAll()
}

function photoSrc(url) {
  return url || ''
}

function normalizedPhotoUrls(memory) {
  const urls = []
  const seen = new Set()
  const add = (value) => {
    const url = String(value || '').trim()
    if (url && !seen.has(url)) {
      seen.add(url)
      urls.push(url)
    }
  }
  ;(memory?.photos || [])
    .slice()
    .sort((left, right) => Number(left.sortOrder ?? 0) - Number(right.sortOrder ?? 0))
    .forEach((photo) => add(photo?.photoUrl))
  add(memory?.photoUrl)
  return urls
}

function photoCount(memory) {
  return normalizedPhotoUrls(memory).length
}

function hasMemoryPhoto(memory) {
  return photoCount(memory) > 0
}

function formatDate(value) {
  const date = recordDate(value)
  return date ? date.replaceAll('-', '.') : '日期未记录'
}

function formatTime(value) {
  const rawValue = String(value || '')
  return rawValue.length >= 16 ? rawValue.slice(11, 16) : '时间未记录'
}

function formatDateTime(value) {
  const rawValue = String(value || '')
  if (rawValue.length >= 16) return `${rawValue.slice(0, 10).replaceAll('-', '.')} ${rawValue.slice(11, 16)}`
  return rawValue || '时间未记录'
}

function coordinateLabel(memory) {
  if (!hasValidCoordinates(memory)) return ''
  const latitude = Number(memory.latitude)
  const longitude = Number(memory.longitude)
  return `${Math.abs(latitude).toFixed(4)}° ${latitude >= 0 ? 'N' : 'S'} · ${Math.abs(longitude).toFixed(4)}° ${longitude >= 0 ? 'E' : 'W'}`
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

    await nextTick()
    const requestedMemory = points.value.find((item) => memoryIdKey(item.id) === memoryIdKey(route.query.memoryId))
    const initialMemory = requestedMemory || points.value[0] || null
    selectedMemoryId.value = initialMemory?.id ?? null
    activeDate.value = initialMemory?.mapDate || ''
  } catch (err) {
    error.value = err.message || '地图暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

watch(() => props.id, loadPage, { immediate: true })
watch(() => route.query.memoryId, (memoryId) => {
  if (loading.value) return
  if (memoryId == null) {
    selectedMemoryId.value = null
    return
  }
  selectMemory(memoryId, { updateRoute: false })
})
</script>

<template>
  <section class="trip-map-page">
    <MobilePageHeader title="地图" back-to="/trips" />
    <TripContextCard v-if="!loading && trip" :trip="trip" variant="compact" />

    <TripViewNav v-if="!loading && trip" :trip-id="id" active="map" />

    <p v-if="loading" class="trip-map-status">正在整理这些记忆的位置...</p>
    <p v-if="error" class="error trip-map-status">{{ error }}</p>

    <div v-if="!loading && !error && trip && points.length === 0 && !hasDestinationCoordinates" class="trip-map-empty">
      <h2>这趟旅行还没有可以显示在地图上的记忆</h2>
      <p>为 Memory 添加地点或在地图中选点后，它们就会出现在这里。</p>
      <div class="trip-map-empty-actions">
        <RouterLink :to="`/trips/${id}`" class="button-link">去时间线看看</RouterLink>
      </div>
    </div>

    <template v-else-if="!loading && !error && trip">
      <div class="trip-map-summary">
        <span v-if="points.length">{{ points.length }} 段记忆显示在地图上</span>
        <span v-else>已定位到 {{ trip.destination || '目的城市' }}，新增带位置的 Memory 后会显示旅行路线。</span>
        <span v-if="memoriesWithoutLocation > 0">
          另有 {{ memoriesWithoutLocation }} 段记忆暂未记录位置。
        </span>
      </div>

      <div :class="['trip-map-stage', { 'has-selection': selectedMemory }]">
        <MemoryMap
          ref="memoryMap"
          :points="points"
          :selected-id="selectedMemoryId"
          :active-date="activeDate"
          :focus-selected-on-ready="Boolean(route.query.memoryId)"
          :fallback-latitude="trip.destinationLatitude"
          :fallback-longitude="trip.destinationLongitude"
          :fallback-label="trip.destination || '目的城市'"
          @select="selectMemory"
        />

        <button type="button" class="trip-map-fit-all" aria-label="显示全部地图记忆" title="显示全部记忆" @click="fitAllMemories">
          <Maximize2 :size="18" :stroke-width="1.8" aria-hidden="true" />
        </button>

        <article v-if="selectedMemory" class="map-memory-card">
          <header class="map-memory-card-head">
            <p><span aria-hidden="true"></span>第 {{ selectedMemory.dayNumber }} 天 · 第 {{ selectedMemory.stopNumber }} 站</p>
            <button type="button" class="map-memory-close" aria-label="关闭记忆详情" @click="closeMemoryCard">
              <X :size="18" :stroke-width="1.8" aria-hidden="true" />
            </button>
          </header>

          <div class="map-memory-heading">
            <h2>{{ selectedMemory.locationName || '已记录位置' }}</h2>
            <p>{{ coordinateLabel(selectedMemory) }}</p>
          </div>

          <div class="map-memory-facts">
            <span>{{ formatTime(selectedMemory.recordTime) }}</span>
            <span v-if="selectedPhotoCount">共 {{ selectedPhotoCount }} 张照片</span>
          </div>

          <MemoryPhotoGallery
            v-if="hasMemoryPhoto(selectedMemory)"
            class="map-memory-photo"
            :photos="selectedMemory.photos"
            :fallback-url="photoSrc(selectedMemory.photoUrl)"
            layout="favorite"
            alt="地图记忆照片"
          />

          <div class="map-memory-body">
            <p :class="['map-memory-quote', { muted: !selectedMemory.content }]">
              “{{ selectedMemory.content || '这一刻没有留下文字。' }}”
            </p>
            <p v-if="selectedMemory.companions?.length" class="map-memory-companions">
              和 {{ selectedMemory.companions.map(item => item.name).join('、') }} 一起
            </p>
            <p class="map-memory-recorded">记录于 {{ formatDateTime(selectedMemory.recordTime) }}</p>
            <RouterLink class="map-memory-detail-link" :to="`/trips/${id}/memories/${selectedMemory.id}`">
              查看记忆详情 <span aria-hidden="true">→</span>
            </RouterLink>
          </div>
        </article>

        <nav v-if="dayOptions.length" class="map-day-navigation" aria-label="地图自然日导航">
          <button
            v-for="day in dayOptions"
            :key="day.date"
            type="button"
            :class="{ active: activeDate === day.date }"
            :aria-current="activeDate === day.date ? 'date' : undefined"
            @click="selectDay(day)"
          >
            <strong>第 {{ day.dayNumber }} 天</strong>
            <span>{{ formatDate(day.date) }}</span>
          </button>
          <label class="map-day-calendar" aria-label="按日期选择地图记忆">
            <CalendarDays :size="19" :stroke-width="1.7" aria-hidden="true" />
            <input
              type="date"
              :value="activeDate"
              :min="dayOptions[0]?.date"
              :max="dayOptions[dayOptions.length - 1]?.date"
              @change="selectDateValue"
            />
          </label>
        </nav>
      </div>
    </template>
  </section>
</template>
