<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { Clock3, MapPin } from '@lucide/vue'
import { getTrip } from '../api/trip'
import { getTimeline } from '../api/memory'
import { getChronologicalTripDayNumber } from '../utils/tripDay'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'
import TripViewNav from '../components/TripViewNav.vue'
import MobilePageHeader from '../components/MobilePageHeader.vue'
import TripContextCard from '../components/TripContextCard.vue'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const trip = ref(null)
const memories = ref([])
const loading = ref(false)
const error = ref('')
const activeDate = ref('')
const journeyPhotoLayouts = ref({})
const dayButtonElements = new Map()

const dayGroups = computed(() => {
  const groups = []
  const groupMap = new Map()

  sortedMemories(memories.value).forEach((memory) => {
    const date = dateKey(memory.recordTime)
    if (!groupMap.has(date)) {
      const group = { date, memories: [] }
      groupMap.set(date, group)
      groups.push(group)
    }
    groupMap.get(date).memories.push(memory)
  })

  let previousDayNumber = 0
  return groups.map((group, groupIndex) => {
    const fallbackDayNumber = groupIndex + 1
    const dayNumber = getChronologicalTripDayNumber(
      trip.value?.startDate,
      group.date,
      fallbackDayNumber,
      previousDayNumber,
    )
    previousDayNumber = dayNumber

    return {
      ...group,
      dayNumber,
      locationCount: new Set(
        group.memories
          .map(memory => String(memory.locationName || '').trim())
          .filter(Boolean),
      ).size,
    }
  })
})

const visibleDayGroups = computed(() => {
  const activeGroup = dayGroups.value.find(group => group.date === activeDate.value)
  return activeGroup ? [activeGroup] : dayGroups.value.slice(0, 1)
})

function timeValue(value) {
  if (!value) return Number.MAX_SAFE_INTEGER
  const parsed = new Date(value).getTime()
  return Number.isNaN(parsed) ? Number.MAX_SAFE_INTEGER : parsed
}

function compareIds(left, right) {
  const leftNumber = Number(left)
  const rightNumber = Number(right)
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) return leftNumber - rightNumber
  return String(left ?? '').localeCompare(String(right ?? ''))
}

function sortedMemories(source) {
  return [...source].sort((left, right) => (
    timeValue(left.recordTime) - timeValue(right.recordTime)
    || timeValue(left.createTime) - timeValue(right.createTime)
    || compareIds(left.id, right.id)
  ))
}

function dateKey(value) {
  const raw = String(value || '')
  return /^\d{4}-\d{2}-\d{2}/.test(raw) ? raw.slice(0, 10) : '未知日期'
}

function formatTime(value) {
  if (!value) return '--:--'
  const raw = String(value)
  if (raw.length >= 16) return raw.slice(11, 16)
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return '--:--'
  return parsed.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}

function formatShortDate(value) {
  const match = String(value || '').match(/^\d{4}-(\d{2})-(\d{2})/)
  return match ? `${match[1]}.${match[2]}` : value
}

function memoryPhotos(memory) {
  return memory?.photos?.length ? memory.photos : (memory?.photoUrl ? [{ photoUrl: memory.photoUrl }] : [])
}

function hasPhotos(memory) {
  return memoryPhotos(memory).some(photo => photo?.photoUrl)
}

function memoryPhotoCount(memory) {
  return new Set(
    memoryPhotos(memory)
      .map(photo => String(photo?.photoUrl || '').trim())
      .filter(Boolean),
  ).size
}

function setJourneyPhotoLayout(memoryId, layout) {
  if (memoryId == null || !layout) return
  journeyPhotoLayouts.value = {
    ...journeyPhotoLayouts.value,
    [memoryId]: layout,
  }
}

function hasEditorialPhotoLayout(memory) {
  return journeyPhotoLayouts.value[memory?.id]?.variant === 'editorial-landscape-support'
}

function setDayButtonRef(date, element) {
  if (element) dayButtonElements.set(date, element)
  else dayButtonElements.delete(date)
}

function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

function selectDay(date) {
  activeDate.value = date
  syncActiveDayButton()
}

function syncActiveDayButton() {
  dayButtonElements.get(activeDate.value)?.scrollIntoView({
    behavior: prefersReducedMotion() ? 'auto' : 'smooth',
    block: 'nearest',
    inline: 'center',
  })
}

