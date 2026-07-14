<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
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
const error = ref('')
const trip = ref(null)
const memory = ref(null)
const timeline = ref([])
const favoriteSaving = ref(false)
const removing = ref(false)
const menuOpen = ref(false)

const orderedMemories = computed(() => [...timeline.value].sort((left, right) => {
  const timeDiff = timeValue(left.recordTime) - timeValue(right.recordTime)
  return timeDiff || timeValue(left.createTime) - timeValue(right.createTime)
}))

const memoryIndex = computed(() => orderedMemories.value.findIndex(item => String(item.id) === String(memory.value?.id)))
const previousMemory = computed(() => memoryIndex.value > 0 ? orderedMemories.value[memoryIndex.value - 1] : null)
const nextMemory = computed(() => (
  memoryIndex.value >= 0 && memoryIndex.value < orderedMemories.value.length - 1
    ? orderedMemories.value[memoryIndex.value + 1]
    : null
))

const photos = computed(() => {
  if (memory.value?.photos?.length) return memory.value.photos
  return memory.value?.photoUrl ? [{ photoUrl: memory.value.photoUrl }] : []
})

const dayLabel = computed(() => {
  if (!memory.value?.recordTime) return '留下的这一刻'
  const day = getChronologicalTripDayNumber(trip.value?.startDate, dateKey(memory.value.recordTime), 1)
  return `第 ${day} 天`
})

const recordTimeLabel = computed(() => formatRecordTime(memory.value?.recordTime))
const tripContext = computed(() => {
  if (!trip.value) return ''
  const dates = [trip.value.startDate, trip.value.endDate].filter(Boolean).join(' — ')
  return [trip.value.title, dates].filter(Boolean).join(' · ')
})

function timeValue(value) {
  if (!value) return Number.MAX_SAFE_INTEGER
  const parsed = new Date(value).getTime()
  return Number.isNaN(parsed) ? Number.MAX_SAFE_INTEGER : parsed
}

function dateKey(value) {
  return value ? String(value).slice(0, 10) : ''
}

function formatRecordTime(value) {
  if (!value) return '时间还没有补充'
  const raw = String(value)
  const date = raw.slice(0, 10).replaceAll('-', '.')
  const time = raw.length >= 16 ? raw.slice(11, 16) : ''
  return [date, time].filter(Boolean).join(' ')
}

function photoLabel(item) {
  return item?.content?.trim() || item?.locationName?.trim() || '这段记忆'
}

