<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { Bookmark, BookOpenText, CalendarDays, Clock3, Images, MapPin, MapPinned, Pencil } from '@lucide/vue'
import { getTripRecap } from '../api/trip'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'
import { resolveTripCoverUrl } from '../utils/tripCover'
import TripViewNav from '../components/TripViewNav.vue'
import MobilePageHeader from '../components/MobilePageHeader.vue'

const props = defineProps({ id: { type: String, required: true } })
const trip = ref(null)
const memories = ref([])
const recapData = ref(null)
const loading = ref(false)
const error = ref('')
const coverFailed = ref(false)

const recap = computed(() => {
  const data = recapData.value
  if (!data) return {
    durationDays: 0, memoryCount: 0, photoCount: 0, favoriteCount: 0,
    recordedDayCount: 0, locatedCount: 0, placeCount: 0, days: [], places: [], favorites: [],
  }
  return {
    durationDays: data.tripDays,
    memoryCount: data.memoryCount,
    photoCount: data.photoCount,
    favoriteCount: data.favoriteCount,
    recordedDayCount: data.days?.length || 0,
    locatedCount: data.locatedMemoryCount,
    placeCount: data.locationCount,
    days: (data.days || []).map(day => ({
      ...day,
      locations: day.locationSummary ? day.locationSummary.split(' · ') : [],
    })),
    places: (data.topLocations || []).map(place => ({ name: place.name, count: place.memoryCount })),
    favorites: data.favoriteMemories || [],
  }
})
const coverPhotoUrl = computed(() => coverFailed.value ? '' : resolveTripCoverUrl(trip.value, memories.value))
const dateRange = computed(() => {
  if (!trip.value) return ''
  const start = formatTripDate(trip.value.startDate)
  const end = formatTripDate(trip.value.endDate)
  if (!start && !end) return '日期待补充'
  if (!start) return end
  if (!end || end === start) return start
  return `${start} – ${end}`
})
const tripPlaceLabel = computed(() => {
  const country = String(trip.value?.destinationCountry || '').trim()
  const destination = String(trip.value?.destination || '').trim()
  return [...new Set([country, destination].filter(Boolean))].join(' · ') || '目的地待补充'
})

function formatTripDate(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? `${match[1]}.${match[2]}.${match[3]}` : ''
}

function formatTime(value) {
  if (!value) return '--:--'
  const raw = String(value)
  return raw.includes('T') || raw.includes(' ') ? raw.slice(11, 16) : raw.slice(0, 5)
}

function formatShortDate(value) {
  const match = String(value || '').match(/^\d{4}-(\d{2})-(\d{2})/)
  return match ? `${match[1]}.${match[2]}` : '日期待补充'
}

