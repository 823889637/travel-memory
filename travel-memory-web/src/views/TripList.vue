<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  BookOpen,
  Bookmark,
  CalendarDays,
  Compass,
  Image as ImageIcon,
  List,
  Luggage,
  Map,
  MapPin,
  Plus,
  Stamp,
} from '@lucide/vue'
import { deleteTrip, favoriteTrip, getTrips } from '../api/trip'
import { resolveTripCoverUrl } from '../utils/tripCover'
import UserMenu from '../components/UserMenu.vue'

const trips = ref([])
const loading = ref(false)
const error = ref('')
const failedCoverIds = ref(new Set())
const favoriteSavingIds = ref(new Set())

function formatDateRange(trip) {
  const start = trip.startDate || '未知日期'
  const end = trip.endDate || '未知日期'
  return `${start} - ${end}`
}

function tripDuration(trip) {
  if (!trip.startDate || !trip.endDate) return ''
  const start = new Date(`${trip.startDate}T00:00:00`)
  const end = new Date(`${trip.endDate}T00:00:00`)
  const days = Math.floor((end - start) / 86400000) + 1
  return Number.isFinite(days) && days > 0 ? `${days} 天 ${Math.max(0, days - 1)} 晚` : ''
}

function tripPlaceLabel(trip) {
  const names = [trip.destinationCountry, trip.destination]
    .map((value) => String(value || '').trim())
    .filter(Boolean)
  return [...new Set(names)].join(' · ') || '目的地还没有补充'
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

async function toggleTripFavorite(trip) {
  if (favoriteSavingIds.value.has(trip.id)) return
  const previous = Boolean(trip.isFavorite)
  trip.isFavorite = !previous
  favoriteSavingIds.value = new Set([...favoriteSavingIds.value, trip.id])
  try {
    const updated = await favoriteTrip(trip.id, !previous)
    trip.isFavorite = Boolean(updated.isFavorite)
  } catch (err) {
    trip.isFavorite = previous
    error.value = err.message || '旅行收藏状态暂时没有更新成功。'
  } finally {
    const next = new Set(favoriteSavingIds.value)
    next.delete(trip.id)
    favoriteSavingIds.value = next
  }
}

function closeTripMenus(event) {
  document.querySelectorAll('.trip-card-more[open]').forEach((menu) => {
    if (!menu.contains(event.target)) menu.removeAttribute('open')
  })
}

onMounted(() => {
  document.addEventListener('pointerdown', closeTripMenus)
  loadTrips()
})
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeTripMenus))
</script>

<template>
  <section class="trip-list-page">
    <div class="trip-list-hero">
      <div class="trip-list-intro">
        <p class="trip-list-kicker">Travel Memory</p>
        <h1>我的旅行记忆</h1>
        <p class="muted">把每一次旅行，留给未来重新经过。</p>
        <RouterLink v-if="trips.length > 0" class="trip-create-button" to="/trips/new">
          <Plus :size="20" aria-hidden="true" />
          新建一次旅行
        </RouterLink>
      </div>
      <div class="trip-list-postmark" aria-hidden="true">
        <Stamp :size="62" :stroke-width="1" />
        <span></span><span></span><span></span>
      </div>
      <UserMenu class="trip-list-user-menu" />
    </div>

    <p v-if="loading" class="trip-list-status">正在找回旅行记忆...</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!loading && !error && trips.length === 0" class="trip-empty trip-continuation trip-continuation-empty">
      <span class="trip-continuation-illustration" aria-hidden="true"><Luggage :size="48" :stroke-width="1.2" /></span>
      <div>
        <h2>第一段旅程，等你写下</h2>
        <p>从一次真实发生过的旅行开始，把照片、原话、时间和地点留给未来。</p>
      </div>
      <RouterLink class="trip-continuation-action" to="/trips/new" aria-label="新建第一次旅行">
        <Plus :size="22" aria-hidden="true" />
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
            <span><MapPin :size="16" aria-hidden="true" />{{ tripPlaceLabel(trip) }}</span>
          </div>
          <span v-if="trip.destination" class="trip-cover-destination"><MapPin :size="13" aria-hidden="true" />{{ tripPlaceLabel(trip) }}</span>
          <span v-if="trip.photoCount" class="trip-cover-photo-count"><ImageIcon :size="13" aria-hidden="true" />{{ trip.photoCount }}</span>
        </div>

        <div class="trip-card-body">
          <div class="trip-card-title-row">
            <h2>{{ trip.title }}</h2>
            <div class="trip-card-title-actions">
              <button
                type="button"
                :class="['trip-bookmark-button', { active: trip.isFavorite }]"
                :disabled="favoriteSavingIds.has(trip.id)"
                :aria-label="trip.isFavorite ? '取消收藏旅行' : '收藏旅行'"
                :title="trip.isFavorite ? '取消收藏旅行' : '收藏旅行'"
                :aria-pressed="Boolean(trip.isFavorite)"
                @click.stop="toggleTripFavorite(trip)"
              >
                <Bookmark :size="19" :fill="trip.isFavorite ? 'currentColor' : 'none'" aria-hidden="true" />
              </button>
              <details class="trip-card-more">
                <summary aria-label="更多旅行操作" title="更多旅行操作" @click.stop></summary>
                <div class="trip-card-more-menu">
                  <RouterLink :to="`/trips/${trip.id}/memories/new`">新增记忆</RouterLink>
                  <RouterLink :to="`/trips/${trip.id}/edit`">编辑旅行</RouterLink>
                  <button class="danger-text" @click="removeTrip(trip.id)">删除旅行</button>
                </div>
              </details>
            </div>
          </div>
          <div class="trip-meta">
            <p class="trip-destination"><MapPin :size="14" aria-hidden="true" />{{ tripPlaceLabel(trip) }}</p>
            <p class="trip-date"><CalendarDays :size="14" aria-hidden="true" />{{ formatDateRange(trip) }}<span v-if="tripDuration(trip)"> · {{ tripDuration(trip) }}</span></p>
          </div>
          <div class="trip-card-summary">
            <p v-if="trip.description" class="trip-description">{{ trip.description }}</p>
          </div>

          <nav class="trip-card-view-links" aria-label="旅行浏览入口">
            <RouterLink class="primary" :to="`/trips/${trip.id}/journey`"><Compass :size="16" aria-hidden="true" />进入 Journey</RouterLink>
            <RouterLink :to="`/trips/${trip.id}`"><List :size="16" aria-hidden="true" />查看 Timeline</RouterLink>
            <RouterLink :to="`/trips/${trip.id}/map`"><Map :size="16" aria-hidden="true" />地图</RouterLink>
            <RouterLink :to="`/trips/${trip.id}/recap`"><BookOpen :size="16" aria-hidden="true" />旅行回顾</RouterLink>
          </nav>
        </div>
      </article>
    </div>

    <RouterLink v-if="trips.length > 0" class="trip-continuation" to="/trips/new">
      <span class="trip-continuation-illustration" aria-hidden="true"><Luggage :size="52" :stroke-width="1.15" /></span>
      <span class="trip-continuation-copy">
        <strong>下一段旅程，正在路上 ✨</strong>
        <small>记录更多美好瞬间，留给未来的自己。</small>
      </span>
      <span class="trip-continuation-action" aria-hidden="true"><Plus :size="23" /></span>
    </RouterLink>
  </section>
</template>
