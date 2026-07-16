<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ImagePlus, LoaderCircle, Save } from '@lucide/vue'
import { useRouter } from 'vue-router'
import {
  createTrip,
  deleteTripDraft,
  getTripDraft,
  saveTripDraft,
  uploadImage,
} from '../api/trip'
import MobilePageHeader from '../components/MobilePageHeader.vue'
import TripCityField from '../components/TripCityField.vue'

const router = useRouter()
const saving = ref(false)
const uploadingCover = ref(false)
const draftSaving = ref(false)
const draftLoaded = ref(false)
const draftMessage = ref('')
const error = ref('')
const coverInput = ref(null)
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

async function chooseCover(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
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
    <MobilePageHeader title="创建旅行" back-to="/trips">
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

    <form class="form card trip-form-card" @submit.prevent="submit">
      <section class="trip-create-cover-field">
        <div v-if="coverPreview" class="trip-create-cover-preview">
          <img :src="coverPreview" alt="旅行封面预览" />
        </div>
        <div v-else class="trip-create-cover-placeholder">
          <span>{{ form.destination || '旅行封面' }}</span>
          <small>选择一张真实照片，让这趟旅行更容易被认出。</small>
        </div>
        <button type="button" class="trip-cover-picker" :disabled="uploadingCover" @click="coverInput?.click()">
          <LoaderCircle v-if="uploadingCover" :size="17" class="spin" aria-hidden="true" />
          <ImagePlus v-else :size="17" aria-hidden="true" />
          {{ uploadingCover ? '上传中…' : (coverPreview ? '更换封面' : '添加封面') }}
        </button>
        <input ref="coverInput" class="visually-hidden" type="file" accept="image/*" @change="chooseCover" />
      </section>

      <div class="field">
        <label for="create-trip-title">旅行标题</label>
        <input id="create-trip-title" v-model="form.title" required maxlength="100" placeholder="例如：天津之旅" />
      </div>
      <TripCityField
        id="create-trip-destination"
        v-model="form.destination"
        v-model:country="form.destinationCountry"
        v-model:latitude="form.destinationLatitude"
        v-model:longitude="form.destinationLongitude"
      />
      <div class="trip-form-date-grid">
        <div class="field">
          <label for="create-trip-start-date">开始日期</label>
          <input id="create-trip-start-date" v-model="form.startDate" type="date" :max="form.endDate || undefined" />
        </div>
        <div class="field">
          <label for="create-trip-end-date">结束日期</label>
          <input id="create-trip-end-date" v-model="form.endDate" type="date" :min="form.startDate || undefined" />
        </div>
      </div>
      <p v-if="dateError" class="error">{{ dateError }}</p>

      <div class="field">
        <label for="create-trip-description">一句话描述</label>
        <textarea id="create-trip-description" v-model="form.description" maxlength="500" placeholder="这趟旅行最想留住的是什么？"></textarea>
        <small>{{ form.description.length }}/500</small>
      </div>
      <div class="field">
        <label for="create-trip-notes">旅行笔记 <span class="muted">（可选）</span></label>
        <textarea id="create-trip-notes" v-model="form.notes" maxlength="1000" placeholder="记录期待、灵感，或以后想补充的内容…"></textarea>
        <small>{{ form.notes.length }}/1000</small>
      </div>

      <p v-if="draftMessage" class="trip-draft-message">{{ draftMessage }}</p>
      <p v-if="error" class="error">{{ error }}</p>

      <div class="actions trip-form-actions">
        <button :disabled="!canSubmit"><Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : '保存旅行' }}</button>
        <button type="button" class="ghost" :disabled="saving" @click="router.push('/trips')">取消</button>
      </div>
    </form>
  </section>
</template>
