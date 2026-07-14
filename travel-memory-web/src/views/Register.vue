<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getRegistrationStatus, register } from '../api/auth'
import { currentUser, prepareCsrf } from '../auth'

const router = useRouter()
const statusLoaded = ref(false)
const registrationEnabled = ref(false)
const firstAdminPending = ref(false)
const statusError = ref('')
const error = ref('')
const saving = ref(false)
const form = reactive({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
  invitationCode: '',
})

async function loadRegistrationStatus() {
  try {
    const status = await getRegistrationStatus()
    registrationEnabled.value = status.enabled
    firstAdminPending.value = status.firstAdminPending
    statusError.value = ''
    return status.enabled
  } catch {
    registrationEnabled.value = false
    firstAdminPending.value = false
    statusError.value = '暂时无法读取注册状态，请确认后端已启动并已连接数据库。'
    return false
  } finally {
    statusLoaded.value = true
  }
}

onMounted(loadRegistrationStatus)

async function submit () {
  error.value = ''
  if (form.password !== form.confirmPassword) {
    error.value = '两次输入的密码不一致。'
    return
  }
  saving.value = true
  try {
    if (!await loadRegistrationStatus()) return
    await prepareCsrf()
    const user = await register({
      username: form.username,
      displayName: form.displayName,
      password: form.password,
      invitationCode: form.invitationCode,
    })
    currentUser.value = user
    router.push('/trips')
  } catch (requestError) {
    error.value = requestError.message || '创建账号失败，请稍后再试。'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-brand-panel" aria-hidden="true">
      <span class="auth-brand-mark">◇</span>
      <p>Travel Memory</p>
      <strong>开始保存属于你的旅行记忆</strong>
      <span>每个账号只看到自己的旅行、Memory 和照片。</span>
    </div>
    <div v-if="statusLoaded && statusError" class="form card auth-card">
      <h1>暂时无法读取注册状态</h1>
      <p class="muted">{{ statusError }}</p>
      <RouterLink class="auth-link" to="/login">返回登录</RouterLink>
    </div>

    <div v-else-if="statusLoaded && !registrationEnabled" class="form card auth-card">
      <h1>暂未开放注册</h1>
      <p class="muted">需要账号时，请向管理员获取注册码。</p>
      <RouterLink class="auth-link" to="/login">返回登录</RouterLink>
    </div>

    <form v-else-if="registrationEnabled" class="form card auth-card" @submit.prevent="submit">
      <h1>创建账号</h1>
      <p class="muted">
        {{ firstAdminPending ? '创建第一个账号后，它将成为管理员。' : '创建后即可开始保存属于自己的旅行回忆。' }}
      </p>
      <div class="field">
        <label for="register-username">用户名</label>
        <input id="register-username" v-model="form.username" autocomplete="username" required />
      </div>
      <div class="field">
        <label for="register-display-name">显示名称</label>
        <input id="register-display-name" v-model="form.displayName" autocomplete="name" required />
      </div>
      <div class="field">
        <label for="register-password">密码</label>
        <input id="register-password" v-model="form.password" type="password" autocomplete="new-password" minlength="12" required />
      </div>
      <div class="field">
        <label for="register-confirm-password">确认密码</label>
        <input id="register-confirm-password" v-model="form.confirmPassword" type="password" autocomplete="new-password" minlength="12" required />
      </div>
      <div class="field">
        <label for="register-invitation-code">注册码</label>
        <input id="register-invitation-code" v-model="form.invitationCode" type="password" autocomplete="off" required />
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <button :disabled="saving">{{ saving ? '创建中…' : '创建账号' }}</button>
      <RouterLink class="auth-link" to="/login">已有账号，去登录</RouterLink>
    </form>
  </section>
</template>
