<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { getMemory, reverseGeocode, updateMemory, uploadPhoto } from '../api/memory'
import LocationPicker from '../components/LocationPicker.vue'
import CompanionSelector from '../components/CompanionSelector.vue'
import { toDateTimeLocalValue } from '../utils/dateTime'

const props = defineProps({
  tripId: { type: String, required: true },
  memoryId: { type: String, required: true },
})

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const loadError = ref('')
const showMoreLocation = ref(false)
const photoDrafts = ref([])
const photoInput = ref(null)
const locationNameTouched = ref(false)
const locationSuggestion = ref(null)
const locationSuggestionStatus = ref('idle')
const showLocationPicker = ref(false)
const selectedCompanionIds = ref([])
const originalSnapshot = ref(null)
const skipLeavePrompt = ref(false)

const MAX_PHOTOS = 6
const MAX_PHOTO_SIZE = 50 * 1024 * 1024
const ALLOWED_PHOTO_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'heic', 'heif']

const form = reactive({
  content: '',
  locationName: '',
  recordTime: '',
  latitude: '',
  longitude: '',
})

const latitudeError = computed(() => validateCoordinate(form.latitude, -90, 90, '纬度'))
const longitudeError = computed(() => validateCoordinate(form.longitude, -180, 180, '经度'))
const coordinateError = computed(() => latitudeError.value || longitudeError.value)
const hasCoordinates = computed(() => hasCoordinateValues())
const canSubmit = computed(() => !loading.value && !saving.value && !coordinateError.value)
const hasLocationSuggestion = computed(() => (
  locationSuggestionStatus.value === 'success' && locationSuggestion.value?.locationName
))
const isDirty = computed(() => originalSnapshot.value && JSON.stringify(currentSnapshot()) !== JSON.stringify(originalSnapshot.value))

function validateCoordinate(value, min, max, label) {
  if (value === '' || value == null) return ''
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) return `${label}必须是数字`
  if (numberValue < min || numberValue > max) return `${label}范围应为 ${min} 到 ${max}`
  return ''
}

function normalizeText(value) {
  return String(value ?? '').trim()
}

function normalizeCoordinate(value) {
  return value === '' || value == null ? '' : String(Number(value))
}

function currentSnapshot() {
  return {
    content: normalizeText(form.content),
    locationName: normalizeText(form.locationName),
    recordTime: form.recordTime || '',
    latitude: normalizeCoordinate(form.latitude),
    longitude: normalizeCoordinate(form.longitude),
    companionIds: [...selectedCompanionIds.value].map(Number).sort((a, b) => a - b),
    photos: photoDrafts.value.map(photo => photo.id ? `id:${photo.id}` : `url:${photo.photoUrl || photo.previewUrl}`),
  }
}

function hasCoordinateValues() {
  return form.latitude !== '' && form.latitude != null && form.longitude !== '' && form.longitude != null
}

function photoDisplayUrl(photo) {
  return photo.previewUrl || photo.photoUrl
}

function releasePreview(photo) {
  if (photo?.isNew && photo.previewUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(photo.previewUrl)
  }
}

function releaseAllPreviews() {
  photoDrafts.value.forEach(releasePreview)
}

function triggerPhotoPicker() {
  if (photoDrafts.value.length < MAX_PHOTOS) photoInput.value?.click()
}

function getFileExtension(filename) {
  const dotIndex = filename.lastIndexOf('.')
  return dotIndex < 0 ? '' : filename.slice(dotIndex + 1).toLowerCase()
}

function validatePhotoFile(file) {
  const extension = getFileExtension(file.name)
  if (!ALLOWED_PHOTO_EXTENSIONS.includes(extension)) return '仅支持 jpg、jpeg、png、gif、webp、heic、heif 图片'
  if (file.size > MAX_PHOTO_SIZE) return '图片不能超过 50MB，请压缩后再上传'
  return ''
}

function onPhotoChange(event) {
  error.value = ''
  const files = Array.from(event.target.files || [])
  const remaining = MAX_PHOTOS - photoDrafts.value.length
  if (!files.length) return
  if (remaining <= 0) {
    error.value = `一段记忆最多保留 ${MAX_PHOTOS} 张照片`
    event.target.value = ''
    return
  }

  const accepted = []
  for (const file of files.slice(0, remaining)) {
    const validationError = validatePhotoFile(file)
    if (validationError) {
      error.value = validationError
      continue
    }
    accepted.push({
      key: `new-${crypto.randomUUID()}`,
      id: null,
      photoUrl: '',
      previewUrl: URL.createObjectURL(file),
      file,
      isNew: true,
    })
  }
  photoDrafts.value.push(...accepted)
  if (files.length > remaining && !error.value) error.value = `只添加了前 ${remaining} 张照片，一段记忆最多保留 ${MAX_PHOTOS} 张。`
  event.target.value = ''
}

function removePhoto(index) {
  if (saving.value) return
  const [photo] = photoDrafts.value.splice(index, 1)
  releasePreview(photo)
}

