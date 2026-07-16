<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createTrip } from '../api/trip'
import MobilePageHeader from '../components/MobilePageHeader.vue'

const router = useRouter()
const saving = ref(false)
const error = ref('')

const form = reactive({
  title: '',
  destination: '',
  startDate: '',
  endDate: '',
  description: '',
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
      title: form.title.trim(),
      destination: form.destination.trim() || null,
      startDate: form.startDate || null,
      endDate: form.endDate || null,
      description: form.description.trim() || null,
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
  <section class="trip-form-page">
    <MobilePageHeader title="创建旅行" back-to="/trips" />
    <RouterLink class="trip-form-back" to="/trips">← 返回旅行列表</RouterLink>
    <div class="page-header trip-form-header">
      <div>
        <p class="trip-form-kicker">新的旅程</p>
        <h1>先留下旅行轮廓</h1>
        <p class="muted">先留下旅行的基本轮廓，照片和 Memory 可以慢慢补充。</p>
      </div>
    </div>

    <form class="form card trip-form-card" @submit.prevent="submit">
      <div class="trip-create-cover-placeholder" aria-label="旅行封面说明">
        <span>旅行封面</span>
        <small>保存第一段 Memory 后，可以从真实照片中选择封面。</small>
      </div>
      <div class="trip-form-section-head">
        <span>01</span>
        <div><strong>基本信息</strong><p>标题会成为这次旅行最主要的名字。</p></div>
      </div>
      <div class="field">
        <label for="create-trip-title">旅行标题</label>
        <input id="create-trip-title" v-model="form.title" required maxlength="100" placeholder="例如：京都春日散步" />
      </div>

      <div class="field">
        <label for="create-trip-destination">目的地</label>
        <input id="create-trip-destination" v-model="form.destination" maxlength="100" placeholder="例如：京都" />
      </div>

      <div class="trip-form-date-grid">
      <div class="field">
        <label for="create-trip-start-date">开始日期</label>
        <input id="create-trip-start-date" v-model="form.startDate" type="date" :max="form.endDate || undefined" />
      </div>

      <div class="field">
        <label for="create-trip-end-date">结束日期</label>
        <input id="create-trip-end-date" v-model="form.endDate" type="date" :min="form.startDate || undefined" />
        <p v-if="dateError" class="error">{{ dateError }}</p>
      </div>
      </div>

      <div class="field">
        <label for="create-trip-description">旅行描述</label>
        <textarea id="create-trip-description" v-model="form.description" maxlength="500" placeholder="例如：第一次和小雨一起去京都，想慢慢看看春天。"></textarea>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="actions trip-form-actions">
        <button :disabled="!canSubmit">{{ saving ? '保存中...' : '创建旅行' }}</button>
        <button type="button" class="ghost" :disabled="saving" @click="router.push('/trips')">
          取消
        </button>
      </div>
    </form>
  </section>
</template>
