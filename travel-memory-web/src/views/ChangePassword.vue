<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword } from '../api/auth'
import { currentUser, prepareCsrf } from '../auth'
import MobilePageHeader from '../components/MobilePageHeader.vue'

const router = useRouter()
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')
const saving = ref(false)

async function submit () {
  error.value = ''
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致。'
    return
  }
  saving.value = true
  try {
    await prepareCsrf()
    await changePassword({ currentPassword: currentPassword.value, newPassword: newPassword.value })
    currentUser.value = { ...currentUser.value, mustChangePassword: false }
    router.push('/trips')
  } catch (requestError) {
    error.value = requestError.message || '修改密码失败，请稍后再试。'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="auth-page change-password-page">
    <MobilePageHeader title="修改密码" back-to="/trips" />
    <div class="auth-brand-panel" aria-hidden="true">
      <span class="auth-brand-mark">◇</span>
      <p>Travel Memory</p>
      <strong>保护只属于你的旅行记忆</strong>
      <span>使用足够长且未在其他网站使用过的密码。</span>
    </div>
    <form class="form card auth-card" @submit.prevent="submit">
      <h1>修改密码</h1>
      <p class="muted">为了保护旅行回忆，请先设置一个新密码。</p>
      <div class="field">
        <label for="change-current-password">当前密码</label>
        <input id="change-current-password" v-model="currentPassword" type="password" autocomplete="current-password" required />
      </div>
      <div class="field">
        <label for="change-new-password">新密码</label>
        <input id="change-new-password" v-model="newPassword" type="password" autocomplete="new-password" minlength="12" required />
      </div>
      <div class="field">
        <label for="change-confirm-password">确认新密码</label>
        <input id="change-confirm-password" v-model="confirmPassword" type="password" autocomplete="new-password" minlength="12" required />
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <button :disabled="saving">{{ saving ? '保存中…' : '保存新密码' }}</button>
    </form>
  </section>
</template>
