<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword } from '../api/auth'
import { currentUser, prepareCsrf } from '../auth'

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
  <section class="auth-page">
    <form class="form card auth-card" @submit.prevent="submit">
      <h1>修改密码</h1>
      <p class="muted">为了保护旅行回忆，请先设置一个新密码。</p>
      <div class="field">
        <label>当前密码</label>
        <input v-model="currentPassword" type="password" required />
      </div>
      <div class="field">
        <label>新密码</label>
        <input v-model="newPassword" type="password" minlength="12" required />
      </div>
      <div class="field">
        <label>确认新密码</label>
        <input v-model="confirmPassword" type="password" minlength="12" required />
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <button :disabled="saving">{{ saving ? '保存中…' : '保存新密码' }}</button>
    </form>
  </section>
</template>
