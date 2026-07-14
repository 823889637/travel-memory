<script setup>
import { computed, reactive, ref } from 'vue'
import { normalizeCoordinate, reverseGeocode, searchLocations } from '../api/memory'
import LocationPickerMap from './LocationPickerMap.vue'

const props = defineProps({
  locationName: { type: String, default: '' },
  latitude: { type: [Number, String], default: '' },
  longitude: { type: [Number, String], default: '' },
})
const emit = defineEmits(['confirm', 'cancel'])

const draft = reactive({
  keyword: '',
  results: [],
  searching: false,
  latitude: props.latitude == null ? '' : String(props.latitude),
  longitude: props.longitude == null ? '' : String(props.longitude),
  locationName: props.locationName || '',
  formattedAddress: '',
  candidates: [],
  reverseGeocoding: false,
  locating: false,
  error: '',
})
const locationNameTouched = ref(Boolean(props.locationName?.trim()))
const hasCoordinates = computed(() => Number.isFinite(Number(draft.latitude)) && Number.isFinite(Number(draft.longitude)))
const selectedLabel = computed(() => draft.locationName.trim() || draft.formattedAddress || '尚未选择地点')

function coordinateOptions() {
  return hasCoordinates.value ? { latitude: draft.latitude, longitude: draft.longitude } : {}
}

async function search() {
  const keyword = draft.keyword.trim()
  if (!keyword) {
    draft.error = '请输入想找的地点名称。'
    return
  }
  draft.searching = true
  draft.error = ''
  try {
    draft.results = await searchLocations(keyword, coordinateOptions())
  } catch (error) {
    draft.results = []
    draft.error = error.message || '地点搜索暂时不可用，请直接在地图上选择。'
  } finally {
    draft.searching = false
  }
}

async function selectSearchResult(result) {
  draft.latitude = String(result.latitude)
  draft.longitude = String(result.longitude)
  draft.locationName = result.name || ''
  draft.formattedAddress = [result.district, result.address].filter(Boolean).join('')
  draft.candidates = result.name ? [{ name: result.name, distance: result.distance }] : []
  locationNameTouched.value = false
  draft.error = ''
}

async function selectMapPoint(point) {
  draft.error = ''
  try {
    const normalized = await normalizeCoordinate(point)
    draft.latitude = String(normalized.latitude)
    draft.longitude = String(normalized.longitude)
    await refreshReverseGeocode()
  } catch (error) {
    draft.error = error.message || '暂时无法识别这里的名称，你可以自己写一个。'
  }
}

async function refreshReverseGeocode() {
  if (!hasCoordinates.value) return
  draft.reverseGeocoding = true
  draft.formattedAddress = ''
  draft.candidates = []
  try {
    const result = await reverseGeocode(draft.latitude, draft.longitude)
    if (!result?.success) return
    draft.formattedAddress = result.formattedAddress || ''
    draft.candidates = result.candidates || []
    if (!locationNameTouched.value && result.locationName) {
      draft.locationName = result.locationName
    }
  } catch (error) {
    // A map point remains usable even if reverse geocoding is temporarily unavailable.
  } finally {
    draft.reverseGeocoding = false
  }
}

function chooseCandidate(candidate) {
  if (!candidate?.name) return
  draft.locationName = candidate.name
  locationNameTouched.value = true
}

function onLocationNameInput() {
  locationNameTouched.value = true
}

