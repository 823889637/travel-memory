<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getRegistrationStatus, login } from '../api/auth'
import { currentUser, prepareCsrf } from '../auth'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const saving = ref(false)
const registrationEnabled = ref(false)

onMounted(async () => {
  try {
    registrationEnabled.value = (await getRegistrationStatus()).enabled
  } catch {
    registrationEnabled.value = false
  }
})

async function submit () {
  error.value = ''
  saving.value = true
  try {
    await prepareCsrf()
    const user = await login({ username: username.value, password: password.value })
    currentUser.value = user
    router.push(user.mustChangePassword ? '/change-password' : (route.query.redirect || '/trips'))
  } catch (requestError) {
    error.value = requestError.message || '登录失败，请稍后再试。'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="auth-page auth-login-page">
    <div class="auth-brand-panel" aria-hidden="true">
      <span class="auth-brand-mark">◇</span>
      <p>Travel Memory</p>
      <strong>把旅行留给未来的自己</strong>
      <span>照片、原话、时间和地点，共同保存一段真实发生过的旅行。</span>
    </div>
    <form class="form card auth-card" @submit.prevent="submit">
      <p class="auth-kicker">欢迎回来</p>
      <h1>登录 Travel Memory</h1>
      <p class="muted">进入属于你的旅行回忆。</p>
      <div class="field">
        <label for="login-username">用户名</label>
        <input id="login-username" v-model="username" autocomplete="username" required />
      </div>
      <div class="field">
        <label for="login-password">密码</label>
        <input id="login-password" v-model="password" type="password" autocomplete="current-password" required />
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <button :disabled="saving">{{ saving ? '登录中…' : '登录' }}</button>
      <RouterLink v-if="registrationEnabled" class="auth-link" to="/register">创建账号</RouterLink>
    </form>
  </section>
</template>
