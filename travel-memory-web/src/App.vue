<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/">Travel Memory</RouterLink>
      <nav v-if="currentUser" class="topbar-nav" aria-label="主导航">
        <RouterLink to="/">旅行列表</RouterLink>
        <RouterLink to="/trips/new">创建旅行</RouterLink>
      </nav>
      <div v-if="currentUser" ref="userMenuRef" class="user-menu">
        <button
          class="user-trigger"
          type="button"
          aria-label="打开账户菜单"
          :aria-expanded="userMenuOpen"
          @click="toggleUserMenu"
        >
          <span class="user-avatar" aria-hidden="true">{{ userInitial }}</span>
          <span class="user-trigger-name">{{ currentUser.displayName || currentUser.username }}</span>
          <span class="user-trigger-caret" aria-hidden="true">⌄</span>
        </button>
        <div v-if="userMenuOpen" class="user-popover" role="menu">
          <div class="user-popover-identity">
            <strong>{{ currentUser.displayName || currentUser.username }}</strong>
            <span v-if="currentUser.username">{{ currentUser.username }}</span>
          </div>
          <RouterLink v-if="currentUser.role === 'ADMIN'" to="/admin/users" @click="closeUserMenu">账号管理</RouterLink>
          <RouterLink to="/change-password" @click="closeUserMenu">修改密码</RouterLink>
          <button class="user-logout" type="button" @click="logout">退出登录</button>
        </div>
      </div>
    </header>

    <main class="page">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { currentUser, signOut } from './auth'

const router = useRouter()
const route = useRoute()
const userMenuOpen = ref(false)
const userMenuRef = ref(null)

const userInitial = computed(() => {
  const name = (currentUser.value?.displayName || currentUser.value?.username || '?').trim()
  return name.slice(0, 1).toUpperCase() || '?'
})

function closeUserMenu () {
  userMenuOpen.value = false
}

function toggleUserMenu () {
  userMenuOpen.value = !userMenuOpen.value
}

function handleDocumentPointerDown (event) {
  if (!userMenuRef.value?.contains(event.target)) {
    closeUserMenu()
  }
}

async function logout () {
  closeUserMenu()
  await signOut()
  router.push('/login')
}

watch(() => route.fullPath, closeUserMenu)

onMounted(() => document.addEventListener('pointerdown', handleDocumentPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', handleDocumentPointerDown))
</script>
