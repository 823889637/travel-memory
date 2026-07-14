<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { deleteTrip, getTrips } from '../api/trip'
import { resolveTripCoverUrl } from '../utils/tripCover'

const trips = ref([])
const loading = ref(false)
const error = ref('')
const failedCoverIds = ref(new Set())

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
    failedCoverIds.value = new Set()
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function coverPhotoUrl(trip) {
  return failedCoverIds.value.has(trip.id) ? '' : resolveTripCoverUrl(trip)
}

function handleCoverError(tripId) {
  failedCoverIds.value = new Set([...failedCoverIds.value, tripId])
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
        <h1>我的旅行</h1>
        <p class="muted">把每一次旅行，留给未来重新经过。</p>
      </div>
      <RouterLink v-if="trips.length > 0" to="/trips/new">
        <button class="trip-create-button"><span aria-hidden="true">+</span> 新建旅行</button>
      </RouterLink>
    </div>

    <p v-if="loading" class="trip-list-status">正在找回旅行记忆...</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!loading && !error && trips.length === 0" class="trip-empty">
      <h2>还没有旅行记忆。</h2>
      <p>新建一次旅行，把第一段路留下来。</p>
      <RouterLink to="/trips/new">
        <button>新建旅行</button>
      </RouterLink>
    </div>

    <div v-if="trips.length > 0" class="trip-grid">
      <article v-for="trip in trips" :key="trip.id" class="trip-card">
        <div class="trip-cover">
          <img
            v-if="coverPhotoUrl(trip)"
            :src="coverPhotoUrl(trip)"
            :alt="`${trip.title}旅行封面`"
            @error="handleCoverError(trip.id)"
          />
          <div v-else class="trip-cover-empty">
            <span>{{ trip.destination || '一段旅程' }}</span>
          </div>
          <span v-if="trip.destination" class="trip-cover-destination">{{ trip.destination }}</span>
        </div>

        <div class="trip-card-body">
          <div class="trip-card-title-row">
            <h2>{{ trip.title }}</h2>
          </div>
          <div class="trip-meta">
            <p class="trip-destination">{{ trip.destination || '目的地还没有补充' }}</p>
            <p class="trip-date">{{ formatDateRange(trip) }}</p>
          </div>
          <div class="trip-card-summary">
            <p v-if="trip.description" class="trip-description">{{ trip.description }}</p>
            <div class="trip-stats" aria-label="旅行统计">
              <span>{{ trip.memoryCount || 0 }} 段记忆</span>
              <span>{{ trip.photoCount || 0 }} 张照片</span>
              <span>{{ trip.locationCount || 0 }} 个地点</span>
            </div>
          </div>

          <div class="trip-main-actions">
            <RouterLink :to="`/trips/${trip.id}`">
              <button>进入旅行</button>
            </RouterLink>
            <RouterLink :to="`/trips/${trip.id}/journey`">
              <button class="ghost">旅程回放</button>
            </RouterLink>
            <details class="trip-card-more">
              <summary aria-label="更多旅行操作" title="更多旅行操作">更多</summary>
              <div class="trip-card-more-menu">
                <RouterLink :to="`/trips/${trip.id}/memories/new`">新增记忆</RouterLink>
                <RouterLink :to="`/trips/${trip.id}/edit`">编辑旅行</RouterLink>
                <button class="danger-text" @click="removeTrip(trip.id)">删除旅行</button>
              </div>
            </details>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
