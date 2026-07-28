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
  ZoomIn,
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
const routeOverviewMode = ref('all')
const memoryMap = ref(null)
const mapCardReady = ref(false)
const mapStageElement = ref(null)
const overlayDockElement = ref(null)
const dayNavigationElement = ref(null)
const mapBottomInset = ref(132)
const cardExpanded = ref(false)
const dayButtonElements = new Map()
const playbackIndex = ref(-1)
const replayState = ref('idle')
const playbackPreparing = ref(false)
let playbackTimer = null
let playbackFitRequest = 0
let playbackStepRequest = 0
let overlayResizeObserver = null
let overlayMeasureFrame = null
const photoPreloadCache = new Map()

const SAME_LOCATION_DISTANCE_METERS = 5
const LONG_ROUTE_BREAK_DISTANCE_METERS = 80_000
const PLAYBACK_CAMERA_LONG_DISTANCE_METERS = 50_000
const LOCATION_GROUP_DISTANCE_METERS = 10
const NAMED_LOCATION_GROUP_DISTANCE_METERS = 50
const PLAYBACK_STEP_DURATION = 2800
const PHOTO_READY_WAIT = 1200

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
const mapPointGroups = computed(() => {
  const groups = []

  points.value.forEach((point) => {
    const group = groups.find(candidate => candidate.members.some(member => isSameMapLocation(member, point)))
    if (group) group.members.push(point)
    else groups.push({ id: `map-group-${memoryIdKey(point.id)}`, members: [point] })
  })

  return groups.map((group) => {
    const members = group.members.slice().sort(compareMemories)
    const replayMembers = members.filter(member => member.isReplayPoint !== false)
    const primary = replayMembers[0] || members[0]
    const latitudes = members.map(member => Number(member.latitude)).sort((left, right) => left - right)
    const longitudes = members.map(member => Number(member.longitude)).sort((left, right) => left - right)
    return {
      ...primary,
      id: group.id,
      latitude: medianCoordinate(latitudes),
      longitude: medianCoordinate(longitudes),
      memberIds: members.map(member => memoryIdKey(member.id)),
      memberCount: members.length,
      members,
      replayIndexes: replayMembers.map(member => member.replayIndex),
      mapDates: [...new Set(replayMembers.map(member => member.mapDate).filter(Boolean))],
      sequenceNumber: replayMembers[0]?.sequenceNumber ?? null,
      isReplayPoint: replayMembers.length > 0,
    }
  })
})
const mapPointGroupByMemoryId = computed(() => {
  const index = new Map()
  mapPointGroups.value.forEach((group) => {
    group.memberIds.forEach(memoryId => index.set(memoryId, group))
  })
  return index
})
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

    // Same-coordinate records share one visual station. Long jumps retain the
    // time relationship but are marked as transitions, not real travel paths.
    if (distance == null || distance <= SAME_LOCATION_DISTANCE_METERS) {
      continue
    }

    segments.push({
      fromId: from.id,
      toId: to.id,
      fromDate: from.mapDate,
      toDate: to.mapDate,
      fromReplayIndex: from.replayIndex,
      toReplayIndex: to.replayIndex,
      distanceMeters: distance,
      isLongDistance: distance > LONG_ROUTE_BREAK_DISTANCE_METERS,
    })
  }

  return segments
})
const groupedRouteSegments = computed(() => routeSegments.value
  .map((segment) => {
    const fromGroup = mapPointGroupByMemoryId.value.get(memoryIdKey(segment.fromId))
    const toGroup = mapPointGroupByMemoryId.value.get(memoryIdKey(segment.toId))
    if (!fromGroup || !toGroup || fromGroup.id === toGroup.id) return null
    return {
      ...segment,
      fromId: fromGroup.id,
      toId: toGroup.id,
    }
  })
  .filter(Boolean))
