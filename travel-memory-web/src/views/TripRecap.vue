<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { getTimeline } from '../api/memory'
import { getTrip } from '../api/trip'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'
import { buildTripRecap } from '../utils/tripRecap'
import { resolveTripCoverUrl } from '../utils/tripCover'
import TripViewNav from '../components/TripViewNav.vue'
import MobilePageHeader from '../components/MobilePageHeader.vue'

const props = defineProps({ id: { type: String, required: true } })
const trip = ref(null)
const memories = ref([])
const loading = ref(false)
const error = ref('')
const coverFailed = ref(false)

const recap = computed(() => buildTripRecap(trip.value, memories.value))
const coverPhotoUrl = computed(() => coverFailed.value ? '' : resolveTripCoverUrl(trip.value, memories.value))
const dateRange = computed(() => {
  if (!trip.value) return ''
  return `${trip.value.startDate || '未知开始'} - ${trip.value.endDate || '未知结束'}`
})

function formatTime(value) {
  return value ? String(value).slice(11, 16) : '--:--'
}

function formatDate(value) {
  if (!value || value === '未知日期') return value
  const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!match) return value

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))
  const weekday = date.toLocaleDateString('zh-CN', { weekday: 'short', timeZone: 'UTC' })
  return `${month}月${day}日 ${weekday}`
}

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    const [tripData, memoryData] = await Promise.all([getTrip(props.id), getTimeline(props.id)])
    trip.value = tripData
    memories.value = memoryData
    coverFailed.value = false
  } catch (err) {
    error.value = err.message || '旅行回顾暂时没有整理成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

watch(() => props.id, loadPage, { immediate: true })
</script>

<template>
  <section class="recap-page">
    <MobilePageHeader title="旅行回顾" back-to="/trips" />
    <p v-if="loading" class="recap-status">正在把这趟旅行慢慢整理回来...</p>
    <p v-if="error" class="error recap-status">{{ error }}</p>

    <template v-if="!loading && !error && trip">
      <header class="recap-hero">
        <div class="recap-cover">
          <img v-if="coverPhotoUrl" :src="coverPhotoUrl" alt="旅行封面" @error="coverFailed = true" />
          <div v-else class="recap-cover-empty">{{ trip.destination || '这趟旅行' }}</div>
        </div>
        <div class="recap-intro">
          <p class="recap-kicker">旅行回顾</p>
          <div class="recap-title-line">
            <h1>{{ trip.title }}</h1>
            <RouterLink :to="`/trips/${id}/edit`" aria-label="编辑旅行">编辑</RouterLink>
          </div>
          <p class="recap-meta">{{ trip.destination || '未填写目的地' }}</p>
          <p class="recap-meta">{{ dateRange }}<span v-if="recap.durationDays"> · 共 {{ recap.durationDays }} 天</span></p>
          <p v-if="trip.description" class="recap-description">{{ trip.description }}</p>
          <p v-else class="recap-description muted">这趟旅行还没有补充说明。</p>
        </div>
        <section class="recap-summary" aria-label="旅行摘要">
          <h2>旅行摘要</h2>
          <div class="recap-summary-grid">
            <div><span>记忆数量</span><strong>{{ recap.memoryCount }}</strong></div>
            <div><span>照片数量</span><strong>{{ recap.photoCount }}</strong></div>
            <div><span>收藏数量</span><strong>{{ recap.favoriteCount }}</strong></div>
            <div><span>记录天数</span><strong>{{ recap.recordedDayCount }}</strong></div>
            <div><span>有地点的记忆</span><strong>{{ recap.locatedCount }}</strong></div>
            <div><span>出现过的地点</span><strong>{{ recap.placeCount }}</strong></div>
          </div>
        </section>
      </header>

      <TripViewNav :trip-id="id" active="recap" />

      <section v-if="recap.days.length" class="recap-section">
        <div class="recap-section-head">
          <h2>每日回顾</h2>
          <p>沿着每天留下的照片和原话，再走一遍。</p>
        </div>
        <div class="recap-days">
          <article v-for="day in recap.days" :key="day.date" class="recap-day-card">
            <div class="recap-day-heading">
              <strong>{{ formatDate(day.date) }}</strong>
              <span>第 {{ day.dayNumber }} 天</span>
            </div>
            <p class="recap-day-place">{{ day.locations.slice(0, 2).join(' · ') || '这一天没有补充地点' }}</p>
            <MemoryPhotoGallery
              v-if="day.representative?.photoUrl"
              :photos="day.representative.photos"
              :fallback-url="day.representative.photoUrl"
              layout="recap"
              count-label="张照片"
              alt="每日代表照片"
            />
            <div v-else class="recap-day-no-photo">这一天没有照片，记忆仍然保留着。</div>
            <p :class="['recap-day-quote', { muted: !day.representative?.content }]">
              {{ day.representative?.content || '这一刻没有留下文字' }}
            </p>
            <p v-if="day.representative?.companions?.length" class="recap-day-companions">
              和 {{ day.representative.companions.map(item => item.name).join('、') }} 一起
            </p>
            <div class="recap-day-foot">
              <span v-if="day.earliestTime">最早 {{ formatTime(day.earliestTime) }}</span>
              <span v-if="day.latestTime && day.latestTime !== day.earliestTime">最晚 {{ formatTime(day.latestTime) }}</span>
              <span>{{ day.memoryCount }} 段记忆</span>
              <span>{{ day.photoCount }} 张照片</span>
            </div>
          </article>
        </div>
      </section>

      <div v-if="recap.memoryCount === 0" class="recap-empty">
        <h2>这趟旅行还在等待第一段记忆。</h2>
        <p>当照片、原话、时间和地点被留下，这里就会慢慢成为一页真实的旅行回顾。</p>
        <RouterLink :to="`/trips/${id}/memories/new`"><button>留下第一段记忆</button></RouterLink>
      </div>

      <div v-if="recap.memoryCount" class="recap-lower-grid">
        <section class="recap-place-panel">
          <h2>常出现的地点</h2>
          <div v-if="recap.places.length" class="recap-place-list">
            <span v-for="place in recap.places" :key="place.name">{{ place.name }} <small>{{ place.count }} 次</small></span>
          </div>
          <p v-else class="muted">地点还没有补充，之后想起来再写也不迟。</p>
        </section>

        <section class="recap-favorites">
          <h2>收藏的回忆片段</h2>
          <div v-if="recap.favorites.length" class="recap-favorite-grid">
            <article v-for="memory in recap.favorites.slice(0, 6)" :key="memory.id">
              <MemoryPhotoGallery v-if="memory.photoUrl" :photos="memory.photos" :fallback-url="memory.photoUrl" layout="recap" alt="收藏照片" />
              <p>{{ memory.content || '这一刻没有留下文字' }}</p>
              <span>{{ memory.locationName || '地点还没有补充' }} · {{ formatTime(memory.recordTime) }}</span>
            </article>
          </div>
          <p v-else class="muted">还没有特别收藏的片段。</p>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.recap-page { display: grid; gap: 22px; }
.recap-status { margin: 0; color: var(--tm-text-muted); }
.recap-hero { display: grid; grid-template-columns: minmax(260px, .9fr) minmax(280px, 1.05fr) minmax(300px, 1fr); gap: 22px; align-items: stretch; }
.recap-cover { min-height: 235px; overflow: hidden; border-radius: var(--tm-radius-md); background: var(--tm-accent-soft); }
.recap-cover img { width: 100%; height: 100%; min-height: 235px; object-fit: cover; }
.recap-cover-empty { display: grid; height: 100%; min-height: 235px; place-items: center; color: var(--tm-accent); font-size: 22px; font-weight: 800; }
.recap-intro { display: grid; align-content: center; gap: 11px; }
.recap-kicker { margin: 0; color: var(--tm-accent); font-size: 13px; font-weight: 800; }
.recap-title-line { display: flex; align-items: baseline; gap: 14px; }
.recap-title-line h1 { margin: 0; font-family: Georgia, "Microsoft YaHei", serif; font-size: 34px; }
.recap-title-line a { color: var(--tm-accent); font-size: 13px; }
.recap-meta, .recap-description { margin: 0; line-height: 1.65; }
.recap-meta { color: var(--tm-text-muted); }
.recap-description { margin-top: 7px; }
.recap-summary, .recap-place-panel, .recap-favorites { border: 1px solid var(--tm-border); border-radius: var(--tm-radius-md); background: rgba(255, 253, 249, .84); padding: 20px; }
.recap-summary h2, .recap-place-panel h2, .recap-favorites h2 { margin: 0 0 16px; font-size: 17px; }
.recap-summary-grid { display: grid; grid-template-columns: repeat(3, 1fr); }
.recap-summary-grid div { display: grid; gap: 7px; padding: 14px 10px; text-align: center; border-right: 1px solid var(--tm-border); border-bottom: 1px solid var(--tm-border); }
.recap-summary-grid div:nth-child(3n) { border-right: 0; }
.recap-summary-grid div:nth-child(n+4) { border-bottom: 0; }
.recap-summary-grid span { color: var(--tm-text-muted); font-size: 12px; }
.recap-summary-grid strong { font-size: 24px; }
.recap-section { display: grid; gap: 14px; }
.recap-section-head h2, .recap-section-head p { margin: 0; }
.recap-section-head p { margin-top: 5px; color: var(--tm-text-muted); font-size: 14px; }
.recap-days { display: grid; grid-auto-flow: column; grid-auto-columns: minmax(235px, 1fr); gap: 12px; overflow-x: auto; padding: 2px 1px 8px; scroll-padding-inline: 1px; scroll-snap-type: x proximity; }
.recap-day-card { display: grid; grid-template-rows: auto auto auto minmax(48px, auto) auto; gap: 10px; min-width: 0; border: 1px solid var(--tm-border); border-radius: var(--tm-radius-md); background: var(--tm-surface); padding: 13px; box-shadow: 0 8px 20px rgba(63, 49, 38, .045); scroll-snap-align: start; }
.recap-day-heading { display: flex; justify-content: space-between; gap: 8px; align-items: baseline; }
.recap-day-heading span, .recap-day-place, .recap-day-foot { color: var(--tm-text-muted); font-size: 12px; }
.recap-day-place, .recap-day-quote { margin: 0; }
.recap-day-quote { line-height: 1.55; }
.recap-day-companions { margin: -3px 0 0; color: var(--tm-accent); font-size: 12px; }
.recap-day-no-photo { display: grid; min-height: 145px; place-items: center; padding: 18px; background: var(--tm-accent-soft); color: var(--tm-text-muted); text-align: center; }
.recap-day-foot { display: flex; gap: 12px; padding-top: 8px; border-top: 1px solid var(--tm-border); }
.recap-lower-grid { display: grid; grid-template-columns: minmax(250px, .8fr) minmax(0, 1.6fr); gap: 18px; }
.recap-place-list { display: flex; flex-wrap: wrap; gap: 10px; }
.recap-place-list span { padding: 8px 11px; border-radius: 999px; background: var(--tm-accent-soft); }
.recap-place-list small { color: var(--tm-text-muted); }
.recap-favorite-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.recap-favorite-grid article { min-width: 0; }
.recap-favorite-grid p { margin: 8px 0 5px; line-height: 1.5; }
.recap-favorite-grid span { color: var(--tm-text-muted); font-size: 12px; }
.recap-empty { display: grid; justify-items: start; gap: 12px; padding: 34px; border: 1px solid var(--tm-border); background: var(--tm-surface); }
.recap-empty h2, .recap-empty p { margin: 0; }
@media (max-width: 900px) { .recap-hero { grid-template-columns: 1fr 1fr; } .recap-summary { grid-column: 1 / -1; } }
@media (max-width: 680px) {
  .recap-hero, .recap-lower-grid { grid-template-columns: 1fr; }
  .recap-cover, .recap-cover img { min-height: 210px; }
  .recap-title-line h1 { font-size: 28px; }
  .recap-favorite-grid { grid-template-columns: 1fr; }
  .recap-summary-grid { grid-template-columns: repeat(2, 1fr); }
  .recap-page .recap-summary-grid > div { border-right: 1px solid var(--tm-border); border-bottom: 1px solid var(--tm-border); }
  .recap-page .recap-summary-grid > div:nth-child(2n) { border-right: 0; }
  .recap-page .recap-summary-grid > div:nth-last-child(-n + 2) { border-bottom: 0; }
  .recap-days { grid-auto-columns: min(82vw, 300px); scroll-snap-type: x mandatory; }
}
</style>
