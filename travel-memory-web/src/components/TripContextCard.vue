<script setup>
import { computed, ref, watch } from 'vue'
import { CalendarDays, MapPin } from '@lucide/vue'
import { resolveTripCoverUrl } from '../utils/tripCover'

const props = defineProps({
  trip: { type: Object, required: true },
  variant: { type: String, default: 'compact' },
})

const coverFailed = ref(false)
const coverUrl = computed(() => coverFailed.value ? '' : resolveTripCoverUrl(props.trip))

const dateRange = computed(() => {
  const start = props.trip?.startDate || '日期待补充'
  const end = props.trip?.endDate || props.trip?.startDate || '日期待补充'
  return `${start} — ${end}`
})

const duration = computed(() => {
  if (!props.trip?.startDate || !props.trip?.endDate) return ''
  const start = new Date(`${props.trip.startDate}T00:00:00`)
  const end = new Date(`${props.trip.endDate}T00:00:00`)
  const days = Math.floor((end - start) / 86400000) + 1
  return Number.isFinite(days) && days > 0 ? `${days} 天 ${Math.max(0, days - 1)} 晚` : ''
})

watch(() => props.trip?.id, () => { coverFailed.value = false })
</script>

<template>
  <article :class="['trip-context-card', `trip-context-card--${variant}`]">
    <div class="trip-context-cover" aria-hidden="true">
      <img v-if="coverUrl" :src="coverUrl" alt="" @error="coverFailed = true" />
      <span v-else>{{ (trip.destination || trip.title || '旅').slice(0, 1) }}</span>
    </div>
    <div class="trip-context-copy">
      <h1>{{ trip.title || '这次旅行' }}</h1>
      <p v-if="variant === 'favorite' && trip.destination"><MapPin :size="15" aria-hidden="true" />{{ trip.destination }}</p>
      <p><CalendarDays :size="15" aria-hidden="true" />{{ dateRange }}<span v-if="duration"> · {{ duration }}</span></p>
    </div>
    <div class="trip-context-actions"><slot name="actions" /></div>
  </article>
</template>

<style scoped>
.trip-context-card {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  align-items: center;
  gap: 15px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--tm-border);
  border-radius: 12px;
  background: rgba(255, 253, 249, .9);
  box-shadow: 0 8px 22px rgba(69, 51, 39, .05);
}

.trip-context-cover {
  display: grid;
  width: 72px;
  aspect-ratio: 1;
  place-items: center;
  overflow: hidden;
  border-radius: 11px;
  background: var(--tm-accent-soft);
  color: var(--tm-accent);
  font-family: Georgia, "Microsoft YaHei", serif;
  font-size: 24px;
  font-weight: 800;
}

.trip-context-cover img { width: 100%; height: 100%; object-fit: cover; }
.trip-context-copy { display: grid; min-width: 0; gap: 6px; }
.trip-context-copy h1,
.trip-context-copy p { margin: 0; }
.trip-context-copy h1 {
  overflow: hidden;
  font-family: Georgia, "Microsoft YaHei", serif;
  font-size: 24px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.trip-context-copy p {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  color: var(--tm-text-muted);
  font-size: 13px;
}
.trip-context-copy p span { white-space: nowrap; }
.trip-context-actions { display: flex; align-items: center; gap: 6px; }

.trip-context-card--favorite {
  grid-template-columns: minmax(116px, 30%) minmax(0, 1fr) auto;
  padding: 14px;
}
.trip-context-card--favorite .trip-context-cover {
  width: 100%;
  max-width: 190px;
  aspect-ratio: 16 / 9;
}

@media (max-width: 760px) {
  .trip-context-card {
    grid-template-columns: 64px minmax(0, 1fr) auto;
    gap: 13px;
    padding: 10px 4px;
    border: 0;
    border-radius: 0;
    background: transparent;
    box-shadow: none;
  }
  .trip-context-cover { width: 64px; border-radius: 14px; }
  .trip-context-copy h1 { font-size: 21px; }
  .trip-context-copy p { font-size: 12px; }
  .trip-context-card--favorite {
    grid-template-columns: 112px minmax(0, 1fr) auto;
    padding: 12px;
    border: 1px solid var(--tm-border);
    border-radius: 18px;
    background: rgba(255, 253, 249, .92);
    box-shadow: 0 8px 24px rgba(69, 51, 39, .06);
  }
  .trip-context-card--favorite .trip-context-cover { width: 112px; border-radius: 14px; }
  .trip-context-card--favorite .trip-context-copy h1 { font-size: 19px; }
  .trip-context-card--favorite .trip-context-copy p:first-of-type { display: none; }
}

@media (max-width: 370px) {
  .trip-context-card--favorite { grid-template-columns: 96px minmax(0, 1fr); }
  .trip-context-card--favorite .trip-context-cover { width: 96px; }
}
</style>
