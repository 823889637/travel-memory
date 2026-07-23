<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ArrowLeft, CalendarDays, ChevronDown, MapPin } from '@lucide/vue'
import {
  deleteMemoryDraft,
  getMemory,
  getMemoryDraft,
  reverseGeocode,
  saveMemoryDraft,
  updateMemory,
  uploadPhoto,
} from '../api/memory'
import CompanionSelector from '../components/CompanionSelector.vue'
import LocationPicker from '../components/LocationPicker.vue'
import MemoryPhotoEditor from '../components/MemoryPhotoEditor.vue'
import { toDateTimeLocalValue } from '../utils/dateTime'

const props = defineProps({
  tripId: { type: String, required: true },
  memoryId: { type: String, required: true },
})

const router = useRouter()
const MAX_PHOTOS = 6
const MAX_PHOTO_SIZE = 50 * 1024 * 1024
const ALLOWED_PHOTO_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'heic', 'heif']

const loading = ref(true)
const saving = ref(false)
const draftSaving = ref(false)
const uploading = ref(false)
const error = ref('')
const loadError = ref('')
const photoError = ref('')
const draftMessage = ref('')
const showMoreLocation = ref(false)
const showLocationPicker = ref(false)
const photoDrafts = ref([])
const selectedPhotoIndex = ref(0)
const selectedCompanionIds = ref([])
const locationNameTouched = ref(false)
const locationSuggestion = ref(null)
const locationSuggestionStatus = ref('idle')
const savedSnapshot = ref('')
const skipLeavePrompt = ref(false)
const originalPhotosByUrl = new Map()

const form = reactive({ content: '', locationName: '', recordTime: '', latitude: '', longitude: '' })
const latitudeError = computed(() => validateCoordinate(form.latitude, -90, 90, '纬度'))
const longitudeError = computed(() => validateCoordinate(form.longitude, -180, 180, '经度'))
const coordinateError = computed(() => latitudeError.value || longitudeError.value)
const hasCoordinates = computed(() => form.latitude !== '' && form.longitude !== '')
const hasLocationSuggestion = computed(() => locationSuggestionStatus.value === 'success' && locationSuggestion.value?.locationName)
const isDirty = computed(() => savedSnapshot.value !== '' && currentSnapshot() !== savedSnapshot.value)
const canSubmit = computed(() => !loading.value && !saving.value && !draftSaving.value && !uploading.value && !coordinateError.value)

function memoryDetailPath() {
  return `/trips/${props.tripId}/memories/${props.memoryId}`
}

function returnToMemoryDetail() {
  const target = memoryDetailPath()
  if (window.history.state?.back === target) {
    router.back()
    return
  }
  router.replace(target)
}

function normalizeText(value) {
  return String(value ?? '').trim()
}

function normalizeCoordinate(value) {
  return value === '' || value == null ? '' : String(Number(value))
}

function currentSnapshot() {
  return JSON.stringify({
    content: normalizeText(form.content),
    locationName: normalizeText(form.locationName),
    recordTime: form.recordTime || '',
    latitude: normalizeCoordinate(form.latitude),
    longitude: normalizeCoordinate(form.longitude),
    companionIds: [...selectedCompanionIds.value].map(Number).sort((a, b) => a - b),
    photos: photoDrafts.value.map(photo => photo.photoUrl || photo.previewUrl),
  })
}

function validateCoordinate(value, min, max, label) {
  if (value === '' || value == null) return ''
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) return `${label}必须是数字`
  if (numberValue < min || numberValue > max) return `${label}范围应为 ${min} 到 ${max}`
  return ''
}

function getFileExtension(filename) {
  const dotIndex = filename.lastIndexOf('.')
  return dotIndex < 0 ? '' : filename.slice(dotIndex + 1).toLowerCase()
}

function validatePhotoFile(file) {
  if (!ALLOWED_PHOTO_EXTENSIONS.includes(getFileExtension(file.name))) return '仅支持 jpg、jpeg、png、gif、webp、heic、heif 图片'
  if (file.size > MAX_PHOTO_SIZE) return '图片不能超过 50MB，请压缩后再上传'
  return ''
}

