<script setup>
import { RouterLink } from 'vue-router'

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
  { key: 'timeline', label: '时间线', icon: '◷', suffix: '' },
  { key: 'journey', label: '旅程回放', icon: '▷', suffix: '/journey' },
  { key: 'map', label: '地图', icon: '⌖', suffix: '/map' },
  { key: 'recap', label: '旅行回顾', icon: '▤', suffix: '/recap' },
  { key: 'companions', label: '同行的人', icon: '♧', suffix: '/companions' },
  { key: 'favorites', label: '收藏回看', icon: '☆', suffix: '/favorites' },
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
      <span class="trip-view-nav-icon" aria-hidden="true">{{ item.icon }}</span>
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
  border-radius: var(--tm-radius-lg);
  background: rgba(251, 249, 244, 0.72);
  padding: 5px;
  box-shadow: 0 8px 22px rgba(68, 52, 39, 0.04);
}

.trip-view-nav-link {
  display: inline-flex;
  flex: 1 1 0;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  border-radius: var(--tm-radius-md);
  color: var(--tm-text-muted);
  font-size: 14px;
  font-weight: 700;
  padding: 10px 8px;
  white-space: nowrap;
  transition: background 0.15s ease, color 0.15s ease;
}

.trip-view-nav-link:hover,
.trip-view-nav-link.active {
  background: var(--tm-accent-soft);
  color: var(--tm-accent);
}

.trip-view-nav-icon {
  color: currentColor;
  font-size: 18px;
  font-weight: 400;
  line-height: 1;
}

@media (max-width: 640px) {
  .trip-view-nav {
    overflow-x: auto;
    border-radius: 14px;
    padding: 3px;
    scroll-snap-type: x proximity;
    scrollbar-width: none;
  }

  .trip-view-nav::-webkit-scrollbar {
    display: none;
  }

  .trip-view-nav-link {
    display: grid;
    flex: 0 0 66px;
    justify-items: center;
    gap: 4px;
    padding: 8px 4px 7px;
    border-radius: 10px;
    font-size: 11px;
    scroll-snap-align: start;
  }

  .trip-view-nav-icon {
    font-size: 19px;
  }
}
</style>
