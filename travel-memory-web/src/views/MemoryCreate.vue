<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createMemory, reverseGeocode, uploadPhoto } from '../api/memory'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const router = useRouter()
const saving = ref(false)
const locating = ref(false)
const uploading = ref(false)
const error = ref('')
const photo = ref(null)
const photoPreview = ref('')
const photoInput = ref(null)
const photoUploadResult = ref(null)
const showMoreLocation = ref(false)
const recordTimeTouched = ref(false)
const latitudeTouched = ref(false)
const longitudeTouched = ref(false)
const locationNameTouched = ref(false)
const locationSuggestion = ref(null)
const locationSuggestionStatus = ref('idle')

const MAX_PHOTO_SIZE = 50 * 1024 * 1024
const ALLOWED_PHOTO_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'heic', 'heif']

const form = reactive({
  content: '',
  latitude: '',
  longitude: '',
  locationName: '',
  recordTime: '',
})

const latitudeError = computed(() => validateCoordinate(form.latitude, -90, 90, '纬度'))
const longitudeError = computed(() => validateCoordinate(form.longitude, -180, 180, '经度'))
const coordinateError = computed(() => latitudeError.value || longitudeError.value)
const hasCoordinates = computed(() => Boolean(form.latitude && form.longitude))
const canSubmit = computed(() => !saving.value && !uploading.value && !coordinateError.value)
const hasUploadResult = computed(() => Boolean(photoUploadResult.value))
const hasExifTime = computed(() => Boolean(photoUploadResult.value?.photoTakenTime))
const hasExifLocation = computed(() => (
  photoUploadResult.value?.latitude != null && photoUploadResult.value?.longitude != null
))
const formattedExifTime = computed(() => formatDisplayDateTime(photoUploadResult.value?.photoTakenTime))
const formattedExifLocation = computed(() => {
  if (!hasExifLocation.value) {
    return ''
  }
  return `${formatCoordinate(photoUploadResult.value.latitude)}, ${formatCoordinate(photoUploadResult.value.longitude)}`
})
const hasLocationSuggestion = computed(() => (
  locationSuggestionStatus.value === 'success' && locationSuggestion.value?.locationName
))

function formatLocalDateTime(date) {
  const offset = date.getTimezoneOffset()
  const localDate = new Date(date.getTime() - offset * 60 * 1000)
  return localDate.toISOString().slice(0, 16)
}

function toDateTimeLocalValue(value) {
  if (!value) {
    return ''
  }
  const normalizedValue = String(value)
  if (normalizedValue.length >= 16) {
    return normalizedValue.slice(0, 16)
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : formatLocalDateTime(date)
}

function formatDisplayDateTime(value) {
  const dateTimeValue = toDateTimeLocalValue(value)
  return dateTimeValue ? dateTimeValue.replace('T', ' ') : ''
}

function formatCoordinate(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue.toFixed(7) : String(value)
}

function validateCoordinate(value, min, max, label) {
  if (!value) {
    return ''
  }
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) {
    return `${label}必须是数字`
  }
  if (numberValue < min || numberValue > max) {
    return `${label}范围应为 ${min} 到 ${max}`
  }
  return ''
}

function triggerPhotoPicker() {
  photoInput.value?.click()
}

async function onPhotoChange(event) {
  error.value = ''
  photoUploadResult.value = null
  const selectedFile = event.target.files?.[0] || null
  if (!selectedFile) {
    clearPhoto()
    return
  }

  const extension = getFileExtension(selectedFile.name)
  if (!ALLOWED_PHOTO_EXTENSIONS.includes(extension)) {
    event.target.value = ''
    clearPhoto()
    error.value = '仅支持 jpg、jpeg、png、gif、webp、heic、heif 图片'
    return
  }

  if (selectedFile.size > MAX_PHOTO_SIZE) {
    event.target.value = ''
    clearPhoto()
    error.value = '图片不能超过 50MB，请压缩后再上传'
    return
  }

  setPhoto(selectedFile)
  await uploadSelectedPhoto(selectedFile)
}

