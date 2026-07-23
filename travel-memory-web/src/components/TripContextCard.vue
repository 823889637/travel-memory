<script setup>
import { computed, ref, watch } from 'vue'
import { CalendarDays } from '@lucide/vue'
import { normalizePhotoUrl } from '../utils/tripCover'

const props = defineProps({
  trip: { type: Object, required: true },
  memories: { type: Array, default: () => [] },
  variant: { type: String, default: 'compact' },
})

const failedCoverUrls = ref(new Set())
const coverCandidates = computed(() => {
  const memoryUrls = props.memories.flatMap((memory) => [
    normalizePhotoUrl(memory?.photoUrl),
    ...(Array.isArray(memory?.photos) ? memory.photos.map(photo => normalizePhotoUrl(photo?.photoUrl)) : []),
  ])

  return [...new Set([
    normalizePhotoUrl(props.trip?.coverPhotoUrl),
    normalizePhotoUrl(props.trip?.effectiveCoverPhotoUrl),
    ...memoryUrls,
  ].filter(Boolean))]
})
const coverUrl = computed(() => (
  coverCandidates.value.find(url => !failedCoverUrls.value.has(url)) || ''
))

const headline = computed(() => {
  const destination = String(props.trip?.destination || '').trim()
  const title = String(props.trip?.title || '').trim()
  const parts = [destination, title].filter(Boolean)
  return parts.join(' · ') || '这次旅行'
})

const dateRange = computed(() => {
  const start = formatDate(props.trip?.startDate)
  const end = formatDate(props.trip?.endDate || props.trip?.startDate)
  if (!start && !end) return '日期待补充'
  if (!start) return end
  if (!end || end === start) return start
  return `${start} – ${end}`
})

function formatDate(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? `${match[1]}.${match[2]}.${match[3]}` : ''
}

function markCoverFailed(url) {
  if (!url) return
  failedCoverUrls.value = new Set(failedCoverUrls.value).add(url)
}

watch(coverCandidates, () => {
  failedCoverUrls.value = new Set()
})
</script>

<template>
  <article :class="['trip-context-card', `trip-context-card--${variant}`]">
    <div class="trip-context-cover" aria-hidden="true">
      <img v-if="coverUrl" :src="coverUrl" alt="" @error="markCoverFailed(coverUrl)" />
      <span v-else>{{ (trip.destination || trip.title || '旅').slice(0, 1) }}</span>
    </div>
    <div class="trip-context-copy">
      <h1>{{ headline }}</h1>
      <p><CalendarDays :size="17" :stroke-width="1.7" aria-hidden="true" />{{ dateRange }}</p>
    </div>
    <div class="trip-context-actions"><slot name="actions" /></div>
  </article>
</template>

<style scoped>
.trip-context-card {
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  min-width: 0;
  padding: 10px 2px 14px;
  background: transparent;
}

.trip-context-cover {
  display: grid;
  width: 78px;
  aspect-ratio: 1;
  place-items: center;
  overflow: hidden;
  border-radius: 16px;
  background: var(--tm-accent-soft);
  color: var(--tm-accent);
  font-family: var(--tm-font-serif);
  font-size: 25px;
  font-weight: 700;
  box-shadow: 0 7px 18px rgba(69, 51, 39, .11);
}

.trip-context-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.trip-context-copy {
  display: grid;
  min-width: 0;
  gap: 8px;
}

.trip-context-copy h1,
.trip-context-copy p {
  margin: 0;
}

.trip-context-copy h1 {
  overflow: hidden;
  color: var(--tm-text);
  font-family: var(--tm-font-serif);
  font-size: 22px;
  font-weight: 600;
  line-height: 30px;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-context-copy p {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: var(--tm-text-muted);
  font-family: var(--tm-font-sans);
  font-size: 13px;
  line-height: 20px;
}

.trip-context-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 760px) {
  .trip-context-card {
    grid-template-columns: 72px minmax(0, 1fr) auto;
    gap: 14px;
    padding: 8px 10px 13px;
  }

  .trip-context-cover {
    width: 72px;
    border-radius: 15px;
    font-size: 23px;
  }

  .trip-context-copy {
    gap: 5px;
  }

  .trip-context-copy h1 {
    font-size: 21px;
    line-height: 28px;
  }

  .trip-context-copy p {
    gap: 5px;
    font-size: 12px;
    line-height: 18px;
  }
}

@media (max-width: 370px) {
  .trip-context-card {
    grid-template-columns: 64px minmax(0, 1fr);
    gap: 12px;
    padding-inline: 6px;
  }

  .trip-context-cover {
    width: 64px;
    border-radius: 13px;
  }

  .trip-context-copy h1 {
    font-size: 19px;
    line-height: 26px;
  }

  .trip-context-actions {
    display: none;
  }
}
</style>
