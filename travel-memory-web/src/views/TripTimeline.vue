<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { clearTripCover, getTrip, setTripCover } from '../api/trip'
import { deleteMemory, favoriteMemory, getTimeline, searchMemories } from '../api/memory'
import { hasExplicitTripCover, normalizePhotoUrl, resolveTripCoverUrl } from '../utils/tripCover'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
  favoriteOnly: {
    type: Boolean,
    default: false,
  },
})

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

const displayedMemories = computed(() => (
  props.favoriteOnly
    ? memories.value.filter((memory) => memory.isFavorite === 1)
    : memories.value
))

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
      const group = {
        date: dateKey,
        dayLabel: `第 ${groups.length + 1} 天`,
        memories: [],
      }
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

const photoCount = computed(() => displayedMemories.value.reduce((total, memory) => total + (memory.photoCount || (memory.photoUrl ? 1 : 0)), 0))

const tripDateRange = computed(() => {
  if (!trip.value) {
    return ''
  }
  const start = trip.value.startDate || '未知开始'
  const end = trip.value.endDate || '未知结束'
  return `${start} - ${end}`
})

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

function formatDateTime(value) {
  if (!value) {
    return '未知时间'
  }
  const rawValue = String(value)
  if (rawValue.length >= 16) {
    return `${rawValue.slice(0, 10)} ${rawValue.slice(11, 16)}`
  }
  return rawValue
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

async function clearCover() {
  if (!hasExplicitCover.value || coverActionId.value) {
    return
  }
  if (!window.confirm('确定取消自定义封面吗？之后会自动使用第一张旅行照片。')) {
    return
  }

  coverMessage.value = ''
  coverError.value = ''
  coverActionId.value = 'clear'
  try {
    trip.value = await clearTripCover(props.id)
    coverImageFailed.value = false
    coverMessage.value = '已取消自定义封面。'
  } catch (err) {
    coverError.value = err.message || '取消自定义封面失败，请稍后再试。'
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
  try {
    searchResults.value = await searchMemories(props.id, keyword)
  } catch (err) {
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

async function removeMemory(id) {
  if (!window.confirm('确定删除这条记忆吗？')) {
    return
  }
  try {
    await deleteMemory(id)
    await loadPage()
  } catch (err) {
    window.alert(err.message || '删除失败')
  }
}

async function toggleFavorite(memory) {
  try {
    const nextFavorite = memory.isFavorite !== 1
    await favoriteMemory(memory.id, nextFavorite)
    memory.isFavorite = nextFavorite ? 1 : 0
    if (props.favoriteOnly && !nextFavorite) {
      memories.value = memories.value.filter((item) => item.id !== memory.id)
    }
  } catch (err) {
    window.alert(err.message || '操作失败')
  }
}

onMounted(loadPage)
</script>

<template>
  <section class="timeline-page">
    <header class="trip-memory-hero">
      <img
        v-if="coverPhotoUrl"
        class="trip-memory-cover"
        :src="photoSrc(coverPhotoUrl)"
        alt="旅行封面"
        @error="coverImageFailed = true"
      />
      <div class="trip-memory-hero-content">
        <p class="journey-kicker">{{ favoriteOnly ? '收藏回看' : '时间线' }}</p>
        <h1>{{ favoriteOnly ? '想再回看的片段' : '这次旅行的记忆' }}</h1>
        <p class="trip-memory-subtitle">{{ favoriteOnly ? '那些被你认真标记过的瞬间，慢慢再看一遍。' : '按记录时间整理，保留当时留下的照片和一句话。' }}</p>
        <div class="trip-memory-info">
          <strong>{{ trip?.title || '这次旅行' }}</strong>
          <span>{{ trip?.destination || '未填写目的地' }}</span>
          <span>{{ tripDateRange }}</span>
        </div>
        <div class="trip-memory-stats">
          <span>{{ dayGroups.length }} 天</span>
          <span>{{ displayedMemories.length }} 段记忆</span>
          <span>{{ photoCount }} 张照片</span>
        </div>
      </div>
    </header>

    <div class="view-tabs">
      <RouterLink :to="`/trips/${id}`" :class="['view-tab', { active: !favoriteOnly }]">时间线</RouterLink>
      <RouterLink :to="`/trips/${id}/journey`" class="view-tab view-tab-journey">旅程回放</RouterLink>
      <RouterLink :to="`/trips/${id}/map`" class="view-tab">地图</RouterLink>
      <RouterLink :to="`/trips/${id}/favorites`" :class="['view-tab', { active: favoriteOnly }]">收藏回看</RouterLink>
    </div>

    <div class="timeline-toolbar">
      <div>
        <h2>{{ favoriteOnly ? '收藏回看' : '原始记忆记录' }}</h2>
        <p class="muted">{{ favoriteOnly ? '只留下这趟旅行里你收藏的片段。' : '这里保留每一段记忆，可以搜索、编辑、收藏或删除。' }}</p>
      </div>
      <div class="actions">
        <RouterLink :to="`/trips/${id}/memories/new`">
          <button class="secondary">新增记忆</button>
        </RouterLink>
        <RouterLink :to="`/trips/${id}/edit`">
          <button class="ghost">编辑旅行</button>
        </RouterLink>
      </div>
    </div>

    <div v-if="hasExplicitCover" class="cover-context">
      <span>当前使用自定义旅行封面</span>
      <button class="cover-clear-link" :disabled="coverActionId" @click="clearCover">
        {{ coverActionId === 'clear' ? '恢复中…' : '恢复自动封面' }}
      </button>
    </div>
    <p v-if="coverMessage" class="muted">{{ coverMessage }}</p>
    <p v-if="coverError" class="error">{{ coverError }}</p>

    <p v-if="loading" class="timeline-status">正在整理这次旅行的记忆...</p>
    <p v-if="error" class="error timeline-status">{{ error }}</p>

    <section v-if="!favoriteOnly" class="memory-search">
      <form class="search-form" @submit.prevent="doSearch">
        <input v-model="searchKeyword" placeholder="搜索一句话或地点" />
        <button type="submit" class="ghost" :disabled="searchLoading">搜索</button>
        <button v-if="hasSearched" type="button" class="ghost" @click="clearSearch">清空</button>
      </form>
      <p v-if="searchLoading" class="muted">正在搜索记忆...</p>
      <p v-if="searchError" class="error">{{ searchError }}</p>
      <div v-if="hasSearched && !searchLoading" class="search-summary">
        <template v-if="searchResults.length > 0">
          找到 {{ searchResults.length }} 段记忆
        </template>
        <template v-else>
          没有找到相关记忆。可以换一句话或地点再试试。
        </template>
      </div>
      <div v-if="hasSearched && searchResults.length > 0" class="search-results">
        <article v-for="memory in searchResults" :key="memory.id" class="search-result-item">
          <img
            v-if="memory.photoUrl"
            class="search-result-photo"
            :src="photoSrc(memory.photoUrl)"
            alt="旅行记忆照片"
          />
          <div v-else class="search-result-photo empty">没有照片</div>
          <div class="search-result-body">
            <div class="search-result-meta">
              <span>{{ formatDateTime(memory.recordTime) }}</span>
              <span>{{ memory.locationName || '未填写地点' }}</span>
            </div>
            <p>“{{ memory.content || '没有文字记录' }}”</p>
          </div>
        </article>
      </div>
    </section>

    <div v-if="!loading && displayedMemories.length === 0" class="timeline-empty">
      <h2>{{ favoriteOnly ? '还没有特别标记的片段。' : '这次旅行还没有留下记忆。' }}</h2>
      <p>{{ favoriteOnly ? '回到时间线，收藏那些你想以后再慢慢看的瞬间。' : '上传一张照片，写一句当时想记住的话。' }}</p>
      <RouterLink :to="`/trips/${id}/memories/new`">
        <button>留下第一段记忆</button>
      </RouterLink>
    </div>

    <div v-if="!loading && displayedMemories.length > 0" class="day-timeline">
      <section v-for="group in dayGroups" :key="group.date" class="day-section">
        <div class="day-header">
          <div>
            <h2>{{ group.dayLabel }} · {{ group.date }}</h2>
            <p>这一天留下了 {{ group.memories.length }} 段记忆</p>
          </div>
        </div>

        <div class="timeline-list">
          <article v-for="memory in group.memories" :key="memory.id" class="memory-item">
            <div class="memory-time">{{ formatTime(memory.recordTime) }}</div>
            <div class="timeline-marker" aria-hidden="true">
              <span></span>
            </div>
            <div class="memory-card" :class="{ 'has-photo': Boolean(memory.photoUrl) }">
              <div class="memory-main">
                <div v-if="memory.photoUrl" class="memory-photo-frame">
                  <MemoryPhotoGallery :photos="memory.photos" :fallback-url="photoSrc(memory.photoUrl)" fit="contain" layout="timeline" count-label="张照片" alt="旅行记忆照片" />
                </div>
                <div v-else class="memory-photo-empty">这段记忆没有照片，文字还在。</div>
              </div>

              <div class="memory-details">
                <p :class="['memory-quote', { muted: !memory.content }]">
                  <template v-if="memory.content">“{{ memory.content }}”</template>
                  <template v-else>这一刻没有留下文字</template>
                </p>

                <div class="memory-title-line">
                  <p :class="['memory-location', { muted: !memory.locationName }]">{{ memory.locationName || '地点还没有补充' }}</p>
                </div>

                <div class="actions memory-actions">
                  <button
                    :class="memory.isFavorite === 1 ? 'favorite active' : 'favorite'"
                    @click="toggleFavorite(memory)"
                  >
                    {{ memory.isFavorite === 1 ? '取消收藏' : '收藏' }}
                  </button>
                  <span v-if="memory.photoUrl && isExplicitCover(memory)" class="favorite-badge">当前封面</span>
                  <details class="memory-more">
                    <summary aria-label="更多操作">更多</summary>
                    <div class="memory-more-menu">
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
          </article>
        </div>
      </section>
    </div>
  </section>
</template>
