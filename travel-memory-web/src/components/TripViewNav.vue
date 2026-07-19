<script setup>
import { RouterLink } from 'vue-router'
import { BookOpen, CirclePlay, Clock3, Map, Users } from '@lucide/vue'

defineProps({
  tripId: {
    type: [String, Number],
    required: true,
  },
  active: {
    type: String,
    required: true,
  },
})

const items = [
  { key: 'timeline', label: '时间线', icon: Clock3, suffix: '' },
  { key: 'journey', label: '旅程回放', icon: CirclePlay, suffix: '/journey' },
  { key: 'map', label: '地图', icon: Map, suffix: '/map' },
  { key: 'recap', label: '旅行回顾', icon: BookOpen, suffix: '/recap' },
  { key: 'companions', label: '同行的人', icon: Users, suffix: '/companions' },
]
</script>

<template>
  <nav class="trip-view-nav" aria-label="旅行页面导航">
    <RouterLink
      v-for="item in items"
      :key="item.key"
      :to="`/trips/${tripId}${item.suffix}`"
      :class="['trip-view-nav-link', { active: active === item.key }]"
      :aria-current="active === item.key ? 'page' : undefined"
    >
      <span class="trip-view-nav-icon" aria-hidden="true">
        <component :is="item.icon" :size="18" :stroke-width="1.8" />
      </span>
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.trip-view-nav {
  display: flex;
  gap: 0;
  overflow: hidden;
  border: 1px solid var(--tm-border);
  border-radius: var(--tm-radius-md);
  background: rgba(255, 253, 249, 0.82);
  padding: 3px;
  box-shadow: 0 6px 18px rgba(68, 52, 39, 0.035);
}

.trip-view-nav-link {
  display: inline-flex;
  flex: 1 1 0;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  border-radius: 5px;
  color: var(--tm-text-muted);
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  padding: 11px 8px;
  white-space: nowrap;
  transition: background 0.15s ease, color 0.15s ease;
}

.trip-view-nav-link:hover,
.trip-view-nav-link.active {
  background: #f7efe7;
  color: var(--tm-accent);
  font-weight: 600;
}

.trip-view-nav-icon {
  display: inline-grid;
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  place-items: center;
  color: currentColor;
  line-height: 18px;
}

.trip-view-nav-icon :deep(svg) {
  display: block;
  width: 18px;
  height: 18px;
}

@media (max-width: 640px) {
  .trip-view-nav {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    overflow: hidden;
    border-radius: 14px;
    padding: 3px;
  }

  .trip-view-nav-link {
    display: grid;
    width: 100%;
    min-width: 0;
    justify-items: center;
    gap: 4px;
    padding: 8px 1px 7px;
    border-radius: 10px;
    font-size: 12px;
    line-height: 18px;
  }

}
</style>
