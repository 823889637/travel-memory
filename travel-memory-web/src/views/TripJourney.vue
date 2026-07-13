<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getTrip } from '../api/trip'
import { getTimeline } from '../api/memory'
import { resolveTripCoverUrl } from '../utils/tripCover'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const trip = ref(null)
const memories = ref([])
const activeDayIndex = ref(0)
const loading = ref(false)
const error = ref('')
const coverImageFailed = ref(false)

const dayGroups = computed(() => {
  const groups = []
  const groupMap = new Map()

  const sortedMemories = [...memories.value].sort((left, right) => {
    const leftTime = getTimeValue(left.recordTime)
    const rightTime = getTimeValue(right.recordTime)
    if (leftTime !== rightTime) {
      return leftTime - rightTime
    }
    return getTimeValue(left.createTime) - getTimeValue(right.createTime)
  })

  sortedMemories.forEach((memory) => {
    const dateKey = getDateKey(memory.recordTime)
    if (!groupMap.has(dateKey)) {
      const group = {
        date: dateKey,
        dayLabel: `第 ${groups.length + 1} 天`,
        memories: [],
        stops: [],
      }
      groupMap.set(dateKey, group)
      groups.push(group)
    }
    groupMap.get(dateKey).memories.push(memory)
  })

  groups.forEach((group) => {
    group.stops = buildStops(group.memories)
  })

  return groups
})

const activeDay = computed(() => dayGroups.value[activeDayIndex.value] || null)

const coverPhotoUrl = computed(() => {
  return coverImageFailed.value ? '' : resolveTripCoverUrl(trip.value, memories.value)
})

const tripDateRange = computed(() => {
  if (!trip.value) {
    return ''
  }
  const start = trip.value.startDate || '未知开始'
  const end = trip.value.endDate || '未知结束'
  return `${start} - ${end}`
})

function getTimeValue(value) {
  if (!value) {
    return Number.MAX_SAFE_INTEGER
  }
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? Number.MAX_SAFE_INTEGER : time
}

function getDateKey(value) {
  if (!value) {
    return '未知日期'
  }
  return String(value).slice(0, 10)
}

function buildStops(dayMemories) {
  const stops = []
  let currentStop = null

  dayMemories.forEach((memory) => {
    const locationName = normalizeLocationName(memory.locationName)
    if (!currentStop || currentStop.locationName !== locationName) {
      currentStop = {
        order: stops.length + 1,
        locationName,
        memories: [],
      }
      stops.push(currentStop)
    }
    currentStop.memories.push(memory)
  })

  return stops.map((stop) => {
    const photos = stop.memories.flatMap((memory) => memory.photos?.length ? memory.photos : (memory.photoUrl ? [memory] : []))
    const contents = stop.memories.filter((memory) => memory.content)
    return {
      ...stop,
      photos,
      contents,
      startTime: stop.memories[0]?.recordTime,
      endTime: stop.memories[stop.memories.length - 1]?.recordTime,
    }
  })
}

function normalizeLocationName(value) {
  const normalized = value ? String(value).trim() : ''
  return normalized || '途中留下的记忆'
}

function formatTime(value) {
  if (!value) {
    return '--:--'
  }
  const rawValue = String(value)
  if (rawValue.length >= 16) {
    return rawValue.slice(11, 16)
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--:--'
  }
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function formatTimeRange(startTime, endTime) {
  const start = formatTime(startTime)
  const end = formatTime(endTime)
  if (start === end) {
    return start
  }
  return `${start} - ${end}`
}

function photoSrc(url) {
  return url || ''
}

function selectDay(index) {
  activeDayIndex.value = index
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
    coverImageFailed.value = false
    activeDayIndex.value = 0
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadPage)
</script>

<template>
  <section class="journey-page">
    <div class="journey-hero">
      <img
        v-if="coverPhotoUrl"
        class="journey-hero-photo"
        :src="photoSrc(coverPhotoUrl)"
        alt="旅行封面"
        @error="coverImageFailed = true"
      />
      <div class="journey-hero-content">
        <p class="journey-kicker">重新走一遍</p>
        <h1>{{ trip?.title || '这段旅行' }}</h1>
        <p v-if="trip?.destination">{{ trip.destination }}</p>
        <p>{{ tripDateRange }}</p>
      </div>
    </div>

    <p v-if="loading">加载中...</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!loading && dayGroups.length === 0" class="journey-empty">
      <p class="journey-empty-title">这段旅行还在等待第一段记忆</p>
      <p class="journey-empty-hint">当你留下照片和当时的心情，这里就会重新为你铺开走过的路。</p>
    </div>

    <template v-if="!loading && activeDay">
      <div class="journey-top-controls">
        <div class="journey-quiet-nav">
          <RouterLink :to="`/trips/${id}`">返回时间线</RouterLink>
          <RouterLink :to="`/trips/${id}/map`">查看地图</RouterLink>
        </div>
        <div class="day-switcher">
          <button
            v-for="(group, index) in dayGroups"
            :key="group.date"
            type="button"
            :class="index === activeDayIndex ? 'day-switch active' : 'day-switch'"
            @click="selectDay(index)"
          >
            <span>{{ group.dayLabel }}</span>
            <small>{{ group.date }}</small>
          </button>
        </div>
      </div>

      <section class="journey-day">
        <div class="journey-day-header">
          <div>
            <p class="journey-day-kicker">{{ activeDay.dayLabel }} · {{ activeDay.date }}</p>
            <p class="muted">这一天经过了 {{ activeDay.stops.length }} 个地方，留下了 {{ activeDay.memories.length }} 段记忆。</p>
          </div>
        </div>

        <div class="journey-flow">
          <article v-for="(stop, index) in activeDay.stops" :key="`${stop.order}-${stop.locationName}`" class="journey-stop">
            <div v-if="index > 0" class="journey-step-connector" aria-label="下一站"><span>下一站</span></div>

            <div class="journey-stop-card">
                <div class="journey-stop-header">
                  <h3>{{ stop.locationName }}</h3>
                  <p class="journey-stop-meta">第 {{ stop.order }} 站 · {{ formatTimeRange(stop.startTime, stop.endTime) }} · {{ stop.memories.length }} 段记忆</p>
                </div>

                <div v-if="stop.photos.length > 0" class="journey-stop-photos">
                  <MemoryPhotoGallery
                    :photos="stop.photos"
                    :fallback-url="photoSrc(stop.photos[0]?.photoUrl)"
                    fit="contain"
                    layout="journey"
                    count-label="张照片"
                    :alt="`${stop.locationName} 旅行记忆照片`"
                  />
                </div>
                <div v-else class="journey-stop-photo-empty">这一站没有留下照片，但当时的文字还在。</div>

                <div class="journey-stop-contents">
                  <p v-for="memory in stop.contents" :key="memory.id" class="journey-content">
                    {{ memory.content }}
                  </p>
                  <p v-if="stop.contents.length === 0" class="journey-content muted">这一站没有留下文字，就让照片替你记着吧。</p>
                </div>
            </div>
          </article>

        </div>
      </section>
    </template>
  </section>
</template>
