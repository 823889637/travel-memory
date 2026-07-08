<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { deleteTrip, getTrips } from '../api/trip'

const trips = ref([])
const loading = ref(false)
const error = ref('')

function formatDateRange(trip) {
  const start = trip.startDate || '未知日期'
  const end = trip.endDate || '未知日期'
  return `${start} - ${end}`
}

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
  <section class="trip-list-page">
    <div class="trip-list-hero">
      <div>
        <p class="trip-list-kicker">Travel Memory</p>
        <h1>我的旅行记忆</h1>
        <p class="muted">把每一次旅行，留给未来重新经过。</p>
      </div>
      <RouterLink to="/trips/new">
        <button>新建一次旅行</button>
      </RouterLink>
    </div>

    <p v-if="loading" class="trip-list-status">正在找回旅行记忆...</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!loading && trips.length === 0" class="trip-empty">
      <h2>还没有旅行记忆。</h2>
      <p>新建一次旅行，把第一段路留下来。</p>
      <RouterLink to="/trips/new">
        <button>新建一次旅行</button>
      </RouterLink>
    </div>

    <div v-if="trips.length > 0" class="trip-grid">
      <article v-for="trip in trips" :key="trip.id" class="trip-card">
        <div class="trip-cover">
          <img v-if="trip.coverPhotoUrl" :src="trip.coverPhotoUrl" alt="旅行封面" />
          <div v-else class="trip-cover-empty">
            <span>{{ trip.destination || '一段旅程' }}</span>
          </div>
        </div>

        <div class="trip-card-body">
          <h2>{{ trip.title }}</h2>
          <p class="trip-destination">{{ trip.destination || '未填写目的地' }}</p>
          <p class="trip-date">{{ formatDateRange(trip) }}</p>
          <p v-if="trip.description" class="trip-description">{{ trip.description }}</p>

          <div class="trip-main-actions">
            <RouterLink :to="`/trips/${trip.id}/journey`">
              <button>进入 Journey</button>
            </RouterLink>
            <RouterLink :to="`/trips/${trip.id}`">
              <button class="ghost">查看 Timeline</button>
            </RouterLink>
          </div>

          <div class="trip-soft-actions">
            <RouterLink :to="`/trips/${trip.id}/memories/new`">留下一段记忆</RouterLink>
            <RouterLink :to="`/trips/${trip.id}/map`">地图</RouterLink>
            <button class="danger-text" @click="removeTrip(trip.id)">删除</button>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
