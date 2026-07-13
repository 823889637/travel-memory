<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createUser, getUsers, resetUserPassword, setUserEnabled } from '../api/auth'
import { prepareCsrf } from '../auth'

const users = ref([])
const error = ref('')
const creating = ref(false)
const resetUserId = ref(null)
const resetPassword = ref('')
const form = reactive({ username: '', displayName: '', temporaryPassword: '' })

async function load () {
  try {
    users.value = await getUsers()
  } catch (requestError) {
    error.value = requestError.message || '账号列表加载失败。'
  }
}

async function create () {
  error.value = ''
  creating.value = true
  try {
    await prepareCsrf()
    await createUser(form)
    Object.assign(form, { username: '', displayName: '', temporaryPassword: '' })
    await load()
  } catch (requestError) {
    error.value = requestError.message || '创建账号失败。'
  } finally {
    creating.value = false
  }
}

async function toggle (user) {
  try {
    await prepareCsrf()
    await setUserEnabled(user.id, !user.enabled)
    await load()
  } catch (requestError) {
    error.value = requestError.message || '更新账号失败。'
  }
}

function beginReset (user) {
  resetUserId.value = user.id
  resetPassword.value = ''
}

async function saveReset (user) {
  if (!resetPassword.value) return
  try {
    await prepareCsrf()
    await resetUserPassword(user.id, resetPassword.value)
    resetUserId.value = null
    resetPassword.value = ''
    await load()
  } catch (requestError) {
    error.value = requestError.message || '重置密码失败。'
  }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>账号管理</h1>
        <p class="muted">管理员只管理账号，不自动拥有其他人的旅行访问权限。</p>
      </div>
    </div>

    <form class="form card" @submit.prevent="create">
      <h2>创建用户</h2>
      <div class="field">
        <label>用户名</label>
        <input v-model="form.username" autocomplete="off" required />
      </div>
      <div class="field">
        <label>显示名称</label>
        <input v-model="form.displayName" required />
      </div>
      <div class="field">
        <label>临时密码</label>
        <input v-model="form.temporaryPassword" type="password" minlength="12" required />
      </div>
      <button :disabled="creating">{{ creating ? '创建中…' : '创建账号' }}</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="user-list">
      <article v-for="user in users" :key="user.id" class="card user-row">
        <div>
          <strong>{{ user.displayName }}</strong>
          <p class="muted">
            {{ user.username }} · {{ user.role }}
            <span v-if="user.mustChangePassword"> · 需要修改密码</span>
          </p>
        </div>
        <div class="actions">
          <button class="ghost" type="button" @click="beginReset(user)">重置密码</button>
          <button class="ghost" type="button" @click="toggle(user)">
            {{ user.enabled ? '禁用' : '启用' }}
          </button>
        </div>
        <form v-if="resetUserId === user.id" class="user-password-reset" @submit.prevent="saveReset(user)">
          <input v-model="resetPassword" type="password" minlength="12" placeholder="输入至少 12 位临时密码" required />
          <button type="submit">保存临时密码</button>
          <button class="ghost" type="button" @click="resetUserId = null">取消</button>
        </form>
      </article>
    </div>
  </section>
</template>
