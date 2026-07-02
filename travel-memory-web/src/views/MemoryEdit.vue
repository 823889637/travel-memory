<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMemory, updateMemory } from '../api/memory'

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
const canSubmit = computed(() => !loading.value && !saving.value && !coordinateError.value)

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

function toDateTimeLocalValue(value) {
  if (!value) {
    return ''
  }
  return String(value).slice(0, 16)
}

async function loadMemory() {
  loading.value = true
  error.value = ''
  try {
    const memory = await getMemory(props.memoryId)
    form.content = memory.content || ''
    form.locationName = memory.locationName || ''
    form.recordTime = toDateTimeLocalValue(memory.recordTime)
    form.latitude = memory.latitude == null ? '' : String(memory.latitude)
    form.longitude = memory.longitude == null ? '' : String(memory.longitude)
  } catch (err) {
    error.value = err.message || '加载失败'
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
    })
    router.push(`/trips/${props.tripId}`)
  } catch (err) {
    error.value = err.message || '保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(loadMemory)
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>编辑记忆</h1>
        <p class="muted">修改一句话、地点、时间和经纬度。</p>
      </div>
    </div>

    <p v-if="loading">加载中...</p>

    <form v-else class="form card" @submit.prevent="submit">
      <div class="field">
        <label>一句话</label>
        <textarea v-model="form.content" placeholder="记录当时发生了什么"></textarea>
      </div>

      <div class="field">
        <label>记录时间</label>
        <input v-model="form.recordTime" type="datetime-local" required />
      </div>

      <div class="field">
        <label>地点名称</label>
        <input v-model="form.locationName" placeholder="例如：鸭川三条大桥" />
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
        <button :disabled="!canSubmit">{{ saving ? '保存中...' : '保存修改' }}</button>
        <button type="button" class="secondary" @click="router.push(`/trips/${tripId}`)">取消</button>
      </div>
    </form>
  </section>
</template>