const longRouteBreakCount = computed(() => timedMemories.value.slice(0, -1).filter((memory, index) => {
  const nextMemory = timedMemories.value[index + 1]
  if (!hasValidCoordinates(memory) || !hasValidCoordinates(nextMemory)) return false
  const distance = wgs84DistanceMeters(memory.latitude, memory.longitude, nextMemory.latitude, nextMemory.longitude)
  return distance != null && distance > LONG_ROUTE_BREAK_DISTANCE_METERS
}).length)
const continuousRouteGroups = computed(() => {
  const groups = []
  const replayPointById = new Map(replayPoints.value.map(point => [memoryIdKey(point.id), point]))
  let currentGroup = []
  let previousPoint = null

  timedMemories.value.forEach((memory) => {
    const point = replayPointById.get(memoryIdKey(memory.id))
    if (!point) {
      if (currentGroup.length) groups.push(currentGroup)
      currentGroup = []
      previousPoint = null
      return
    }

    if (!previousPoint) {
      currentGroup = [point]
      previousPoint = point
      return
    }

    const distance = wgs84DistanceMeters(
      previousPoint.latitude,
      previousPoint.longitude,
      point.latitude,
      point.longitude,
    )
    if (distance == null || distance > LONG_ROUTE_BREAK_DISTANCE_METERS) {
      if (currentGroup.length) groups.push(currentGroup)
      currentGroup = [point]
    } else {
      currentGroup.push(point)
    }
    previousPoint = point
  })

  if (currentGroup.length) groups.push(currentGroup)
  return groups
})
const defaultRouteGroup = computed(() => (
  continuousRouteGroups.value.find(group => group.length >= 2)
  || continuousRouteGroups.value[0]
  || []
))
const defaultRoutePointGroupIds = computed(() => {
  const ids = new Set()
  defaultRouteGroup.value.forEach((memory) => {
    const group = mapPointGroupByMemoryId.value.get(memoryIdKey(memory.id))
    if (group) ids.add(group.id)
  })
  return [...ids]
})
const hasFocusedRouteOverview = computed(() => (
  longRouteBreakCount.value > 0
  && defaultRouteGroup.value.length > 0
  && defaultRouteGroup.value.length < replayPoints.value.length
))
const isDefaultRouteFocus = computed(() => (
  !activeDate.value
  && hasFocusedRouteOverview.value
  && routeOverviewMode.value === 'focus'
))
const distantReplayPointCount = computed(() => Math.max(
  0,
  replayPoints.value.length - defaultRouteGroup.value.length,
))
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
const activeDayOption = computed(() => dayOptions.value.find(day => day.date === activeDate.value) || null)
const playbackPointGroupIds = computed(() => {
  const ids = new Set()
  playbackStations.value.forEach((memory) => {
    const group = mapPointGroupByMemoryId.value.get(memoryIdKey(memory.id))
    if (group) ids.add(group.id)
  })
  return [...ids]
})
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
const selectedPointGroup = computed(() => (
  mapPointGroupByMemoryId.value.get(memoryIdKey(selectedMemoryId.value)) || null
))
const selectedGroupMemories = computed(() => {
  const members = selectedPointGroup.value?.members || []
  const scopedMembers = activeDate.value
    ? members.filter(member => member.mapDate === activeDate.value)
    : members
  return scopedMembers.length ? scopedMembers : members
})
const selectedGroupMemoryIndex = computed(() => selectedGroupMemories.value.findIndex(
  memory => memoryIdKey(memory.id) === memoryIdKey(selectedMemoryId.value),
))
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

function normalizedLocationName(value) {
  return String(value || '').trim().replace(/\s+/g, ' ').toLocaleLowerCase('zh-CN')
}

function medianCoordinate(sortedValues) {
  if (!sortedValues.length) return null
  const middle = Math.floor(sortedValues.length / 2)
  return sortedValues.length % 2 === 1
    ? sortedValues[middle]
    : (sortedValues[middle - 1] + sortedValues[middle]) / 2
}

