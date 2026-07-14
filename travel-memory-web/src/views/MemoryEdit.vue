<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { addMemoryPhoto, deleteMemoryPhoto, getMemory, reorderMemoryPhotos, reverseGeocode, updateMemory } from '../api/memory'
import LocationPicker from '../components/LocationPicker.vue'
import CompanionSelector from '../components/CompanionSelector.vue'
import { toDateTimeLocalValue } from '../utils/dateTime'

const props = defineProps({
  tripId: {
    type: String,
    required: true,
  },
  memoryId: {
    type: String,
    required: true,
  },
})

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const loadError = ref('')
const showMoreLocation = ref(false)
const currentPhotoUrl = ref('')
const memoryPhotos = ref([])
const newPhoto = ref(null)
const newPhotoPreview = ref('')
const photoInput = ref(null)
const locationNameTouched = ref(false)
const locationSuggestion = ref(null)
const locationSuggestionStatus = ref('idle')
const showLocationPicker = ref(false)
const selectedCompanionIds = ref([])

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
const hasCoordinates = computed(() => Boolean(form.latitude && form.longitude))
const canSubmit = computed(() => !loading.value && !saving.value && !coordinateError.value)
const previewPhotoUrl = computed(() => newPhotoPreview.value || currentPhotoUrl.value)
const hasLocationSuggestion = computed(() => (
  locationSuggestionStatus.value === 'success' && locationSuggestion.value?.locationName
))

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

function getFileExtension(filename) {
  const dotIndex = filename.lastIndexOf('.')
  if (dotIndex < 0) {
    return ''
  }
  return filename.slice(dotIndex + 1).toLowerCase()
}