async function onPhotoChange(event) {
  photoError.value = ''
  const files = Array.from(event.target.files || [])
  const remaining = MAX_PHOTOS - photoDrafts.value.length
  const addedPhotos = []
  for (const file of files.slice(0, remaining)) {
    const validationError = validatePhotoFile(file)
    if (validationError) {
      photoError.value = `${file.name}：${validationError}`
      continue
    }
    const photo = {
      key: `new-${crypto.randomUUID()}`,
      id: null,
      photoUrl: '',
      previewUrl: URL.createObjectURL(file),
      file,
      error: '',
      uploading: false,
      uploadProgress: null,
    }
    photoDrafts.value.push(photo)
    addedPhotos.push(photo)
  }
  if (files.length > remaining) photoError.value = `一段记忆最多保存 ${MAX_PHOTOS} 张照片，只添加了前 ${remaining} 张。`
  event.target.value = ''

  for (const photo of addedPhotos) {
    try {
      await uploadOnePhoto(photo)
    } catch {
      // Keep the per-photo error and retry control visible in the editor.
    }
  }
}

function releasePreview(photo) {
  if (photo?.previewUrl?.startsWith('blob:')) URL.revokeObjectURL(photo.previewUrl)
}

function removePhoto(index) {
  const [removed] = photoDrafts.value.splice(index, 1)
  releasePreview(removed)
  selectedPhotoIndex.value = Math.min(selectedPhotoIndex.value, Math.max(0, photoDrafts.value.length - 1))
}

function reorderPhoto(fromIndex, toIndex) {
  if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= photoDrafts.value.length || toIndex >= photoDrafts.value.length) return
  const next = [...photoDrafts.value]
  const [photo] = next.splice(fromIndex, 1)
  next.splice(toIndex, 0, photo)
  photoDrafts.value = next
  selectedPhotoIndex.value = toIndex
}

function setPrimaryPhoto(index) {
  if (index > 0 && index < photoDrafts.value.length) {
    reorderPhoto(index, 0)
    selectedPhotoIndex.value = 0
  }
}

async function uploadOnePhoto(photo) {
  if (!photo?.file) return
  photo.uploading = true
  photo.uploadProgress = null
  photo.error = ''
  uploading.value = true
  try {
    const data = new FormData()
    data.append('photo', photo.file)
    const uploaded = await uploadPhoto(data, {
      onUploadProgress(event) {
        if (!event.total) return
        photo.uploadProgress = Math.min(99, Math.max(0, Math.round((event.loaded / event.total) * 100)))
      },
    })
    photo.uploadProgress = 100
    photo.photoUrl = uploaded.photoUrl
    releasePreview(photo)
    photo.previewUrl = uploaded.photoUrl
    photo.file = null
  } catch (uploadError) {
    photo.error = uploadError.message || '照片上传失败，请重试'
    throw uploadError
  } finally {
    photo.uploading = false
    photo.uploadProgress = null
    uploading.value = photoDrafts.value.some(item => item.uploading)
  }
}

async function retryPhoto(photo) {
  try {
    await uploadOnePhoto(photo)
  } catch {
    // The per-photo error is shown beside the thumbnail.
  }
}

async function uploadPendingPhotos() {
  for (let index = 0; index < photoDrafts.value.length; index += 1) {
    const photo = photoDrafts.value[index]
    if (!photo.file) continue
    try {
      await uploadOnePhoto(photo)
    } catch (uploadError) {
      throw new Error(`第 ${index + 1} 张照片上传失败：${uploadError.message || '请重试'}`)
    }
  }
}

function resetLocationSuggestion() {
  locationSuggestion.value = null
  locationSuggestionStatus.value = 'idle'
}

async function requestLocationSuggestion(options = {}) {
  if (!hasCoordinates.value || coordinateError.value || (form.locationName.trim() && options.force !== true)) return
  locationSuggestionStatus.value = 'loading'
  try {
    const result = await reverseGeocode(form.latitude, form.longitude)
    if (result?.success && result.locationName) {
      locationSuggestion.value = result
      locationSuggestionStatus.value = 'success'
    } else {
      locationSuggestion.value = null
      locationSuggestionStatus.value = 'empty'
    }
  } catch {
    locationSuggestion.value = null
    locationSuggestionStatus.value = 'empty'
  }
}

