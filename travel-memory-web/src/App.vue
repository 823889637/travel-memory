<template>
  <div class="app-shell">
    <header :class="['topbar', `mobile-header-${mobileHeaderMode}`]">
      <div class="topbar-inner">
        <RouterLink class="brand" to="/">
          <span class="brand-mark" aria-hidden="true">◇</span>
          <span>Travel Memory</span>
        </RouterLink>
        <nav v-if="currentUser" class="topbar-nav" aria-label="主导航">
          <RouterLink to="/trips">我的旅行</RouterLink>
          <RouterLink to="/trips/new">创建旅行</RouterLink>
        </nav>
        <UserMenu />
      </div>
    </header>

    <main :class="['page', `mobile-header-${mobileHeaderMode}`]">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { currentUser } from './auth'
import UserMenu from './components/UserMenu.vue'

const route = useRoute()
const mobileHeaderMode = computed(() => {
  const mode = route.meta.mobileHeader
  return ['global', 'page', 'none'].includes(mode) ? mode : 'global'
})
</script>