function formatRecordDate(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})[T\s](\d{2}):(\d{2})/)
  return match ? `${match[1]}.${match[2]}.${match[3]} ${match[4]}:${match[5]}` : '时间待补充'
}

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    const data = await getTripRecap(props.id)
    recapData.value = data
    trip.value = data.trip
    memories.value = [
      ...(data.days || []).map((day) => day.representative).filter(Boolean),
      ...(data.favoriteMemories || []),
    ]
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
          <div class="recap-title-line">
            <h1>{{ trip.title }}</h1>
            <RouterLink :to="`/trips/${id}/edit`" class="recap-edit" aria-label="编辑旅行">
              <Pencil :size="15" aria-hidden="true" />编辑
            </RouterLink>
          </div>
          <p class="recap-meta"><MapPin :size="16" aria-hidden="true" />{{ tripPlaceLabel }}</p>
          <p class="recap-meta">
            <CalendarDays :size="16" aria-hidden="true" />{{ dateRange }}
            <span v-if="recap.durationDays"> · {{ recap.durationDays }} 天</span>
          </p>
        </div>
      </header>

      <TripViewNav :trip-id="id" active="recap" />

      <section class="recap-overview recap-section" aria-labelledby="recap-overview-title">
        <h2 id="recap-overview-title">这趟旅行</h2>
        <div class="recap-summary-grid">
          <div><BookOpenText :size="22" aria-hidden="true" /><strong>{{ recap.memoryCount }}</strong><span>记忆数量</span></div>
          <div><Images :size="22" aria-hidden="true" /><strong>{{ recap.photoCount }}</strong><span>照片数量</span></div>
          <div><Bookmark :size="22" aria-hidden="true" /><strong>{{ recap.favoriteCount }}</strong><span>收藏数量</span></div>
          <div><MapPinned :size="22" aria-hidden="true" /><strong>{{ recap.locatedCount }}</strong><span>有地点记忆</span></div>
        </div>
      </section>

      <section v-if="recap.days.length" class="recap-section">
        <div class="recap-section-head">
          <h2>每日回顾</h2>
        </div>
        <div class="recap-days">
          <RouterLink
            v-for="day in recap.days"
            :key="day.date"
            :to="`/trips/${id}/memories/${day.representative.id}`"
            class="recap-day-card"
            :aria-label="`查看第 ${day.dayNumber} 天的代表记忆`"
          >
            <div class="recap-day-heading">
              <strong><MapPin :size="14" aria-hidden="true" />第 {{ day.dayNumber }} 天 · {{ formatShortDate(day.date) }}</strong>
            </div>
            <MemoryPhotoGallery
              v-if="day.representative?.photoUrl"
              :photos="day.representative.photos"
              :fallback-url="day.representative.photoUrl"
              layout="recap"
              count-label="张照片"
              alt="每日代表照片"
            />
            <div v-else class="recap-day-no-photo">这一天没有照片，记忆仍然保留着。</div>
            <div class="recap-day-foot">
              <span><Clock3 :size="13" aria-hidden="true" />最早 {{ formatTime(day.earliestTime) }}<template v-if="day.latestTime"> · 最晚 {{ formatTime(day.latestTime) }}</template></span>
              <span>{{ day.memoryCount }} 段记忆</span>
            </div>
          </RouterLink>
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
            <span v-for="place in recap.places" :key="place.name">
              <MapPin :size="14" aria-hidden="true" /><strong>{{ place.name }}</strong><small>{{ place.count }} 段记忆</small>
            </span>
            <span class="recap-place-total"><strong>全部地点</strong><small>{{ recap.placeCount }} 个</small></span>
          </div>
          <p v-else class="muted">地点还没有补充，之后想起来再写也不迟。</p>
        </section>

        <section class="recap-favorites">
          <div class="recap-panel-heading">
            <h2>被你收藏的瞬间</h2>
            <RouterLink v-if="recap.favorites.length > 1" :to="`/trips/${id}?favorite=true`">查看全部</RouterLink>
          </div>
          <div v-if="recap.favorites.length" class="recap-favorite-list">
            <article v-for="memory in recap.favorites.slice(0, 1)" :key="memory.id" class="recap-favorite-card">
              <MemoryPhotoGallery v-if="memory.photoUrl" :photos="memory.photos" :fallback-url="memory.photoUrl" layout="favorite" count-label="张" alt="收藏照片" />
              <div class="recap-favorite-copy">
                <p class="recap-favorite-location"><MapPin :size="14" aria-hidden="true" />{{ memory.locationName || '地点还没有补充' }}</p>
                <RouterLink :to="`/trips/${id}/memories/${memory.id}`">{{ memory.content || '这一刻没有留下文字' }}</RouterLink>
                <span><CalendarDays :size="14" aria-hidden="true" />{{ formatRecordDate(memory.recordTime) }}</span>
              </div>
              <Bookmark class="recap-favorite-mark" :size="20" aria-label="已收藏" />
            </article>
          </div>
          <p v-else class="muted">还没有特别收藏的片段。</p>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.recap-page {
  display: grid;
  gap: 24px;
  padding-bottom: calc(32px + env(safe-area-inset-bottom));
}

.recap-status { margin: 0; color: var(--tm-text-muted); }

.recap-hero {
  position: relative;
  min-height: 300px;
  overflow: hidden;
  border-radius: 24px;
  background: var(--tm-accent-soft);
  box-shadow: 0 14px 34px rgba(57, 42, 31, .12);
}

.recap-cover,
.recap-cover img,
.recap-cover-empty {
  width: 100%;
  height: 100%;
  min-height: 300px;
}

.recap-cover {
  position: absolute;
  inset: 0;
}

.recap-cover img { display: block; object-fit: cover; }

