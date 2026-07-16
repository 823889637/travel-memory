<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Image as ImageIcon, LoaderCircle, Save } from '@lucide/vue'
import { useRouter } from 'vue-router'
import {
  createTrip,
  deleteTripDraft,
  getTripDraft,
  saveTripDraft,
  uploadImage,
} from '../api/trip'
import MobilePageHeader from '../components/MobilePageHeader.vue'
import TripCoverEditor from '../components/TripCoverEditor.vue'
import TripFormFields from '../components/TripFormFields.vue'

const router = useRouter()
const saving = ref(false)
const uploadingCover = ref(false)
const draftSaving = ref(false)
const draftLoaded = ref(false)
const draftMessage = ref('')
const error = ref('')
const coverPreview = ref('')
let previewObjectUrl = ''
let draftTimer = null

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
  if (!form.startDate || !form.endDate) return ''
  return form.endDate < form.startDate ? '结束日期不能早于开始日期' : ''
})

const canSubmit = computed(() => !saving.value && !uploadingCover.value && !dateError.value && form.title.trim())

function draftPayload() {
  return {
    title: form.title.trim() || null,
    destination: form.destination.trim() || null,
    destinationCountry: form.destinationCountry.trim() || null,
    destinationLatitude: form.destinationLatitude,
    destinationLongitude: form.destinationLongitude,
    startDate: form.startDate || null,
    endDate: form.endDate || null,
    description: form.description.trim() || null,
    notes: form.notes.trim() || null,
    coverPhotoUrl: form.coverPhotoUrl || null,
  }
}

function clearPreviewObjectUrl() {
  if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl)
  previewObjectUrl = ''
}

async function chooseCover(file) {
  if (!file || uploadingCover.value) return
  error.value = ''
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
    await saveDraft('封面已保存到草稿')
  } catch (err) {
    form.coverPhotoUrl = ''
    coverPreview.value = ''
    error.value = err.message || '封面上传失败，请重新选择。'
  } finally {
    uploadingCover.value = false
    clearPreviewObjectUrl()
  }
}

async function loadDraft() {
  try {
    const draft = await getTripDraft()
    if (draft) {
      form.title = draft.title || ''
      form.destination = draft.destination || ''
      form.destinationCountry = draft.destinationCountry || ''
      form.destinationLatitude = draft.destinationLatitude ?? null
      form.destinationLongitude = draft.destinationLongitude ?? null
      form.startDate = draft.startDate || ''
      form.endDate = draft.endDate || ''
      form.description = draft.description || ''
      form.notes = draft.notes || ''
      form.coverPhotoUrl = draft.coverPhotoUrl || ''
      coverPreview.value = draft.coverPhotoUrl || ''
      draftMessage.value = '已恢复上次未完成的旅行草稿'
    }
  } catch (err) {
    error.value = err.message || '旅行草稿暂时无法读取。'
  } finally {
    draftLoaded.value = true
  }
}

async function saveDraft(message = '草稿已保存') {
  if (!draftLoaded.value || saving.value || draftSaving.value || dateError.value) return
  draftSaving.value = true
  try {
    await saveTripDraft(draftPayload())
    draftMessage.value = message
  } catch (err) {
    error.value = err.message || '草稿暂时没有保存成功。'
  } finally {
    draftSaving.value = false
  }
}

async function submit() {
  error.value = ''
  if (!form.title.trim()) {
    error.value = '请填写旅行标题。'
    return
  }
  if (dateError.value) {
    error.value = dateError.value
    return
  }

  saving.value = true
  try {
    const trip = await createTrip(draftPayload())
    await deleteTripDraft().catch(() => {})
    router.push(`/trips/${trip.id}`)
  } catch (err) {
    error.value = err.message || '创建失败'
  } finally {
    saving.value = false
  }
}

watch(form, () => {
  if (!draftLoaded.value) return
  clearTimeout(draftTimer)
  draftTimer = setTimeout(() => saveDraft(), 900)
}, { deep: true })

onMounted(loadDraft)
onBeforeUnmount(() => {
  clearTimeout(draftTimer)
  clearPreviewObjectUrl()
})
</script>

<template>
  <section class="trip-form-page trip-create-mobile-page">
    <MobilePageHeader title="创建旅行" back-to="/trips" :show-user-menu="false">
      <template #actions>
        <button class="trip-draft-header-action" type="button" :disabled="draftSaving" aria-label="保存旅行草稿" @click="saveDraft()">
          <LoaderCircle v-if="draftSaving" :size="17" class="spin" aria-hidden="true" />
          <span>{{ draftSaving ? '保存中…' : '保存草稿' }}</span>
        </button>
      </template>
    </MobilePageHeader>
    <RouterLink class="trip-form-back" to="/trips">← 返回旅行列表</RouterLink>
    <div class="page-header trip-form-header">
      <div>
        <p class="trip-form-kicker">新的旅程</p>
        <h1>创建旅行</h1>
        <p class="muted">先留下旅行的轮廓，照片和 Memory 可以慢慢补充。</p>
      </div>
    </div>

    <form class="form trip-form-layout" @submit.prevent="submit">
      <TripCoverEditor
        :preview-url="coverPreview"
        :location-label="form.destination"
        :uploading="uploadingCover"
        @select="chooseCover"
      />

      <section class="card trip-form-card">
        <TripFormFields :form="form" id-prefix="create-trip" :date-error="dateError" />
        <p v-if="draftMessage" class="trip-draft-message">{{ draftMessage }}</p>
        <p v-if="error" class="error">{{ error }}</p>
      </section>

      <aside class="trip-cover-guidance">
        <span aria-hidden="true"><ImageIcon :size="22" :stroke-width="1.5" /></span>
        <p>封面以后仍可以从旅行记忆中随时更换。</p>
      </aside>
      <div class="actions trip-form-actions">
        <button :disabled="!canSubmit"><Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : '保存旅行' }}</button>
        <button type="button" class="ghost trip-form-cancel-action" :disabled="saving" @click="router.push('/trips')">取消</button>
      </div>
    </form>
  </section>
</template>
