<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ArrowLeft, CalendarDays, CheckCircle2, ChevronDown, MapPin } from '@lucide/vue'
import {
  createMemory,
  deleteMemoryDraft,
  getMemoryDraft,
  reverseGeocode,
  saveMemoryDraft,
  uploadPhoto,
} from '../api/memory'
import CompanionSelector from '../components/CompanionSelector.vue'
import LocationPicker from '../components/LocationPicker.vue'
import MemoryPhotoEditor from '../components/MemoryPhotoEditor.vue'
import { formatLocalDateTime, toDateTimeLocalValue } from '../utils/dateTime'

const props = defineProps({ id: { type: String, required: true } })
const router = useRouter()

const MAX_PHOTOS = 6
const MAX_PHOTO_SIZE = 50 * 1024 * 1024
const ALLOWED_PHOTO_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'heic', 'heif']

const saving = ref(false)
const draftSaving = ref(false)
const locating = ref(false)
const uploading = ref(false)
const error = ref('')
const photoError = ref('')
const draftMessage = ref('')
const photoInput = ref(null)
const photoItems = ref([])
const selectedPhotoIndex = ref(0)
const photoUploadResult = ref(null)
const showMoreLocation = ref(false)
const recordTimeTouched = ref(false)
const latitudeTouched = ref(false)
const longitudeTouched = ref(false)
const locationNameTouched = ref(false)
const locationSuggestion = ref(null)
const locationSuggestionStatus = ref('idle')
const showLocationPicker = ref(false)
const selectedCompanionIds = ref([])
const savedSnapshot = ref('')
const skipLeavePrompt = ref(false)

const form = reactive({ content: '', latitude: '', longitude: '', locationName: '', recordTime: '' })
const latitudeError = computed(() => validateCoordinate(form.latitude, -90, 90, '纬度'))
const longitudeError = computed(() => validateCoordinate(form.longitude, -180, 180, '经度'))
const coordinateError = computed(() => latitudeError.value || longitudeError.value)
const hasCoordinates = computed(() => form.latitude !== '' && form.longitude !== '')
const canSubmit = computed(() => !saving.value && !uploading.value && !coordinateError.value)
const hasUploadResult = computed(() => Boolean(photoUploadResult.value))
const hasExifTime = computed(() => Boolean(photoUploadResult.value?.photoTakenTime))
const hasExifLocation = computed(() => photoUploadResult.value?.latitude != null && photoUploadResult.value?.longitude != null)
const formattedExifLocation = computed(() => hasExifLocation.value
  ? `${formatCoordinate(photoUploadResult.value.latitude)}, ${formatCoordinate(photoUploadResult.value.longitude)}`
  : '')
const hasLocationSuggestion = computed(() => locationSuggestionStatus.value === 'success' && locationSuggestion.value?.locationName)
const isDirty = computed(() => savedSnapshot.value !== '' && currentSnapshot() !== savedSnapshot.value)

function currentSnapshot() {
  return JSON.stringify({
    content: form.content,
    latitude: form.latitude,
    longitude: form.longitude,
    locationName: form.locationName,
    recordTime: form.recordTime,
    photos: photoItems.value.map(item => item.result?.photoUrl || item.preview),
    companionIds: [...selectedCompanionIds.value].map(Number).sort((a, b) => a - b),
  })
}