function movePhoto(index, direction) {
  const nextIndex = index + direction
  if (saving.value || nextIndex < 0 || nextIndex >= photoDrafts.value.length) return
  const drafts = [...photoDrafts.value]
  const [photo] = drafts.splice(index, 1)
  drafts.splice(nextIndex, 0, photo)
  photoDrafts.value = drafts
}

function resetLocationSuggestion() {
  locationSuggestion.value = null
  locationSuggestionStatus.value = 'idle'
}

async function requestLocationSuggestion(options = {}) {
  const force = options.force === true
  if (!hasCoordinateValues() || coordinateError.value || (form.locationName?.trim() && !force)) return
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

function onCoordinateInput() {
  resetLocationSuggestion()
}

async function loadMemory() {
  loading.value = true
  error.value = ''
  loadError.value = ''
  try {
    const memory = await getMemory(props.memoryId)
    form.content = memory.content || ''
    form.locationName = memory.locationName || ''
    form.recordTime = toDateTimeLocalValue(memory.recordTime)
    form.latitude = memory.latitude == null ? '' : String(memory.latitude)
    form.longitude = memory.longitude == null ? '' : String(memory.longitude)
    const photos = memory.photos?.length ? memory.photos : (memory.photoUrl ? [{ photoUrl: memory.photoUrl }] : [])
    photoDrafts.value = photos.map((photo, index) => ({
      key: photo.id ? `stored-${photo.id}` : `legacy-${index}-${photo.photoUrl}`,
      id: photo.id || null,
      photoUrl: photo.photoUrl,
      previewUrl: photo.photoUrl,
      file: null,
      isNew: false,
    }))
    selectedCompanionIds.value = memory.companionIds || memory.companions?.map(item => item.id) || []
    originalSnapshot.value = currentSnapshot()
    if (hasCoordinateValues() && !form.locationName) requestLocationSuggestion()
  } catch (err) {
    loadError.value = err.message || '记忆暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

async function uploadNewPhotos() {
  for (const photo of photoDrafts.value) {
    if (!photo.file) continue
    const data = new FormData()
    data.append('photo', photo.file)
    const uploaded = await uploadPhoto(data)
    photo.photoUrl = uploaded.photoUrl
    photo.file = null
  }
}

function buildPhotoReferences() {
  return photoDrafts.value.map(photo => photo.id ? { id: photo.id } : { photoUrl: photo.photoUrl })
}

async function submit() {
  error.value = ''
  if (!form.recordTime) {
    error.value = '请选择记录时间'
    return
  }
  if (coordinateError.value) {
    error.value = coordinateError.value
    return
  }
  if ((form.latitude === '') !== (form.longitude === '')) {
    error.value = '请同时填写纬度和经度'
    return
  }

  saving.value = true
  try {
    await uploadNewPhotos()
    await updateMemory(props.memoryId, {
      content: normalizeText(form.content) || null,
      locationName: normalizeText(form.locationName) || null,
      recordTime: form.recordTime,
      latitude: form.latitude === '' ? null : Number(form.latitude),
      longitude: form.longitude === '' ? null : Number(form.longitude),
      companionIds: selectedCompanionIds.value,
      photos: buildPhotoReferences(),
    })
    originalSnapshot.value = currentSnapshot()
    skipLeavePrompt.value = true
    router.push(`/trips/${props.tripId}`)
  } catch (err) {
    error.value = err.message || '保存失败。文字和照片草稿仍保留在当前页面。'
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
  router.push(`/trips/${props.tripId}`)
}

function handleBeforeUnload(event) {
  if (!skipLeavePrompt.value && isDirty.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onBeforeRouteLeave(() => {
  if (!skipLeavePrompt.value && !confirmDiscard()) return false
  return true
})

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  loadMemory()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  releaseAllPreviews()
})
</script>

<template>
  <section class="memory-edit-page">
    <header class="memory-edit-head">
      <h1>修正这段记忆</h1>
      <p>可以调整照片、时间、地点和当时留下的话。</p>
    </header>

    <p v-if="loading" class="memory-edit-status">正在取回这段记忆...</p>
    <p v-if="!loading && loadError" class="error error-block">{{ loadError }}</p>

    <form v-if="!loading && !loadError" class="memory-edit-form" @submit.prevent="submit">
      <div class="edit-photo-block">
        <input ref="photoInput" hidden multiple type="file" accept="image/*" @change="onPhotoChange" />

        <div v-if="photoDrafts.length" class="edit-photo-preview">
          <img :src="photoDisplayUrl(photoDrafts[0])" alt="记忆主图预览" />
          <button type="button" class="edit-photo-change" :disabled="photoDrafts.length >= MAX_PHOTOS" @click="triggerPhotoPicker">
            添加照片
          </button>
        </div>
        <button v-else type="button" class="edit-photo-drop" @click="triggerPhotoPicker">
          <span class="edit-photo-title">为这段记忆补充照片</span>
          <span class="edit-photo-hint">一次最多选择 {{ MAX_PHOTOS }} 张，第一张将作为主图。</span>
        </button>

        <p class="hint">照片、排序和删除会在保存修改后一起生效。</p>
        <div v-if="photoDrafts.length" class="stored-photo-list">
          <div v-for="(photo, index) in photoDrafts" :key="photo.key" class="stored-photo-item">
            <img :src="photoDisplayUrl(photo)" :alt="`照片 ${index + 1}`" />
            <span v-if="index === 0">主图</span>
            <div class="stored-photo-actions">
              <button type="button" :disabled="index === 0 || saving" @click="movePhoto(index, -1)">前移</button>
              <button type="button" :disabled="index === photoDrafts.length - 1 || saving" @click="movePhoto(index, 1)">后移</button>
              <button type="button" :disabled="saving" @click="removePhoto(index)">删除</button>
            </div>
          </div>
        </div>
      </div>

      <div class="field memory-edit-sentence">
        <label for="edit-content">这一刻想记住什么？</label>
        <textarea id="edit-content" v-model="form.content" rows="3" maxlength="300" placeholder="例如：这个船好漂亮啊"></textarea>
        <p class="hint">保留当时真实写下的话，短短一句也很好。</p>
      </div>

      <div class="field">
        <label for="edit-time">记录时间</label>
        <input id="edit-time" v-model="form.recordTime" type="datetime-local" required />
        <p class="time-warning">这个时间会影响时间线和旅程回放的顺序。</p>
      </div>

      <div class="field">
        <label for="edit-location">地点名称</label>
        <input id="edit-location" v-model="form.locationName" maxlength="255" placeholder="例如：海河边上、酒店楼下、那家很好吃的店" @input="onLocationNameInput" />
        <p class="hint">可以写你自己记得住的地点名称，不一定是官方地址。</p>
        <div v-if="locationSuggestionStatus !== 'idle'" class="location-suggestion">
          <p v-if="locationSuggestionStatus === 'loading'">正在根据定位推荐地点...</p>
          <template v-else-if="hasLocationSuggestion">
            <p>根据照片定位，可能是：{{ locationSuggestion.locationName }}</p>
            <button type="button" @click="useLocationSuggestion">使用这个地点</button>
          </template>
          <p v-else>暂时没有识别出地点名称，你可以手动填写。</p>
        </div>
        <button type="button" class="location-picker-trigger" @click="showLocationPicker = true">搜索 / 地图选点</button>
      </div>

      <div class="more">
        <button type="button" class="more-toggle" :aria-expanded="showMoreLocation" @click="showMoreLocation = !showMoreLocation">
          <span>更多位置信息</span>
          <span class="more-status"><span v-if="hasCoordinates" class="more-dot" aria-hidden="true"></span>{{ showMoreLocation ? '收起' : (hasCoordinates ? '已填写坐标' : '展开') }}</span>
        </button>
        <div v-if="showMoreLocation" class="more-panel">
          <div class="coord-grid">
            <div class="field">
              <label for="edit-lat">纬度 latitude</label>
              <input id="edit-lat" v-model.trim="form.latitude" inputmode="decimal" placeholder="例如：39.1234000" @input="onCoordinateInput" />
              <p v-if="latitudeError" class="error">{{ latitudeError }}</p>
            </div>
            <div class="field">
              <label for="edit-lng">经度 longitude</label>
              <input id="edit-lng" v-model.trim="form.longitude" inputmode="decimal" placeholder="例如：117.1234000" @input="onCoordinateInput" />
              <p v-if="longitudeError" class="error">{{ longitudeError }}</p>
            </div>
          </div>
          <button v-if="hasCoordinates" type="button" class="location-suggestion-refresh" :disabled="locationSuggestionStatus === 'loading'" @click="requestLocationSuggestion({ force: true })">
            {{ locationSuggestionStatus === 'loading' ? '推荐中...' : '重新推荐地点' }}
          </button>
        </div>
      </div>

      <CompanionSelector v-model="selectedCompanionIds" :trip-id="tripId" />
      <p v-if="error" class="error error-block">{{ error }}</p>

      <div class="memory-edit-actions">
        <button type="submit" class="save-btn" :disabled="!canSubmit">{{ saving ? '保存中...' : '保存修改' }}</button>
        <button type="button" class="memory-edit-cancel" :disabled="saving" @click="cancel">取消</button>
      </div>
    </form>

    <LocationPicker v-if="showLocationPicker" :location-name="form.locationName" :latitude="form.latitude" :longitude="form.longitude" @confirm="applyPickedLocation" @cancel="showLocationPicker = false" />
  </section>
</template>

<style scoped>
.stored-photo-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-top: 10px; }
.stored-photo-item { display: grid; gap: 4px; color: #8a7f76; font-size: 12px; }
.stored-photo-item img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 8px; }
.stored-photo-actions { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; }
.stored-photo-item button { padding: 4px; border: 1px solid #ece3d8; border-radius: 5px; background: #fff; color: #2c2521; font-size: 11px; }
.location-picker-trigger { margin-top: 8px; padding: 7px 10px; border: 1px solid #ece3d8; border-radius: 6px; background: transparent; color: #76543e; font: inherit; font-size: 13px; }
</style>
