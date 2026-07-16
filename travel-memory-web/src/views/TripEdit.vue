<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Image as ImageIcon } from '@lucide/vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { getTrip, updateTrip, uploadImage } from '../api/trip'
import { getTimeline } from '../api/memory'
import { hasExplicitTripCover, resolveTripCoverUrl } from '../utils/tripCover'
import MobilePageHeader from '../components/MobilePageHeader.vue'
import TripCoverEditor from '../components/TripCoverEditor.vue'
import TripFormFields from '../components/TripFormFields.vue'

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
const uploadingCover = ref(false)
const coverPreview = ref('')
const clearCoverRequested = ref(false)
const coverImageFailed = ref(false)
const skipLeavePrompt = ref(false)
let redirectTimer = null
let previewObjectUrl = ''

const form = reactive({
  title: '',
  destination: '',
  destinationCountry: '',
  destinationLatitude: null,
  destinationLongitude: null,
  startDate: '',
  endDate: '',
  description: '',
  notes: '',
  coverPhotoUrl: '',
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

const canSubmit = computed(() => !loading.value && !saving.value && !redirecting.value && !uploadingCover.value)
const automaticCoverPhotoUrl = computed(() => resolveTripCoverUrl(
  originalTrip.value ? { ...originalTrip.value, coverPhotoUrl: null } : null,
  memories.value,
))
const coverPhotoUrl = computed(() => (
  coverImageFailed.value
    ? ''
    : (coverPreview.value || form.coverPhotoUrl || automaticCoverPhotoUrl.value)
))
const hasExplicitCover = computed(() => hasExplicitTripCover(originalTrip.value))
const hasPendingCover = computed(() => (
  normalizeText(form.coverPhotoUrl) !== normalizeText(originalTrip.value?.coverPhotoUrl)
))
const coverSecondaryLabel = computed(() => {
  if (clearCoverRequested.value) return '撤销恢复自动封面'
  return hasPendingCover.value ? '放弃新封面' : '恢复自动封面'
})

function normalizeText(value) {
  return String(value ?? '').trim()
}

function normalizeDate(value) {
  return value ? String(value).slice(0, 10) : ''
}

function normalizeCoordinate(value) {
  if (value == null || String(value).trim() === '') return null
  const coordinate = Number(value)
  return Number.isFinite(coordinate) ? coordinate : null
}

function normalizeForm(source) {
  return {
    title: normalizeText(source.title),
    destination: normalizeText(source.destination),
    destinationCountry: normalizeText(source.destinationCountry),
    destinationLatitude: normalizeCoordinate(source.destinationLatitude),
    destinationLongitude: normalizeCoordinate(source.destinationLongitude),
    startDate: normalizeDate(source.startDate),
    endDate: normalizeDate(source.endDate),
    description: normalizeText(source.description),
    notes: normalizeText(source.notes),
    coverPhotoUrl: normalizeText(source.coverPhotoUrl),
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

function clearPreviewObjectUrl() {
  if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl)
  previewObjectUrl = ''
}

async function chooseCover(file) {
  if (!file || uploadingCover.value) return
  coverError.value = ''
  coverImageFailed.value = false
  clearPreviewObjectUrl()
  previewObjectUrl = URL.createObjectURL(file)
  coverPreview.value = previewObjectUrl
  uploadingCover.value = true
  try {
    const data = new FormData()
    data.append('photo', file)
    const result = await uploadImage(data)
    form.coverPhotoUrl = result.photoUrl
    coverPreview.value = result.photoUrl
    clearCoverRequested.value = false
  } catch (err) {
    coverPreview.value = ''
    coverError.value = err.message || '封面上传失败，请重新选择。'
  } finally {
    uploadingCover.value = false
    clearPreviewObjectUrl()
  }
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
    coverPreview.value = ''
    clearCoverRequested.value = false
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
  if (clearCoverRequested.value) {
    clearCoverRequested.value = false
    form.coverPhotoUrl = normalizeText(originalTrip.value?.coverPhotoUrl)
    coverPreview.value = ''
    return
  }
  if (hasPendingCover.value) {
    form.coverPhotoUrl = normalizeText(originalTrip.value?.coverPhotoUrl)
    coverPreview.value = ''
    coverError.value = ''
    return
  }
  if (!hasExplicitCover.value) return
  clearCoverRequested.value = true
  form.coverPhotoUrl = ''
  coverPreview.value = ''
  coverError.value = ''
  coverImageFailed.value = false
}

function validate() {
  const normalized = normalizeForm(form)
  if (!normalized.title) {
    return '请填写旅行标题。'
  }
  if (normalized.startDate && normalized.endDate && normalized.endDate < normalized.startDate) {
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
    const updated = await updateTrip(route.params.id, {
      ...normalized,
      startDate: normalized.startDate || null,
      endDate: normalized.endDate || null,
      clearCover: clearCoverRequested.value,
    })
    originalTrip.value = updated
    coverPreview.value = ''
    clearCoverRequested.value = false
    applyTrip(updated)
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
  clearPreviewObjectUrl()
})
</script>

<template>
  <section class="trip-form-page">
    <MobilePageHeader title="编辑旅行" :back-to="`/trips/${route.params.id}`" />
    <RouterLink class="trip-form-back" :to="`/trips/${route.params.id}`">← 返回时间线</RouterLink>
    <div class="page-header trip-form-header">
      <div>
        <p class="trip-form-kicker">旅行设置</p>
        <h1>编辑旅行</h1>
        <p class="muted">修正这次旅行的基本信息，原有封面会保留。</p>
      </div>
    </div>

    <p v-if="loading" class="muted">正在加载这次旅行...</p>
    <p v-else-if="error && !originalTrip" class="error">{{ error }}</p>

    <form v-else-if="originalTrip" class="form trip-form-layout" @submit.prevent="submit">
      <TripCoverEditor
        :preview-url="coverPhotoUrl"
        :location-label="form.destination"
        :uploading="uploadingCover"
        alt="当前旅行封面"
        @select="chooseCover"
        @error="coverImageFailed = true"
      />
      <div class="trip-cover-status">
        <span>{{ form.coverPhotoUrl ? '当前使用自定义旅行封面。' : '当前使用旅行记忆中的自动封面。' }}</span>
        <button
          v-if="hasExplicitCover || hasPendingCover"
          type="button"
          @click="clearCover"
        >
          {{ coverSecondaryLabel }}
        </button>
      </div>
      <p v-if="coverError" class="error">{{ coverError }}</p>

      <section class="card trip-form-card">
        <TripFormFields :form="form" id-prefix="trip" :date-error="dateError" />
        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="successMessage" class="muted">{{ successMessage }}</p>
      </section>

      <aside class="trip-cover-guidance">
        <span aria-hidden="true"><ImageIcon :size="22" :stroke-width="1.5" /></span>
        <p>更换封面会在保存修改后生效，取消编辑不会改变原封面。</p>
      </aside>
      <RouterLink :to="`/trips/${route.params.id}/companions`" class="trip-companion-manage-link">管理同行的人</RouterLink>
      <div class="actions trip-form-actions">
        <button :disabled="!canSubmit">{{ saving ? '保存中…' : '保存修改' }}</button>
        <button type="button" class="ghost trip-form-cancel-action" :disabled="saving || redirecting" @click="cancel">取消</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.trip-companion-manage-link {
  justify-self: center;
  color: var(--tm-accent);
  font-size: 13px;
}
</style>
