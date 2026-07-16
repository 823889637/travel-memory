<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  CalendarDays,
  ChevronDown,
  Image as ImageIcon,
  MapPin,
  MoreHorizontal,
  NotebookText,
  Plus,
  Search,
  Star,
  X,
} from '@lucide/vue'
import { getTrip, setTripCover } from '../api/trip'
import { deleteMemory, favoriteMemory, getTimeline, searchMemories } from '../api/memory'
import { isCoordinateInChina } from '../utils/coordinates'
import { hasExplicitTripCover, normalizePhotoUrl, resolveTripCoverUrl } from '../utils/tripCover'
import { getChronologicalTripDayNumber } from '../utils/tripDay'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'
import TripViewNav from '../components/TripViewNav.vue'
import MobilePageHeader from '../components/MobilePageHeader.vue'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const route = useRoute()
const router = useRouter()

const trip = ref(null)
const memories = ref([])
const loading = ref(false)
const error = ref('')
const searchKeyword = ref('')
const searchResults = ref([])
const searchLoading = ref(false)
const searchError = ref('')
const hasSearched = ref(false)
const coverActionId = ref(null)
const coverMessage = ref('')
const coverError = ref('')
const coverImageFailed = ref(false)
const collapsedDates = ref(new Set())

const favoriteFilterActive = computed(() => route.query.favorite === 'true')
const baseMemories = computed(() => memories.value)

const displayedMemories = computed(() => {
  const source = hasSearched.value ? searchResults.value : baseMemories.value
  return favoriteFilterActive.value
    ? source.filter(memory => memory.isFavorite === 1)
    : source
})

const dayGroups = computed(() => {
  const groups = []
  const groupMap = new Map()

  const sortedMemories = [...displayedMemories.value].sort((left, right) => {
    const leftTime = getTimeValue(left.recordTime)
    const rightTime = getTimeValue(right.recordTime)
    if (leftTime !== rightTime) {
      return leftTime - rightTime
    }
    return getTimeValue(left.createTime) - getTimeValue(right.createTime)
  })

  sortedMemories.forEach((memory) => {
    const dateKey = getDateKey(memory.recordTime)
    if (!groupMap.has(dateKey)) {
      const previousDayNumber = groups.at(-1)?.dayNumber || 0
      const fallbackDayNumber = groups.length + 1
      const group = {
        date: dateKey,
        dayNumber: getChronologicalTripDayNumber(trip.value?.startDate, dateKey, fallbackDayNumber, previousDayNumber),
        memories: [],
      }
      group.dayLabel = `第 ${group.dayNumber} 天`
      groupMap.set(dateKey, group)
      groups.push(group)
    }
    groupMap.get(dateKey).memories.push(memory)
  })

  return groups
})

const coverPhotoUrl = computed(() => {
  return coverImageFailed.value ? '' : resolveTripCoverUrl(trip.value, memories.value)
})

const hasExplicitCover = computed(() => hasExplicitTripCover(trip.value))

const photoCount = computed(() => baseMemories.value.reduce((total, memory) => total + (memory.photoCount || (memory.photoUrl ? 1 : 0)), 0))

const tripDayCount = computed(() => {
  const start = parseDateOnly(trip.value?.startDate)
  const end = parseDateOnly(trip.value?.endDate)
  if (start != null && end != null && end >= start) {
    return Math.floor((end - start) / 86400000) + 1
  }
  return new Set(baseMemories.value.map(memory => getDateKey(memory.recordTime))).size
})

const tripDateRange = computed(() => {
  if (!trip.value) {
    return ''
  }
  const start = formatDateLabel(trip.value.startDate) || '未知开始'
  const end = formatDateLabel(trip.value.endDate) || '未知结束'
  return `${start} - ${end}`
})

const tripPlaceLabel = computed(() => {
  const explicitCountry = String(trip.value?.destinationCountry || '').trim()
  const coordinateSuggestsChina = isCoordinateInChina(
    trip.value?.destinationLatitude,
    trip.value?.destinationLongitude,
  ) || memories.value.some(memory => isCoordinateInChina(memory.latitude, memory.longitude))
  const country = explicitCountry || (coordinateSuggestsChina ? '中国' : '')
  const names = [country, trip.value?.destination]
    .map(value => String(value || '').trim())
    .filter(Boolean)
  return [...new Set(names)].join(' · ') || '未填写目的地'
})

function parseDateOnly(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!match) return null
  return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

