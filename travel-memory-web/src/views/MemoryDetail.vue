<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import {
  ArrowLeft,
  CalendarDays,
  ChevronRight,
  Ellipsis,
  Heart,
  MapPin,
  Pencil,
  Trash2,
} from '@lucide/vue'
import { deleteMemory, favoriteMemory, getMemory, getTimeline } from '../api/memory'
import { getTrip } from '../api/trip'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'
import { getChronologicalTripDayNumber } from '../utils/tripDay'

const props = defineProps({
  tripId: { type: String, required: true },
  memoryId: { type: String, required: true },
})

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const trip = ref(null)
const memory = ref(null)
const timeline = ref([])
const favoriteSaving = ref(false)
const removing = ref(false)
const menuOpen = ref(false)
const menuAnchor = ref('top')

const orderedMemories = computed(() => [...timeline.value].sort((left, right) => {
  const timeDiff = timeValue(left.recordTime) - timeValue(right.recordTime)
  const createDiff = timeValue(left.createTime) - timeValue(right.createTime)
  return timeDiff || createDiff || Number(left.id || 0) - Number(right.id || 0)
}))
const memoryIndex = computed(() => orderedMemories.value.findIndex(item => String(item.id) === String(memory.value?.id)))
const previousMemory = computed(() => memoryIndex.value > 0 ? orderedMemories.value[memoryIndex.value - 1] : null)
const nextMemory = computed(() => (
  memoryIndex.value >= 0 && memoryIndex.value < orderedMemories.value.length - 1
    ? orderedMemories.value[memoryIndex.value + 1]
    : null
))
const showNeighborNavigation = computed(() => orderedMemories.value.length > 1)
const photos = computed(() => normalizePhotos(memory.value))
const hasCoordinates = computed(() => (
  memory.value?.latitude != null
  && memory.value?.longitude != null
  && Number.isFinite(Number(memory.value.latitude))
  && Number.isFinite(Number(memory.value.longitude))
))
const dayLabel = computed(() => {
  if (!memory.value?.recordTime) return '记录日待补充'
  const day = getChronologicalTripDayNumber(trip.value?.startDate, dateKey(memory.value.recordTime), 1)
  return `第 ${day} 天`
})
const recordTimeLabel = computed(() => formatRecordTime(memory.value?.recordTime))
const detailTitle = computed(() => {
  const locationName = memory.value?.locationName?.trim()
  if (locationName) return locationName
  if (memory.value?.content?.trim()) return ''
  return fallbackMemoryTitle(memory.value?.recordTime)
})
const tripDateLabel = computed(() => formatDateRange(trip.value?.startDate, trip.value?.endDate))
const tripCoverUrl = computed(() => trip.value?.coverPhotoUrl || '')

function normalizePhotos(item) {
  if (item?.photos?.length) return item.photos
  return item?.photoUrl ? [{ photoUrl: item.photoUrl, sortOrder: 0 }] : []
}

function firstPhotoUrl(item) {
  return normalizePhotos(item)
    .slice()
    .sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0))[0]?.photoUrl || ''
}

function timeValue(value) {
  if (!value) return Number.MAX_SAFE_INTEGER
  const parsed = new Date(value).getTime()
  return Number.isNaN(parsed) ? Number.MAX_SAFE_INTEGER : parsed
}

function dateKey(value) {
  return value ? String(value).slice(0, 10) : ''
}

function parseDateParts(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) } : null
}

function formatRecordTime(value) {
  if (!value) return '时间还没有补充'
  const parts = parseDateParts(value)
  if (!parts) return String(value)
  const time = String(value).length >= 16 ? String(value).slice(11, 16) : ''
  return `${parts.year}年${parts.month}月${parts.day}日${time ? ` ${time}` : ''}`
}

