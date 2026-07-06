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
const selectedPhotoByStop = ref({})
const photoOrientations = ref({})

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
    const photos = stop.memories.filter((memory) => memory.photoUrl)
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

function stopLabel(index) {
  const labels = ['第一站', '第二站', '第三站', '第四站', '第五站', '第六站', '第七站', '第八站', '第九站', '第十站']
  return labels[index] || `第${index + 1}站`
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

function stopTimeText(stop) {
  return `${formatTimeRange(stop.startTime, stop.endTime)} · ${stop.memories.length} 段记忆`
}

function photoSrc(url) {
  return url || ''
}

function photoKey(photo) {
  return photo?.id || photo?.photoUrl || ''
}

function stopKey(stop) {
  return `${stop.order}-${stop.locationName}`
}

function selectedPhoto(stop) {
  const selectedKey = selectedPhotoByStop.value[stopKey(stop)]
  return stop.photos.find((photo) => photoKey(photo) === selectedKey) || stop.photos[0]
}

function selectStopPhoto(stop, photo) {
  selectedPhotoByStop.value = {
    ...selectedPhotoByStop.value,
    [stopKey(stop)]: photoKey(photo),
  }
}

function photoOrientationClass(photo) {
  const orientation = photoOrientations.value[photoKey(photo)] || 'landscape'
  return `is-${orientation}`
}

function handlePhotoLoad(photo, event) {
  const image = event.target
  const width = image.naturalWidth
  const height = image.naturalHeight
  if (!width || !height) {
    return
  }
  const ratio = width / height
  let orientation = 'square'
  if (ratio >= 1.15) {
    orientation = 'landscape'
  } else if (ratio <= 0.85) {
    orientation = 'portrait'
  }
  photoOrientations.value = {
    ...photoOrientations.value,
    [photoKey(photo)]: orientation,
  }
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
            <p class="muted">这一天经过了 {{ activeDay.stops.length }} 个地方，留下了 {{ activeDay.memories.length }} 段记忆</p>
            <p class="journey-day-start-note">{{ activeDay.dayLabel }} 开始</p>
          </div>
        </div>

        <div class="journey-flow">
          <article v-for="(stop, index) in activeDay.stops" :key="`${stop.order}-${stop.locationName}`" class="journey-stop">
            <div v-if="index > 0" class="journey-step-connector" aria-hidden="true">继续往前走</div>

            <div class="journey-stop-card">
                <div class="journey-stop-header">
                  <span class="journey-stop-order">{{ stopLabel(index) }}</span>
                  <div class="journey-stop-title-block">
                    <h3>{{ stop.locationName }}</h3>
                    <p>{{ stopTimeText(stop) }}</p>
                  </div>
                </div>

                <div v-if="stop.photos.length > 0" class="journey-stop-photos">
                  <div
                    :class="['journey-stop-main-photo-frame', photoOrientationClass(selectedPhoto(stop))]"
                    :style="{ '--journey-photo-bg': `url(${photoSrc(selectedPhoto(stop).photoUrl)})` }"
                  >
                  <img
                    :class="['journey-stop-main-photo', photoOrientationClass(selectedPhoto(stop))]"
                    :src="photoSrc(selectedPhoto(stop).photoUrl)"
                    @load="handlePhotoLoad(selectedPhoto(stop), $event)"
                    :alt="`${stop.locationName} 旅行记忆照片`"
                  />
                  </div>
                  <div v-if="stop.photos.length > 1" class="journey-stop-thumbs">
                    <img
                      v-for="photo in stop.photos"
                      :key="photo.id"
                      :class="[
                        'journey-stop-thumb',
                        photoKey(selectedPhoto(stop)) === photoKey(photo) ? 'active' : '',
                        photoOrientationClass(photo),
                      ]"
                      :src="photoSrc(photo.photoUrl)"
                      @click="selectStopPhoto(stop, photo)"
                      @load="handlePhotoLoad(photo, $event)"
                      :alt="`${stop.locationName} 旅行记忆照片`"
                    />
                  </div>
                </div>
                <div v-else class="journey-stop-photo-empty">这一站没有照片，但保留了当时留下的文字。</div>

                <div class="journey-stop-contents">
                  <p v-for="memory in stop.contents" :key="memory.id" class="journey-content">
                    “{{ memory.content }}”
                  </p>
                  <p v-if="stop.contents.length === 0" class="journey-content muted">“没有文字记录”</p>
                </div>
            </div>
          </article>

          <div class="journey-day-end">
            <span></span>
            <div>
              <strong>{{ activeDay.dayLabel }} 结束</strong>
              <p>这一天的 {{ activeDay.memories.length }} 段记忆，已经被重新串联起来。</p>
            </div>
          </div>
        </div>
      </section>
    </template>
  </section>
</template>
