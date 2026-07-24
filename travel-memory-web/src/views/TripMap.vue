<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  Clock3,
  LocateFixed,
  MapPin,
  Pause,
  Play,
  RotateCcw,
  X,
} from '@lucide/vue'
import MemoryMap from '../components/MemoryMap.vue'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'
import { getTrip } from '../api/trip'
import { getTimeline } from '../api/memory'
import { isCoordinateInChina, isValidWgs84Coordinate, wgs84DistanceMeters } from '../utils/coordinates'
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
const mapCardReady = ref(false)
const mapStageElement = ref(null)
const overlayDockElement = ref(null)
const mapBottomInset = ref(132)
const cardExpanded = ref(false)
const dayButtonElements = new Map()
const playbackIndex = ref(-1)
const replayState = ref('idle')
let playbackTimer = null
let overlayResizeObserver = null
let overlayMeasureFrame = null

const SAME_LOCATION_DISTANCE_METERS = 5
const LONG_ROUTE_BREAK_DISTANCE_METERS = 80_000

const timedMemories = computed(() => memories.value
  .filter(hasValidRecordTime)
  .slice()
  .sort(compareMemories))
const replayPoints = computed(() => {
  const fallbackDays = new Map()
  const stopsByDate = new Map()

  return timedMemories.value
    .filter(hasValidCoordinates)
    .slice()
    .sort(compareMemories)
    .map((memory, sequenceIndex) => {
      const mapDate = recordDate(memory.recordTime)
      if (!fallbackDays.has(mapDate)) fallbackDays.set(mapDate, fallbackDays.size + 1)
      const stopNumber = (stopsByDate.get(mapDate) || 0) + 1
      stopsByDate.set(mapDate, stopNumber)

      return {
        ...memory,
        mapDate,
        dayNumber: getTripDayNumber(trip.value?.startDate, mapDate, fallbackDays.get(mapDate)),
        stopNumber,
        sequenceNumber: sequenceIndex + 1,
        replayIndex: sequenceIndex,
        isReplayPoint: true,
      }
    })
})
const browseOnlyPoints = computed(() => memories.value
  .filter(memory => hasValidCoordinates(memory) && !hasValidRecordTime(memory))
  .slice()
  .sort(compareMemories)
  .map(memory => ({
    ...memory,
    mapDate: '',
    dayNumber: null,
    stopNumber: null,
    sequenceNumber: null,
    replayIndex: null,
    isReplayPoint: false,
  })))
