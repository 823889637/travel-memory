<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createMemory } from '../api/memory'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const router = useRouter()
const saving = ref(false)
const locating = ref(false)
const error = ref('')
const photo = ref(null)
const photoPreview = ref('')
const photoInput = ref(null)
const showMoreLocation = ref(false)

const MAX_PHOTO_SIZE = 50 * 1024 * 1024
const ALLOWED_PHOTO_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp']

const form = reactive({
  content: '',
  latitude: '',
  longitude: '',
  locationName: '',
  recordTime: formatLocalDateTime(new Date()),
})

const latitudeError = computed(() => validateCoordinate(form.latitude, -90, 90, '纬度'))
const longitudeError = computed(() => validateCoordinate(form.longitude, -180, 180, '经度'))
const coordinateError = computed(() => latitudeError.value || longitudeError.value)
const hasCoordinates = computed(() => Boolean(form.latitude && form.longitude))
const canSubmit = computed(() => !saving.value && !coordinateError.value)

function formatLocalDateTime(date) {
  const offset = date.getTimezoneOffset()
  const localDate = new Date(date.getTime() - offset * 60 * 1000)
  return localDate.toISOString().slice(0, 16)
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

function onPhotoChange(event) {
  error.value = ''
  const selectedFile = event.target.files?.[0] || null
  if (!selectedFile) {
    return
  }

  const extension = getFileExtension(selectedFile.name)
  if (!ALLOWED_PHOTO_EXTENSIONS.includes(extension)) {
    event.target.value = ''
    error.value = '仅支持 jpg、jpeg、png、gif、webp 图片'
    return
  }

  if (selectedFile.size > MAX_PHOTO_SIZE) {
    event.target.value = ''
    error.value = '图片不能超过 50MB，请压缩后再上传'
    return
  }

  setPhoto(selectedFile)
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
  if (photoInput.value) {
    photoInput.value.value = ''
  }
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

async function submit() {
  error.value = ''
  if (coordinateError.value) {
    error.value = coordinateError.value
    return
  }

  saving.value = true

  const data = new FormData()
  data.append('tripId', props.id)
  if (form.content) data.append('content', form.content)
  if (form.latitude) data.append('latitude', form.latitude)
  if (form.longitude) data.append('longitude', form.longitude)
  if (form.locationName) data.append('locationName', form.locationName)
  if (form.recordTime) data.append('recordTime', form.recordTime)
  if (photo.value) data.append('photo', photo.value)

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
      <!-- 第一层：照片 -->
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
          <span class="photo-drop-hint">点击选择一张旅行照片，记录此刻的画面</span>
        </button>

        <div v-else class="photo-preview">
          <img :src="photoPreview" alt="照片预览" />
          <button type="button" class="photo-change" @click="triggerPhotoPicker">更换</button>
          <button type="button" class="photo-remove" aria-label="移除照片" @click="clearPhoto">×</button>
        </div>
      </div>

      <!-- 第一层：一句话 -->
      <div class="field">
        <label for="moment-content">这一刻想记住什么？</label>
        <textarea
          id="moment-content"
          v-model="form.content"
          rows="3"
          placeholder="例如：这个船好漂亮啊"
        ></textarea>
      </div>

      <!-- 第二层：时间 -->
      <div class="field">
        <label for="moment-time">记录时间</label>
        <input id="moment-time" v-model="form.recordTime" type="datetime-local" />
        <p class="hint">这个时间会影响 Journey 的日期和顺序。</p>
      </div>

      <!-- 第二层：地点名称 -->
      <div class="field">
        <label for="moment-location">地点名称</label>
        <input
          id="moment-location"
          v-model="form.locationName"
          placeholder="例如：海河边上、酒店楼下、那家很好吃的店"
        />
        <p class="hint">可以写你自己记得住的地点名，不一定是官方地址。</p>
      </div>

      <!-- 第三层：更多位置信息（折叠） -->
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
          <button type="button" class="locate-btn" :disabled="locating" @click="getLocation">
            {{ locating ? '定位中…' : '获取当前位置' }}
          </button>

          <div class="coord-grid">
            <div class="field">
              <label for="moment-lat">纬度 latitude</label>
              <input
                id="moment-lat"
                v-model.trim="form.latitude"
                inputmode="decimal"
                placeholder="例如：39.1234000"
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
              />
              <p v-if="longitudeError" class="error">{{ longitudeError }}</p>
            </div>
          </div>
        </div>
      </div>

      <p v-if="error" class="error error-block">{{ error }}</p>

      <div class="submit-bar">
        <button type="submit" class="save-btn" :disabled="!canSubmit">
          {{ saving ? '保存中…' : '保存这段记忆' }}
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

/* 照片 */
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
  max-width: 260px;
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
  object-fit: cover;
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

/* 字段 */
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

/* 更多位置信息 */
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

/* 反馈 & 提交 */
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
