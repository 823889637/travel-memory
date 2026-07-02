<script setup>
import { computed, reactive, ref } from 'vue'
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

function onPhotoChange(event) {
  photo.value = event.target.files?.[0] || null
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
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>新增记忆</h1>
        <p class="muted">上传一张照片，写一句话，保存当时的位置。</p>
      </div>
    </div>

    <form class="form card" @submit.prevent="submit">
      <div class="field">
        <label>一句话</label>
        <textarea v-model="form.content" placeholder="例如：傍晚在鸭川边坐了很久"></textarea>
      </div>

      <div class="field">
        <label>照片</label>
        <input type="file" accept="image/*" @change="onPhotoChange" />
      </div>

      <div class="field">
        <label>记录时间</label>
        <input v-model="form.recordTime" type="datetime-local" />
      </div>

      <div class="field">
        <label>地点名称</label>
        <input v-model="form.locationName" placeholder="例如：鸭川三条大桥" />
      </div>

      <div class="actions">
        <button type="button" class="secondary" :disabled="locating" @click="getLocation">
          {{ locating ? '定位中...' : '获取当前位置' }}
        </button>
      </div>

      <div class="field">
        <label>纬度</label>
        <input v-model.trim="form.latitude" inputmode="decimal" placeholder="例如：35.0116000" />
        <p v-if="latitudeError" class="error">{{ latitudeError }}</p>
      </div>

      <div class="field">
        <label>经度</label>
        <input v-model.trim="form.longitude" inputmode="decimal" placeholder="例如：135.7681000" />
        <p v-if="longitudeError" class="error">{{ longitudeError }}</p>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="actions">
        <button :disabled="!canSubmit">{{ saving ? '保存中...' : '保存记忆' }}</button>
      </div>
    </form>
  </section>
</template>