.recap-cover-empty {
  display: grid;
  place-items: center;
  color: var(--tm-accent-strong);
  font-family: var(--tm-font-serif);
  font-size: 28px;
  background: linear-gradient(145deg, #efe4d6, #d9baa0);
}

.recap-intro {
  position: absolute;
  z-index: 1;
  inset: 0;
  display: grid;
  align-content: end;
  gap: 7px;
  padding: 26px;
  color: #fff;
  background: linear-gradient(180deg, rgba(28, 21, 17, .04) 20%, rgba(28, 21, 17, .78) 100%);
}

.recap-title-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.recap-title-line h1 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  font-family: var(--tm-font-serif);
  font-size: 36px;
  font-weight: 600;
  line-height: 44px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recap-edit {
  display: inline-flex;
  flex: 0 0 auto;
  min-height: 38px;
  align-items: center;
  gap: 5px;
  padding: 8px 13px;
  border-radius: 999px;
  color: #4a3427;
  background: rgba(255, 253, 249, .9);
  font-family: var(--tm-font-sans);
  font-size: 13px;
  font-weight: 500;
}

.recap-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  color: rgba(255, 255, 255, .9);
  font-size: 14px;
  line-height: 21px;
}

.recap-section { display: grid; gap: 14px; }

.recap-section > h2,
.recap-section-head h2,
.recap-place-panel h2,
.recap-favorites h2 {
  margin: 0;
  font-family: var(--tm-font-serif);
  font-size: 22px;
  font-weight: 600;
  line-height: 30px;
}

.recap-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.recap-summary-grid > div {
  display: grid;
  min-width: 0;
  min-height: 112px;
  grid-template-columns: auto 1fr;
  align-content: center;
  align-items: center;
  gap: 4px 10px;
  padding: 16px;
  border: 1px solid var(--tm-border);
  border-radius: 16px;
  background: rgba(255, 253, 249, .84);
  box-shadow: 0 7px 20px rgba(63, 49, 38, .05);
}

.recap-summary-grid svg { color: var(--tm-accent-strong); }
.recap-summary-grid strong { font-size: 26px; font-weight: 600; line-height: 32px; font-variant-numeric: tabular-nums; }
.recap-summary-grid span { grid-column: 1 / -1; color: var(--tm-text-muted); font-size: 12px; line-height: 18px; }

.recap-days {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(240px, 1fr);
  gap: 12px;
  overflow-x: auto;
  padding: 2px 1px 8px;
  scroll-padding-inline: 1px;
  scroll-snap-type: x proximity;
  scrollbar-width: none;
}

.recap-days::-webkit-scrollbar { display: none; }

.recap-day-card {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--tm-border);
  border-radius: 16px;
  background: var(--tm-surface);
  box-shadow: 0 8px 22px rgba(63, 49, 38, .055);
  scroll-snap-align: start;
  color: inherit;
  text-decoration: none;
}

.recap-day-heading strong {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
}

.recap-day-heading svg { color: var(--tm-text-muted); }

.recap-day-card :deep(.gallery-layout-recap .gallery-main) {
  height: 145px;
  border-radius: 10px;
}

.recap-day-no-photo {
  display: grid;
  min-height: 145px;
  place-items: center;
  padding: 16px;
  border-radius: 10px;
  color: var(--tm-text-muted);
  background: var(--tm-accent-soft);
  font-size: 12px;
  text-align: center;
}

.recap-day-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--tm-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.recap-day-foot span:first-child { display: inline-flex; align-items: center; gap: 5px; }

.recap-lower-grid { display: grid; gap: 30px; }

.recap-place-panel,
.recap-favorites {
  display: grid;
  gap: 14px;
  padding: 0;
  border: 0;
  background: transparent;
}

.recap-place-list { display: flex; flex-wrap: wrap; gap: 9px; }

.recap-place-list > span {
  display: grid;
  grid-template-columns: auto auto;
  align-items: center;
  gap: 1px 6px;
  padding: 9px 12px;
  border: 1px solid var(--tm-border);
  border-radius: 14px;
  background: rgba(255, 253, 249, .72);
}

.recap-place-list svg { color: var(--tm-text-muted); }
.recap-place-list strong { font-size: 13px; font-weight: 500; line-height: 19px; }
.recap-place-list small { grid-column: 2; color: var(--tm-text-muted); font-size: 11px; line-height: 16px; }
.recap-place-total { grid-template-columns: auto !important; }
.recap-place-total small { grid-column: 1; }

.recap-panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.recap-panel-heading a { color: var(--tm-text-muted); font-size: 13px; }

