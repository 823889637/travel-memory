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
const activeDayIndex = ref(0)
const loading = ref(false)
const error = ref('')

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
        dayLabel: `Day${groups.length + 1}`,
        memories: [],
      }
      groupMap.set(dateKey, group)
      groups.push(group)
    }
    groupMap.get(dateKey).memories.push(memory)
  })

  return groups
})

const activeDay = computed(() => dayGroups.value[activeDayIndex.value] || null)

const coverPhotoUrl = computed(() => {
  if (trip.value?.coverPhotoUrl) {
    return trip.value.coverPhotoUrl
  }
  return memories.value.find((memory) => memory.photoUrl)?.photoUrl || ''
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
      />
      <div class="journey-hero-content">
        <p class="journey-kicker">Journey</p>
        <h1>{{ trip?.title || '旅行 Journey' }}</h1>
        <p>{{ trip?.destination || '未填写目的地' }}</p>
        <p>{{ tripDateRange }}</p>
      </div>
    </div>

    <div class="journey-quiet-nav">
      <RouterLink :to="`/trips/${id}`">返回 Timeline</RouterLink>
      <RouterLink :to="`/trips/${id}/map`">查看 Map</RouterLink>
    </div>

    <p v-if="loading">加载中...</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!loading && dayGroups.length === 0" class="card">
      这次旅行还没有记忆，Journey 会在留下记忆后出现。
    </div>

    <template v-if="!loading && activeDay">
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

      <section class="journey-day">
        <div class="journey-day-header">
          <div>
            <p class="journey-day-kicker">{{ activeDay.dayLabel }} · {{ activeDay.date }}</p>
            <p class="muted">{{ activeDay.date }} · {{ activeDay.memories.length }} 段记忆</p>
          </div>
        </div>

        <div class="journey-flow">
          <article v-for="memory in activeDay.memories" :key="memory.id" class="journey-memory">
            <div class="card journey-card">
              <img
                v-if="memory.photoUrl"
                class="journey-photo"
                :src="photoSrc(memory.photoUrl)"
                alt="旅行记忆照片"
              />
              <div v-else class="journey-photo empty">没有照片</div>

              <div class="journey-card-body">
                <p class="journey-content">{{ memory.content || '没有文字记录' }}</p>
                <div class="journey-meta">
                  <span>{{ memory.locationName || '未记录地点' }}</span>
                  <span>{{ formatTime(memory.recordTime) }}</span>
                </div>
              </div>
            </div>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>