async function loadPage() {
  loading.value = true
  error.value = ''
  journeyPhotoLayouts.value = {}
  try {
    const [tripData, timelineData] = await Promise.all([
      getTrip(props.id),
      getTimeline(props.id),
    ])
    trip.value = tripData
    memories.value = Array.isArray(timelineData) ? timelineData : []
    activeDate.value = dayGroups.value[0]?.date || ''
  } catch (err) {
    error.value = err.message || '旅程回放暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }

}

watch(() => props.id, loadPage)
onMounted(loadPage)
</script>

<template>
  <section class="journey-page journey-reader-page">
    <MobilePageHeader title="旅程回放" back-to="/trips" />
    <TripContextCard v-if="!loading && trip" :trip="trip" :memories="memories" variant="compact" />

    <TripViewNav v-if="!loading && trip" :trip-id="id" active="journey" />

    <p v-if="loading" class="journey-state">正在整理这趟旅行...</p>
    <div v-else-if="error" class="journey-state journey-state-error">
      <p>{{ error }}</p>
      <button type="button" @click="loadPage">重新加载</button>
    </div>

    <div v-else-if="trip && dayGroups.length === 0" class="journey-empty">
      <p class="journey-empty-title">这趟旅行还在等待第一段记忆</p>
      <p class="journey-empty-hint">留下照片、原话、时间和地点后，就可以从这里重新走一遍。</p>
      <RouterLink :to="`/trips/${id}/memories/new`"><button type="button">新增第一段记忆</button></RouterLink>
    </div>

    <template v-else-if="trip">
      <nav class="journey-record-day-nav" aria-label="记录日期导航">
        <div class="journey-record-day-track">
          <button
            v-for="group in dayGroups"
            :key="group.date"
            :ref="element => setDayButtonRef(group.date, element)"
            type="button"
            :class="['journey-record-day-button', { active: activeDate === group.date }]"
            :aria-current="activeDate === group.date ? 'true' : undefined"
            @click="selectDay(group.date)"
          >
            <span>Day {{ group.dayNumber }}</span>
            <small>{{ formatShortDate(group.date) }}</small>
          </button>
        </div>
      </nav>

      <div class="journey-record-days">
        <section
          v-for="group in visibleDayGroups"
          :key="group.date"
          :data-journey-date="group.date"
          class="journey-record-day-section"
        >
          <header class="journey-record-day-header">
            <h2>Day {{ group.dayNumber }} · {{ group.date }}</h2>
            <p>这一天经过了 {{ group.locationCount }} 个地点，留下了 {{ group.memories.length }} 段记忆。</p>
          </header>

          <div class="journey-memory-flow">
            <template v-for="(memory, memoryIndex) in group.memories" :key="memory.id">
              <article
                :class="[
                  'journey-memory-section',
                  {
                    'is-text-only': !hasPhotos(memory),
                    'is-multi-photo': memoryPhotoCount(memory) > 1,
                    'has-right-photo-layout': hasPhotos(memory),
                    'has-editorial-photo-layout': hasEditorialPhotoLayout(memory),
                  },
                ]"
              >
                <div class="journey-memory-copy-panel">
                  <p class="journey-memory-station">第 {{ memoryIndex + 1 }} 站</p>
                  <RouterLink
                    v-if="memory.locationName"
                    class="journey-memory-location"
                    :to="{ path: `/trips/${id}/map`, query: { memoryId: memory.id } }"
                  >
                    {{ memory.locationName }}
                  </RouterLink>
                  <p v-else class="journey-memory-location muted">地点还没有补充</p>
                  <p class="journey-memory-time">
                    <Clock3 :size="14" :stroke-width="1.7" aria-hidden="true" />
                    {{ formatTime(memory.recordTime) }}
                  </p>
                  <RouterLink class="journey-memory-detail-link" :to="`/trips/${id}/memories/${memory.id}`">
                    <span v-if="memory.content">“{{ memory.content }}”</span>
                    <span v-else class="muted">这一刻没有留下文字。</span>
                  </RouterLink>
                  <p v-if="memory.companions?.length" class="journey-memory-companions">
                    和 {{ memory.companions.map(item => item.name).join('、') }} 一起
                  </p>
                  <RouterLink class="journey-memory-view-detail" :to="`/trips/${id}/memories/${memory.id}`">查看详情</RouterLink>
                </div>

                <div v-if="hasPhotos(memory)" class="journey-memory-photos">
                  <MemoryPhotoGallery
                    :photos="memory.photos"
                    :fallback-url="memory.photoUrl || ''"
                    fit="contain"
                    layout="journey"
                    count-label="张照片"
                    :alt="`${memory.locationName || '旅途中'}的记忆照片`"
                    @journey-layout-change="layout => setJourneyPhotoLayout(memory.id, layout)"
                  />
                </div>
              </article>

              <div v-if="memoryIndex < group.memories.length - 1" class="journey-next-memory" aria-hidden="true">
                <span><MapPin :size="14" :stroke-width="1.6" />下一站</span>
              </div>
            </template>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>