function formatCompactTime(value) {
  if (!value) return '时间待补充'
  const raw = String(value)
  const date = raw.slice(0, 10).replaceAll('-', '.')
  const time = raw.length >= 16 ? raw.slice(11, 16) : ''
  return [date, time].filter(Boolean).join(' · ')
}

function formatDateRange(startDate, endDate) {
  const values = [startDate, endDate].filter(Boolean).map(value => String(value).replaceAll('-', '.'))
  return values.join(' — ')
}

function fallbackMemoryTitle(value) {
  const parts = parseDateParts(value)
  return parts ? `${parts.year}年${parts.month}月${parts.day}日的记忆` : '这段记忆'
}

function memoryLabel(item) {
  return item?.locationName?.trim() || item?.content?.trim() || fallbackMemoryTitle(item?.recordTime)
}

function companionInitial(companion) {
  return String(companion?.name || '?').trim().slice(0, 1) || '?'
}

function goBack() {
  if (window.history.state?.back) router.back()
  else router.push(`/trips/${props.tripId}`)
}

function toggleMenu(anchor) {
  if (menuOpen.value && menuAnchor.value === anchor) {
    closeMenu()
    return
  }
  menuAnchor.value = anchor
  menuOpen.value = true
}

function closeMenu() {
  menuOpen.value = false
}

function handleKeydown(event) {
  if (event.key === 'Escape') closeMenu()
}

