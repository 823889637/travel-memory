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

const points = computed(() => memories.value.filter((item) => item.latitude && item.longitude))

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

onMounted(loadPage)
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>{{ trip?.title || '地图展示' }}</h1>
        <p class="muted">基于已保存经纬度的简易地图视图。</p>
      </div>
      <div class="actions">
        <RouterLink :to="`/trips/${id}`">
          <button class="secondary">返回时间线</button>
        </RouterLink>
        <RouterLink :to="`/trips/${id}/memories/new`">
          <button>新增记忆</button>
        </RouterLink>
      </div>
    </div>

    <p v-if="loading">加载中...</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!loading && points.length === 0" class="card">
      暂无带经纬度的记忆点。
    </div>

    <div v-else class="map-stage">
      <template v-for="memory in points" :key="memory.id">
        <span class="map-point" :style="pointStyle(memory)"></span>
        <div class="map-label" :style="pointStyle(memory)">
          <strong>{{ memory.locationName || '未命名地点' }}</strong>
          <div class="muted">{{ memory.recordTime }}</div>
          <div>{{ memory.content || '没有文字记录' }}</div>
        </div>
      </template>
    </div>
  </section>
</template>