async function loadPage() {
  loading.value = true
  error.value = ''
  menuOpen.value = false
  try {
    const [tripData, memoryData, timelineData] = await Promise.all([
      getTrip(props.tripId),
      getMemory(props.memoryId),
      getTimeline(props.tripId),
    ])
    if (String(memoryData.tripId) !== String(props.tripId)) {
      error.value = '这段记忆不属于当前旅行。'
      return
    }
    trip.value = tripData
    memory.value = memoryData
    timeline.value = Array.isArray(timelineData) ? timelineData : []
  } catch (err) {
    error.value = err.message || '这段记忆暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (!memory.value || favoriteSaving.value) return
  favoriteSaving.value = true
  try {
    const nextFavorite = memory.value.isFavorite !== 1
    await favoriteMemory(memory.value.id, nextFavorite)
    memory.value.isFavorite = nextFavorite ? 1 : 0
    const timelineMemory = timeline.value.find(item => String(item.id) === String(memory.value.id))
    if (timelineMemory) timelineMemory.isFavorite = memory.value.isFavorite
  } catch (err) {
    error.value = err.message || '收藏状态暂时没有更新成功。'
  } finally {
    favoriteSaving.value = false
  }
}

async function removeMemory() {
  if (!memory.value || removing.value) return
  if (!window.confirm('确定要删除这段记忆吗？删除后无法恢复。')) return
  removing.value = true
  try {
    await deleteMemory(memory.value.id)
    router.replace(`/trips/${props.tripId}`)
  } catch (err) {
    error.value = err.message || '删除失败，请稍后再试。'
  } finally {
    removing.value = false
  }
}

function closeMenu() {
  menuOpen.value = false
}

watch(() => [props.tripId, props.memoryId], loadPage)
onMounted(loadPage)
</script>

<template>
  <section class="memory-detail-page" @click="closeMenu">
    <p v-if="loading" class="memory-detail-status">正在取回这段记忆...</p>
    <p v-else-if="error" class="error memory-detail-status">{{ error }}</p>

    <template v-else-if="memory && trip">
      <header class="memory-detail-topbar">
        <RouterLink class="memory-back-link" :to="`/trips/${tripId}`">← 返回时间线</RouterLink>
      </header>

      <nav class="memory-breadcrumb" aria-label="当前位置">
        <RouterLink :to="`/trips/${tripId}`">{{ trip.title || '这次旅行' }}</RouterLink>
        <span aria-hidden="true">/</span>
        <span>{{ memory.locationName || '这段记忆' }}</span>
      </nav>

      <article :class="['memory-detail-content', { 'text-only': !photos.length }]">
        <section v-if="photos.length" class="memory-detail-photo-column">
          <MemoryPhotoGallery
            :photos="photos"
            :fallback-url="memory.photoUrl"
            fit="contain"
            layout="detail"
            count-label="张照片"
            :max-height="'min(66vh, 620px)'"
            :alt="`${photoLabel(memory)} 的照片`"
          />
          <p class="memory-photo-instruction">点击照片，查看完整原图</p>
        </section>

        <section class="memory-detail-reading">
          <div class="memory-detail-reading-head">
            <p class="memory-detail-time"><span class="memory-day-chip">{{ dayLabel }}</span>{{ recordTimeLabel }}</p>
            <div class="memory-detail-icon-actions">
              <button type="button" :class="['icon-action', { active: memory.isFavorite === 1 }]" :disabled="favoriteSaving" :aria-label="memory.isFavorite === 1 ? '取消收藏' : '收藏'" @click.stop="toggleFavorite">
                {{ memory.isFavorite === 1 ? '★' : '☆' }}
              </button>
              <div class="memory-detail-more" @click.stop>
                <button type="button" class="icon-action" aria-label="更多操作" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">···</button>
                <div v-if="menuOpen" class="memory-detail-menu">
                  <RouterLink :to="`/trips/${tripId}/memories/${memory.id}/edit`" @click="closeMenu">编辑记忆</RouterLink>
                  <button type="button" class="danger" :disabled="removing" @click="removeMemory">{{ removing ? '删除中...' : '删除记忆' }}</button>
                </div>
              </div>
            </div>
          </div>

          <blockquote :class="['memory-detail-quote', { 'is-empty': !memory.content }]">
            <template v-if="memory.content">“{{ memory.content }}”</template>
            <template v-else>这一刻没有留下文字。</template>
          </blockquote>

          <div class="memory-detail-context">
            <div v-if="memory.locationName" class="memory-detail-context-row">
              <span class="context-label">地点</span>
              <strong>{{ memory.locationName }}</strong>
            </div>
            <div v-if="memory.companions?.length" class="memory-detail-context-row">
              <span class="context-label">同行</span>
              <span>{{ memory.companions.map(item => item.name).join('、') }}</span>
            </div>
            <div class="memory-detail-context-row trip-context-row">
              <span class="context-label">旅行</span>
              <RouterLink :to="`/trips/${tripId}`">{{ tripContext || '回到这次旅行' }}</RouterLink>
            </div>
          </div>

          <div class="memory-detail-reading-actions">
            <button type="button" class="reading-favorite" :disabled="favoriteSaving" @click="toggleFavorite">
              {{ memory.isFavorite === 1 ? '取消收藏' : '收藏' }}
            </button>
            <RouterLink v-if="memory.latitude != null && memory.longitude != null" :to="`/trips/${tripId}/map`" class="reading-map-link">查看地图</RouterLink>
          </div>
        </section>
      </article>

      <section class="memory-continuity" aria-label="继续回看">
        <RouterLink v-if="previousMemory" class="continuity-card" :to="`/trips/${tripId}/memories/${previousMemory.id}`">
          <small>上一段记忆 · {{ formatRecordTime(previousMemory.recordTime) }}</small>
          <strong>{{ photoLabel(previousMemory) }}</strong>
          <span>{{ previousMemory.locationName || '途中留下的记忆' }}</span>
        </RouterLink>
        <div v-else class="continuity-card is-empty"><small>已经是这次旅行最早的一段记忆</small></div>

        <RouterLink v-if="nextMemory" class="continuity-card" :to="`/trips/${tripId}/memories/${nextMemory.id}`">
          <small>下一段记忆 · {{ formatRecordTime(nextMemory.recordTime) }}</small>
          <strong>{{ photoLabel(nextMemory) }}</strong>
          <span>{{ nextMemory.locationName || '途中留下的记忆' }}</span>
        </RouterLink>
        <div v-else class="continuity-card is-empty"><small>这是这次旅行最新的一段记忆</small></div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.memory-detail-page { width: min(1180px, calc(100vw - 32px)); margin: 0 auto; padding: 10px 0 54px; }
.memory-detail-status { margin: 70px 0; color: var(--tm-text-muted); text-align: center; }
.memory-detail-topbar { display: flex; justify-content: flex-end; min-height: 46px; align-items: center; }
.memory-back-link, .memory-breadcrumb a, .trip-context-row a, .reading-map-link { color: var(--tm-text-muted); }
.memory-back-link { font-size: 13px; }
.memory-back-link:hover, .memory-breadcrumb a:hover, .trip-context-row a:hover, .reading-map-link:hover { color: var(--tm-accent); }
.memory-breadcrumb { display: flex; gap: 8px; align-items: center; margin: 7px 0 18px; color: var(--tm-text-muted); font-size: 13px; }
.memory-breadcrumb span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.memory-detail-content { display: grid; grid-template-columns: minmax(0, 1.8fr) minmax(320px, .92fr); gap: 32px; align-items: start; }
.memory-detail-photo-column { min-width: 0; }
.memory-detail-photo-column :deep(.gallery-main) { border-radius: var(--tm-radius-lg); background: #e8e0d4; box-shadow: var(--tm-shadow-soft); }
.memory-detail-photo-column :deep(.gallery-main img.gallery-image-contain) { border-radius: var(--tm-radius-lg); }
.memory-detail-photo-column :deep(.gallery-thumbs) { padding-top: 4px; }
.memory-detail-photo-column :deep(.gallery-thumbs button) { border-radius: 7px; }
.memory-photo-instruction { margin: 9px 0 0; color: var(--tm-text-muted); font-size: 12px; }
.memory-detail-reading { display: grid; gap: 23px; align-content: start; min-height: min(66vh, 620px); padding: 8px 0; }
.memory-detail-reading-head { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
.memory-detail-time { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 0; color: var(--tm-accent); font-size: 13px; font-weight: 700; }
.memory-day-chip { padding: 5px 8px; border-radius: 999px; background: var(--tm-accent-soft); color: var(--tm-accent); font-size: 12px; }
.memory-detail-icon-actions { display: flex; gap: 7px; align-items: center; }
.icon-action { display: grid; width: 34px; height: 34px; place-items: center; padding: 0; border: 1px solid var(--tm-border); border-radius: 50%; background: var(--tm-surface); color: var(--tm-text-muted); font-size: 19px; line-height: 1; }
.icon-action:hover, .icon-action.active { border-color: var(--tm-accent); color: var(--tm-accent); }
.memory-detail-more { position: relative; }
.memory-detail-menu { position: absolute; z-index: 10; top: calc(100% + 8px); right: 0; display: grid; min-width: 122px; overflow: hidden; border: 1px solid var(--tm-border); border-radius: 9px; background: var(--tm-surface); box-shadow: 0 12px 28px rgba(52, 42, 33, .15); }
.memory-detail-menu a, .memory-detail-menu button { padding: 10px 12px; border: 0; background: transparent; color: var(--tm-text); font: inherit; font-size: 13px; text-align: left; }
.memory-detail-menu a:hover, .memory-detail-menu button:hover { background: var(--tm-accent-soft); }
.memory-detail-menu button.danger { color: #a94b36; }
.memory-detail-quote { margin: 0; color: var(--tm-text); font-size: clamp(25px, 2.55vw, 38px); font-weight: 750; letter-spacing: 0; line-height: 1.42; }
.memory-detail-quote.is-empty { color: var(--tm-text-muted); font-size: 20px; font-weight: 500; }
.memory-detail-context { display: grid; gap: 10px; padding: 17px 0; border-top: 1px solid var(--tm-border); border-bottom: 1px solid var(--tm-border); }
.memory-detail-context-row { display: grid; grid-template-columns: 38px minmax(0, 1fr); gap: 10px; align-items: baseline; font-size: 14px; }
.context-label { color: var(--tm-text-muted); font-size: 12px; }
.memory-detail-context-row strong { color: var(--tm-text); }
.memory-detail-reading-actions { display: flex; gap: 10px; align-items: center; }
.reading-favorite { padding: 9px 13px; border: 1px solid var(--tm-border); border-radius: 8px; background: transparent; color: var(--tm-text-muted); font: inherit; font-size: 13px; }
.reading-favorite:hover { border-color: var(--tm-accent); color: var(--tm-accent); }
.reading-map-link { padding: 9px 2px; font-size: 13px; }
.memory-detail-content.text-only { grid-template-columns: minmax(0, 900px); justify-content: center; }
.memory-detail-content.text-only .memory-detail-reading { min-height: 420px; padding: 34px 38px; border: 1px solid var(--tm-border); border-radius: var(--tm-radius-lg); background: rgba(251, 249, 244, .72); box-shadow: var(--tm-shadow-soft); }
.memory-continuity { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-top: 42px; }
.continuity-card { display: grid; gap: 7px; min-height: 92px; padding: 17px 19px; border: 1px solid var(--tm-border); border-radius: var(--tm-radius-md); background: rgba(251, 249, 244, .68); color: var(--tm-text); transition: border-color .15s ease, transform .15s ease; }
.continuity-card:not(.is-empty):hover { border-color: var(--tm-accent); transform: translateY(-2px); }
.continuity-card small, .continuity-card span { color: var(--tm-text-muted); font-size: 12px; }
.continuity-card strong { overflow: hidden; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.continuity-card.is-empty { align-content: center; background: transparent; color: var(--tm-text-muted); }
@media (max-width: 760px) {
  .memory-detail-page { width: min(100%, calc(100vw - 24px)); padding-top: 0; }
  .memory-detail-topbar { min-height: 54px; padding: 0 2px; }
  .memory-back-link { font-size: 0; }
  .memory-back-link::before { content: '←'; font-size: 25px; line-height: 1; }
  .memory-breadcrumb { display: none; }
  .memory-detail-content { gap: 22px; grid-template-columns: 1fr; width: calc(100% + 24px); margin-left: -12px; }
  .memory-detail-photo-column :deep(.gallery-main) { border-radius: 0; box-shadow: none; }
  .memory-detail-photo-column :deep(.gallery-main img.gallery-image-contain) { border-radius: 0; max-height: min(52vh, 430px); }
  .memory-detail-photo-column :deep(.gallery-thumbs), .memory-photo-instruction { width: calc(100% - 24px); margin-right: auto; margin-left: auto; }
  .memory-detail-reading { min-height: 0; gap: 18px; padding: 0 12px; }
  .memory-detail-quote { font-size: 27px; line-height: 1.45; }
  .memory-detail-context { padding: 14px 0; }
  .memory-detail-content.text-only { width: 100%; margin-left: 0; }
  .memory-detail-content.text-only .memory-detail-reading { min-height: 380px; padding: 24px 18px; }
  .memory-continuity { gap: 10px; margin-top: 28px; }
  .continuity-card { min-height: 76px; padding: 12px; }
  .continuity-card strong { font-size: 13px; }
  .continuity-card span { display: none; }
}
</style>