async function loadPage() {
  loading.value = true
  loadError.value = ''
  actionError.value = ''
  closeMenu()
  try {
    const [tripData, memoryData, timelineData] = await Promise.all([
      getTrip(props.tripId),
      getMemory(props.memoryId),
      getTimeline(props.tripId),
    ])
    if (String(memoryData.tripId) !== String(props.tripId)) throw new Error('这段记忆不存在或不属于当前旅行。')
    trip.value = tripData
    memory.value = memoryData
    timeline.value = Array.isArray(timelineData) ? timelineData : []
  } catch (error) {
    loadError.value = error.message || '这段记忆暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (!memory.value || favoriteSaving.value) return
  actionError.value = ''
  favoriteSaving.value = true
  try {
    const nextFavorite = memory.value.isFavorite !== 1
    await favoriteMemory(memory.value.id, nextFavorite)
    memory.value.isFavorite = nextFavorite ? 1 : 0
    const timelineMemory = timeline.value.find(item => String(item.id) === String(memory.value.id))
    if (timelineMemory) timelineMemory.isFavorite = memory.value.isFavorite
  } catch (error) {
    actionError.value = error.message || '收藏状态暂时没有更新成功，请重试。'
  } finally {
    favoriteSaving.value = false
  }
}

async function removeMemory() {
  if (!memory.value || removing.value) return
  if (!window.confirm('确定要删除这段记忆吗？删除后无法恢复。')) return
  const target = nextMemory.value || previousMemory.value
  actionError.value = ''
  removing.value = true
  try {
    await deleteMemory(memory.value.id)
    if (target) router.replace(`/trips/${props.tripId}/memories/${target.id}`)
    else router.replace(`/trips/${props.tripId}`)
  } catch (error) {
    actionError.value = error.message || '删除失败，请稍后再试。'
  } finally {
    removing.value = false
    closeMenu()
  }
}

watch(() => [props.tripId, props.memoryId], loadPage, { immediate: true })
onMounted(() => document.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <section class="memory-detail-page" @click="closeMenu">
    <p v-if="loading" class="memory-detail-status" role="status">正在取回这段记忆…</p>
    <div v-else-if="loadError" class="memory-detail-status memory-detail-load-error">
      <p role="alert">{{ loadError }}</p>
      <button type="button" @click="loadPage">重新加载</button>
    </div>

    <template v-else-if="memory && trip">
      <header class="memory-detail-topbar">
        <button type="button" class="memory-detail-topbar-button" aria-label="返回上一页" @click="goBack">
          <ArrowLeft :size="21" aria-hidden="true" />
        </button>
        <span class="memory-detail-topbar-title">记忆详情</span>
        <div class="memory-detail-topbar-actions">
          <button
            type="button"
            :class="['memory-detail-topbar-button', { active: memory.isFavorite === 1 }]"
            :disabled="favoriteSaving"
            :aria-label="memory.isFavorite === 1 ? '取消收藏' : '收藏这段记忆'"
            :aria-pressed="memory.isFavorite === 1"
            @click.stop="toggleFavorite"
          >
            <Heart :size="20" :fill="memory.isFavorite === 1 ? 'currentColor' : 'none'" aria-hidden="true" />
          </button>
          <div class="memory-detail-more" @click.stop>
            <button type="button" class="memory-detail-topbar-button" aria-label="更多操作" :aria-expanded="menuOpen && menuAnchor === 'top'" @click="toggleMenu('top')">
              <Ellipsis :size="21" aria-hidden="true" />
            </button>
            <div v-if="menuOpen && menuAnchor === 'top'" class="memory-detail-menu" role="menu">
              <RouterLink role="menuitem" :to="`/trips/${tripId}/memories/${memory.id}/edit`" @click="closeMenu">
                <Pencil :size="15" aria-hidden="true" />编辑记忆
              </RouterLink>
              <button type="button" role="menuitem" class="danger" :disabled="removing" @click="removeMemory">
                <Trash2 :size="15" aria-hidden="true" />{{ removing ? '删除中…' : '删除记忆' }}
              </button>
            </div>
          </div>
        </div>
      </header>

      <article :class="['memory-detail-content', { 'text-only': !photos.length }]">
        <section v-if="photos.length" class="memory-detail-photo-column" aria-label="记忆照片">
          <MemoryPhotoGallery
            :photos="photos"
            :fallback-url="memory.photoUrl"
            fit="contain"
            layout="detail"
            count-label="张照片"
            :max-height="'min(58vh, 560px)'"
            :backdrop="true"
            :backdrop-portrait-only="true"
            :backdrop-dim="true"
            :alt="`${memoryLabel(memory)}的照片`"
          />
        </section>

        <section class="memory-detail-reading">
          <p v-if="!photos.length" class="memory-no-photo-note">没有照片，也是一段完整记忆。</p>

          <div class="memory-detail-meta-line">
            <span class="memory-day-chip">{{ dayLabel }}</span>
            <time :datetime="memory.recordTime || undefined">{{ recordTimeLabel }}</time>
          </div>

          <h1 v-if="detailTitle" class="memory-detail-title">{{ detailTitle }}</h1>

          <blockquote :class="['memory-detail-quote', { 'is-empty': !memory.content }]">
            <span class="memory-quote-mark" aria-hidden="true">“</span>
            <p>{{ memory.content || '这一刻没有留下文字。' }}</p>
          </blockquote>

          <section class="memory-meta-section" aria-label="记忆信息">
            <div class="memory-meta-item">
              <CalendarDays :size="18" aria-hidden="true" />
              <span><small>记录时间</small><strong>{{ recordTimeLabel }}</strong></span>
            </div>
            <div v-if="memory.locationName || hasCoordinates" class="memory-meta-item">
              <MapPin :size="18" aria-hidden="true" />
              <span><small>地点</small><strong>{{ memory.locationName || '地点还没有补充' }}</strong></span>
              <RouterLink v-if="hasCoordinates" class="memory-map-link" :to="{ path: `/trips/${tripId}/map`, query: { memoryId: memory.id } }">在地图中查看</RouterLink>
            </div>
          </section>

          <section v-if="memory.companions?.length" class="memory-companion-section">
            <h2>同行的人</h2>
            <div class="memory-companion-list">
              <span v-for="companion in memory.companions" :key="companion.id || companion.name" class="memory-companion">
                <span class="memory-companion-avatar" aria-hidden="true"><img v-if="companion.avatarUrl" :src="companion.avatarUrl" alt="" /><template v-else>{{ companionInitial(companion) }}</template></span>
                <span>{{ companion.name }}<small v-if="companion.isSelf">（你）</small></span>
              </span>
            </div>
          </section>

          <section class="memory-trip-source">
            <h2>来自旅行</h2>
            <RouterLink class="memory-trip-card" :to="`/trips/${tripId}`">
              <span class="memory-trip-cover">
                <img v-if="tripCoverUrl" :src="tripCoverUrl" :alt="`${trip.title || '旅行'}封面`" />
                <span v-else aria-hidden="true">{{ (trip.destination || trip.title || '旅').slice(0, 1) }}</span>
              </span>
              <span class="memory-trip-copy">
                <strong>{{ trip.title || '这次旅行' }}</strong>
                <small>{{ [trip.destination, tripDateLabel].filter(Boolean).join(' · ') }}</small>
              </span>
              <ChevronRight :size="19" aria-hidden="true" />
            </RouterLink>
          </section>
        </section>
      </article>

      <p v-if="actionError" class="memory-detail-action-error" role="alert">{{ actionError }}</p>

      <section v-if="showNeighborNavigation" class="memory-neighbors" aria-label="相邻记忆">
        <RouterLink v-if="previousMemory" class="memory-neighbor-card" :to="`/trips/${tripId}/memories/${previousMemory.id}`">
          <img v-if="firstPhotoUrl(previousMemory)" :src="firstPhotoUrl(previousMemory)" :alt="`${memoryLabel(previousMemory)}缩略图`" />
          <span class="memory-neighbor-copy">
            <small>上一段记忆 · {{ formatCompactTime(previousMemory.recordTime) }}</small>
            <strong>{{ memoryLabel(previousMemory) }}</strong>
            <span>{{ previousMemory.content || '这一刻没有留下文字。' }}</span>
          </span>
        </RouterLink>
        <p v-else class="memory-neighbor-boundary">已经是这次旅行最早的一段记忆</p>

        <RouterLink v-if="nextMemory" class="memory-neighbor-card" :to="`/trips/${tripId}/memories/${nextMemory.id}`">
          <img v-if="firstPhotoUrl(nextMemory)" :src="firstPhotoUrl(nextMemory)" :alt="`${memoryLabel(nextMemory)}缩略图`" />
          <span class="memory-neighbor-copy">
            <small>下一段记忆 · {{ formatCompactTime(nextMemory.recordTime) }}</small>
            <strong>{{ memoryLabel(nextMemory) }}</strong>
            <span>{{ nextMemory.content || '这一刻没有留下文字。' }}</span>
          </span>
        </RouterLink>
        <p v-else class="memory-neighbor-boundary">已经是这次旅行最后的一段记忆</p>
      </section>

      <nav class="memory-detail-bottom-actions" aria-label="记忆操作">
        <RouterLink :to="`/trips/${tripId}/memories/${memory.id}/edit`">
          <Pencil :size="18" aria-hidden="true" /><span>编辑</span>
        </RouterLink>
        <button type="button" :disabled="favoriteSaving" :aria-pressed="memory.isFavorite === 1" @click="toggleFavorite">
          <Heart :size="19" :fill="memory.isFavorite === 1 ? 'currentColor' : 'none'" aria-hidden="true" />
          <span>{{ memory.isFavorite === 1 ? '取消收藏' : '收藏' }}</span>
        </button>
        <div class="memory-bottom-more" @click.stop>
          <button type="button" aria-label="更多操作" :aria-expanded="menuOpen && menuAnchor === 'bottom'" @click="toggleMenu('bottom')">
            <Ellipsis :size="20" aria-hidden="true" /><span>更多</span>
          </button>
          <div v-if="menuOpen && menuAnchor === 'bottom'" class="memory-detail-menu memory-bottom-menu" role="menu">
            <button type="button" role="menuitem" class="danger" :disabled="removing" @click="removeMemory">
              <Trash2 :size="15" aria-hidden="true" />{{ removing ? '删除中…' : '删除记忆' }}
            </button>
          </div>
        </div>
      </nav>
    </template>
  </section>
</template>

<style scoped>
.memory-detail-page { width: min(1160px, calc(100vw - 32px)); margin: 0 auto; padding: 0 0 72px; color: var(--tm-text); }
.memory-detail-status { margin: 72px auto; color: var(--tm-text-muted); text-align: center; }
.memory-detail-load-error { display: grid; justify-items: center; gap: 12px; }
.memory-detail-load-error p { margin: 0; }
.memory-detail-load-error button { padding: 8px 13px; border: 1px solid var(--tm-border); border-radius: 7px; background: var(--tm-surface); color: var(--tm-accent); }
.memory-detail-topbar { position: sticky; z-index: 24; top: 64px; display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; min-height: 56px; margin-bottom: 20px; border-bottom: 1px solid rgba(226, 214, 201, .82); background: rgba(255, 253, 249, .94); backdrop-filter: blur(14px); }
.memory-detail-topbar-title { color: var(--tm-text-muted); font-size: 13px; font-weight: 700; }
.memory-detail-topbar-actions { display: flex; justify-self: end; gap: 5px; }
.memory-detail-topbar-button { display: grid; width: 40px; height: 40px; place-items: center; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--tm-text-muted); }
.memory-detail-topbar-button:hover, .memory-detail-topbar-button:focus-visible, .memory-detail-topbar-button.active { background: var(--tm-accent-soft); color: var(--tm-accent); }
.memory-detail-content { display: grid; grid-template-columns: minmax(0, 1.52fr) minmax(330px, 1fr); gap: clamp(28px, 4vw, 54px); align-items: start; }
.memory-detail-photo-column { min-width: 0; }
.memory-detail-photo-column :deep(.gallery-main) { border-radius: 10px; box-shadow: 0 15px 34px rgba(55, 43, 34, .1); }
.memory-detail-photo-column :deep(.gallery-main img.gallery-image-contain) { border-radius: 10px; }
.memory-detail-photo-column :deep(.gallery-thumbs) { padding-top: 9px; }
.memory-detail-reading { display: grid; gap: 20px; align-content: start; padding: 6px 0 0; }
.memory-no-photo-note { margin: 0; color: var(--tm-text-muted); font-size: 13px; }
.memory-detail-meta-line { display: flex; flex-wrap: wrap; gap: 9px; align-items: center; color: var(--tm-accent); font-size: 13px; font-weight: 750; }
.memory-day-chip { padding: 5px 9px; border-radius: 999px; background: var(--tm-accent-soft); }
.memory-detail-title { margin: -2px 0 0; font-family: Georgia, "Microsoft YaHei", serif; font-size: clamp(27px, 3vw, 39px); line-height: 1.24; }
.memory-detail-quote { position: relative; margin: 0; padding: 19px 18px 19px 37px; border: 1px solid rgba(226, 214, 201, .76); border-radius: 9px; background: rgba(247, 241, 233, .78); }
.memory-detail-quote p { margin: 0; white-space: pre-wrap; font-size: 18px; line-height: 1.8; }
.memory-detail-quote.is-empty p { color: var(--tm-text-muted); font-size: 15px; }
.memory-quote-mark { position: absolute; top: 7px; left: 12px; color: #c9a88d; font-family: Georgia, serif; font-size: 34px; line-height: 1; }
.memory-meta-section { display: grid; gap: 13px; padding: 17px 0; border-top: 1px solid var(--tm-border); border-bottom: 1px solid var(--tm-border); }
.memory-meta-item { display: grid; grid-template-columns: 22px minmax(0, 1fr) auto; gap: 10px; align-items: center; }
.memory-meta-item > svg { color: var(--tm-accent); }
.memory-meta-item > span { display: grid; gap: 2px; }
.memory-meta-item small { color: var(--tm-text-muted); font-size: 11px; font-weight: 500; }
.memory-meta-item strong { font-size: 14px; }
.memory-map-link { color: var(--tm-accent); font-size: 12px; }
.memory-companion-section, .memory-trip-source { display: grid; gap: 10px; }
.memory-companion-section h2, .memory-trip-source h2 { margin: 0; color: var(--tm-text-muted); font-size: 12px; font-weight: 650; }
.memory-companion-list { display: flex; flex-wrap: wrap; gap: 10px 15px; }
.memory-companion { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; }
.memory-companion-avatar { display: grid; width: 30px; height: 30px; place-items: center; overflow: hidden; border: 1px solid #e1cbb9; border-radius: 50%; background: #f3e5da; color: #8d4d31; font-size: 12px; font-weight: 800; }
.memory-companion-avatar img { width: 100%; height: 100%; object-fit: cover; }
.memory-trip-card { display: grid; grid-template-columns: 66px minmax(0, 1fr) auto; gap: 11px; align-items: center; min-width: 0; padding: 9px; border: 1px solid var(--tm-border); border-radius: 9px; background: rgba(255, 253, 249, .74); color: var(--tm-text); }
.memory-trip-card:hover { border-color: #d2ad95; }
.memory-trip-cover { display: grid; width: 66px; height: 48px; place-items: center; overflow: hidden; border-radius: 6px; background: linear-gradient(145deg, #ead7c7, #f6eee5); color: var(--tm-accent); font-weight: 800; }
.memory-trip-cover img { width: 100%; height: 100%; object-fit: cover; }
.memory-trip-copy { display: grid; min-width: 0; gap: 4px; }
.memory-trip-copy strong, .memory-trip-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.memory-trip-copy strong { font-size: 14px; }
.memory-trip-copy small { color: var(--tm-text-muted); font-size: 11px; }
.memory-detail-more, .memory-bottom-more { position: relative; }
.memory-detail-menu { position: absolute; z-index: 30; top: calc(100% + 7px); right: 0; display: grid; min-width: 142px; overflow: hidden; border: 1px solid var(--tm-border); border-radius: 8px; background: var(--tm-surface); box-shadow: 0 14px 30px rgba(46, 36, 29, .16); }
.memory-detail-menu a, .memory-detail-menu button { display: flex; align-items: center; gap: 8px; min-height: 42px; padding: 0 12px; border: 0; background: transparent; color: var(--tm-text); font: inherit; font-size: 13px; text-align: left; }
.memory-detail-menu a:hover, .memory-detail-menu button:hover { background: var(--tm-accent-soft); }
.memory-detail-menu .danger { border-top: 1px solid var(--tm-border); color: #a44735; }
.memory-detail-action-error { margin: 18px 0 0; padding: 10px 12px; border-radius: 7px; background: #fbeae6; color: #a43f2e; font-size: 13px; }
.memory-neighbors { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 42px; }
.memory-neighbor-card { display: grid; grid-template-columns: 82px minmax(0, 1fr); gap: 12px; min-height: 102px; padding: 11px; border: 1px solid var(--tm-border); border-radius: 9px; background: rgba(255, 253, 249, .7); color: var(--tm-text); }
.memory-neighbor-card:hover { border-color: #d1aa91; }
.memory-neighbor-card > img { width: 82px; height: 78px; object-fit: cover; border-radius: 6px; }
.memory-neighbor-copy { display: grid; min-width: 0; align-content: center; gap: 5px; }
.memory-neighbor-copy small { color: var(--tm-text-muted); font-size: 11px; }
.memory-neighbor-copy strong { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.memory-neighbor-copy > span { display: -webkit-box; overflow: hidden; color: var(--tm-text-muted); font-size: 12px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.memory-neighbor-boundary { align-self: center; margin: 0; color: var(--tm-text-muted); font-size: 12px; text-align: center; }
.memory-detail-bottom-actions { display: none; }
.memory-detail-content.text-only { grid-template-columns: minmax(0, 780px); justify-content: center; }
.memory-detail-content.text-only .memory-detail-reading { padding: 28px; border: 1px solid var(--tm-border); border-radius: 10px; background: rgba(255, 253, 249, .68); }

@media (max-width: 760px) {
  :global(.app-shell:has(.memory-detail-page) > .topbar) { display: none; }
  :global(.page:has(.memory-detail-page)) { width: 100%; margin: 0; }
  .memory-detail-page { width: 100%; padding-bottom: calc(92px + env(safe-area-inset-bottom)); }
  .memory-detail-topbar { top: 0; min-height: 56px; margin: 0; padding: env(safe-area-inset-top) 12px 0; }
  .memory-detail-topbar-title { color: var(--tm-text); font-size: 15px; }
  .memory-detail-content { grid-template-columns: 1fr; gap: 0; }
  .memory-detail-photo-column :deep(.gallery-main) { border-radius: 0; box-shadow: none; }
  .memory-detail-photo-column :deep(.gallery-main img.gallery-image-contain) { max-height: min(58vh, 460px); border-radius: 0; }
  .memory-detail-photo-column :deep(.gallery-main-with-backdrop) { height: min(58vh, 460px); }
  .memory-detail-photo-column :deep(.gallery-thumbs) { padding: 9px 16px 3px; }
  .memory-detail-reading { gap: 17px; padding: 20px 20px 0; }
  .memory-detail-meta-line { font-size: 12px; }
  .memory-detail-title { font-size: 27px; }
  .memory-detail-quote { padding: 17px 15px 17px 34px; }
  .memory-detail-quote p { font-size: 17px; line-height: 1.75; }
  .memory-detail-quote.is-empty p { font-size: 14px; }
  .memory-meta-item { grid-template-columns: 21px minmax(0, 1fr); }
  .memory-map-link { grid-column: 2; justify-self: start; }
  .memory-trip-card { grid-template-columns: 62px minmax(0, 1fr) auto; }
  .memory-detail-content.text-only { display: block; }
  .memory-detail-content.text-only .memory-detail-reading { padding: 24px 20px 0; border: 0; background: transparent; }
  .memory-neighbors { grid-template-columns: 1fr; gap: 9px; margin: 30px 20px 0; }
  .memory-neighbor-card { grid-template-columns: 72px minmax(0, 1fr); min-height: 92px; }
  .memory-neighbor-card > img { width: 72px; height: 68px; }
  .memory-neighbor-boundary { padding: 8px 0; }
  .memory-detail-action-error { margin: 18px 20px 0; }
  .memory-detail-bottom-actions { position: fixed; z-index: 26; right: 0; bottom: 0; left: 0; display: grid; grid-template-columns: repeat(3, 1fr); min-height: 60px; padding: 7px 12px max(7px, env(safe-area-inset-bottom)); border-top: 1px solid rgba(226, 214, 201, .9); background: rgba(255, 253, 249, .96); box-shadow: 0 -8px 22px rgba(55, 43, 34, .06); backdrop-filter: blur(14px); }
  .memory-detail-bottom-actions > a, .memory-detail-bottom-actions > button, .memory-bottom-more > button { display: grid; justify-items: center; align-content: center; gap: 3px; min-height: 44px; padding: 0; border: 0; background: transparent; color: var(--tm-text-muted); font: inherit; font-size: 11px; }
  .memory-detail-bottom-actions > a:hover, .memory-detail-bottom-actions button:hover, .memory-detail-bottom-actions button:focus-visible { color: var(--tm-accent); }
  .memory-bottom-menu { top: auto; right: 0; bottom: calc(100% + 9px); min-width: 136px; }
}
</style>