function setPhoto(selectedFile) {
  if (photoPreview.value) {
    URL.revokeObjectURL(photoPreview.value)
  }
  photo.value = selectedFile
  photoPreview.value = URL.createObjectURL(selectedFile)
}

function clearPhoto() {
  if (photoPreview.value) {
    URL.revokeObjectURL(photoPreview.value)
  }
  photo.value = null
  photoPreview.value = ''
  photoUploadResult.value = null
  resetLocationSuggestion()
  if (photoInput.value) {
    photoInput.value.value = ''
  }
}

async function uploadSelectedPhoto(selectedFile) {
  uploading.value = true
  const data = new FormData()
  data.append('photo', selectedFile)

  try {
    const result = await uploadPhoto(data)
    photoUploadResult.value = result
    applyPhotoMetadata(result)
  } catch (err) {
    error.value = err.message || '图片上传失败，请稍后重试'
  } finally {
    uploading.value = false
  }
}

function applyPhotoMetadata(result) {
  if (result?.photoTakenTime && !recordTimeTouched.value && !form.recordTime) {
    form.recordTime = toDateTimeLocalValue(result.photoTakenTime)
  }

  if (result?.latitude != null && !latitudeTouched.value && !form.latitude) {
    form.latitude = String(result.latitude)
  }
  if (result?.longitude != null && !longitudeTouched.value && !form.longitude) {
    form.longitude = String(result.longitude)
  }

  if (hasCoordinateValues() && !form.locationName && !locationNameTouched.value) {
    requestLocationSuggestion()
  }
}

function hasCoordinateValues() {
  return Boolean(form.latitude && form.longitude)
}

function resetLocationSuggestion() {
  locationSuggestion.value = null
  locationSuggestionStatus.value = 'idle'
}

async function requestLocationSuggestion(options = {}) {
  const force = options.force === true
  if (!hasCoordinateValues() || coordinateError.value) {
    return
  }
  if (form.locationName?.trim()) {
    return
  }
  if (!force && locationNameTouched.value) {
    return
  }

  locationSuggestionStatus.value = 'loading'
  try {
    const result = await reverseGeocode(form.latitude, form.longitude)
    if (result?.success && result.locationName) {
      locationSuggestion.value = result
      locationSuggestionStatus.value = 'success'
      return
    }
    locationSuggestion.value = null
    locationSuggestionStatus.value = 'empty'
  } catch (err) {
    locationSuggestion.value = null
    locationSuggestionStatus.value = 'empty'
  }
}

function useLocationSuggestion() {
  if (!locationSuggestion.value?.locationName) {
    return
  }
  form.locationName = locationSuggestion.value.locationName
  locationNameTouched.value = true
}

function getFileExtension(filename) {
  const dotIndex = filename.lastIndexOf('.')
  if (dotIndex < 0) {
    return ''
  }
  return filename.slice(dotIndex + 1).toLowerCase()
}

function getLocation() {
  if (!navigator.geolocation) {
    error.value = '当前浏览器不支持定位，请手动填写经纬度'
    return
  }

  locating.value = true
  error.value = ''
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
        1: '定位权限被拒绝，请允许位置权限或手动填写经纬度',
        2: '暂时无法获取当前位置，请手动填写经纬度',
        3: '定位超时，请手动填写经纬度',
      }
      error.value = messages[positionError.code] || '定位失败，请手动填写经纬度'
      locating.value = false
    },
    {
      enableHighAccuracy: true,
      timeout: 10000,
      maximumAge: 60000,
    }
  )
}

function onRecordTimeInput() {
  recordTimeTouched.value = true
}

function onLatitudeInput() {
  latitudeTouched.value = true
  resetLocationSuggestion()
}

function onLongitudeInput() {
  longitudeTouched.value = true
  resetLocationSuggestion()
}

function onLocationNameInput() {
  locationNameTouched.value = true
  if (form.locationName.trim()) {
    resetLocationSuggestion()
  }
}