function formatCoordinate(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue.toFixed(7) : String(value)
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

function triggerPhotoPicker() {
  if (photoItems.value.length < MAX_PHOTOS && !saving.value) photoInput.value?.click()
}

async function onPhotoChange(event) {
  photoError.value = ''
  const selectedFiles = Array.from(event.target.files || [])
  const available = MAX_PHOTOS - photoItems.value.length
  if (selectedFiles.length > available) photoError.value = `一段记忆最多保存 ${MAX_PHOTOS} 张照片，只添加了前 ${available} 张。`

  for (const selectedFile of selectedFiles.slice(0, available)) {
    const extension = getFileExtension(selectedFile.name)
    if (!ALLOWED_PHOTO_EXTENSIONS.includes(extension)) {
      photoError.value = `${selectedFile.name} 不是支持的图片格式。`
      continue
    }
    if (selectedFile.size > MAX_PHOTO_SIZE) {
      photoError.value = `${selectedFile.name} 超过 50MB，请压缩后重试。`
      continue
    }
    const item = {
      key: `new-${crypto.randomUUID()}`,
      file: selectedFile,
      preview: URL.createObjectURL(selectedFile),
      result: null,
      error: '',
      uploading: false,
    }
    photoItems.value.push(item)
    await uploadSelectedPhoto(item)
  }
  event.target.value = ''
}

async function uploadSelectedPhoto(item) {
  if (!item?.file) return
  uploading.value = true
  item.uploading = true
  item.error = ''
  const data = new FormData()
  data.append('photo', item.file)
  try {
    item.result = await uploadPhoto(data)
    if (photoItems.value[0]?.key === item.key) {
      photoUploadResult.value = item.result
      applyPhotoMetadata(item.result)
    }
  } catch (err) {
    item.error = err.message || '图片上传失败，请重试'
  } finally {
    item.uploading = false
    uploading.value = photoItems.value.some(photo => photo.uploading)
  }
}

function retryPhoto(item) { uploadSelectedPhoto(item) }

function releasePreview(item) {
  if (item?.preview?.startsWith('blob:')) URL.revokeObjectURL(item.preview)
}

function removePhoto(index) {
  const [removed] = photoItems.value.splice(index, 1)
  releasePreview(removed)
  selectedPhotoIndex.value = Math.min(selectedPhotoIndex.value, Math.max(0, photoItems.value.length - 1))
  syncPrimaryPhoto()
}

function reorderPhoto(fromIndex, toIndex) {
  if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= photoItems.value.length || toIndex >= photoItems.value.length) return
  const items = [...photoItems.value]
  const [item] = items.splice(fromIndex, 1)
  items.splice(toIndex, 0, item)
  photoItems.value = items
  selectedPhotoIndex.value = toIndex
  syncPrimaryPhoto()
}

function setPrimaryPhoto(index) {
  if (index <= 0 || index >= photoItems.value.length) return
  reorderPhoto(index, 0)
  selectedPhotoIndex.value = 0
}

function syncPrimaryPhoto() {
  photoUploadResult.value = photoItems.value[0]?.result || null
  if (photoUploadResult.value) applyPhotoMetadata(photoUploadResult.value)
}

