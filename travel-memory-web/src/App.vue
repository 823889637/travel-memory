<template>
  <div class="app-shell">
    <header :class="['topbar', { 'mobile-standalone-hidden': route.meta.mobileStandalone }]">
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

    <main :class="['page', { 'mobile-standalone-page': route.meta.mobileStandalone }]">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { currentUser } from './auth'
import UserMenu from './components/UserMenu.vue'

const route = useRoute()
</script>