async function submit() {
  error.value = ''
  if (coordinateError.value) {
    error.value = coordinateError.value
    return
  }

  let recordTime = form.recordTime
  if (!recordTime) {
    const confirmed = window.confirm('未选择记录时间，将使用当前时间保存。这样可能影响 Journey 的日期和顺序，是否继续？')
    if (!confirmed) {
      error.value = '请选择记录时间，让这段记忆回到正确的一天。'
      return
    }
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

  if (photoUploadResult.value?.photoUrl) {
    data.append('photoUrl', photoUploadResult.value.photoUrl)
    data.append('photoPath', photoUploadResult.value.photoPath)
  } else if (photo.value) {
    data.append('photo', photo.value)
  }

  try {
    await createMemory(data)
    router.push(`/trips/${props.id}`)
  } catch (err) {
    error.value = err.message || '保存失败'
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(() => {
  if (photoPreview.value) {
    URL.revokeObjectURL(photoPreview.value)
  }
})
</script>

<template>
  <section class="moment">
    <header class="moment-head">
      <h1>留下这一刻</h1>
      <p>上传一张照片，写一句当时想记住的话。</p>
    </header>

    <form class="moment-form" @submit.prevent="submit">
      <div class="photo-block">
        <input
          ref="photoInput"
          class="sr-only"
          type="file"
          accept="image/*"
          @change="onPhotoChange"
        />

        <button
          v-if="!photoPreview"
          type="button"
          class="photo-drop"
          @click="triggerPhotoPicker"
        >
          <span class="photo-drop-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="5" width="18" height="14" rx="3" />
              <circle cx="8.5" cy="10" r="1.6" />
              <path d="M21 16l-4.5-4.5L7 21" />
            </svg>
          </span>
          <span class="photo-drop-title">上传一张旅行照片</span>
          <span class="photo-drop-hint">系统会尝试识别拍摄时间和定位，你只需要确认一下。</span>
        </button>

        <div v-else class="photo-preview">
          <img :src="photoPreview" alt="照片预览" />
          <button type="button" class="photo-change" @click="triggerPhotoPicker">更换</button>
          <button type="button" class="photo-remove" aria-label="移除照片" @click="clearPhoto">×</button>
        </div>

        <div v-if="uploading || hasUploadResult" class="exif-strip">
          <p v-if="uploading">正在识别照片信息...</p>
          <template v-else>
            <p v-if="hasExifTime">已识别拍摄时间：{{ formattedExifTime }}</p>
            <p v-if="hasExifLocation">已识别照片定位</p>
            <p v-if="!hasExifTime && !hasExifLocation">这张照片没有留下时间或定位，你可以自己补上。</p>
          </template>
        </div>
      </div>

      <div class="field">
        <label for="moment-content">这一刻想记住什么？</label>
        <textarea
          id="moment-content"
          v-model="form.content"
          rows="3"
          placeholder="例如：这个船好漂亮啊"
        ></textarea>
        <p class="hint">短短一句就够了，保留当时真实的感觉。</p>
      </div>

      <div class="field">
        <label for="moment-time">记录时间</label>
        <input id="moment-time" v-model="form.recordTime" type="datetime-local" @input="onRecordTimeInput" />
        <p v-if="hasExifTime" class="hint">
          已识别照片拍摄时间：{{ formattedExifTime }}。你可以确认或修改。
        </p>
        <p v-else class="time-warning">
          未识别到照片拍摄时间，请选择记录时间。这个时间会影响 Journey 的日期和顺序。
        </p>
      </div>

      <div class="field">
        <label for="moment-location">地点名称</label>
        <input
          id="moment-location"
          v-model="form.locationName"
          placeholder="例如：海河边上、酒店楼下、那家很好吃的店"
          @input="onLocationNameInput"
        />
        <p class="hint">可以写你自己记得住的地点名，不一定是官方地址。</p>
        <div
          v-if="locationSuggestionStatus !== 'idle' && !form.locationName"
          class="location-suggestion"
        >
          <p v-if="locationSuggestionStatus === 'loading'">正在根据定位推荐地点...</p>
          <template v-else-if="hasLocationSuggestion">
            <p>根据照片定位，可能是：{{ locationSuggestion.locationName }}</p>
            <button type="button" @click="useLocationSuggestion">使用这个地点</button>
          </template>
          <p v-else>暂时没有识别出地点名称，你可以手动填写。</p>
        </div>
      </div>

      <div class="more">
        <button
          type="button"
          class="more-toggle"
          :aria-expanded="showMoreLocation"
          @click="showMoreLocation = !showMoreLocation"
        >
          <span>更多位置信息</span>
          <span class="more-status">
            <span v-if="hasCoordinates" class="more-dot" aria-hidden="true"></span>
            {{ showMoreLocation ? '收起' : (hasCoordinates ? '已定位' : '展开') }}
          </span>
        </button>

        <div v-if="showMoreLocation" class="more-panel">
          <div v-if="hasUploadResult" class="exif-detail">
            <p v-if="hasExifTime">已识别拍摄时间：{{ formattedExifTime }}</p>
            <p v-else>未识别到照片拍摄时间，请手动确认记录时间。</p>
            <p v-if="hasExifLocation">已识别照片定位：{{ formattedExifLocation }}。你可以补充地点名称。</p>
            <p v-else>未识别到照片定位，你可以手动填写地点。</p>
          </div>

          <button type="button" class="locate-btn" :disabled="locating" @click="getLocation">
            {{ locating ? '定位中...' : '获取当前位置' }}
          </button>
          <button
            v-if="hasCoordinates && !form.locationName"
            type="button"
            class="locate-btn"
            :disabled="locationSuggestionStatus === 'loading'"
            @click="requestLocationSuggestion({ force: true })"
          >
            {{ locationSuggestionStatus === 'loading' ? '推荐中...' : '推荐地点名称' }}
          </button>

          <div class="coord-grid">
            <div class="field">
              <label for="moment-lat">纬度 latitude</label>
              <input
                id="moment-lat"
                v-model.trim="form.latitude"
                inputmode="decimal"
                placeholder="例如：39.1234000"
                @input="onLatitudeInput"
              />
              <p v-if="latitudeError" class="error">{{ latitudeError }}</p>
            </div>
            <div class="field">
              <label for="moment-lng">经度 longitude</label>
              <input
                id="moment-lng"
                v-model.trim="form.longitude"
                inputmode="decimal"
                placeholder="例如：117.1234000"
                @input="onLongitudeInput"
              />
              <p v-if="longitudeError" class="error">{{ longitudeError }}</p>
            </div>
          </div>
        </div>
      </div>

      <p v-if="error" class="error error-block">{{ error }}</p>

      <div class="submit-bar">
        <button type="submit" class="save-btn" :disabled="!canSubmit">
          {{ saving ? '保存中...' : '保存这段记忆' }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.moment {
  --paper: #fbf7f1;
  --card: #ffffff;
  --ink: #2c2521;
  --ink-soft: #8a7f76;
  --line: #ece3d8;
  --accent: #c8734a;
  --accent-soft: #f6e7dd;
  width: min(520px, 100%);
  margin: 0 auto;
  padding: 4px 2px 32px;
  color: var(--ink);
}

.moment-head {
  padding: 8px 4px 20px;
}

.moment-head h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.moment-head p {
  margin: 8px 0 0;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.6;
}

.moment-form {
  display: grid;
  gap: 22px;
}

.photo-block {
  display: grid;
  gap: 12px;
}

.photo-drop {
  display: grid;
  justify-items: center;
  gap: 8px;
  width: 100%;
  min-height: 240px;
  padding: 32px 24px;
  border: 1.5px dashed #dcccbb;
  border-radius: 20px;
  background: var(--accent-soft);
  color: var(--ink);
  text-align: center;
}

.photo-drop-icon {
  display: grid;
  place-items: center;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: var(--card);
  color: var(--accent);
  box-shadow: 0 6px 16px rgba(200, 115, 74, 0.14);
}

.photo-drop-title {
  font-size: 16px;
  font-weight: 700;
}

.photo-drop-hint {
  max-width: 280px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.6;
}

.photo-preview {
  position: relative;
  overflow: hidden;
  border-radius: 20px;
  background: #efe7dc;
  box-shadow: 0 18px 40px rgba(44, 37, 33, 0.12);
}

.photo-preview img {
  display: block;
  width: 100%;
  max-height: 60vh;
  object-fit: contain;
  background: #efe7dc;
}

.photo-change {
  position: absolute;
  left: 12px;
  bottom: 12px;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(28, 22, 18, 0.6);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  backdrop-filter: blur(4px);
}

.photo-remove {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border-radius: 50%;
  background: rgba(28, 22, 18, 0.55);
  color: #fff;
  font-size: 20px;
  line-height: 1;
  backdrop-filter: blur(4px);
}

.exif-strip,
.exif-detail {
  display: grid;
  gap: 6px;
  border-radius: 14px;
  background: #fff8ef;
  color: #805135;
  padding: 11px 13px;
  font-size: 13px;
  line-height: 1.5;
}

.exif-strip p,
.exif-detail p {
  margin: 0;
}

.field {
  display: grid;
  gap: 8px;
}

.field label {
  font-size: 15px;
  font-weight: 700;
}

.field textarea,
.field input {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--card);
  padding: 14px 15px;
  color: var(--ink);
  font-size: 15px;
}

.field textarea {
  min-height: 92px;
  line-height: 1.6;
  resize: vertical;
}

.field textarea::placeholder,
.field input::placeholder {
  color: #b8aca0;
}

.field textarea:focus,
.field input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(200, 115, 74, 0.12);
}

.hint {
  margin: 0;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.5;
}

.time-warning {
  margin: 0;
  border-radius: 12px;
  background: #fff6db;
  color: #8a5a17;
  font-size: 13px;
  line-height: 1.5;
  padding: 10px 12px;
}

.location-suggestion {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-radius: 12px;
  background: #fff8ef;
  color: #805135;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.5;
}

.location-suggestion p {
  margin: 0;
}

.location-suggestion button {
  flex: 0 0 auto;
  border: 1px solid rgba(200, 115, 74, 0.28);
  border-radius: 999px;
  background: #fff;
  color: var(--accent);
  padding: 7px 12px;
  font-size: 13px;
  font-weight: 700;
}

.more {
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--card);
  overflow: hidden;
}

.more-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 14px 15px;
  background: transparent;
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

.more-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-soft);
  font-size: 13px;
  font-weight: 500;
}

.more-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
}

.more-panel {
  display: grid;
  gap: 14px;
  padding: 4px 15px 16px;
  border-top: 1px solid var(--line);
}

.locate-btn {
  justify-self: start;
  padding: 9px 16px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--paper);
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.coord-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.coord-grid .field label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
}

.coord-grid .field input {
  padding: 11px 12px;
  font-size: 14px;
}

.error {
  margin: 0;
  color: #c0402c;
  font-size: 13px;
}

.error-block {
  padding: 12px 14px;
  border-radius: 12px;
  background: #fbeae6;
}

.submit-bar {
  position: sticky;
  bottom: 0;
  padding: 12px 0 4px;
  background: linear-gradient(180deg, rgba(246, 247, 249, 0), #f6f7f9 46%);
}

.save-btn {
  width: 100%;
  padding: 16px;
  border-radius: 16px;
  background: var(--accent);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
  box-shadow: 0 12px 26px rgba(200, 115, 74, 0.26);
}

.save-btn:disabled {
  opacity: 0.55;
  box-shadow: none;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 640px) {
  .coord-grid {
    grid-template-columns: 1fr;
  }
}
</style>