function locateCurrentPosition() {
  if (!navigator.geolocation) {
    draft.error = '当前浏览器不支持定位，仍可以搜索或在地图上选择。'
    return
  }
  draft.locating = true
  draft.error = ''
  navigator.geolocation.getCurrentPosition(
    async (position) => {
      draft.latitude = position.coords.latitude.toFixed(7)
      draft.longitude = position.coords.longitude.toFixed(7)
      await refreshReverseGeocode()
      draft.locating = false
    },
    () => {
      draft.locating = false
      draft.error = '暂时无法获取当前位置，仍可以搜索或在地图上选择。'
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
  )
}

function confirm() {
  emit('confirm', {
    locationName: draft.locationName.trim(),
    latitude: hasCoordinates.value ? Number(draft.latitude) : null,
    longitude: hasCoordinates.value ? Number(draft.longitude) : null,
  })
}
</script>

<template>
  <div class="location-picker-backdrop" role="presentation" @click.self="emit('cancel')">
    <section class="location-picker" role="dialog" aria-modal="true" aria-labelledby="location-picker-title">
      <header class="location-picker-header">
        <h2 id="location-picker-title">搜索或选择地点</h2>
        <button type="button" class="location-picker-close" aria-label="关闭地点选点" @click="emit('cancel')">关闭</button>
      </header>

      <form class="location-picker-search" @submit.prevent="search">
        <input v-model="draft.keyword" type="search" placeholder="搜索景点、酒店、餐厅或地址" aria-label="搜索地点" />
        <button type="submit" :disabled="draft.searching">{{ draft.searching ? '搜索中...' : '搜索' }}</button>
        <button type="button" class="location-picker-locate" :disabled="draft.locating" @click="locateCurrentPosition">
          {{ draft.locating ? '定位中...' : '定位到当前位置' }}
        </button>
      </form>

      <p v-if="draft.error" class="location-picker-error">{{ draft.error }}</p>

      <div class="location-picker-body">
        <aside class="location-picker-results" aria-label="地点搜索结果">
          <p v-if="draft.searching" class="location-picker-empty">正在寻找地点...</p>
          <p v-else-if="draft.keyword && !draft.results.length" class="location-picker-empty">没有找到合适的地点，可以直接在地图上选择。</p>
          <p v-else-if="!draft.results.length" class="location-picker-empty">搜索地点后，候选结果会显示在这里；你也可以直接点击地图选点。</p>
          <button
            v-for="result in draft.results"
            :key="result.id || `${result.latitude}:${result.longitude}`"
            type="button"
            class="location-picker-result"
            @click="selectSearchResult(result)"
          >
            <strong>{{ result.name }}</strong>
            <span>{{ [result.district, result.address].filter(Boolean).join('') || '未提供详细地址' }}</span>
            <small v-if="result.distance != null">约 {{ result.distance }} 米</small>
          </button>
        </aside>

        <LocationPickerMap
          :latitude="draft.latitude"
          :longitude="draft.longitude"
          @pick="selectMapPoint"
        />
      </div>

      <footer class="location-picker-footer">
        <div class="location-picker-selection">
          <span>已选择</span>
          <strong>{{ selectedLabel }}</strong>
          <p v-if="draft.formattedAddress">{{ draft.formattedAddress }}</p>
          <p v-else-if="draft.reverseGeocoding">正在识别附近地点...</p>
          <div v-if="draft.candidates.length > 1" class="location-picker-candidates">
            <span>推荐地点</span>
            <button v-for="candidate in draft.candidates" :key="candidate.name" type="button" @click="chooseCandidate(candidate)">{{ candidate.name }}</button>
          </div>
          <label>
            地点名称
            <input v-model="draft.locationName" type="text" placeholder="可以保留你自己的地点描述" @input="onLocationNameInput" />
          </label>
        </div>
        <div class="location-picker-actions">
          <button type="button" class="location-picker-cancel" @click="emit('cancel')">取消</button>
          <button type="button" class="location-picker-confirm" @click="confirm">使用此地点</button>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.location-picker-backdrop { position: fixed; z-index: 40; inset: 0; display: grid; place-items: center; padding: 24px; background: rgb(49 40 34 / 36%); }
.location-picker { display: grid; grid-template-rows: auto auto minmax(0, 1fr) auto; width: min(1060px, 100%); max-height: min(820px, calc(100vh - 48px)); overflow: hidden; border: 1px solid #e4d8ca; border-radius: 10px; background: #fffdf9; box-shadow: 0 24px 65px rgb(55 42 32 / 22%); color: #342b25; }
.location-picker-header { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px 12px; }
.location-picker-header h2 { margin: 0; font-size: 19px; }
.location-picker-close, .location-picker-search button, .location-picker-actions button, .location-picker-candidates button { border: 1px solid #dfd1c1; border-radius: 6px; background: #fffdfa; color: #76543e; cursor: pointer; }
.location-picker-close { padding: 6px 10px; font-size: 13px; }
.location-picker-search { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: 8px; padding: 0 20px 14px; }
.location-picker-search input, .location-picker-selection input { min-width: 0; border: 1px solid #dfd4c8; border-radius: 6px; background: #fff; color: #342b25; font: inherit; }
.location-picker-search input { height: 40px; padding: 0 11px; }
.location-picker-search button { padding: 0 14px; }
.location-picker-search button:disabled { cursor: wait; opacity: .6; }
.location-picker-locate { color: #6f6258 !important; }
.location-picker-error { margin: -4px 20px 10px; color: #b05034; font-size: 13px; }
.location-picker-body { display: grid; grid-template-columns: minmax(230px, 32%) minmax(0, 1fr); min-height: 360px; border-top: 1px solid #eee3d8; border-bottom: 1px solid #eee3d8; }
.location-picker-results { overflow: auto; padding: 10px; background: #fcf8f2; }
.location-picker-empty { margin: 10px; color: #85796e; font-size: 14px; line-height: 1.6; }
.location-picker-result { display: grid; gap: 4px; width: 100%; padding: 11px; border: 0; border-bottom: 1px solid #eee4da; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.location-picker-result:hover { background: #f3eadf; }
.location-picker-result strong { font-size: 14px; }
.location-picker-result span, .location-picker-result small { color: #81756b; font-size: 12px; line-height: 1.4; }
.location-picker-footer { display: flex; align-items: end; justify-content: space-between; gap: 18px; padding: 15px 20px 18px; }
.location-picker-selection { display: grid; gap: 5px; min-width: 0; }
.location-picker-selection > span, .location-picker-selection p, .location-picker-candidates > span { margin: 0; color: #897c70; font-size: 12px; }
.location-picker-selection strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 15px; }
.location-picker-selection label { display: grid; grid-template-columns: auto minmax(180px, 1fr); align-items: center; gap: 8px; color: #65594f; font-size: 13px; }
.location-picker-selection input { height: 34px; padding: 0 9px; }
.location-picker-candidates { display: flex; flex-wrap: wrap; align-items: center; gap: 5px; }
.location-picker-candidates button { padding: 3px 7px; font-size: 12px; }
.location-picker-actions { display: flex; gap: 8px; flex: none; }
.location-picker-actions button { padding: 9px 13px; }
.location-picker-confirm { border-color: #9c7256 !important; background: #8c7768 !important; color: #fff !important; }
@media (max-width: 700px) {
  .location-picker-backdrop { padding: 0; align-items: end; }
  .location-picker { width: 100%; height: 100dvh; max-height: 100dvh; border-radius: 0; }
  .location-picker-header { padding: 13px 16px 9px; }
  .location-picker-search { grid-template-columns: minmax(0, 1fr) auto; padding: 0 16px 10px; }
  .location-picker-locate { grid-column: 1 / -1; height: 34px; }
  .location-picker-body { grid-template-columns: 1fr; grid-template-rows: auto minmax(220px, 1fr); min-height: 0; }
  .location-picker-results { display: flex; min-height: 74px; max-height: 126px; overflow-x: auto; gap: 8px; padding: 8px 16px; }
  .location-picker-result { flex: 0 0 184px; min-height: 82px; padding: 9px; border: 1px solid #eee4da; border-radius: 6px; }
  .location-picker-empty { min-width: min(310px, calc(100vw - 32px)); margin: 4px 0; }
  .location-picker-footer { display: grid; gap: 10px; padding: 11px 16px max(12px, env(safe-area-inset-bottom)); }
  .location-picker-selection label { grid-template-columns: 1fr; }
  .location-picker-selection p { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .location-picker-actions { display: grid; grid-template-columns: 1fr 1fr; }
  .location-picker-actions button { min-height: 42px; }
}
</style>
