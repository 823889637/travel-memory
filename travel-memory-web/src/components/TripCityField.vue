<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { MapPinned, Search, X } from '@lucide/vue'
import { searchCities } from '../api/memory'
import { isValidWgs84Coordinate } from '../utils/coordinates'

const props = defineProps({
  id: { type: String, required: true },
  modelValue: { type: String, default: '' },
  country: { type: String, default: '' },
  latitude: { type: [Number, String], default: null },
  longitude: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'update:country', 'update:latitude', 'update:longitude'])

const root = ref(null)
const keyword = ref(props.modelValue || '')
const results = ref([])
const searching = ref(false)
const searchAttempted = ref(false)
const searchError = ref('')
const selectedName = ref(hasCoordinates() ? String(props.modelValue || '').trim() : '')
let searchTimer = null
let searchSequence = 0

const located = computed(() => isValidWgs84Coordinate(props.latitude, props.longitude))

function hasCoordinates() {
  return isValidWgs84Coordinate(props.latitude, props.longitude)
}

function clearCoordinates() {
  selectedName.value = ''
  emit('update:country', '')
  emit('update:latitude', null)
  emit('update:longitude', null)
}

function handleInput(event) {
  const value = event.target.value
  keyword.value = value
  emit('update:modelValue', value)
  searchError.value = ''
  searchAttempted.value = false
  if (value.trim() !== selectedName.value) clearCoordinates()
  clearTimeout(searchTimer)
  if (value.trim()) searchTimer = window.setTimeout(runSearch, 360)
  else results.value = []
}

async function runSearch() {
  const value = keyword.value.trim()
  if (!value) {
    results.value = []
    return
  }
  const sequence = ++searchSequence
  searching.value = true
  searchAttempted.value = true
  searchError.value = ''
  try {
    const response = await searchCities(value)
    if (sequence === searchSequence) results.value = Array.isArray(response) ? response : []
  } catch (error) {
    if (sequence === searchSequence) {
      results.value = []
      searchError.value = error.message || '城市搜索暂时不可用，你仍可以直接填写。'
    }
  } finally {
    if (sequence === searchSequence) searching.value = false
  }
}

function selectCity(city) {
  const name = String(city?.name || '').trim()
  if (!name) return
  keyword.value = name
  selectedName.value = name
  emit('update:modelValue', name)
  emit('update:country', city.countryName || '')
  emit('update:latitude', Number(city.latitude))
  emit('update:longitude', Number(city.longitude))
  results.value = []
  searchAttempted.value = false
  searchError.value = ''
}

function levelLabel(level) {
  return ({ province: '省级行政区', city: '城市', district: '区县' })[level] || '城市或地区'
}

function cityContext(city) {
  const names = [city?.countryName, city?.provinceName]
    .map((value) => String(value || '').trim())
    .filter(Boolean)
  return [...new Set(names)].join(' · ') || levelLabel(city?.level)
}

const locatedLabel = computed(() => {
  const names = [props.country, props.modelValue]
    .map((value) => String(value || '').trim())
    .filter(Boolean)
  return [...new Set(names)].join(' · ') || '目的城市'
})

function closeResults(event) {
  if (!root.value?.contains(event.target)) {
    results.value = []
    searchAttempted.value = false
  }
}

watch(() => props.modelValue, (value) => {
  if (String(value || '') !== keyword.value) keyword.value = String(value || '')
})
watch([() => props.latitude, () => props.longitude], () => {
  if (located.value) selectedName.value = String(props.modelValue || '').trim()
})

onMounted(() => document.addEventListener('pointerdown', closeResults))
onBeforeUnmount(() => {
  clearTimeout(searchTimer)
  document.removeEventListener('pointerdown', closeResults)
})
</script>