.recap-favorite-card {
  position: relative;
  display: grid;
  grid-template-columns: minmax(150px, 30%) minmax(0, 1fr) auto;
  gap: 16px;
  align-items: stretch;
  padding: 12px;
  border: 1px solid var(--tm-border);
  border-radius: 16px;
  background: rgba(255, 253, 249, .84);
  box-shadow: 0 8px 24px rgba(63, 49, 38, .055);
}

.recap-favorite-card :deep(.gallery-favorite-preview) { height: 132px; border-radius: 10px; }

.recap-favorite-copy {
  display: grid;
  min-width: 0;
  align-content: center;
  gap: 9px;
}

.recap-favorite-copy p,
.recap-favorite-copy a,
.recap-favorite-copy span { margin: 0; }

.recap-favorite-location,
.recap-favorite-copy span {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--tm-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.recap-favorite-copy > a {
  overflow: hidden;
  color: var(--tm-text);
  font-family: var(--tm-font-serif);
  font-size: 16px;
  line-height: 25px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.recap-favorite-mark { align-self: end; color: var(--tm-accent-strong); fill: rgba(184, 92, 55, .12); }

.recap-empty {
  display: grid;
  justify-items: start;
  gap: 12px;
  padding: 28px;
  border: 1px solid var(--tm-border);
  border-radius: 16px;
  background: var(--tm-surface);
}

.recap-empty h2,
.recap-empty p { margin: 0; }

@media (min-width: 1000px) {
  .recap-hero { min-height: 340px; }
  .recap-cover,
  .recap-cover img,
  .recap-cover-empty { min-height: 340px; }
  .recap-days { grid-auto-columns: minmax(250px, calc((100% - 36px) / 4)); }
}

@media (max-width: 680px) {
  .recap-page { gap: 20px; }
  .recap-hero { min-height: 220px; border-radius: 20px; }
  .recap-cover,
  .recap-cover img,
  .recap-cover-empty { min-height: 220px; }
  .recap-intro { gap: 5px; padding: 19px 16px 15px; }
  .recap-title-line h1 { font-size: 30px; line-height: 38px; }
  .recap-edit { min-height: 36px; padding: 7px 11px; }
  .recap-meta { font-size: 13px; line-height: 19px; }
  .recap-section > h2,
  .recap-section-head h2,
  .recap-place-panel h2,
  .recap-favorites h2 { font-size: 21px; line-height: 29px; }
  .recap-summary-grid { gap: 8px; }
  .recap-summary-grid > div {
    min-height: 94px;
    grid-template-columns: 1fr;
    justify-items: center;
    gap: 2px;
    padding: 11px 4px;
    text-align: center;
  }
  .recap-summary-grid span { grid-column: 1; font-size: 11px; }
  .recap-summary-grid strong { font-size: 23px; line-height: 29px; }
  .recap-days { grid-auto-columns: minmax(168px, calc((100% - 10px) / 2)); gap: 10px; scroll-snap-type: x mandatory; }
  .recap-day-card { gap: 8px; padding: 10px; }
  .recap-day-heading strong { font-size: 12px; line-height: 18px; }
  .recap-day-card :deep(.gallery-layout-recap .gallery-main),
  .recap-day-no-photo { height: 104px; min-height: 104px; }
  .recap-day-foot { display: grid; gap: 2px; font-size: 11px; line-height: 16px; }
  .recap-day-foot > span:last-child { justify-self: end; }
  .recap-lower-grid { gap: 26px; }
  .recap-place-list { flex-wrap: nowrap; overflow-x: auto; padding-bottom: 4px; scrollbar-width: none; }
  .recap-place-list::-webkit-scrollbar { display: none; }
  .recap-place-list > span { flex: 0 0 auto; }
  .recap-favorite-card { grid-template-columns: 112px minmax(0, 1fr) 20px; gap: 12px; padding: 10px; }
  .recap-favorite-card :deep(.gallery-favorite-preview) { height: 104px; }
  .recap-favorite-copy { gap: 6px; }
  .recap-favorite-copy > a { font-size: 14px; line-height: 22px; }
  .recap-favorite-location,
  .recap-favorite-copy span { font-size: 11px; line-height: 16px; }
}

@media (max-width: 370px) {
  .recap-summary-grid { grid-template-columns: repeat(2, 1fr); }
  .recap-days { grid-auto-columns: min(78vw, 275px); }
  .recap-day-card :deep(.gallery-layout-recap .gallery-main),
  .recap-day-no-photo { height: 132px; min-height: 132px; }
}
</style>
