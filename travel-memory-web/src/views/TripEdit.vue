<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { clearTripCover, getTrip, updateTrip } from '../api/trip'
import { getTimeline } from '../api/memory'
import { hasExplicitTripCover, resolveTripCoverUrl } from '../utils/tripCover'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const redirecting = ref(false)
const error = ref('')
const successMessage = ref('')
const originalTrip = ref(null)
const originalForm = ref(null)
const memories = ref([])
const coverError = ref('')
const clearingCover = ref(false)
const coverImageFailed = ref(false)
const skipLeavePrompt = ref(false)
let redirectTimer = null

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

const isDirty = computed(() => {
  if (!originalForm.value) {
    return false
  }
  return JSON.stringify(normalizeForm(form)) !== JSON.stringify(originalForm.value)
})

const canSubmit = computed(() => !loading.value && !saving.value && !redirecting.value)
const coverPhotoUrl = computed(() => (
  coverImageFailed.value ? '' : resolveTripCoverUrl(originalTrip.value, memories.value)
))
const hasExplicitCover = computed(() => hasExplicitTripCover(originalTrip.value))

function normalizeText(value) {
  return String(value ?? '').trim()
}

function normalizeDate(value) {
  return value ? String(value).slice(0, 10) : ''
}

function normalizeForm(source) {
  return {
    title: normalizeText(source.title),
    destination: normalizeText(source.destination),
    startDate: normalizeDate(source.startDate),
    endDate: normalizeDate(source.endDate),
    description: normalizeText(source.description),
  }
}

function isValidTripId(value) {
  return /^\d+$/.test(String(value)) && Number(value) > 0
}

function isNotFoundError(message) {
  return /not found|404/i.test(message || '')
}

function applyTrip(trip) {
  const normalized = normalizeForm(trip)
  Object.assign(form, normalized)
  originalForm.value = normalized
}

async function loadTrip() {
  error.value = ''
  successMessage.value = ''
  coverError.value = ''

  if (!isValidTripId(route.params.id)) {
    originalTrip.value = null
    originalForm.value = null
    error.value = '旅行编号无效，无法编辑。'
    return
  }

  loading.value = true
  try {
    const [trip, timeline] = await Promise.all([
      getTrip(route.params.id),
      getTimeline(route.params.id),
    ])
    if (!trip) {
      throw new Error('旅行不存在或已被删除。')
    }
    originalTrip.value = trip
    memories.value = timeline
    coverImageFailed.value = false
    applyTrip(trip)
  } catch (err) {
    originalTrip.value = null
    originalForm.value = null
    error.value = isNotFoundError(err.message)
      ? '这次旅行不存在或已被删除，无法继续编辑。'
      : err.message || '旅行信息暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

async function clearCover() {
  if (!hasExplicitCover.value || clearingCover.value) {
    return
  }
  if (!window.confirm('确定取消自定义封面吗？之后会自动使用第一张旅行照片。')) {
    return
  }

  coverError.value = ''
  clearingCover.value = true
  try {
    originalTrip.value = await clearTripCover(route.params.id)
    coverImageFailed.value = false
  } catch (err) {
    coverError.value = err.message || '取消自定义封面失败，请稍后再试。'
  } finally {
    clearingCover.value = false
  }
}

function validate() {
  const normalized = normalizeForm(form)
  if (!normalized.title) {
    return '请填写旅行标题。'
  }
  if (!normalized.startDate) {
    return '请选择开始日期。'
  }
  if (!normalized.endDate) {
    return '请选择结束日期。'
  }
  if (normalized.endDate < normalized.startDate) {
    return '结束日期不能早于开始日期。'
  }
  return ''
}

async function submit() {
  if (loading.value || saving.value || !originalTrip.value) {
    return
  }

  error.value = ''
  const validationError = validate()
  if (validationError) {
    error.value = validationError
    return
  }

  const normalized = normalizeForm(form)
  saving.value = true
  try {
    await updateTrip(route.params.id, {
      ...normalized,
    })
    originalForm.value = normalized
    skipLeavePrompt.value = true
    redirecting.value = true
    successMessage.value = '旅行信息已保存。'
    await nextTick()
    redirectTimer = window.setTimeout(() => {
      router.push(`/trips/${route.params.id}`)
    }, 250)
  } catch (err) {
    error.value = isNotFoundError(err.message)
      ? '这次旅行不存在或已被删除，无法保存修改。'
      : err.message || '保存失败，请稍后再试。'
  } finally {
    saving.value = false
  }
}

function confirmDiscard() {
  return !isDirty.value || window.confirm('还有未保存的修改，确定要放弃吗？')
}

function cancel() {
  if (!confirmDiscard()) {
    return
  }
  skipLeavePrompt.value = true
  router.push(`/trips/${route.params.id}`)
}

function handleBeforeUnload(event) {
  if (!skipLeavePrompt.value && isDirty.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onBeforeRouteLeave(() => {
  if (!skipLeavePrompt.value && !confirmDiscard()) {
    return false
  }
  return true
})

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  loadTrip()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  if (redirectTimer) {
    window.clearTimeout(redirectTimer)
  }
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>编辑旅行</h1>
        <p class="muted">修正这次旅行的基本信息，原有封面会保留。</p>
      </div>
    </div>

    <p v-if="loading" class="muted">正在加载这次旅行...</p>
    <p v-else-if="error && !originalTrip" class="error">{{ error }}</p>

    <form v-else-if="originalTrip" class="form card" @submit.prevent="submit">
      <div class="field">
        <label>旅行封面</label>
        <img v-if="coverPhotoUrl" :src="coverPhotoUrl" alt="当前旅行封面" @error="coverImageFailed = true" />
        <p v-else class="muted">这次旅行还没有照片可作为封面。</p>
        <p class="muted">{{ hasExplicitCover ? '当前使用已设置的旅行封面。' : '当前自动使用按时间排序的第一张旅行照片。' }}</p>
        <button v-if="hasExplicitCover" type="button" class="ghost" :disabled="clearingCover" @click="clearCover">
          {{ clearingCover ? '取消中…' : '取消自定义封面' }}
        </button>
        <p v-if="coverError" class="error">{{ coverError }}</p>
      </div>

      <div class="field">
        <label for="trip-title">旅行标题</label>
        <input id="trip-title" v-model="form.title" required placeholder="例如：京都春日散步" />
      </div>

      <div class="field">
        <label for="trip-destination">目的地</label>
        <input id="trip-destination" v-model="form.destination" placeholder="例如：京都" />
      </div>

      <div class="field">
        <label for="trip-start-date">开始日期</label>
        <input
          id="trip-start-date"
          v-model="form.startDate"
          type="date"
          required
          :max="form.endDate || undefined"
        />
      </div>

      <div class="field">
        <label for="trip-end-date">结束日期</label>
        <input
          id="trip-end-date"
          v-model="form.endDate"
          type="date"
          required
          :min="form.startDate || undefined"
        />
        <p v-if="dateError" class="error">{{ dateError }}</p>
      </div>

      <div class="field">
        <label for="trip-description">旅行描述</label>
        <textarea
          id="trip-description"
          v-model="form.description"
          placeholder="简单写一点这趟旅行的背景"
        ></textarea>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="successMessage" class="muted">{{ successMessage }}</p>

      <div class="actions">
        <button :disabled="!canSubmit">{{ saving ? '保存中…' : '保存修改' }}</button>
        <button type="button" class="ghost" :disabled="saving || redirecting" @click="cancel">取消</button>
      </div>
    </form>
  </section>
</template>