<template>
  <div ref="root" class="field trip-city-field">
    <label :for="id">目的城市 <span class="trip-field-optional">（可选）</span></label>
    <div class="trip-city-control">
      <MapPinned :size="18" :stroke-width="1.7" aria-hidden="true" />
      <input
        :id="id"
        :value="keyword"
        maxlength="100"
        autocomplete="off"
        placeholder="搜索城市，例如：天津、京都、巴黎"
        @input="handleInput"
        @keydown.enter.prevent="runSearch"
      />
      <button type="button" class="trip-city-icon-button" :disabled="searching" aria-label="搜索目的城市" title="搜索城市" @click="runSearch">
        <Search :size="18" :stroke-width="1.8" aria-hidden="true" />
      </button>
    </div>

    <div v-if="results.length || (searchAttempted && !searching && !searchError)" class="trip-city-results" role="listbox" aria-label="城市候选">
      <button v-for="city in results" :key="city.id" type="button" role="option" @click="selectCity(city)">
        <span><strong>{{ city.name }}</strong><small>{{ cityContext(city) }}</small></span>
        <MapPinned :size="16" :stroke-width="1.7" aria-hidden="true" />
      </button>
      <p v-if="!results.length">没有找到合适的城市，可以继续直接填写名称。</p>
    </div>

    <p v-if="located" class="trip-city-located">
      已定位到 {{ locatedLabel }}，没有带位置的 Memory 时地图会从这里开始。
      <button type="button" aria-label="清除城市定位" title="清除城市定位" @click="clearCoordinates">
        <X :size="14" aria-hidden="true" />
      </button>
    </p>
    <p v-else-if="searching" class="trip-city-help">正在查找城市候选…</p>
    <p v-else-if="searchError" class="trip-city-error">{{ searchError }}</p>

  </div>
</template>

<style scoped>
.trip-city-field { position: relative; }
.trip-city-control { display: grid; grid-template-columns: auto minmax(0, 1fr) 42px; align-items: center; overflow: hidden; min-height: 48px; border: 1px solid var(--tm-border); border-radius: 10px; background: #fff; }
.trip-city-control > svg { margin-left: 13px; color: #8d7666; }
.trip-city-control input { min-width: 0; min-height: 46px; padding: 0 10px; border: 0; background: transparent; box-shadow: none; }
.trip-city-control input:focus { outline: none; }
.trip-city-icon-button { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 5px; margin: 5px; padding: 0 10px; border: 0; border-left: 1px solid #eadfd3; border-radius: 0; background: transparent; color: var(--tm-accent); }
.trip-city-icon-button { width: 38px; margin-inline: 0; padding: 0; }
.trip-city-results { position: absolute; z-index: 12; top: calc(100% - 28px); right: 0; left: 0; overflow: hidden; border: 1px solid var(--tm-border); border-radius: 10px; background: #fffdfa; box-shadow: 0 16px 36px rgba(63, 47, 37, .14); }
.trip-city-results button { display: flex; width: 100%; min-height: 52px; align-items: center; justify-content: space-between; padding: 9px 13px; border: 0; border-bottom: 1px solid #eee4da; background: transparent; color: var(--tm-text); text-align: left; }
.trip-city-results button:hover { background: #f8f0e7; }
.trip-city-results span { display: grid; gap: 3px; }
.trip-city-results small, .trip-city-results p { color: var(--tm-text-muted); font-size: 12px; }
.trip-city-results p { margin: 0; padding: 14px; }
.trip-city-located, .trip-city-help, .trip-city-error { margin: 0; font-size: 12px; line-height: 1.5; }
.trip-city-located { display: flex; align-items: center; gap: 6px; color: #4f7d61; }
.trip-city-located button { display: grid; width: 24px; height: 24px; place-items: center; padding: 0; border: 0; border-radius: 50%; background: transparent; color: inherit; }
.trip-city-help { color: var(--tm-text-muted); }
.trip-city-error { color: var(--tm-danger, #a94f35); }
@media (max-width: 640px) {
  .trip-city-control { grid-template-columns: auto minmax(0, 1fr) 40px; }
  .trip-city-results { top: calc(100% + 6px); }
}
</style>
