<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { LogOut, Settings, ShieldCheck, UserRound } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import { uploadAvatar } from '../api/auth'
import { currentUser, signOut } from '../auth'

const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
const menuRef = ref(null)
const avatarInput = ref(null)
const avatarError = ref('')

const userInitial = computed(() => {
  const name = (currentUser.value?.displayName || currentUser.value?.username || '?').trim()
  return name.slice(0, 1).toUpperCase() || '?'
})

function closeMenu() {
  menuOpen.value = false
  avatarError.value = ''
}

function handleOutsidePointer(event) {
  if (!menuRef.value?.contains(event.target)) closeMenu()
}

async function logout() {
  closeMenu()
  await signOut()
  router.push('/login')
}

async function handleAvatarChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const data = new FormData()
  data.append('photo', file)
  avatarError.value = ''
  try {
    currentUser.value = await uploadAvatar(data)
  } catch (error) {
    avatarError.value = error.message || '头像暂时没有更新成功。'
  }
}

watch(() => route.fullPath, closeMenu)
onMounted(() => document.addEventListener('pointerdown', handleOutsidePointer))
onBeforeUnmount(() => document.removeEventListener('pointerdown', handleOutsidePointer))
</script>

<template>
  <div v-if="currentUser" ref="menuRef" :class="['user-menu', { 'is-open': menuOpen }]">
    <button
      class="user-trigger"
      type="button"
      aria-label="打开账户菜单"
      :aria-expanded="menuOpen"
      @click="menuOpen = !menuOpen"
    >
      <span class="user-avatar" aria-hidden="true">
        <img v-if="currentUser.avatarUrl" :src="currentUser.avatarUrl" alt="" />
        <template v-else>{{ userInitial }}</template>
      </span>
      <span class="user-trigger-name">{{ currentUser.displayName || currentUser.username }}</span>
      <span class="user-trigger-caret" aria-hidden="true">⌄</span>
    </button>

    <div v-if="menuOpen" class="user-popover" role="menu">
      <div class="user-popover-identity">
        <span class="user-popover-avatar" aria-hidden="true">
          <img v-if="currentUser.avatarUrl" :src="currentUser.avatarUrl" alt="" />
          <template v-else>{{ userInitial }}</template>
        </span>
        <span>
          <strong>{{ currentUser.displayName || currentUser.username }}</strong>
          <small>{{ currentUser.username }}</small>
        </span>
      </div>
      <button type="button" role="menuitem" @click="avatarInput?.click()">
        <UserRound :size="17" aria-hidden="true" />更换头像
      </button>
      <input ref="avatarInput" class="visually-hidden" type="file" accept="image/*" @change="handleAvatarChange" />
      <RouterLink v-if="currentUser.role === 'ADMIN'" to="/admin/users" role="menuitem" @click="closeMenu">
        <ShieldCheck :size="17" aria-hidden="true" />账号管理
      </RouterLink>
      <RouterLink to="/change-password" role="menuitem" @click="closeMenu">
        <Settings :size="17" aria-hidden="true" />修改密码
      </RouterLink>
      <button class="user-logout" type="button" role="menuitem" @click="logout">
        <LogOut :size="17" aria-hidden="true" />退出登录
      </button>
      <p v-if="avatarError" class="user-popover-error" role="alert">{{ avatarError }}</p>
    </div>
  </div>
</template>
