<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Briefcase,
  ChevronLeft,
  ChevronRight,
  Ellipsis,
  Heart,
  Image as ImageIcon,
  Map as MapIcon,
  MapPin,
  Pencil,
  Share2,
  Trash2,
  Users,
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
const photoGallery = ref(null)
const actionMessage = ref('')
let actionMessageTimer = null

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
const memoryPlaceLabel = computed(() => {
  const values = [
    trip.value?.destinationCountry,
    trip.value?.destination,
    memory.value?.locationName,
  ].map(value => String(value || '').trim()).filter(Boolean)
  return [...new Set(values)].join(' · ') || '地点还没有补充'
})

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

function openGallery() {
  if (photos.value.length) photoGallery.value?.open(0)
}

function showActionMessage(message) {
  actionMessage.value = message
  if (actionMessageTimer) window.clearTimeout(actionMessageTimer)
  actionMessageTimer = window.setTimeout(() => {
    actionMessage.value = ''
    actionMessageTimer = null
  }, 2400)
}

async function shareMemory() {
  if (!memory.value) return
  const shareData = {
    title: memoryLabel(memory.value),
    text: memory.value.content?.trim() || '一段旅行记忆',
    url: window.location.href,
  }
  try {
    if (navigator.share) {
      await navigator.share(shareData)
      return
    }
    if (!navigator.clipboard?.writeText) throw new Error('当前浏览器暂不支持分享')
    await navigator.clipboard.writeText(shareData.url)
    showActionMessage('链接已复制')
  } catch (error) {
    if (error?.name !== 'AbortError') actionError.value = error.message || '暂时无法分享这段记忆。'
  }
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
onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  if (actionMessageTimer) window.clearTimeout(actionMessageTimer)
})
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
        <span class="memory-detail-topbar-title">旅行记忆</span>
        <div class="memory-detail-topbar-actions">
          <button
            type="button"
            class="memory-detail-topbar-button"
            aria-label="分享这段记忆"
            @click.stop="shareMemory"
          >
            <Share2 :size="20" aria-hidden="true" />
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
            ref="photoGallery"
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
            <div class="memory-detail-time">
              <span class="memory-day-chip">{{ dayLabel }}</span>
              <time :datetime="memory.recordTime || undefined">{{ recordTimeLabel }}</time>
            </div>
            <button
              type="button"
              :class="['memory-detail-favorite', { active: memory.isFavorite === 1 }]"
              :disabled="favoriteSaving"
              :aria-label="memory.isFavorite === 1 ? '取消收藏' : '收藏这段记忆'"
              :aria-pressed="memory.isFavorite === 1"
              @click="toggleFavorite"
            >
              <Heart :size="25" :fill="memory.isFavorite === 1 ? 'currentColor' : 'none'" aria-hidden="true" />
            </button>
          </div>

          <h1 v-if="detailTitle" class="memory-detail-title">{{ detailTitle }}</h1>

          <blockquote :class="['memory-detail-quote', { 'is-empty': !memory.content }]">
            <span class="memory-quote-mark" aria-hidden="true">“</span>
            <p>{{ memory.content || '这一刻没有留下文字。' }}</p>
            <span class="memory-quote-mark memory-quote-mark-end" aria-hidden="true">”</span>
          </blockquote>

          <section class="memory-context-list" aria-label="记忆信息">
            <RouterLink
              v-if="hasCoordinates"
              class="memory-context-row"
              :to="{ path: `/trips/${tripId}/map`, query: { memoryId: memory.id } }"
            >
              <MapPin :size="18" aria-hidden="true" />
              <strong>地点</strong>
              <span>{{ memoryPlaceLabel }}</span>
              <ChevronRight :size="18" aria-hidden="true" />
            </RouterLink>
            <div v-else class="memory-context-row">
              <MapPin :size="18" aria-hidden="true" />
              <strong>地点</strong>
              <span>{{ memoryPlaceLabel }}</span>
              <span aria-hidden="true"></span>
            </div>

            <RouterLink class="memory-context-row" :to="`/trips/${tripId}`">
              <Briefcase :size="18" aria-hidden="true" />
              <strong>旅行</strong>
              <span>{{ trip.title || '这次旅行' }}</span>
              <ChevronRight :size="18" aria-hidden="true" />
            </RouterLink>

            <RouterLink class="memory-context-row memory-context-companions" :to="`/trips/${tripId}/companions`">
              <Users :size="18" aria-hidden="true" />
              <strong>同行的人</strong>
              <span class="memory-context-companion-value">
                <span v-if="memory.companions?.length" class="memory-context-avatars" aria-hidden="true">
                  <span v-for="companion in memory.companions.slice(0, 3)" :key="companion.id || companion.name" class="memory-context-avatar">
                    <img v-if="companion.avatarUrl" :src="companion.avatarUrl" alt="" />
                    <template v-else>{{ companionInitial(companion) }}</template>
                  </span>
                </span>
                <span>{{ memory.companions?.length ? `${memory.companions.length} 人` : '未标记' }}</span>
              </span>
              <ChevronRight :size="18" aria-hidden="true" />
            </RouterLink>
          </section>

          <section v-if="photos.length || hasCoordinates" :class="['memory-detail-quick-actions', { single: !photos.length || !hasCoordinates }]" aria-label="记忆浏览入口">
            <button v-if="photos.length" type="button" @click="openGallery">
              <ImageIcon :size="25" aria-hidden="true" />
              <span><strong>查看全部照片</strong><small>{{ photos.length }} 张</small></span>
              <ChevronRight :size="19" aria-hidden="true" />
            </button>
            <RouterLink v-if="hasCoordinates" :to="{ path: `/trips/${tripId}/map`, query: { memoryId: memory.id } }">
              <MapIcon :size="25" aria-hidden="true" />
              <span><strong>查看地图</strong><small>查看此地位置</small></span>
              <ChevronRight :size="19" aria-hidden="true" />
            </RouterLink>
          </section>

        </section>
      </article>

      <p v-if="actionError" class="memory-detail-action-error" role="alert">{{ actionError }}</p>
      <p v-if="actionMessage" class="memory-detail-action-message" role="status">{{ actionMessage }}</p>

      <section v-if="showNeighborNavigation" class="memory-neighbors" aria-label="相邻记忆">
        <RouterLink v-if="previousMemory" class="memory-neighbor-card" :to="`/trips/${tripId}/memories/${previousMemory.id}`">
          <ChevronLeft class="memory-neighbor-arrow" :size="22" aria-hidden="true" />
          <img v-if="firstPhotoUrl(previousMemory)" :src="firstPhotoUrl(previousMemory)" :alt="`${memoryLabel(previousMemory)}缩略图`" />
          <span class="memory-neighbor-copy">
            <small>上一篇记忆</small>
            <strong>{{ memoryLabel(previousMemory) }}</strong>
            <span>{{ formatCompactTime(previousMemory.recordTime) }}</span>
          </span>
        </RouterLink>
        <p v-else class="memory-neighbor-boundary">已经是这次旅行最早的一段记忆</p>

        <RouterLink v-if="nextMemory" class="memory-neighbor-card memory-neighbor-next" :to="`/trips/${tripId}/memories/${nextMemory.id}`">
          <span class="memory-neighbor-copy">
            <small>下一篇记忆</small>
            <strong>{{ memoryLabel(nextMemory) }}</strong>
            <span>{{ formatCompactTime(nextMemory.recordTime) }}</span>
          </span>
          <img v-if="firstPhotoUrl(nextMemory)" :src="firstPhotoUrl(nextMemory)" :alt="`${memoryLabel(nextMemory)}缩略图`" />
          <ChevronRight class="memory-neighbor-arrow" :size="22" aria-hidden="true" />
        </RouterLink>
        <p v-else class="memory-neighbor-boundary">已经是这次旅行最后的一段记忆</p>
      </section>
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
.memory-detail-meta-line { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; color: var(--tm-accent); font-size: 13px; font-weight: 750; }
.memory-detail-time { display: flex; flex-wrap: wrap; align-items: center; gap: 9px; }
.memory-day-chip { padding: 5px 9px; border-radius: 999px; background: var(--tm-accent-soft); }
.memory-detail-favorite { display: grid; flex: 0 0 42px; width: 42px; height: 42px; place-items: center; padding: 0; border: 1px solid var(--tm-border); border-radius: 50%; background: transparent; color: #846f62; }
.memory-detail-favorite:hover, .memory-detail-favorite:focus-visible, .memory-detail-favorite.active { border-color: #d8a88f; background: var(--tm-accent-soft); color: var(--tm-accent); }
.memory-detail-title { margin: -2px 0 0; font-family: Georgia, "Microsoft YaHei", serif; font-size: clamp(27px, 3vw, 39px); line-height: 1.24; }
.memory-detail-quote { position: relative; margin: 0; padding: 20px 34px 22px; border: 0; border-bottom: 1px solid rgba(226, 214, 201, .76); background: transparent; }
.memory-detail-quote p { margin: 0; white-space: pre-wrap; font-size: 18px; line-height: 1.8; }
.memory-detail-quote.is-empty p { color: var(--tm-text-muted); font-size: 15px; }
.memory-quote-mark { position: absolute; top: 10px; left: 5px; color: #d7b89f; font-family: Georgia, serif; font-size: 36px; line-height: 1; }
.memory-quote-mark-end { top: auto; right: 5px; bottom: 7px; left: auto; }
.memory-context-list { display: grid; border-bottom: 1px solid rgba(226, 214, 201, .76); }
.memory-context-row { display: grid; grid-template-columns: 24px 86px minmax(0, 1fr) 18px; gap: 9px; align-items: center; min-height: 55px; border-bottom: 1px solid rgba(226, 214, 201, .68); color: var(--tm-text); }
.memory-context-row:last-child { border-bottom: 0; }
.memory-context-row > svg { color: #796052; }
.memory-context-row > strong { font-size: 14px; font-weight: 650; }
.memory-context-row > span { min-width: 0; overflow: hidden; color: var(--tm-text-muted); font-size: 13px; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.memory-context-companion-value { display: flex; align-items: center; justify-content: flex-end; gap: 8px; overflow: visible !important; }
.memory-context-avatars { display: flex; padding-left: 10px; }
.memory-context-avatar { display: grid; width: 28px; height: 28px; place-items: center; overflow: hidden; margin-left: -10px; border: 2px solid #fffaf4; border-radius: 50%; background: #f1ded1; color: #8c5035; font-size: 10px; font-weight: 800; }
.memory-context-avatar img { width: 100%; height: 100%; object-fit: cover; }
.memory-detail-quick-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.memory-detail-quick-actions.single { grid-template-columns: minmax(0, 1fr); }
.memory-detail-quick-actions > button, .memory-detail-quick-actions > a { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 10px; align-items: center; min-height: 66px; padding: 10px 12px; border: 1px solid var(--tm-border); border-radius: 10px; background: rgba(255, 253, 249, .8); color: var(--tm-text); text-align: left; }
.memory-detail-quick-actions > button { font: inherit; }
.memory-detail-quick-actions > button > svg:first-child, .memory-detail-quick-actions > a > svg:first-child { color: #886a59; }
.memory-detail-quick-actions > button > svg:last-child, .memory-detail-quick-actions > a > svg:last-child { color: #ae998b; }
.memory-detail-quick-actions span { display: grid; min-width: 0; gap: 2px; }
.memory-detail-quick-actions strong { font-size: 13px; }
.memory-detail-quick-actions small { color: var(--tm-text-muted); font-size: 10px; }
.memory-detail-more, .memory-bottom-more { position: relative; }
.memory-detail-menu { position: absolute; z-index: 30; top: calc(100% + 7px); right: 0; display: grid; min-width: 142px; overflow: hidden; border: 1px solid var(--tm-border); border-radius: 8px; background: var(--tm-surface); box-shadow: 0 14px 30px rgba(46, 36, 29, .16); }
.memory-detail-menu a, .memory-detail-menu button { display: flex; align-items: center; gap: 8px; min-height: 42px; padding: 0 12px; border: 0; background: transparent; color: var(--tm-text); font: inherit; font-size: 13px; text-align: left; }
.memory-detail-menu a:hover, .memory-detail-menu button:hover { background: var(--tm-accent-soft); }
.memory-detail-menu .danger { border-top: 1px solid var(--tm-border); color: #a44735; }
.memory-detail-action-error { margin: 18px 0 0; padding: 10px 12px; border-radius: 7px; background: #fbeae6; color: #a43f2e; font-size: 13px; }
.memory-detail-action-message { position: fixed; z-index: 60; bottom: 24px; left: 50%; margin: 0; padding: 9px 15px; border-radius: 999px; background: rgba(48, 41, 36, .9); color: #fff; font-size: 12px; transform: translateX(-50%); }
.memory-neighbors { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 42px; }
.memory-neighbor-card { display: grid; grid-template-columns: auto 82px minmax(0, 1fr); gap: 10px; align-items: center; min-height: 102px; padding: 11px; border: 1px solid var(--tm-border); border-radius: 12px; background: rgba(255, 253, 249, .76); color: var(--tm-text); }
.memory-neighbor-next { grid-template-columns: minmax(0, 1fr) 82px auto; }
.memory-neighbor-card:not(:has(img)) { grid-template-columns: auto minmax(0, 1fr); }
.memory-neighbor-next:not(:has(img)) { grid-template-columns: minmax(0, 1fr) auto; }
.memory-neighbor-card:hover { border-color: #d1aa91; }
.memory-neighbor-card > img { width: 82px; height: 78px; object-fit: cover; border-radius: 6px; }
.memory-neighbor-arrow { color: #a18473; }
.memory-neighbor-copy { display: grid; min-width: 0; align-content: center; gap: 5px; }
.memory-neighbor-copy small { color: var(--tm-text-muted); font-size: 11px; }
.memory-neighbor-copy strong { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.memory-neighbor-copy > span { overflow: hidden; color: var(--tm-text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.memory-neighbor-boundary { align-self: center; margin: 0; color: var(--tm-text-muted); font-size: 12px; text-align: center; }
.memory-detail-bottom-actions { display: none; }
.memory-detail-content.text-only { grid-template-columns: minmax(0, 780px); justify-content: center; }
.memory-detail-content.text-only .memory-detail-reading { padding: 28px; border: 1px solid var(--tm-border); border-radius: 10px; background: rgba(255, 253, 249, .68); }

@media (max-width: 760px) {
  :global(.app-shell:has(.memory-detail-page) > .topbar) { display: none; }
  :global(.page:has(.memory-detail-page)) { width: 100%; margin: 0; }
  .memory-detail-page { width: 100%; padding-bottom: calc(26px + env(safe-area-inset-bottom)); }
  .memory-detail-topbar {
    top: 0;
    min-height: 58px;
    margin: 0;
    padding: env(safe-area-inset-top) 14px 0;
    border-bottom: 0;
    background: rgba(255, 252, 247, .96);
  }
  .memory-detail-topbar-title {
    color: var(--tm-text);
    font-family: Georgia, "Songti SC", "Microsoft YaHei", serif;
    font-size: 18px;
  }
  .memory-detail-content {
    grid-template-columns: 1fr;
    gap: 0;
    overflow: hidden;
    margin: 8px 12px 0;
    border: 1px solid rgba(226, 214, 201, .82);
    border-radius: 22px;
    background: rgba(255, 253, 249, .96);
    box-shadow: 0 10px 28px rgba(63, 49, 38, .075);
  }
  .memory-detail-photo-column :deep(.gallery) { gap: 0; }
  .memory-detail-photo-column :deep(.gallery-main) {
    width: 100%;
    height: auto !important;
    aspect-ratio: 16 / 10;
    border-radius: 0;
    background: #eee7df;
    box-shadow: none;
  }
  .memory-detail-photo-column :deep(.gallery-main img.gallery-image-contain) {
    width: 100%;
    height: 100%;
    min-height: 0;
    max-height: none;
    border-radius: 0;
    object-fit: cover;
  }
  .memory-detail-photo-column :deep(.gallery-backdrop) { display: none; }
  .memory-detail-photo-column :deep(.gallery-thumbs) { display: none; }
  .memory-detail-photo-column :deep(.gallery-count) {
    top: 14px;
    right: 14px;
    bottom: auto;
    padding: 6px 10px;
    border-radius: 999px;
    background: rgba(48, 42, 37, .66);
    font-size: 12px;
  }
  .memory-detail-reading { gap: 0; padding: 0 20px 22px; }
  .memory-no-photo-note { padding-top: 22px; }
  .memory-detail-meta-line { min-height: 66px; font-size: 12px; }
  .memory-detail-time { gap: 10px; }
  .memory-day-chip { padding: 6px 11px; }
  .memory-detail-favorite { flex-basis: 40px; width: 40px; height: 40px; border: 0; }
  .memory-detail-title { display: none; }
  .memory-detail-quote { padding: 20px 26px 24px; }
  .memory-detail-quote p {
    font-family: Georgia, "Songti SC", "Microsoft YaHei", serif;
    font-size: 18px;
    line-height: 1.85;
  }
  .memory-detail-quote.is-empty p { font-size: 14px; }
  .memory-quote-mark { top: 12px; left: 0; }
  .memory-quote-mark-end { top: auto; right: 0; bottom: 7px; left: auto; }
  .memory-context-row { grid-template-columns: 22px 72px minmax(0, 1fr) 16px; min-height: 57px; }
  .memory-context-row > strong { font-size: 14px; }
  .memory-context-row > span { font-size: 12px; }
  .memory-context-avatar { width: 27px; height: 27px; }
  .memory-detail-quick-actions { gap: 9px; padding-top: 18px; }
  .memory-detail-quick-actions > button, .memory-detail-quick-actions > a { min-height: 64px; padding: 9px 10px; border-radius: 13px; }
  .memory-detail-content.text-only { display: block; }
  .memory-detail-content.text-only .memory-detail-reading { padding: 0 20px 22px; border: 0; background: transparent; }
  .memory-neighbors { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 14px 12px 0; }
  .memory-neighbor-card {
    grid-template-columns: auto 50px minmax(0, 1fr);
    gap: 6px;
    min-height: 88px;
    padding: 8px 7px;
    border-radius: 14px;
  }
  .memory-neighbor-next { grid-template-columns: minmax(0, 1fr) 50px auto; }
  .memory-neighbor-card > img { width: 50px; height: 60px; border-radius: 8px; }
  .memory-neighbor-arrow { width: 16px; }
  .memory-neighbor-copy { gap: 3px; }
  .memory-neighbor-copy small { font-size: 9px; }
  .memory-neighbor-copy strong { font-size: 11px; }
  .memory-neighbor-copy > span { font-size: 9px; }
  .memory-neighbor-boundary { align-self: stretch; display: grid; min-height: 88px; place-items: center; padding: 8px; border: 1px solid var(--tm-border); border-radius: 14px; background: rgba(255, 253, 249, .65); font-size: 10px; }
  .memory-detail-action-error { margin: 18px 20px 0; }
  .memory-detail-action-message { bottom: max(22px, env(safe-area-inset-bottom)); }
}
</style>
