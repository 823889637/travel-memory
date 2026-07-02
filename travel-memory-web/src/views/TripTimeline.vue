<script setup>
import { onMounted, ref } from 'vue'
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

function photoSrc(url) {
  return url || ''
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

    <div class="list">
      <article v-for="memory in memories" :key="memory.id" class="card">
        <h3>{{ memory.recordTime }}</h3>
        <p>{{ memory.content || '没有文字记录' }}</p>
        <p class="muted">
          {{ memory.locationName || '未记录地点' }}
          <span v-if="memory.latitude && memory.longitude">
            ｜ {{ memory.latitude }}, {{ memory.longitude }}
          </span>
        </p>
        <img
          v-if="memory.photoUrl"
          class="memory-photo"
          :src="photoSrc(memory.photoUrl)"
          alt="旅行记忆照片"
        />
        <div class="actions">
          <button class="danger" @click="removeMemory(memory.id)">删除</button>
        </div>
      </article>
    </div>
  </section>
</template>