function useLocationSuggestion() {
  if (!locationSuggestion.value?.locationName) return
  form.locationName = locationSuggestion.value.locationName
  locationNameTouched.value = true
}

function onLocationNameInput() {
  locationNameTouched.value = true
  if (form.locationName.trim()) resetLocationSuggestion()
}

function applyPickedLocation(location) {
  form.locationName = location.locationName || ''
  form.latitude = location.latitude == null ? '' : String(location.latitude)
  form.longitude = location.longitude == null ? '' : String(location.longitude)
  locationNameTouched.value = true
  resetLocationSuggestion()
  showLocationPicker.value = false
}

function validateForm({ requireTime = true } = {}) {
  error.value = ''
  if (requireTime && !form.recordTime) {
    error.value = '请选择记录时间'
    return false
  }
  if (coordinateError.value) {
    error.value = coordinateError.value
    return false
  }
  if ((form.latitude === '') !== (form.longitude === '')) {
    error.value = '请同时填写纬度和经度'
    return false
  }
  return true
}

function draftPayload() {
  return {
    tripId: Number(props.tripId),
    memoryId: Number(props.memoryId),
    content: normalizeText(form.content) || null,
    locationName: normalizeText(form.locationName) || null,
    recordTime: form.recordTime || null,
    latitude: form.latitude === '' ? null : Number(form.latitude),
    longitude: form.longitude === '' ? null : Number(form.longitude),
    photoUrls: photoDrafts.value.map(photo => photo.photoUrl),
    companionIds: selectedCompanionIds.value,
  }
}

async function saveDraft() {
  draftMessage.value = ''
  if (!validateForm({ requireTime: false })) return
  draftSaving.value = true
  try {
    await uploadPendingPhotos()
    await saveMemoryDraft(draftPayload())
    savedSnapshot.value = currentSnapshot()
    draftMessage.value = '草稿已保存'
  } catch (saveError) {
    error.value = saveError.message || '草稿暂时没有保存成功。'
  } finally {
    draftSaving.value = false
  }
}

function mapStoredPhoto(photo, index) {
  const mapped = {
    key: photo.id ? `stored-${photo.id}` : `stored-${index}-${photo.photoUrl}`,
    id: photo.id || null,
    photoUrl: photo.photoUrl,
    previewUrl: photo.photoUrl,
    file: null,
    error: '',
    uploading: false,
  }
  originalPhotosByUrl.set(photo.photoUrl, mapped)
  return mapped
}

function mapDraftPhoto(photoUrl, index) {
  const original = originalPhotosByUrl.get(photoUrl)
  return {
    key: original?.key || `draft-${index}-${photoUrl}`,
    id: original?.id || null,
    photoUrl,
    previewUrl: photoUrl,
    file: null,
    error: '',
    uploading: false,
  }
}