function isSameMapLocation(left, right) {
  const distance = wgs84DistanceMeters(
    left.latitude,
    left.longitude,
    right.latitude,
    right.longitude,
  )
  if (distance == null) return false
  if (distance <= LOCATION_GROUP_DISTANCE_METERS) return true
  const leftName = normalizedLocationName(left.locationName)
  const rightName = normalizedLocationName(right.locationName)
  return Boolean(
    leftName
    && leftName === rightName
    && distance <= NAMED_LOCATION_GROUP_DISTANCE_METERS,
  )
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
  const nextMemoryId = memoryId == null ? '' : memoryIdKey(memoryId)
  const currentMemoryId = memoryIdKey(route.query.memoryId)
  if (nextMemoryId === currentMemoryId) return Promise.resolve()

  const nextQuery = { ...route.query }
  if (memoryId == null) delete nextQuery.memoryId
  else nextQuery.memoryId = nextMemoryId
  return router.replace({ query: nextQuery }).catch(() => {})
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

function handleMapSelection(groupId) {
  playbackFitRequest += 1
  playbackPreparing.value = false
  pausePlayback({ preserveSession: true })
  const group = mapPointGroups.value.find(pointGroup => pointGroup.id === groupId)
  if (!group) return
  const scopedMembers = activeDate.value
    ? group.members.filter(member => member.mapDate === activeDate.value)
    : group.members
  const candidates = scopedMembers.length ? scopedMembers : group.members
  const currentSelection = candidates.find(
    memory => memoryIdKey(memory.id) === memoryIdKey(selectedMemoryId.value),
  )
  selectMemory((currentSelection || candidates[0])?.id)
}

function selectGroupedMemory(direction) {
  const members = selectedGroupMemories.value
  if (members.length < 2) return
  const currentIndex = selectedGroupMemoryIndex.value >= 0 ? selectedGroupMemoryIndex.value : 0
  const nextIndex = (currentIndex + direction + members.length) % members.length
  selectMemory(members[nextIndex].id)
}

function clearMemorySelection({ updateRoute = true } = {}) {
  mapCardReady.value = false
  cardExpanded.value = false
  selectedMemoryId.value = null
  if (updateRoute) replaceMemoryQuery(null)
  scheduleOverlayMeasure()
}

function handleMapInteraction() {
  if (playbackPreparing.value) {
    playbackFitRequest += 1
    playbackPreparing.value = false
  }
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
  const mobile = stageRect.width <= 640
  const overlaySelector = mobile
    ? '.map-memory-card, .map-replay-status, .map-replay-unavailable, .map-playback-controls, .map-playback-date, .map-day-navigation'
    : '.map-replay-status, .map-replay-unavailable, .map-playback-controls, .map-playback-date, .map-day-navigation'
  const visibleOverlayTop = [...dock.querySelectorAll(overlaySelector)].reduce((top, element) => {
    const rect = element.getBoundingClientRect()
    const visibleInsideStage = rect.height > 0
      && rect.bottom > stageRect.top
      && rect.top < stageRect.bottom
    return visibleInsideStage ? Math.min(top, rect.top) : top
  }, Number.POSITIVE_INFINITY)
  const markerClearance = mobile ? 48 : 28
  let measuredInset = Number.isFinite(visibleOverlayTop)
    ? stageRect.bottom - visibleOverlayTop + markerClearance
    : (mobile ? 90 : 112)
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

function nextAnimationFrame() {
  return new Promise(resolve => window.requestAnimationFrame(resolve))
}

async function handleMapReady() {
  await nextTick()
  await nextAnimationFrame()
  measureMapBottomInset()
  await nextTick()
  await nextAnimationFrame()

  if (route.query.memoryId && selectedMemory.value) return
  if (activeDate.value) {
    if (playbackPointGroupIds.value.length) {
      await memoryMap.value?.fitPointIds(playbackPointGroupIds.value, 13)
    }
    return
  }
  await memoryMap.value?.fitAll()
}

function setDayButtonRef(date, element) {
  if (element) dayButtonElements.set(date, element)
  else dayButtonElements.delete(date)
}

function syncActiveDayButton() {
  const navigation = dayNavigationElement.value
  const activeKey = activeDate.value
    || (isDefaultRouteFocus.value ? 'focus' : 'all')
  const button = dayButtonElements.get(activeKey)
  if (!navigation || !button) return

  const targetLeft = button.offsetLeft - (navigation.clientWidth - button.offsetWidth) / 2
  navigation.scrollTo({
    left: Math.max(0, targetLeft),
    behavior: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
  })
}

async function selectDay(day) {
  endPlaybackSession()
  playbackIndex.value = -1
  activeDate.value = day.date
  routeOverviewMode.value = 'focus'
  clearMemorySelection()
  await nextTick()
  syncActiveDayButton()
  const pointIds = playbackPointGroupIds.value
  if (pointIds.length) await memoryMap.value?.fitPointIds(pointIds, 13)
  scheduleOverlayMeasure()
}

async function selectDefaultRoute() {
  endPlaybackSession()
  playbackIndex.value = -1
  activeDate.value = ''
  routeOverviewMode.value = 'focus'
  clearMemorySelection()
  await nextTick()
  syncActiveDayButton()
  if (defaultRoutePointGroupIds.value.length) {
    await memoryMap.value?.fitPointIds(defaultRoutePointGroupIds.value, 13)
  }
  scheduleOverlayMeasure()
}

async function selectAllDays() {
  endPlaybackSession()
  playbackIndex.value = -1
  activeDate.value = ''
  routeOverviewMode.value = 'all'
  clearMemorySelection()
  await nextTick()
  syncActiveDayButton()
  await memoryMap.value?.fitAll()
  scheduleOverlayMeasure()
}

async function fitAllMemories(event) {
  event?.currentTarget?.blur()
  endPlaybackSession()
  playbackIndex.value = -1
  activeDate.value = ''
  routeOverviewMode.value = 'all'
  clearMemorySelection()
  await nextTick()
  syncActiveDayButton()
  await memoryMap.value?.fitAll()
}

async function zoomSelectedMemory(event) {
  if (!selectedMemory.value) return
  event?.currentTarget?.blur()
  playbackFitRequest += 1
  playbackPreparing.value = false
  if (isPlaying.value) pausePlayback({ preserveSession: true })
  await memoryMap.value?.zoomToSelected(15)
}

function clearPlaybackTimer() {
  if (playbackTimer != null) window.clearTimeout(playbackTimer)
  playbackTimer = null
  playbackStepRequest += 1
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
  playbackFitRequest += 1
  playbackPreparing.value = false
  clearPlaybackTimer()
  replayState.value = 'idle'
  scheduleOverlayMeasure()
}

async function selectPlaybackStation(index, { waitForPhoto = false } = {}) {
  const station = playbackStations.value[index]
  if (!station) return false
  if (waitForPhoto) await waitForPrimaryPhoto(station)
  playbackIndex.value = index
  selectMemory(station.id, { updateRoute: false })
  preloadPlaybackWindow(index)
  return true
}

async function transitionPlaybackStation(index, { requestId = null } = {}) {
  const station = playbackStations.value[index]
  if (!station) return false
  const previousStation = currentPlaybackPoint.value
  const transitionRequest = ++playbackFitRequest
  playbackPreparing.value = true
  preloadPlaybackWindow(index)
  await waitForPrimaryPhoto(station)
  if (
    transitionRequest !== playbackFitRequest
    || (requestId != null && requestId !== playbackStepRequest)
  ) {
    if (transitionRequest === playbackFitRequest) playbackPreparing.value = false
    return false
  }

  const distanceMeters = previousStation
    ? wgs84DistanceMeters(
      previousStation.latitude,
      previousStation.longitude,
      station.latitude,
      station.longitude,
    ) || 0
    : 0

  memoryMap.value?.suspendSelectionPositioning()
  try {
    const moved = await memoryMap.value?.transitionToMemory(station.id, {
      distanceMeters,
      targetZoom: 14,
      longDistanceMeters: PLAYBACK_CAMERA_LONG_DISTANCE_METERS,
    })
    if (
      moved === false
      || transitionRequest !== playbackFitRequest
      || (requestId != null && requestId !== playbackStepRequest)
    ) {
      return false
    }

    playbackIndex.value = index
    selectMemory(station.id, { updateRoute: false })
    await nextTick()
    mapCardReady.value = true
    scheduleOverlayMeasure()
    await nextAnimationFrame()
    measureMapBottomInset()
    return true
  } finally {
    memoryMap.value?.resumeSelectionPositioning()
    if (transitionRequest === playbackFitRequest) playbackPreparing.value = false
  }
}

function queuePlaybackStep() {
  clearPlaybackTimer()
  const requestId = playbackStepRequest
  playbackTimer = window.setTimeout(async () => {
    playbackTimer = null
    if (requestId !== playbackStepRequest || replayState.value !== 'playing') return
    const nextIndex = playbackIndex.value + 1
    if (nextIndex >= playbackStations.value.length) {
      replayState.value = 'ended'
      scheduleOverlayMeasure()
      return
    }
    const moved = await transitionPlaybackStation(nextIndex, { requestId })
    if (!moved) return
    if (requestId !== playbackStepRequest || replayState.value !== 'playing') return
    if (nextIndex >= playbackStations.value.length - 1) {
      replayState.value = 'ended'
      scheduleOverlayMeasure()
      return
    }
    queuePlaybackStep()
  }, PLAYBACK_STEP_DURATION)
}

function resumePlayback() {
  if (!canReplayCurrentScope.value || isPlaybackComplete.value) return
  replayState.value = 'playing'
  preloadPlaybackWindow(Math.max(0, playbackIndex.value))
  scheduleOverlayMeasure()
  queuePlaybackStep()
}

async function startPlayback({ restart = false } = {}) {
  const stations = playbackStations.value
  if (stations.length < 2 || playbackPreparing.value) return
  if (replayState.value === 'paused' && !restart) {
    resumePlayback()
    return
  }
  clearPlaybackTimer()
  const requestId = ++playbackFitRequest
  playbackPreparing.value = true
  cardExpanded.value = false
  playbackIndex.value = -1
  clearMemorySelection({ updateRoute: false })
  preloadPlaybackWindow(0)
  const firstPhotoReady = waitForPrimaryPhoto(stations[0])
  await firstPhotoReady
  if (requestId !== playbackFitRequest) {
    playbackPreparing.value = false
    return
  }

  memoryMap.value?.suspendSelectionPositioning()
  try {
    await selectPlaybackStation(0)
    await nextTick()
    await memoryMap.value?.zoomToSelected(14)
  } finally {
    memoryMap.value?.resumeSelectionPositioning()
  }
  if (requestId !== playbackFitRequest) {
    playbackPreparing.value = false
    return
  }
  playbackPreparing.value = false
  replayState.value = 'playing'
  scheduleOverlayMeasure()
  queuePlaybackStep()
}

function togglePlayback() {
  if (isPlaying.value) pausePlayback()
  else if (replayState.value === 'paused') resumePlayback()
  else startPlayback({ restart: replayState.value === 'ended' })
}

async function previousStation() {
  pausePlayback({ preserveSession: true })
  if (!playbackStations.value.length) return
  replayState.value = 'paused'
  const requestId = playbackStepRequest
  await transitionPlaybackStation(Math.max(0, playbackIndex.value - 1), { requestId })
}

async function nextStation() {
  pausePlayback({ preserveSession: true })
  if (!playbackStations.value.length) return
  replayState.value = 'paused'
  const nextIndex = playbackIndex.value < 0 ? 0 : Math.min(playbackStations.value.length - 1, playbackIndex.value + 1)
  const requestId = playbackStepRequest
  await transitionPlaybackStation(nextIndex, { requestId })
}

function exitPlaybackSession() {
  playbackPreparing.value = false
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

function preloadPhoto(url) {
  const normalizedUrl = String(url || '').trim()
  if (!normalizedUrl) return Promise.resolve(false)
  if (photoPreloadCache.has(normalizedUrl)) return photoPreloadCache.get(normalizedUrl)

  const request = new Promise((resolve) => {
    const image = new Image()
    const finish = (loaded) => {
      image.onload = null
      image.onerror = null
      resolve(loaded)
    }
    image.decoding = 'async'
    image.onload = () => finish(true)
    image.onerror = () => finish(false)
    image.src = normalizedUrl
    if (image.complete) finish(image.naturalWidth > 0)
  })
  photoPreloadCache.set(normalizedUrl, request)
  return request
}

function waitForPrimaryPhoto(memory) {
  const primaryUrl = normalizedPhotoUrls(memory)[0]
  if (!primaryUrl) return Promise.resolve(true)
  return Promise.race([
    preloadPhoto(primaryUrl),
    new Promise(resolve => window.setTimeout(() => resolve(false), PHOTO_READY_WAIT)),
  ])
}

function preloadPlaybackWindow(index) {
  const stations = playbackStations.value
  const current = stations[index]
  if (current) normalizedPhotoUrls(current).forEach(preloadPhoto)
  ;[stations[index + 1], stations[index + 2]].forEach((station) => {
    const primaryUrl = normalizedPhotoUrls(station)[0]
    if (primaryUrl) preloadPhoto(primaryUrl)
  })
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
    mapCardReady.value = false
    cardExpanded.value = false
    selectedMemoryId.value = requestedMemory?.id ?? null
    activeDate.value = ''
    routeOverviewMode.value = 'all'
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
        <span v-if="activeDate && activeDayOption">
          第 {{ activeDayOption.dayNumber }} 天 · {{ formatDate(activeDate) }} ·
          {{ playbackStations.length }} 个站点
        </span>
        <span v-else-if="replayPoints.length >= 2">{{ replayPoints.length }} 段记忆可按时间回放</span>
        <span v-else-if="replayPoints.length === 1">1 段带时间和地点的记忆显示在地图上</span>
        <span v-else-if="browseOnlyPoints.length">{{ browseOnlyPoints.length }} 个位置仅供浏览</span>
        <span v-else>已定位到 {{ trip.destination || '目的城市' }}，新增带位置的 Memory 后会显示旅行路线。</span>
        <span v-if="!activeDate && replayPoints.length && browseOnlyPoints.length">
          {{ browseOnlyPoints.length }} 个位置仅供浏览，不参与回放。
        </span>
        <span v-if="memoriesWithoutLocation > 0">
          另有 {{ memoriesWithoutLocation }} 段记忆未显示在地图上。
        </span>
        <span v-if="isDefaultRouteFocus">
          当前先展示最早的连续路线，另有 {{ distantReplayPointCount }} 个站点位于其他路线段。
        </span>
        <span v-else-if="longRouteBreakCount">
          完整路线包含 {{ longRouteBreakCount }} 段远距离虚线，仅表示先后顺序。
        </span>
        <span v-if="activeDate && playbackStations.length === 0">这一天没有带位置的记忆。</span>
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
          :points="mapPointGroups"
          :route-segments="groupedRouteSegments"
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
          @ready="handleMapReady"
        />

        <button
          v-if="isDefaultRouteFocus"
          type="button"
          class="trip-map-fit-all"
          aria-label="查看完整旅行路线"
          title="查看完整旅行路线"
          @mousedown.prevent
          @click="fitAllMemories"
        >
          <LocateFixed :size="19" :stroke-width="1.8" aria-hidden="true" />
        </button>
        <button
          v-if="selectedMemory"
          type="button"
          class="trip-map-zoom-selected"
          aria-label="放大当前记忆地点"
          title="放大当前记忆地点"
          @mousedown.prevent
          @click="zoomSelectedMemory"
        >
          <ZoomIn :size="19" :stroke-width="1.8" aria-hidden="true" />
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
                  eager-preview
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

            <div
              v-if="selectedGroupMemories.length > 1"
              class="map-memory-group-nav"
              aria-label="切换同一地点的记忆"
              @click.stop
            >
              <button
                type="button"
                aria-label="查看同一地点的上一段记忆"
                @click="selectGroupedMemory(-1)"
              >
                <ChevronLeft :size="16" :stroke-width="1.8" aria-hidden="true" />
                上一段
              </button>
              <span>
                同一地点 · {{ Math.max(0, selectedGroupMemoryIndex) + 1 }} /
                {{ selectedGroupMemories.length }}
              </span>
              <button
                type="button"
                aria-label="查看同一地点的下一段记忆"
                @click="selectGroupedMemory(1)"
              >
                下一段
                <ChevronRight :size="16" :stroke-width="1.8" aria-hidden="true" />
              </button>
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
            <button type="button" :disabled="playbackPreparing" @click="startPlayback">
              <Play :size="14" fill="currentColor" aria-hidden="true" />
              {{ playbackPreparing ? '准备中' : '播放' }}
            </button>
          </div>

          <p
            v-else-if="!cardExpanded && !replaySessionActive && playbackStations.length === 1"
            class="map-replay-unavailable"
          >
            {{
              activeDate
                ? '当前日期只有 1 个站点，可浏览但无法回放'
                : '至少需要 2 段带时间和地点的记忆才能形成回放路径'
            }}
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
            ref="dayNavigationElement"
            class="map-day-navigation"
            aria-label="地图自然日导航"
          >
            <div class="map-day-navigation-track">
              <button
                v-if="hasFocusedRouteOverview"
                :ref="element => setDayButtonRef('focus', element)"
                type="button"
                :class="['map-day-button', { active: isDefaultRouteFocus }]"
                :aria-current="isDefaultRouteFocus ? 'date' : undefined"
                @click="selectDefaultRoute"
              >
                <strong>连续路线</strong>
                <span>{{ defaultRouteGroup.length }} 站</span>
              </button>
              <button
                :ref="element => setDayButtonRef('all', element)"
                type="button"
                :class="['map-day-button', { active: !activeDate && !isDefaultRouteFocus }]"
                :aria-current="!activeDate && !isDefaultRouteFocus ? 'date' : undefined"
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
