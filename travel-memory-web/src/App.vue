<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/">Travel Memory</RouterLink>
      <nav v-if="currentUser">
        <RouterLink to="/">旅行列表</RouterLink>
        <RouterLink to="/trips/new">创建旅行</RouterLink>
      </nav>
      <div v-if="currentUser" class="user-menu">
        <span>{{ currentUser.displayName }}</span>
        <RouterLink v-if="currentUser.role === 'ADMIN'" to="/admin/users">账号管理</RouterLink>
        <RouterLink to="/change-password">修改密码</RouterLink>
        <button type="button" @click="logout">退出</button>
      </div>
    </header>

    <main class="page">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { currentUser, signOut } from './auth'

const router = useRouter()

async function logout () {
  await signOut()
  router.push('/login')
}
</script>