const points = computed(() => [...replayPoints.value, ...browseOnlyPoints.value])
const memoriesWithoutLocation = computed(() => Math.max(0, memories.value.length - points.value.length))
const routeSegments = computed(() => {
  const segments = []
  const replayPointById = new Map(replayPoints.value.map(point => [memoryIdKey(point.id), point]))
  const timeline = timedMemories.value

  for (let index = 0; index < timeline.length - 1; index += 1) {
    const fromMemory = timeline[index]
    const toMemory = timeline[index + 1]
    // A timed Memory without coordinates is a real gap in the route, not a
    // reason to draw a direct link between the stations around it.
    if (!hasValidCoordinates(fromMemory) || !hasValidCoordinates(toMemory)) continue
    const from = replayPointById.get(memoryIdKey(fromMemory.id))
    const to = replayPointById.get(memoryIdKey(toMemory.id))
    if (!from || !to) continue
    const distance = wgs84DistanceMeters(from.latitude, from.longitude, to.latitude, to.longitude)

    // Same-coordinate records share a station visually; long jumps remain an
    // intentional break rather than inventing a cross-country route line.
    if (distance == null || distance <= SAME_LOCATION_DISTANCE_METERS || distance > LONG_ROUTE_BREAK_DISTANCE_METERS) {
      continue
    }

    segments.push({
      fromId: from.id,
      toId: to.id,
      fromDate: from.mapDate,
      toDate: to.mapDate,
      fromReplayIndex: from.replayIndex,
      toReplayIndex: to.replayIndex,
    })
  }

  return segments
})
const longRouteBreakCount = computed(() => timedMemories.value.slice(0, -1).filter((memory, index) => {
  const nextMemory = timedMemories.value[index + 1]
  if (!hasValidCoordinates(memory) || !hasValidCoordinates(nextMemory)) return false
  const distance = wgs84DistanceMeters(memory.latitude, memory.longitude, nextMemory.latitude, nextMemory.longitude)
  return distance != null && distance > LONG_ROUTE_BREAK_DISTANCE_METERS
}).length)
const dayOptions = computed(() => {
  const days = new Map()
  replayPoints.value.forEach((memory) => {
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
const playbackStations = computed(() => activeDate.value
  ? replayPoints.value.filter(memory => memory.mapDate === activeDate.value)
  : replayPoints.value)
const currentPlaybackPoint = computed(() => playbackStations.value[playbackIndex.value] || null)
const playbackReplayIndex = computed(() => currentPlaybackPoint.value?.replayIndex ?? -1)
const isPlaying = computed(() => replayState.value === 'playing')
const replaySessionActive = computed(() => replayState.value !== 'idle')
const canReplayCurrentScope = computed(() => playbackStations.value.length >= 2)
const isPlaybackComplete = computed(() => playbackStations.value.length > 0
  && playbackIndex.value >= playbackStations.value.length - 1)
const playbackDateLabel = computed(() => {
  const point = currentPlaybackPoint.value
  if (!point) return ''
  return `第 ${point.dayNumber} 天 · ${formatDate(point.mapDate)}`
})
const selectedMemory = computed(() => {
  const selectedKey = memoryIdKey(selectedMemoryId.value)
  if (!selectedKey) return null
  return points.value.find((item) => memoryIdKey(item.id) === selectedKey) || null
})
const hasDestinationCoordinates = computed(() => isValidWgs84Coordinate(
  trip.value?.destinationLatitude,
  trip.value?.destinationLongitude,
))
const selectedLocationLabel = computed(() => {
  if (!selectedMemory.value) return ''
  const explicitCountry = String(trip.value?.destinationCountry || '').trim()
  const coordinateSuggestsChina = isCoordinateInChina(
    selectedMemory.value.latitude,
    selectedMemory.value.longitude,
  ) || isCoordinateInChina(
    trip.value?.destinationLatitude,
    trip.value?.destinationLongitude,
  )
  const country = explicitCountry || (coordinateSuggestsChina ? '中国' : '')
  const names = [
    country,
    trip.value?.destination,
    selectedMemory.value.locationName,
  ]
    .map(value => String(value || '').trim())
    .filter(Boolean)
  return [...new Set(names)].join(' · ') || '地点还没有补充'
})

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

function hasValidRecordTime(memory) {
  const date = recordDate(memory.recordTime)
  return Boolean(date) && Number.isFinite(sortableTime(memory.recordTime))
    && sortableTime(memory.recordTime) !== Number.MAX_SAFE_INTEGER
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
  const changed = memoryIdKey(selectedMemoryId.value) !== memoryIdKey(selected.id)
  if (changed) {
    mapCardReady.value = false
    cardExpanded.value = false
  }
  selectedMemoryId.value = selected.id
  if (updateRoute) replaceMemoryQuery(selected.id)
  scheduleOverlayMeasure()
}

function handleMapSelection(memoryId) {
  pausePlayback({ preserveSession: true })
  selectMemory(memoryId)
}

function handleMapInteraction() {
  if (isPlaying.value) pausePlayback({ preserveSession: true })
}

function closeMemoryCard() {
  mapCardReady.value = false
  cardExpanded.value = false
  selectedMemoryId.value = null
  replaceMemoryQuery(null)
  scheduleOverlayMeasure()
}

function revealMemoryCard(memoryId) {
  if (memoryIdKey(memoryId) === memoryIdKey(selectedMemoryId.value)) {
    mapCardReady.value = true
    scheduleOverlayMeasure()
  }
}

function toggleMemoryCard() {
  if (!selectedMemory.value) return
  if (!cardExpanded.value && isPlaying.value) pausePlayback({ preserveSession: true })
  cardExpanded.value = !cardExpanded.value
  scheduleOverlayMeasure()
}

function measureMapBottomInset() {
  overlayMeasureFrame = null
  const stage = mapStageElement.value
  const dock = overlayDockElement.value
  if (!stage || !dock) {
    mapBottomInset.value = 90
    return
  }

  const stageRect = stage.getBoundingClientRect()
  const dockRect = dock.getBoundingClientRect()
  let measuredInset = stageRect.bottom - dockRect.top + 12
  if (stageRect.width > 640) {
    const bottomControls = [...dock.querySelectorAll(
      '.map-replay-status, .map-replay-unavailable, .map-playback-controls, .map-playback-date, .map-day-navigation',
    )]
    const controlTop = bottomControls.reduce((top, element) => {
      const rect = element.getBoundingClientRect()
      return rect.height > 0 ? Math.min(top, rect.top) : top
    }, Number.POSITIVE_INFINITY)
    measuredInset = Number.isFinite(controlTop) ? stageRect.bottom - controlTop + 12 : 112
  }
  measuredInset = Math.max(90, measuredInset)
  if (Math.abs(measuredInset - mapBottomInset.value) > 2) {
    mapBottomInset.value = measuredInset
  }
}

function scheduleOverlayMeasure() {
  if (overlayMeasureFrame != null) window.cancelAnimationFrame(overlayMeasureFrame)
  overlayMeasureFrame = window.requestAnimationFrame(() => {
    nextTick(measureMapBottomInset)
  })
}

function setupOverlayMeasurement() {
  overlayResizeObserver?.disconnect()
  overlayResizeObserver = new ResizeObserver(scheduleOverlayMeasure)
  if (mapStageElement.value) overlayResizeObserver.observe(mapStageElement.value)
  if (overlayDockElement.value) overlayResizeObserver.observe(overlayDockElement.value)
  scheduleOverlayMeasure()
}

function setDayButtonRef(date, element) {
  if (element) dayButtonElements.set(date, element)
  else dayButtonElements.delete(date)
}

function syncActiveDayButton() {
  dayButtonElements.get(activeDate.value || 'all')?.scrollIntoView({
    behavior: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    block: 'nearest',
    inline: 'center',
  })
}

function selectDay(day) {
  endPlaybackSession()
  playbackIndex.value = -1
  activeDate.value = day.date
  cardExpanded.value = false
  const firstMemory = day.memories[0]
  if (firstMemory) {
    if (memoryIdKey(selectedMemoryId.value) !== memoryIdKey(firstMemory.id)) {
      mapCardReady.value = false
    }
    selectedMemoryId.value = firstMemory.id
    replaceMemoryQuery(firstMemory.id)
  }
  nextTick(syncActiveDayButton)
  scheduleOverlayMeasure()
}

function selectAllDays() {
  endPlaybackSession()
  playbackIndex.value = -1
  activeDate.value = ''
  cardExpanded.value = false
  const selected = selectedMemory.value || replayPoints.value[0] || browseOnlyPoints.value[0]
  if (selected) {
    mapCardReady.value = false
    selectedMemoryId.value = selected.id
    replaceMemoryQuery(selected.id)
  }
  nextTick(syncActiveDayButton)
  scheduleOverlayMeasure()
}

function fitAllMemories() {
  pausePlayback({ preserveSession: true })
  if (selectedMemoryId.value != null) mapCardReady.value = false
  memoryMap.value?.fitAll()
}

function clearPlaybackTimer() {
  if (playbackTimer != null) window.clearTimeout(playbackTimer)
  playbackTimer = null
}

function pausePlayback({ preserveSession = true } = {}) {
  clearPlaybackTimer()
  if (replayState.value === 'playing') {
    replayState.value = preserveSession ? 'paused' : 'idle'
  } else if (!preserveSession) {
    replayState.value = 'idle'
  }
  scheduleOverlayMeasure()
}

function endPlaybackSession() {
  clearPlaybackTimer()
  replayState.value = 'idle'
  scheduleOverlayMeasure()
}

function selectPlaybackStation(index) {
  const station = playbackStations.value[index]
  if (!station) return
  playbackIndex.value = index
  selectMemory(station.id)
}

function queuePlaybackStep() {
  clearPlaybackTimer()
  playbackTimer = window.setTimeout(() => {
    const nextIndex = playbackIndex.value + 1
    if (nextIndex >= playbackStations.value.length) {
      replayState.value = 'ended'
      playbackTimer = null
      scheduleOverlayMeasure()
      return
    }
    selectPlaybackStation(nextIndex)
    if (nextIndex >= playbackStations.value.length - 1) {
      replayState.value = 'ended'
      playbackTimer = null
      scheduleOverlayMeasure()
      return
    }
    queuePlaybackStep()
  }, 1800)
}

function startPlayback() {
  const stations = playbackStations.value
  if (stations.length < 2) return
  const startIndex = playbackIndex.value < 0 || isPlaybackComplete.value ? 0 : playbackIndex.value
  cardExpanded.value = false
  selectPlaybackStation(startIndex)
  replayState.value = 'playing'
  scheduleOverlayMeasure()
  queuePlaybackStep()
}

function togglePlayback() {
  if (isPlaying.value) pausePlayback()
  else startPlayback()
}

function previousStation() {
  pausePlayback({ preserveSession: true })
  if (!playbackStations.value.length) return
  selectPlaybackStation(Math.max(0, playbackIndex.value - 1))
}

function nextStation() {
  pausePlayback({ preserveSession: true })
  if (!playbackStations.value.length) return
  const nextIndex = playbackIndex.value < 0 ? 0 : Math.min(playbackStations.value.length - 1, playbackIndex.value + 1)
  selectPlaybackStation(nextIndex)
}

function exitPlaybackSession() {
  endPlaybackSession()
  nextTick(syncActiveDayButton)
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

async function loadPage() {
  endPlaybackSession()
  playbackIndex.value = -1
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
    const initialMemory = requestedMemory || replayPoints.value[0] || browseOnlyPoints.value[0] || null
    mapCardReady.value = false
    cardExpanded.value = false
    selectedMemoryId.value = initialMemory?.id ?? null
    activeDate.value = ''
    await nextTick()
    syncActiveDayButton()
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
    mapCardReady.value = false
    cardExpanded.value = false
    selectedMemoryId.value = null
    scheduleOverlayMeasure()
    return
  }
  selectMemory(memoryId, { updateRoute: false })
})

watch(
  [selectedMemory, cardExpanded, replayState, activeDate],
  scheduleOverlayMeasure,
  { flush: 'post' },
)
watch(
  [mapStageElement, overlayDockElement],
  () => nextTick(setupOverlayMeasurement),
  { flush: 'post' },
)

onMounted(() => {
  nextTick(setupOverlayMeasurement)
})

onBeforeUnmount(() => {
  clearPlaybackTimer()
  if (overlayMeasureFrame != null) window.cancelAnimationFrame(overlayMeasureFrame)
  overlayMeasureFrame = null
  overlayResizeObserver?.disconnect()
  overlayResizeObserver = null
})
</script>

<template>
  <section class="trip-map-page">
    <MobilePageHeader title="地图" back-to="/trips" />
    <TripContextCard v-if="!loading && trip" :trip="trip" :memories="memories" variant="compact" />

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
        <span v-if="replayPoints.length">{{ replayPoints.length }} 段记忆可按时间回放</span>
        <span v-else>已定位到 {{ trip.destination || '目的城市' }}，新增带位置的 Memory 后会显示旅行路线。</span>
        <span v-if="browseOnlyPoints.length">{{ browseOnlyPoints.length }} 个位置仅供浏览，不参与回放。</span>
        <span v-if="memoriesWithoutLocation > 0">
          另有 {{ memoriesWithoutLocation }} 段记忆暂未记录位置。
        </span>
        <span v-if="longRouteBreakCount">长距离行程已保留为路线断点。</span>
      </div>

      <div
        ref="mapStageElement"
        :class="[
          'trip-map-stage',
          {
            'has-selection': selectedMemory,
            'has-expanded-card': cardExpanded,
            'has-replay-session': replaySessionActive,
          },
        ]"
      >
        <MemoryMap
          ref="memoryMap"
          :points="points"
          :route-segments="routeSegments"
          :selected-id="selectedMemoryId"
          :active-date="activeDate"
          :playback-index="playbackReplayIndex"
          :focus-selected-on-ready="Boolean(route.query.memoryId)"
          :fallback-latitude="trip.destinationLatitude"
          :fallback-longitude="trip.destinationLongitude"
          :fallback-label="trip.destination || '目的城市'"
          :bottom-inset="mapBottomInset"
          @select="handleMapSelection"
          @interaction="handleMapInteraction"
          @selection-positioned="revealMemoryCard"
        />

        <button type="button" class="trip-map-fit-all" aria-label="恢复完整旅行路线" title="恢复完整旅行路线" @click="fitAllMemories">
          <LocateFixed :size="19" :stroke-width="1.8" aria-hidden="true" />
        </button>

        <div
          ref="overlayDockElement"
          :class="[
            'map-overlay-dock',
            {
              'is-expanded': cardExpanded,
              'is-replaying': replaySessionActive,
            },
          ]"
        >
          <article
            v-if="selectedMemory"
            :class="[
              'map-memory-card',
              cardExpanded ? 'is-expanded' : 'is-collapsed',
              { 'is-positioning': !mapCardReady },
            ]"
            @wheel.stop
            @touchmove.stop
          >
            <div class="map-memory-card-summary" @click="toggleMemoryCard">
              <div v-if="hasMemoryPhoto(selectedMemory)" class="map-memory-summary-photo" @click.stop>
                <MemoryPhotoGallery
                  :photos="selectedMemory.photos"
                  :fallback-url="photoSrc(selectedMemory.photoUrl)"
                  layout="favorite"
                  alt="地图记忆照片"
                />
              </div>
              <div v-else class="map-memory-summary-photo is-empty" aria-hidden="true">
                <MapPin :size="22" :stroke-width="1.5" />
              </div>

              <div class="map-memory-summary-copy">
                <p v-if="selectedMemory.isReplayPoint" class="map-memory-station">
                  <span class="map-memory-sequence">{{ selectedMemory.sequenceNumber }}</span>
                  第 {{ selectedMemory.dayNumber }} 天 · 第 {{ selectedMemory.stopNumber }} 站
                </p>
                <p v-else class="map-memory-station is-browse-only">仅供浏览的位置</p>
                <h2>{{ selectedMemory.locationName || '已记录位置' }}</h2>
                <p class="map-memory-summary-meta">
                  <span>{{ formatTime(selectedMemory.recordTime) }}</span>
                  <span>共 {{ photoCount(selectedMemory) }} 张照片</span>
                </p>
              </div>

              <div class="map-memory-card-actions">
                <button type="button" class="map-memory-close" aria-label="关闭记忆详情" @click.stop="closeMemoryCard">
                  <X :size="18" :stroke-width="1.8" aria-hidden="true" />
                </button>
                <button
                  type="button"
                  class="map-memory-expand"
                  :aria-label="cardExpanded ? '收起记忆卡片' : '展开记忆卡片'"
                  :aria-expanded="cardExpanded"
                  @click.stop="toggleMemoryCard"
                >
                  <ChevronUp v-if="cardExpanded" :size="18" :stroke-width="1.8" aria-hidden="true" />
                  <ChevronDown v-else :size="18" :stroke-width="1.8" aria-hidden="true" />
                </button>
              </div>
            </div>

            <div v-if="cardExpanded" class="map-memory-expanded-content">
              <div class="map-memory-facts">
                <p>
                  <MapPin :size="16" :stroke-width="1.7" aria-hidden="true" />
                  <span>{{ selectedLocationLabel }}</span>
                </p>
                <p>
                  <Clock3 :size="16" :stroke-width="1.7" aria-hidden="true" />
                  <span>{{ formatDate(selectedMemory.recordTime) }} {{ formatTime(selectedMemory.recordTime) }}</span>
                </p>
              </div>

              <div class="map-memory-body">
                <p :class="['map-memory-quote', { muted: !selectedMemory.content }]">
                  {{ selectedMemory.content || '这一刻没有留下文字。' }}
                </p>
              </div>

              <RouterLink :to="`/trips/${id}/memories/${selectedMemory.id}`" class="map-memory-detail-link">
                查看记忆详情
                <ChevronRight :size="16" :stroke-width="1.8" aria-hidden="true" />
              </RouterLink>
            </div>
          </article>

          <div
            v-if="!cardExpanded && !replaySessionActive && canReplayCurrentScope"
            class="map-replay-status"
            aria-live="polite"
          >
            <span>路径回放 · {{ playbackStations.length }} 站</span>
            <button type="button" @click="startPlayback">
              <Play :size="14" fill="currentColor" aria-hidden="true" />
              播放
            </button>
          </div>

          <p
            v-else-if="!cardExpanded && !replaySessionActive && playbackStations.length === 1"
            class="map-replay-unavailable"
          >
            当前日期只有 1 个站点，可浏览但无法回放
          </p>

          <section
            v-if="!cardExpanded && replaySessionActive && canReplayCurrentScope"
            class="map-playback-controls"
            aria-label="路径回放控制"
          >
            <div class="map-playback-progress">第 {{ playbackIndex + 1 }} / {{ playbackStations.length }} 站</div>
            <div class="map-playback-actions">
              <button type="button" :disabled="playbackIndex <= 0" @click="previousStation">
                <ChevronLeft :size="18" :stroke-width="1.8" aria-hidden="true" />
                上一站
              </button>
              <button type="button" class="map-playback-main" @click="togglePlayback">
                <Pause v-if="isPlaying" :size="20" aria-hidden="true" />
                <RotateCcw v-else-if="isPlaybackComplete" :size="19" aria-hidden="true" />
                <Play v-else :size="20" fill="currentColor" aria-hidden="true" />
                <span>{{ isPlaying ? '暂停' : isPlaybackComplete ? '重新播放' : '继续播放' }}</span>
              </button>
              <button
                type="button"
                :disabled="playbackIndex >= playbackStations.length - 1"
                @click="nextStation"
              >
                下一站
                <ChevronRight :size="18" :stroke-width="1.8" aria-hidden="true" />
              </button>
            </div>
          </section>

          <button
            v-if="!cardExpanded && replaySessionActive && playbackDateLabel"
            type="button"
            class="map-playback-date"
            title="退出回放并查看全部日期"
            @click="exitPlaybackSession"
          >
            {{ playbackDateLabel }}
          </button>

          <nav
            v-if="!cardExpanded && !replaySessionActive && dayOptions.length"
            class="map-day-navigation"
            aria-label="地图自然日导航"
          >
            <div class="map-day-navigation-track">
              <button
                :ref="element => setDayButtonRef('all', element)"
                type="button"
                :class="['map-day-button', { active: !activeDate }]"
                :aria-current="!activeDate ? 'date' : undefined"
                @click="selectAllDays"
              >
                <strong>全部</strong>
                <span>完整路线</span>
              </button>
              <button
                v-for="day in dayOptions"
                :key="day.date"
                :ref="element => setDayButtonRef(day.date, element)"
                type="button"
                :class="['map-day-button', { active: activeDate === day.date }]"
                :aria-current="activeDate === day.date ? 'date' : undefined"
                @click="selectDay(day)"
              >
                <strong>第 {{ day.dayNumber }} 天</strong>
                <span>{{ formatDate(day.date) }}</span>
              </button>
            </div>
          </nav>
        </div>
      </div>
    </template>
  </section>
</template>