function formatDateLabel(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? `${match[1]}.${match[2]}.${match[3]}` : ''
}

function getTimeValue(value) {
  if (!value) {
    return Number.MAX_SAFE_INTEGER
  }
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? Number.MAX_SAFE_INTEGER : time
}

function photoSrc(url) {
  return url || ''
}

function hasMemoryPhoto(memory) {
  return Boolean(memory?.photoUrl || memory?.photos?.some(photo => photo?.photoUrl))
}

function getDateKey(value) {
  if (!value) {
    return '未知日期'
  }
  return String(value).slice(0, 10)
}

function formatTime(value) {
  if (!value) {
    return '--:--'
  }
  const rawValue = String(value)
  if (rawValue.length >= 16) {
    return rawValue.slice(11, 16)
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--:--'
  }
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function isDayCollapsed(date) {
  return collapsedDates.value.has(date)
}

function toggleDay(date) {
  const next = new Set(collapsedDates.value)
  if (next.has(date)) next.delete(date)
  else next.add(date)
  collapsedDates.value = next
}

function openMemory(memory) {
  router.push(`/trips/${props.id}/memories/${memory.id}`)
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
    coverImageFailed.value = false
  } catch (err) {
    error.value = err.message || '记忆暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

function isExplicitCover(memory) {
  return hasExplicitCover.value
    && normalizePhotoUrl(trip.value?.coverPhotoUrl) === normalizePhotoUrl(memory.photoUrl)
}

async function setCover(memory) {
  if (!memory.photoUrl || coverActionId.value) {
    return
  }

  coverMessage.value = ''
  coverError.value = ''
  coverActionId.value = memory.id
  try {
    trip.value = await setTripCover(props.id, memory.id)
    coverImageFailed.value = false
    coverMessage.value = '已设为旅行封面。'
  } catch (err) {
    coverError.value = err.message || '设置旅行封面失败，请稍后再试。'
  } finally {
    coverActionId.value = null
  }
}

async function doSearch() {
  const keyword = searchKeyword.value.trim()
  searchError.value = ''
  if (!keyword) {
    clearSearch()
    return
  }

  searchLoading.value = true
  hasSearched.value = true
  searchResults.value = []
  try {
    const results = await searchMemories(props.id, keyword)
    const normalizedResults = Array.isArray(results) ? results : []
    searchResults.value = normalizedResults
  } catch (err) {
    hasSearched.value = false
    searchError.value = err.message || '搜索暂时没有成功，请稍后再试。'
  } finally {
    searchLoading.value = false
  }
}

function clearSearch() {
  searchKeyword.value = ''
  searchResults.value = []
  searchError.value = ''
  hasSearched.value = false
}

function toggleFavoriteFilter() {
  const query = { ...route.query }
  if (favoriteFilterActive.value) delete query.favorite
  else query.favorite = 'true'
  router.replace({ query }).catch(() => {})
}

async function removeMemory(id) {
  if (!window.confirm('确定删除这条记忆吗？')) {
    return
  }
  try {
    await deleteMemory(id)
    await loadPage()
    if (hasSearched.value) await doSearch()
  } catch (err) {
    window.alert(err.message || '删除失败')
  }
}

async function toggleFavorite(memory) {
  try {
    const nextFavorite = memory.isFavorite !== 1
    await favoriteMemory(memory.id, nextFavorite)
    const value = nextFavorite ? 1 : 0
    memory.isFavorite = value
    const sourceMemory = memories.value.find(item => String(item.id) === String(memory.id))
    if (sourceMemory) sourceMemory.isFavorite = value
    const searchMemory = searchResults.value.find(item => String(item.id) === String(memory.id))
    if (searchMemory) searchMemory.isFavorite = value
  } catch (err) {
    window.alert(err.message || '操作失败')
  }
}

onMounted(loadPage)
</script>

<template>
  <section class="timeline-page">
    <MobilePageHeader title="时间线" back-to="/trips" />
    <header v-if="!loading && trip" :class="['trip-memory-hero', { 'has-cover': coverPhotoUrl }]">
      <img
        v-if="coverPhotoUrl"
        class="trip-memory-cover"
        :src="photoSrc(coverPhotoUrl)"
        alt="旅行封面"
        @error="coverImageFailed = true"
      />
      <div class="trip-memory-hero-content">
        <p class="journey-kicker">时间线</p>
        <h1>{{ trip?.title || '这次旅行的记忆' }}</h1>
        <p class="trip-memory-subtitle">{{ trip?.description || '按记录时间整理，保留当时留下的照片和一句话。' }}</p>
        <div class="trip-memory-info">
          <span><MapPin :size="15" :stroke-width="1.8" aria-hidden="true" />{{ tripPlaceLabel }}</span>
          <span><CalendarDays :size="15" :stroke-width="1.8" aria-hidden="true" />{{ tripDateRange }}</span>
        </div>
        <div class="trip-memory-stats">
          <span><CalendarDays :size="14" aria-hidden="true" />{{ tripDayCount }} 天</span>
          <span><NotebookText :size="14" aria-hidden="true" />{{ baseMemories.length }} 段记忆</span>
          <span><ImageIcon :size="14" aria-hidden="true" />{{ photoCount }} 张照片</span>
        </div>
      </div>
    </header>

    <TripViewNav v-if="!loading && trip" :trip-id="id" active="timeline" />

    <div v-if="!loading && trip" class="timeline-toolbar">
      <div>
        <h2>原始记忆记录</h2>
        <p class="muted">沿着时间线，重温旅途中每一刻的心动。</p>
      </div>
      <div class="actions">
        <RouterLink class="timeline-add-memory-link" :to="`/trips/${id}/memories/new`">
          <button class="timeline-add-memory-button"><Plus :size="17" aria-hidden="true" />新增记忆</button>
        </RouterLink>
      </div>
    </div>

    <p v-if="coverMessage" class="muted">{{ coverMessage }}</p>
    <p v-if="coverError" class="error">{{ coverError }}</p>

    <p v-if="loading" class="timeline-status">正在整理这次旅行的记忆...</p>
    <p v-if="error" class="error timeline-status">{{ error }}</p>

    <section v-if="!loading && trip" class="memory-search">
      <form class="search-form" @submit.prevent="doSearch">
        <div class="memory-search-field">
          <button type="submit" class="memory-search-submit" :disabled="searchLoading" aria-label="搜索记忆">
            <Search :size="19" :stroke-width="1.8" aria-hidden="true" />
          </button>
          <input
            v-model="searchKeyword"
            placeholder="搜索地点、事件或回忆…"
            aria-label="搜索记忆"
            enterkeyhint="search"
          />
          <button v-if="hasSearched" type="button" class="memory-search-clear" aria-label="清空搜索" @click="clearSearch">
            <X :size="18" :stroke-width="1.8" aria-hidden="true" />
          </button>
        </div>
        <button
          type="button"
          :class="['memory-favorite-filter', { active: favoriteFilterActive }]"
          :aria-pressed="favoriteFilterActive"
          @click="toggleFavoriteFilter"
        >
          <Star :size="17" :fill="favoriteFilterActive ? 'currentColor' : 'none'" aria-hidden="true" />
          收藏
        </button>
      </form>
      <p v-if="searchLoading" class="muted">正在搜索记忆...</p>
      <p v-if="searchError" class="error">{{ searchError }}</p>
      <div v-if="hasSearched && !searchLoading && displayedMemories.length > 0" class="search-summary">
        找到 {{ displayedMemories.length }} 段记忆，下面只显示这些结果。
      </div>
    </section>

    <div v-if="!loading && !searchLoading && trip && displayedMemories.length === 0" class="timeline-empty">
      <template v-if="hasSearched">
        <h2>没有找到相关记忆。</h2>
        <p>换一句原话或地点试试，也可以清空搜索回到完整时间线。</p>
      </template>
      <template v-else>
        <h2>{{ favoriteFilterActive ? '还没有特别标记的片段。' : '这次旅行还没有留下记忆。' }}</h2>
        <p>{{ favoriteFilterActive ? '取消收藏筛选，继续浏览这趟旅行的全部记忆。' : '上传一张照片，写一句当时想记住的话。' }}</p>
      </template>
      <button v-if="!hasSearched && favoriteFilterActive" type="button" @click="toggleFavoriteFilter">查看全部记忆</button>
      <RouterLink v-else-if="!hasSearched" :to="`/trips/${id}/memories/new`">
        <button>留下第一段记忆</button>
      </RouterLink>
    </div>

    <div v-if="!loading && !searchLoading && trip && displayedMemories.length > 0" class="day-timeline">
      <section v-for="group in dayGroups" :key="group.date" class="day-section">
        <button
          type="button"
          class="day-header"
          :aria-expanded="!isDayCollapsed(group.date)"
          @click="toggleDay(group.date)"
        >
          <CalendarDays class="day-header-icon" :size="19" :stroke-width="1.8" aria-hidden="true" />
          <div>
            <h2>
              <span class="day-title">{{ group.dayLabel }}</span>
              <span class="day-divider">·</span>
              <span class="day-date">{{ formatDateLabel(group.date) || group.date }}</span>
            </h2>
            <p>这一天留下了 {{ group.memories.length }} 段记忆</p>
          </div>
          <ChevronDown :size="18" :class="{ collapsed: isDayCollapsed(group.date) }" aria-hidden="true" />
        </button>

        <div v-if="!isDayCollapsed(group.date)" class="timeline-list">
          <article v-for="memory in group.memories" :key="memory.id" class="memory-item">
            <div class="memory-time">
              <span>{{ formatTime(memory.recordTime) }}</span>
              <ImageIcon v-if="hasMemoryPhoto(memory)" :size="15" :stroke-width="1.7" aria-label="包含照片" />
            </div>
            <div class="timeline-marker" aria-hidden="true">
              <span></span>
            </div>
            <div
              class="memory-card"
              :class="{ 'has-photo': hasMemoryPhoto(memory) }"
              role="link"
              tabindex="0"
              :aria-label="`查看记忆：${memory.content || memory.locationName || '这段记忆'}`"
              @click="openMemory(memory)"
              @keydown.enter="openMemory(memory)"
            >
              <div class="memory-main">
                <div v-if="hasMemoryPhoto(memory)" class="memory-photo-frame">
                  <MemoryPhotoGallery
                    :photos="memory.photos"
                    :fallback-url="photoSrc(memory.photoUrl)"
                    fit="cover"
                    layout="timeline"
                    count-label="张照片"
                    alt="旅行记忆照片"
                  />
                </div>
                <div v-else class="memory-photo-empty">这段记忆没有照片，文字还在。</div>
              </div>

              <div class="memory-details">
                <p :class="['memory-quote', { muted: !memory.content }]">
                  <template v-if="memory.content">“{{ memory.content }}”</template>
                  <template v-else>这一刻没有留下文字</template>
                </p>

                <p v-if="memory.companions?.length" class="memory-companions">
                  和 {{ memory.companions.map(item => item.name).join('、') }} 一起
                </p>

                <div class="memory-card-footer">
                  <div class="memory-title-line">
                    <MapPin :size="15" :stroke-width="1.8" aria-hidden="true" />
                    <p :class="['memory-location', { muted: !memory.locationName }]">{{ memory.locationName || '地点还没有补充' }}</p>
                  </div>

                  <div class="actions memory-actions" @click.stop @keydown.stop>
                    <button
                      :class="memory.isFavorite === 1 ? 'favorite active' : 'favorite'"
                      :aria-label="memory.isFavorite === 1 ? '取消收藏' : '收藏'"
                      :title="memory.isFavorite === 1 ? '取消收藏' : '收藏'"
                      @click="toggleFavorite(memory)"
                    >
                      <Star :size="16" :fill="memory.isFavorite === 1 ? 'currentColor' : 'none'" aria-hidden="true" />
                      <span>{{ memory.isFavorite === 1 ? '取消收藏' : '收藏' }}</span>
                    </button>
                    <span v-if="memory.photoUrl && isExplicitCover(memory)" class="favorite-badge">当前封面</span>
                    <details class="memory-more">
                      <summary aria-label="更多操作">
                        <MoreHorizontal :size="19" :stroke-width="1.8" aria-hidden="true" />
                      </summary>
                      <div class="memory-more-menu">
                        <RouterLink :to="`/trips/${id}/memories/${memory.id}`">
                          <button class="ghost">查看详情</button>
                        </RouterLink>
                        <RouterLink :to="`/trips/${id}/memories/${memory.id}/edit`">
                          <button class="ghost">编辑</button>
                        </RouterLink>
                        <button
                          v-if="memory.photoUrl && !isExplicitCover(memory)"
                          class="ghost"
                          :disabled="Boolean(coverActionId)"
                          @click="setCover(memory)"
                        >
                          {{ coverActionId === memory.id ? '设置中…' : '设为旅行封面' }}
                        </button>
                        <button class="ghost danger-text" @click="removeMemory(memory.id)">删除</button>
                      </div>
                    </details>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>