function applyPhotoMetadata(result) {
  if (result?.photoTakenTime && !recordTimeTouched.value && !form.recordTime) {
    form.recordTime = toDateTimeLocalValue(result.photoTakenTime)
  }
  if (result?.latitude != null && !latitudeTouched.value && !form.latitude) form.latitude = String(result.latitude)
  if (result?.longitude != null && !longitudeTouched.value && !form.longitude) form.longitude = String(result.longitude)
  if (hasCoordinates.value && !form.locationName && !locationNameTouched.value) requestLocationSuggestion()
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

function getLocation() {
  if (!navigator.geolocation) {
    error.value = '当前浏览器不支持定位，请手动填写经纬度。'
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (position) => {
      form.latitude = position.coords.latitude.toFixed(7)
      form.longitude = position.coords.longitude.toFixed(7)
      latitudeTouched.value = true
      longitudeTouched.value = true
      requestLocationSuggestion({ force: true })
      locating.value = false
    },
    (positionError) => {
      const messages = {
        1: '定位权限被拒绝，仍可以搜索地点或手动填写。',
        2: '暂时无法获取当前位置，仍可以搜索地点或手动填写。',
        3: '定位超时，仍可以搜索地点或手动填写。',
      }
      error.value = messages[positionError.code] || '定位失败，请手动填写地点。'
      locating.value = false
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
  )
}

function onRecordTimeInput() { recordTimeTouched.value = true }
function onLatitudeInput() { latitudeTouched.value = true; resetLocationSuggestion() }
function onLongitudeInput() { longitudeTouched.value = true; resetLocationSuggestion() }
function onLocationNameInput() { locationNameTouched.value = true; if (form.locationName.trim()) resetLocationSuggestion() }

function usePrimaryPhotoTime() {
  const value = toDateTimeLocalValue(photoUploadResult.value?.photoTakenTime)
  if (!value) return
  form.recordTime = value
  recordTimeTouched.value = true
}

function applyPickedLocation(location) {
  form.locationName = location.locationName || ''
  form.latitude = location.latitude == null ? '' : String(location.latitude)
  form.longitude = location.longitude == null ? '' : String(location.longitude)
  latitudeTouched.value = true
  longitudeTouched.value = true
  locationNameTouched.value = true
  resetLocationSuggestion()
  showLocationPicker.value = false
}

function validateBeforePersistence() {
  error.value = ''
  if (coordinateError.value) { error.value = coordinateError.value; return false }
  if ((form.latitude === '') !== (form.longitude === '')) { error.value = '请同时填写纬度和经度'; return false }
  if (photoItems.value.some(item => !item.result?.photoUrl)) {
    error.value = '仍有照片没有上传成功，请重试或删除失败照片。'
    return false
  }
  return true
}

function draftPayload() {
  return {
    tripId: Number(props.id),
    content: form.content || null,
    locationName: form.locationName || null,
    recordTime: form.recordTime || null,
    latitude: form.latitude === '' ? null : Number(form.latitude),
    longitude: form.longitude === '' ? null : Number(form.longitude),
    photoUrls: photoItems.value.map(item => item.result.photoUrl),
    companionIds: selectedCompanionIds.value,
  }
}

async function saveDraft() {
  draftMessage.value = ''
  if (uploading.value || !validateBeforePersistence()) return
  draftSaving.value = true
  try {
    await saveMemoryDraft(draftPayload())
    savedSnapshot.value = currentSnapshot()
    draftMessage.value = '草稿已保存'
  } catch (err) {
    error.value = err.message || '草稿暂时没有保存成功。'
  } finally {
    draftSaving.value = false
  }
}

async function loadDraft() {
  try {
    const draft = await getMemoryDraft(props.id)
    if (draft) {
      form.content = draft.content || ''
      form.locationName = draft.locationName || ''
      form.recordTime = toDateTimeLocalValue(draft.recordTime)
      form.latitude = draft.latitude == null ? '' : String(draft.latitude)
      form.longitude = draft.longitude == null ? '' : String(draft.longitude)
      selectedCompanionIds.value = draft.companionIds || []
      photoItems.value = (draft.photoUrls || []).map((photoUrl, index) => ({
        key: `draft-${index}-${photoUrl}`,
        file: null,
        preview: photoUrl,
        result: { photoUrl },
        error: '',
        uploading: false,
      }))
      syncPrimaryPhoto()
      recordTimeTouched.value = Boolean(form.recordTime)
      locationNameTouched.value = Boolean(form.locationName)
      draftMessage.value = '已恢复上次保存的草稿'
    }
  } catch (err) {
    draftMessage.value = '草稿暂不可用，不影响新增记忆。'
  } finally {
    savedSnapshot.value = currentSnapshot()
  }
}

async function submit() {
  if (!validateBeforePersistence()) return
  let recordTime = form.recordTime
  if (!recordTime) {
    const confirmed = window.confirm('未选择记录时间，将使用当前时间保存。这样可能影响旅程回放顺序，是否继续？')
    if (!confirmed) return
    recordTime = formatLocalDateTime(new Date())
  }
  saving.value = true
  const data = new FormData()
  data.append('tripId', props.id)
  if (form.content) data.append('content', form.content)
  if (form.latitude) data.append('latitude', form.latitude)
  if (form.longitude) data.append('longitude', form.longitude)
  if (form.locationName) data.append('locationName', form.locationName)
  data.append('recordTime', recordTime)
  photoItems.value.forEach(item => data.append('photoUrl', item.result.photoUrl))
  selectedCompanionIds.value.forEach(id => data.append('companionId', id))
  try {
    const created = await createMemory(data)
    await deleteMemoryDraft(props.id).catch(() => {})
    skipLeavePrompt.value = true
    router.push(created?.id ? `/trips/${props.id}/memories/${created.id}` : `/trips/${props.id}`)
  } catch (err) {
    error.value = err.message || '保存失败，当前表单内容仍然保留。'
  } finally {
    saving.value = false
  }
}

function confirmDiscard() { return !isDirty.value || window.confirm('还有未保存的修改，确定要放弃吗？') }
function cancel() { if (confirmDiscard()) { skipLeavePrompt.value = true; router.push(`/trips/${props.id}`) } }
function handleBeforeUnload(event) { if (!skipLeavePrompt.value && isDirty.value) { event.preventDefault(); event.returnValue = '' } }

onBeforeRouteLeave(() => (!skipLeavePrompt.value && !confirmDiscard() ? false : true))
onMounted(() => { window.addEventListener('beforeunload', handleBeforeUnload); loadDraft() })
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  photoItems.value.forEach(releasePreview)
})
</script>