function clearNewPhoto() {
  if (newPhotoPreview.value) {
    URL.revokeObjectURL(newPhotoPreview.value)
  }
  newPhoto.value = null
  newPhotoPreview.value = ''
  if (photoInput.value) {
    photoInput.value.value = ''
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
  if (form.locationName?.trim() && !force) {
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

function onLocationNameInput() {
  locationNameTouched.value = true
  if (form.locationName.trim()) {
    resetLocationSuggestion()
  }
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

function onPhotoChange(event) {
  error.value = ''
  const selectedFile = event.target.files?.[0] || null
  if (!selectedFile) {
    clearNewPhoto()
    return
  }

  const extension = getFileExtension(selectedFile.name)
  if (!ALLOWED_PHOTO_EXTENSIONS.includes(extension)) {
    event.target.value = ''
    clearNewPhoto()
    error.value = '仅支持 jpg、jpeg、png、gif、webp、heic、heif 图片'
    return
  }

  if (selectedFile.size > MAX_PHOTO_SIZE) {
    event.target.value = ''
    clearNewPhoto()
    error.value = '图片不能超过 50MB，请压缩后再上传'
    return
  }

  clearNewPhoto()
  newPhoto.value = selectedFile
  newPhotoPreview.value = URL.createObjectURL(selectedFile)
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
    currentPhotoUrl.value = memory.photoUrl || ''
    memoryPhotos.value = memory.photos?.length ? memory.photos : (memory.photoUrl ? [{ photoUrl: memory.photoUrl }] : [])
    selectedCompanionIds.value = memory.companionIds || memory.companions?.map(item => item.id) || []
    if (hasCoordinateValues() && !form.locationName) {
      requestLocationSuggestion()
    }
  } catch (err) {
    loadError.value = err.message || '记忆暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

async function submit() {
  error.value = ''
  if (coordinateError.value) {
    error.value = coordinateError.value
    return
  }

  saving.value = true
  try {
    await updateMemory(props.memoryId, {
      content: form.content || null,
      locationName: form.locationName || null,
      recordTime: form.recordTime || null,
      latitude: form.latitude ? Number(form.latitude) : null,
      longitude: form.longitude ? Number(form.longitude) : null,
      companionIds: selectedCompanionIds.value,
    })

    if (newPhoto.value) {
      const data = new FormData()
      data.append('photo', newPhoto.value)
      const updated = await addMemoryPhoto(props.memoryId, data)
      memoryPhotos.value = updated.photos || []
      currentPhotoUrl.value = updated.photoUrl || ''
    }

    router.push(`/trips/${props.tripId}`)
  } catch (err) {
    error.value = err.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function removeStoredPhoto(photo) {
  if (!photo.id || saving.value) return
  error.value = ''
  try {
    const updated = await deleteMemoryPhoto(props.memoryId, photo.id)
    memoryPhotos.value = updated.photos || []
    currentPhotoUrl.value = updated.photoUrl || ''
  } catch (err) { error.value = err.message || '删除照片失败' }
}

async function moveStoredPhoto(index, direction) {
  const next = index + direction
  if (next < 0 || next >= memoryPhotos.value.length || saving.value) return
  const photos = [...memoryPhotos.value]; const [photo] = photos.splice(index, 1); photos.splice(next, 0, photo)
  if (photos.some(photo => !photo.id)) return
  try {
    const updated = await reorderMemoryPhotos(props.memoryId, photos.map(photo => photo.id))
    memoryPhotos.value = updated.photos || []
    currentPhotoUrl.value = updated.photoUrl || ''
  } catch (err) { error.value = err.message || '调整照片顺序失败' }
}

onMounted(loadMemory)

onBeforeUnmount(() => {
  if (newPhotoPreview.value) {
    URL.revokeObjectURL(newPhotoPreview.value)
  }
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
        <input
          ref="photoInput"
          hidden
          type="file"
          accept="image/*"
          @change="onPhotoChange"
        />

        <div v-if="previewPhotoUrl" class="edit-photo-preview">
          <img :src="previewPhotoUrl" alt="记忆照片预览" />
          <button type="button" class="edit-photo-change" @click="triggerPhotoPicker">
            更换照片
          </button>
          <button
            v-if="newPhotoPreview"
            type="button"
            class="edit-photo-cancel"
            @click="clearNewPhoto"
          >
            取消更换
          </button>
        </div>

        <button v-else type="button" class="edit-photo-drop" @click="triggerPhotoPicker">
          <span class="edit-photo-title">为这段记忆补一张照片</span>
          <span class="edit-photo-hint">照片会在点击保存修改后更新。</span>
        </button>

        <p v-if="newPhoto" class="hint">已选择新照片，保存修改后会替换当前照片。</p>
        <div v-if="memoryPhotos.length" class="stored-photo-list">
          <div v-for="(photo, index) in memoryPhotos" :key="photo.id || photo.photoUrl" class="stored-photo-item">
            <img :src="photo.photoUrl" :alt="`照片 ${index + 1}`" />
            <span v-if="index === 0">主图</span>
            <button type="button" :disabled="index === 0" @click="moveStoredPhoto(index, -1)">前移</button>
            <button type="button" :disabled="index === memoryPhotos.length - 1" @click="moveStoredPhoto(index, 1)">后移</button>
            <button type="button" @click="removeStoredPhoto(photo)">删除</button>
          </div>
        </div>
      </div>

      <div class="field memory-edit-sentence">
        <label for="edit-content">这一刻想记住什么？</label>
        <textarea
          id="edit-content"
          v-model="form.content"
          rows="3"
          placeholder="例如：这个船好漂亮啊"
        ></textarea>
        <p class="hint">保留当时真实写下的话，短短一句也很好。</p>
      </div>

      <div class="field">
        <label for="edit-time">记录时间</label>
        <input id="edit-time" v-model="form.recordTime" type="datetime-local" />
        <p class="time-warning">这个时间会影响时间线和旅程回放的顺序。</p>
      </div>

      <div class="field">
        <label for="edit-location">地点名称</label>
        <input
          id="edit-location"
          v-model="form.locationName"
          placeholder="例如：海河边上、酒店楼下、那家很好吃的店"
          @input="onLocationNameInput"
        />
        <p class="hint">可以写你自己记得住的地点名，不一定是官方地址。</p>
        <div v-if="locationSuggestionStatus !== 'idle'" class="location-suggestion">
          <p v-if="locationSuggestionStatus === 'loading'">正在根据定位推荐地点...</p>
          <template v-else-if="hasLocationSuggestion">
            <p>根据照片定位，可能是：{{ locationSuggestion.locationName }}</p>
            <button type="button" @click="useLocationSuggestion">使用这个地点</button>
          </template>
          <p v-else>暂时没有识别出地点名称，你可以手动填写。</p>
        </div>
        <button type="button" class="location-picker-trigger" @click="showLocationPicker = true">
          搜索 / 地图选点
        </button>
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
            {{ showMoreLocation ? '收起' : (hasCoordinates ? '已填写坐标' : '展开') }}
          </span>
        </button>

        <div v-if="showMoreLocation" class="more-panel">
          <div class="coord-grid">
            <div class="field">
              <label for="edit-lat">纬度 latitude</label>
              <input
                id="edit-lat"
                v-model.trim="form.latitude"
                inputmode="decimal"
                placeholder="例如：39.1234000"
                @input="onCoordinateInput"
              />
              <p v-if="latitudeError" class="error">{{ latitudeError }}</p>
            </div>
            <div class="field">
              <label for="edit-lng">经度 longitude</label>
              <input
                id="edit-lng"
                v-model.trim="form.longitude"
                inputmode="decimal"
                placeholder="例如：117.1234000"
                @input="onCoordinateInput"
              />
              <p v-if="longitudeError" class="error">{{ longitudeError }}</p>
            </div>
          </div>
          <button
            v-if="hasCoordinates"
            type="button"
            class="location-suggestion-refresh"
            :disabled="locationSuggestionStatus === 'loading'"
            @click="requestLocationSuggestion({ force: true })"
          >
            {{ locationSuggestionStatus === 'loading' ? '推荐中...' : '重新推荐地点' }}
          </button>
        </div>
      </div>

      <CompanionSelector v-model="selectedCompanionIds" :trip-id="tripId" />

      <p v-if="error" class="error error-block">{{ error }}</p>

      <div class="memory-edit-actions">
        <button type="submit" class="save-btn" :disabled="!canSubmit">
          {{ saving ? '保存中...' : '保存修改' }}
        </button>
        <button type="button" class="memory-edit-cancel" @click="router.push(`/trips/${tripId}`)">
          返回
        </button>
      </div>
    </form>

    <LocationPicker
      v-if="showLocationPicker"
      :location-name="form.locationName"
      :latitude="form.latitude"
      :longitude="form.longitude"
      @confirm="applyPickedLocation"
      @cancel="showLocationPicker = false"
    />
  </section>
</template>

<style scoped>
.stored-photo-list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 10px; }
.stored-photo-item { display: grid; gap: 4px; color: #8a7f76; font-size: 12px; }
.stored-photo-item img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 8px; }
.stored-photo-item button { padding: 4px; border: 1px solid #ece3d8; border-radius: 5px; background: #fff; color: #2c2521; font-size: 11px; }
.location-picker-trigger { margin-top: 8px; padding: 7px 10px; border: 1px solid #ece3d8; border-radius: 6px; background: transparent; color: #76543e; font: inherit; font-size: 13px; }
</style>
