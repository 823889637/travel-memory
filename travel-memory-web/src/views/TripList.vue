<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { deleteTrip, getTrips } from '../api/trip'

const trips = ref([])
const loading = ref(false)
const error = ref('')

async function loadTrips() {
  loading.value = true
  error.value = ''
  try {
    trips.value = await getTrips()
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function removeTrip(id) {
  if (!window.confirm('确定删除这次旅行吗？')) {
    return
  }
  try {
    await deleteTrip(id)
    await loadTrips()
  } catch (err) {
    window.alert(err.message || '删除失败')
  }
}

onMounted(loadTrips)
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>旅行列表</h1>
        <p class="muted">管理你的旅行记忆。</p>
      </div>
      <RouterLink to="/trips/new">
        <button>创建旅行</button>
      </RouterLink>
    </div>

    <p v-if="loading">加载中...</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!loading && trips.length === 0" class="card">
      还没有旅行，先创建一次旅行。
    </div>

    <div class="trip-grid">
      <article v-for="trip in trips" :key="trip.id" class="card trip-card">
        <div class="trip-cover">
          <img v-if="trip.coverPhotoUrl" :src="trip.coverPhotoUrl" alt="旅行封面" />
          <div v-else class="trip-cover-empty">暂无封面</div>
        </div>

        <div class="trip-card-body">
          <h2>{{ trip.title }}</h2>
          <p class="muted">
            {{ trip.destination || '未填写目的地' }}
          </p>
          <p class="muted">
            {{ trip.startDate || '未知' }} - {{ trip.endDate || '未知' }}
          </p>
          <p class="muted">{{ trip.memoryCount || 0 }} 条记忆</p>
          <p v-if="trip.description">{{ trip.description }}</p>
          <div class="actions">
            <RouterLink :to="`/trips/${trip.id}`">
              <button>查看时间线</button>
            </RouterLink>
            <RouterLink :to="`/trips/${trip.id}/memories/new`">
              <button class="secondary">新增记忆</button>
            </RouterLink>
            <RouterLink :to="`/trips/${trip.id}/map`">
              <button class="secondary">地图</button>
            </RouterLink>
            <button class="danger" @click="removeTrip(trip.id)">删除</button>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
