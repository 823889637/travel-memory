<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createTrip } from '../api/trip'

const router = useRouter()
const saving = ref(false)
const error = ref('')

const form = reactive({
  title: '',
  destination: '',
  startDate: '',
  endDate: '',
  description: '',
  coverPhotoUrl: '',
})

const dateError = computed(() => {
  if (!form.startDate || !form.endDate) {
    return ''
  }
  return form.endDate < form.startDate ? '结束日期不能早于开始日期' : ''
})

const canSubmit = computed(() => !saving.value && !dateError.value)

async function submit() {
  error.value = ''
  if (dateError.value) {
    error.value = dateError.value
    return
  }

  saving.value = true
  try {
    const trip = await createTrip({
      ...form,
      startDate: form.startDate || null,
      endDate: form.endDate || null,
    })
    router.push(`/trips/${trip.id}`)
  } catch (err) {
    error.value = err.message || '创建失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>创建旅行</h1>
        <p class="muted">先记录一次旅行的基本信息。</p>
      </div>
    </div>

    <form class="form card" @submit.prevent="submit">
      <div class="field">
        <label>旅行标题</label>
        <input v-model="form.title" required placeholder="例如：京都春日散步" />
      </div>

      <div class="field">
        <label>目的地</label>
        <input v-model="form.destination" placeholder="例如：京都" />
      </div>

      <div class="field">
        <label>开始日期</label>
        <input v-model="form.startDate" type="date" :max="form.endDate || undefined" />
      </div>

      <div class="field">
        <label>结束日期</label>
        <input v-model="form.endDate" type="date" :min="form.startDate || undefined" />
        <p v-if="dateError" class="error">{{ dateError }}</p>
      </div>

      <div class="field">
        <label>旅行描述</label>
        <textarea v-model="form.description" placeholder="简单写一点这趟旅行的背景"></textarea>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="actions">
        <button :disabled="!canSubmit">{{ saving ? '保存中...' : '创建' }}</button>
        <button type="button" class="ghost" :disabled="saving" @click="router.push('/trips')">
          取消
        </button>
      </div>
    </form>
  </section>
</template>