async function loadMemoryAndDraft() {
  loading.value = true
  error.value = ''
  loadError.value = ''
  try {
    const memory = await getMemory(props.memoryId)
    if (Number(memory.tripId) !== Number(props.tripId)) throw new Error('记忆不存在或不属于当前旅行')
    form.content = memory.content || ''
    form.locationName = memory.locationName || ''
    form.recordTime = toDateTimeLocalValue(memory.recordTime)
    form.latitude = memory.latitude == null ? '' : String(memory.latitude)
    form.longitude = memory.longitude == null ? '' : String(memory.longitude)
    const photos = memory.photos?.length ? memory.photos : (memory.photoUrl ? [{ photoUrl: memory.photoUrl }] : [])
    photoDrafts.value = photos.map(mapStoredPhoto)
    selectedCompanionIds.value = memory.companionIds || memory.companions?.map(item => item.id) || []

    try {
      const draft = await getMemoryDraft(props.tripId, props.memoryId)
      if (draft) {
        form.content = draft.content || ''
        form.locationName = draft.locationName || ''
        form.recordTime = toDateTimeLocalValue(draft.recordTime)
        form.latitude = draft.latitude == null ? '' : String(draft.latitude)
        form.longitude = draft.longitude == null ? '' : String(draft.longitude)
        photoDrafts.value = (draft.photoUrls || []).map(mapDraftPhoto)
        selectedCompanionIds.value = draft.companionIds || []
        draftMessage.value = '已恢复上次保存的草稿'
      }
    } catch (draftLoadFailure) {
      draftMessage.value = '草稿暂不可用，不影响编辑和保存。'
    }
    if (hasCoordinates.value && !form.locationName) requestLocationSuggestion()
    savedSnapshot.value = currentSnapshot()
  } catch (loadFailure) {
    loadError.value = loadFailure.message || '记忆暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

function buildPhotoReferences() {
  return photoDrafts.value.map(photo => photo.id ? { id: photo.id } : { photoUrl: photo.photoUrl })
}

async function submit() {
  if (!validateForm()) return
  saving.value = true
  try {
    await uploadPendingPhotos()
    await updateMemory(props.memoryId, {
      content: normalizeText(form.content) || null,
      locationName: normalizeText(form.locationName) || null,
      recordTime: form.recordTime,
      latitude: form.latitude === '' ? null : Number(form.latitude),
      longitude: form.longitude === '' ? null : Number(form.longitude),
      companionIds: selectedCompanionIds.value,
      photos: buildPhotoReferences(),
    })
    await deleteMemoryDraft(props.tripId, props.memoryId).catch(() => {})
    savedSnapshot.value = currentSnapshot()
    skipLeavePrompt.value = true
    returnToMemoryDetail()
  } catch (saveError) {
    error.value = saveError.message || '保存失败，当前表单和照片草稿仍保留在页面中。'
  } finally {
    saving.value = false
  }
}

function confirmDiscard() {
  return !isDirty.value || window.confirm('还有未保存的修改，确定要放弃吗？')
}

function cancel() {
  if (!confirmDiscard()) return
  skipLeavePrompt.value = true
  returnToMemoryDetail()
}

function handleBeforeUnload(event) {
  if (!skipLeavePrompt.value && isDirty.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onBeforeRouteLeave(() => (!skipLeavePrompt.value && !confirmDiscard() ? false : true))
onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  loadMemoryAndDraft()
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  photoDrafts.value.forEach(releasePreview)
})
</script>

<template>
  <section class="memory-form-page">
    <header class="memory-form-topbar">
      <button type="button" class="memory-form-back" aria-label="返回记忆详情" @click="cancel">
        <ArrowLeft :size="20" aria-hidden="true" /><span>返回</span>
      </button>
      <h1>编辑记忆</h1>
      <button type="button" class="memory-draft-button" :disabled="loading || draftSaving || saving || uploading" @click="saveDraft">
        {{ draftSaving ? '保存中…' : '保存草稿' }}
      </button>
      <p v-if="draftMessage" class="memory-draft-status" role="status">{{ draftMessage }}</p>
    </header>

    <p v-if="loading" class="memory-form-state" role="status">正在取回这段记忆…</p>
    <div v-else-if="loadError" class="memory-form-state">
      <p class="memory-form-alert" role="alert">{{ loadError }}</p>
      <button type="button" class="memory-inline-action" @click="loadMemoryAndDraft">重新加载</button>
    </div>

    <form v-else class="memory-form" @submit.prevent="submit">
      <section class="memory-photo-section" aria-labelledby="memory-edit-photo-title">
        <div class="memory-photo-section-head">
          <h2 id="memory-edit-photo-title">照片</h2>
          <span>{{ photoDrafts.length }} / {{ MAX_PHOTOS }}</span>
        </div>
        <MemoryPhotoEditor
          :photos="photoDrafts"
          :selected-index="selectedPhotoIndex"
          :max-photos="MAX_PHOTOS"
          :disabled="saving || draftSaving || uploading"
          @select="selectedPhotoIndex = $event"
          @remove="removePhoto"
          @reorder="reorderPhoto"
          @set-primary="setPrimaryPhoto"
          @retry="retryPhoto"
          @files-selected="onPhotoChange"
        />
        <p class="memory-form-help">照片、顺序和删除会在保存修改后一起生效。</p>
        <p v-if="photoError" class="memory-form-error" role="alert">{{ photoError }}</p>
      </section>

      <section class="memory-form-card memory-copy-card">
        <h2 class="memory-form-section-title">这一刻想记住什么？</h2>
        <div class="memory-content-field">
          <label class="sr-only" for="memory-edit-content">这一刻想记住什么</label>
          <textarea id="memory-edit-content" v-model="form.content" maxlength="300" placeholder="记录这一刻的想法…&#10;当时的感受、遇见的风景、听到的话…"></textarea>
          <p class="memory-character-count">{{ form.content.length }} / 300</p>
        </div>
      </section>

      <section class="memory-form-card memory-details-card">
        <div class="memory-detail-group">
          <h2 class="memory-form-section-title">记录时间</h2>
          <label class="memory-time-field" :class="{ 'is-empty': !form.recordTime }" for="memory-edit-time">
            <CalendarDays :size="18" aria-hidden="true" />
            <input id="memory-edit-time" v-model="form.recordTime" type="datetime-local" />
            <span v-if="!form.recordTime" class="memory-time-placeholder" aria-hidden="true">选择记录时间</span>
            <ChevronDown :size="17" aria-hidden="true" />
          </label>
        </div>

        <div class="memory-detail-group">
          <h2 class="memory-form-section-title">地点</h2>
          <div class="memory-location-row">
            <MapPin :size="18" aria-hidden="true" />
            <label class="sr-only" for="memory-edit-location">地点名称</label>
            <input id="memory-edit-location" v-model="form.locationName" maxlength="255" placeholder="输入地点名称" @input="onLocationNameInput" />
            <button type="button" class="memory-location-picker-button" @click="showLocationPicker = true">搜索 / 地图选点</button>
          </div>
          <div v-if="locationSuggestionStatus !== 'idle' && !form.locationName" class="memory-location-suggestion">
            <p v-if="locationSuggestionStatus === 'loading'">正在识别附近地点…</p>
            <template v-else-if="hasLocationSuggestion">
              <p>推荐地点：{{ locationSuggestion.locationName }}</p>
              <button type="button" class="memory-inline-action" @click="useLocationSuggestion">使用</button>
            </template>
            <p v-else>暂时无法识别这里的名称，你可以自己填写。</p>
          </div>
          <div class="memory-more">
            <button type="button" class="memory-more-toggle" :aria-expanded="showMoreLocation" @click="showMoreLocation = !showMoreLocation">
              <span>更多位置信息（可选）</span>
              <ChevronDown :size="16" :class="{ expanded: showMoreLocation }" aria-hidden="true" />
            </button>
            <div v-if="showMoreLocation" class="memory-more-panel">
              <div class="memory-coordinate-grid">
                <label for="memory-edit-lat">纬度<input id="memory-edit-lat" v-model.trim="form.latitude" inputmode="decimal" @input="resetLocationSuggestion" /></label>
                <label for="memory-edit-lng">经度<input id="memory-edit-lng" v-model.trim="form.longitude" inputmode="decimal" @input="resetLocationSuggestion" /></label>
              </div>
              <button v-if="hasCoordinates" type="button" class="memory-inline-action" :disabled="locationSuggestionStatus === 'loading'" @click="requestLocationSuggestion({ force: true })">识别附近地点</button>
              <p v-if="coordinateError" class="memory-form-error">{{ coordinateError }}</p>
            </div>
          </div>
        </div>

        <div class="memory-detail-group memory-companion-group">
          <h2 class="memory-form-section-title">同行的人</h2>
          <CompanionSelector v-model="selectedCompanionIds" :trip-id="tripId" :show-heading="false" />
        </div>
      </section>

      <p v-if="error" class="memory-form-alert" role="alert">{{ error }}</p>
      <div class="memory-save-bar">
        <button type="submit" class="memory-save-button" :disabled="!canSubmit">{{ saving ? '保存中…' : '保存修改' }}</button>
      </div>
    </form>

    <LocationPicker v-if="showLocationPicker" :location-name="form.locationName" :latitude="form.latitude" :longitude="form.longitude" @confirm="applyPickedLocation" @cancel="showLocationPicker = false" />
  </section>
</template>

<style scoped src="../styles/memory-form.css"></style>
