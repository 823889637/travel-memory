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
        <div v-if="currentUser" ref="userMenuRef" class="user-menu">
        <button
          class="user-trigger"
          type="button"
          aria-label="打开账户菜单"
          :aria-expanded="userMenuOpen"
          @click="toggleUserMenu"
        >
          <span class="user-avatar" aria-hidden="true">
            <img v-if="currentUser.avatarUrl" :src="currentUser.avatarUrl" alt="" />
            <template v-else>{{ userInitial }}</template>
          </span>
          <span class="user-trigger-name">{{ currentUser.displayName || currentUser.username }}</span>
          <span class="user-trigger-caret" aria-hidden="true">⌄</span>
        </button>
          <div v-if="userMenuOpen" class="user-popover" role="menu">
            <div class="user-popover-identity">
              <span class="user-popover-avatar" aria-hidden="true">
                <img v-if="currentUser.avatarUrl" :src="currentUser.avatarUrl" alt="" />
                <template v-else>{{ userInitial }}</template>
              </span>
              <span>
                <strong>{{ currentUser.displayName || currentUser.username }}</strong>
                <small v-if="currentUser.username">{{ currentUser.username }}</small>
              </span>
            </div>
            <button type="button" @click="avatarInput?.click()">更换头像</button>
            <input ref="avatarInput" class="visually-hidden" type="file" accept="image/*" @change="handleAvatarChange" />
            <RouterLink v-if="currentUser.role === 'ADMIN'" to="/admin/users" @click="closeUserMenu">账号管理</RouterLink>
            <RouterLink to="/change-password" @click="closeUserMenu">修改密码</RouterLink>
            <button class="user-logout" type="button" @click="logout">退出登录</button>
          </div>
        </div>
      </div>
    </header>

    <main :class="['page', { 'mobile-standalone-page': route.meta.mobileStandalone }]">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { currentUser, signOut } from './auth'
import { uploadAvatar } from './api/auth'

const router = useRouter()
const route = useRoute()
const userMenuOpen = ref(false)
const userMenuRef = ref(null)
const avatarInput = ref(null)

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

async function handleAvatarChange (event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const data = new FormData()
  data.append('photo', file)
  try {
    currentUser.value = await uploadAvatar(data)
  } catch (error) {
    window.alert(error.message || '头像暂时没有更新成功。')
  }
}

watch(() => route.fullPath, closeUserMenu)

onMounted(() => document.addEventListener('pointerdown', handleDocumentPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', handleDocumentPointerDown))
</script>
