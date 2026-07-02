<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getTrip } from '../api/trip'
import { deleteMemory, getTimeline } from '../api/memory'

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

const dayGroups = computed(() => {
  const groups = []
  const groupMap = new Map()

  const sortedMemories = [...memories.value].sort((left, right) => {
    const leftTime = new Date(left.recordTime).getTime()
    const rightTime = new Date(right.recordTime).getTime()
    return leftTime - rightTime
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

function photoSrc(url) {
  return url || ''
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
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function removeMemory(id) {
  if (!window.confirm('确定删除这条记忆吗？')) {
    return
  }
  try {
    await deleteMemory(id)
    await loadPage()
  } catch (err) {
    window.alert(err.message || '删除失败')
  }
}

onMounted(loadPage)
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>{{ trip?.title || '旅行时间线' }}</h1>
        <p class="muted">{{ trip?.destination || '' }}</p>
      </div>
      <div class="actions">
        <RouterLink :to="`/trips/${id}/memories/new`">
          <button>新增记忆</button>
        </RouterLink>
        <RouterLink :to="`/trips/${id}/map`">
          <button class="secondary">地图展示</button>
        </RouterLink>
      </div>
    </div>

    <p v-if="loading">加载中...</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!loading && memories.length === 0" class="card">
      这次旅行还没有记忆。
    </div>

    <div class="day-timeline">
      <section v-for="group in dayGroups" :key="group.date" class="day-section">
        <div class="day-header">
          <h2>{{ group.dayLabel }}</h2>
          <span class="muted">{{ group.date }}</span>
        </div>

        <div class="list">
          <article v-for="memory in group.memories" :key="memory.id" class="card memory-item">
            <div class="memory-main">
              <div class="memory-time">{{ formatTime(memory.recordTime) }}</div>
              <div class="memory-body">
                <p class="memory-location">{{ memory.locationName || '未记录地点' }}</p>
                <p>{{ memory.content || '没有文字记录' }}</p>
                <img
                  v-if="memory.photoUrl"
                  class="memory-photo"
                  :src="photoSrc(memory.photoUrl)"
                  alt="旅行记忆照片"
                />
              </div>
            </div>
            <div class="actions">
              <button class="danger" @click="removeMemory(memory.id)">删除</button>
            </div>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>