<template>
  <section class="memory-form-page">
    <header class="memory-form-topbar">
      <button type="button" class="memory-form-back" aria-label="返回时间线" @click="cancel">
        <ArrowLeft :size="20" aria-hidden="true" /><span>返回</span>
      </button>
      <h1>新增记忆</h1>
      <button type="button" class="memory-draft-button" :disabled="draftSaving || saving || uploading" @click="saveDraft">
        {{ draftSaving ? '保存中…' : '保存草稿' }}
      </button>
      <p v-if="draftMessage" class="memory-draft-status" role="status">{{ draftMessage }}</p>
    </header>

    <form class="memory-form" @submit.prevent="submit">
      <section class="memory-photo-section" aria-labelledby="memory-create-photo-title">
        <div class="memory-photo-section-head">
          <h2 id="memory-create-photo-title">照片</h2>
          <span>{{ photoItems.length }} / {{ MAX_PHOTOS }}</span>
        </div>
        <input ref="photoInput" hidden type="file" accept="image/*" multiple @change="onPhotoChange" />
        <MemoryPhotoEditor
          :photos="photoItems"
          :selected-index="selectedPhotoIndex"
          :max-photos="MAX_PHOTOS"
          :disabled="saving || uploading"
          @add="triggerPhotoPicker"
          @select="selectedPhotoIndex = $event"
          @remove="removePhoto"
          @reorder="reorderPhoto"
          @set-primary="setPrimaryPhoto"
          @retry="retryPhoto"
        />
        <p v-if="photoError" class="memory-form-error" role="alert">{{ photoError }}</p>
        <p v-if="uploading" class="memory-form-help" role="status">正在上传并识别照片信息…</p>
      </section>

      <section class="memory-form-card memory-copy-card">
        <h2 class="memory-form-section-title">这一刻想记住什么？</h2>
        <div class="memory-content-field">
          <label class="sr-only" for="memory-create-content">这一刻想记住什么</label>
          <textarea id="memory-create-content" v-model="form.content" maxlength="300" placeholder="记录这一刻的想法……&#10;当时的感受、遇见的风景、听到的话……"></textarea>
          <p class="memory-character-count">{{ form.content.length }} / 300</p>
        </div>
      </section>

      <section class="memory-form-card memory-details-card">
        <div class="memory-detail-group">
          <h2 class="memory-form-section-title">记录时间</h2>
          <label class="memory-time-field" :class="{ 'is-empty': !form.recordTime }" for="memory-create-time">
            <CalendarDays :size="18" aria-hidden="true" />
            <input id="memory-create-time" v-model="form.recordTime" type="datetime-local" @input="onRecordTimeInput" />
            <span v-if="!form.recordTime" class="memory-time-placeholder" aria-hidden="true">选择记录时间</span>
            <ChevronDown :size="17" aria-hidden="true" />
          </label>
          <p v-if="hasExifTime" class="memory-form-success memory-recognition-status">
            <CheckCircle2 :size="15" aria-hidden="true" />
            <span>已从主图拍摄信息中识别，你可以修改。</span>
            <button type="button" class="memory-inline-action" @click="usePrimaryPhotoTime">使用主图时间</button>
          </p>
          <p v-else-if="hasUploadResult" class="memory-form-help">未识别到照片拍摄时间，请选择记录时间。</p>
        </div>

        <div class="memory-detail-group">
          <h2 class="memory-form-section-title">地点</h2>
          <div class="memory-location-row">
            <MapPin :size="18" aria-hidden="true" />
            <label class="sr-only" for="memory-create-location">地点名称</label>
            <input id="memory-create-location" v-model="form.locationName" maxlength="255" placeholder="输入地点名称" @input="onLocationNameInput" />
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
              <p v-if="hasExifLocation" class="memory-form-help">照片定位：{{ formattedExifLocation }}</p>
              <div class="memory-location-actions">
                <button type="button" :disabled="locating" @click="getLocation">{{ locating ? '定位中…' : '使用当前位置' }}</button>
                <button v-if="hasCoordinates" type="button" :disabled="locationSuggestionStatus === 'loading'" @click="requestLocationSuggestion({ force: true })">识别附近地点</button>
              </div>
              <div class="memory-coordinate-grid">
                <label for="memory-create-lat">纬度<input id="memory-create-lat" v-model.trim="form.latitude" inputmode="decimal" @input="onLatitudeInput" /></label>
                <label for="memory-create-lng">经度<input id="memory-create-lng" v-model.trim="form.longitude" inputmode="decimal" @input="onLongitudeInput" /></label>
              </div>
              <p v-if="latitudeError || longitudeError" class="memory-form-error">{{ latitudeError || longitudeError }}</p>
            </div>
          </div>
        </div>

        <div class="memory-detail-group memory-companion-group">
          <h2 class="memory-form-section-title">同行的人</h2>
          <CompanionSelector v-model="selectedCompanionIds" :trip-id="id" :show-heading="false" />
        </div>
      </section>

      <p v-if="error" class="memory-form-alert" role="alert">{{ error }}</p>
      <div class="memory-save-bar">
        <button type="submit" class="memory-save-button" :disabled="!canSubmit">{{ saving ? '保存中…' : '保存这段记忆' }}</button>
      </div>
    </form>

    <LocationPicker v-if="showLocationPicker" :location-name="form.locationName" :latitude="form.latitude" :longitude="form.longitude" @confirm="applyPickedLocation" @cancel="showLocationPicker = false" />
  </section>
</template>

<style scoped src="../styles/memory-form.css"></style>
