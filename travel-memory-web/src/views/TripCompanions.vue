<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronDown, ChevronRight, Clock3, Images, MapPin, UserRoundCog, X } from '@lucide/vue'
import {
  createTripCompanion,
  getCompanionMemories,
  getTripCompanions,
  setTripCompanionActive,
  updateTripCompanion,
} from '../api/companion'
import { getTimeline } from '../api/memory'
import { getTrip, uploadImage } from '../api/trip'
import { currentUser } from '../auth'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'
import TripViewNav from '../components/TripViewNav.vue'
import MobilePageHeader from '../components/MobilePageHeader.vue'
import TripContextCard from '../components/TripContextCard.vue'

const props = defineProps({ id: { type: String, required: true } })

const trip = ref(null)
const memories = ref([])
const companions = ref([])
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const draftName = ref('')
const avatarUploadingId = ref(null)
const relatedMemories = ref([])
const saving = ref(false)
const editingId = ref(null)
const editingName = ref('')
const selectedCompanionId = ref(null)
const managerOpen = ref(false)
const relatedLoading = ref(false)
const companionSelectorOpen = ref(false)
const companionSelector = ref(null)
let relatedRequestId = 0

const activeCompanions = computed(() => companions.value.filter(item => item.active && !item.isSelf))
const inactiveCompanions = computed(() => companions.value.filter(item => !item.active && !item.isSelf))
const accountSelfCompanion = computed(() => {
  if (!currentUser.value) return null
  return {
    id: '__current_user__',
    name: currentUser.value.displayName || currentUser.value.username || '我',
    avatarUrl: currentUser.value.avatarUrl || '',
    isSelf: true,
    active: true,
    memoryCount: memories.value.length,
    synthetic: true,
    sortOrder: -1,
  }
})
const orderedActiveCompanions = computed(() => {
  const self = accountSelfCompanion.value
  const others = activeCompanions.value
    .sort((left, right) => (
      Number(left.sortOrder ?? Number.MAX_SAFE_INTEGER) - Number(right.sortOrder ?? Number.MAX_SAFE_INTEGER)
      || Number(left.id || 0) - Number(right.id || 0)
    ))
  return self ? [self, ...others] : others
})
const selfCompanion = computed(() => orderedActiveCompanions.value.find(item => item.isSelf) || null)
const selectedCompanion = computed(() => orderedActiveCompanions.value.find(
  item => String(item.id) === String(selectedCompanionId.value),
) || null)
const filteredMemories = computed(() => (
  !selectedCompanion.value
    ? []
    : (selectedCompanion.value.isSelf ? memories.value : relatedMemories.value)
))
const relatedMemoryItems = computed(() => [...filteredMemories.value].sort((left, right) => (
  timeValue(left.recordTime) - timeValue(right.recordTime)
  || Number(left.id || 0) - Number(right.id || 0)
)))

function timeValue(value) {
  const normalized = String(value || '').replace(' ', 'T')
  const result = Date.parse(normalized)
  return Number.isFinite(result) ? result : Number.MAX_SAFE_INTEGER
}

function companionMemoryCount(companionId) {
  const summary = orderedActiveCompanions.value.find(item => String(item.id) === String(companionId))
  if (summary?.isSelf) return memories.value.length
  if (summary?.memoryCount != null) return summary.memoryCount
  return memories.value.filter(memory =>
    memory.companions?.some(companion => String(companion.id) === String(companionId)),
  ).length
}

function normalizedPhotos(memory) {
  const seen = new Set()
  const result = []
  const add = (url, sortOrder = result.length) => {
    const normalizedUrl = String(url || '').trim()
    if (!normalizedUrl || seen.has(normalizedUrl)) return
    seen.add(normalizedUrl)
    result.push({ photoUrl: normalizedUrl, sortOrder })
  }
  ;(memory?.photos || [])
    .slice()
    .sort((left, right) => Number(left.sortOrder ?? 0) - Number(right.sortOrder ?? 0))
    .forEach(photo => add(photo.photoUrl, photo.sortOrder))
  add(memory?.photoUrl, -1)
  return result
}

function memoryPhotoCount(memory) {
  const count = Number(memory?.photoCount)
  return Number.isFinite(count) && count > 0 ? count : normalizedPhotos(memory).length
}

function formatDateTime(value) {
  const raw = String(value || '')
  if (raw.length < 16) return '时间还没有补充'
  return `${raw.slice(0, 10).replaceAll('-', '.')} ${raw.slice(11, 16)}`
}

async function selectCompanionById(companionId) {
  const companion = orderedActiveCompanions.value.find(item => String(item.id) === String(companionId))
  const requestId = ++relatedRequestId
  actionError.value = ''
  relatedMemories.value = []
  if (!companion) {
    selectedCompanionId.value = null
    relatedLoading.value = false
    return
  }
  selectedCompanionId.value = companion.id
  if (companion.isSelf) {
    relatedLoading.value = false
    return
  }
  relatedLoading.value = true
  try {
    const result = await getCompanionMemories(props.id, companion.id)
    if (requestId === relatedRequestId) {
      relatedMemories.value = Array.isArray(result) ? result : []
    }
  } catch (requestError) {
    if (requestId === relatedRequestId) {
      relatedMemories.value = []
      actionError.value = requestError.message || '同行者的相关记忆暂时没有加载成功。'
    }
  } finally {
    if (requestId === relatedRequestId) relatedLoading.value = false
  }
}

function clearCompanionFilter() {
  relatedRequestId += 1
  selectedCompanionId.value = null
  relatedMemories.value = []
  relatedLoading.value = false
}

function chooseCompanion(companionId) {
  companionSelectorOpen.value = false
  selectCompanionById(companionId)
}

function handleOutsidePointer(event) {
  if (!companionSelector.value?.contains(event.target)) {
    companionSelectorOpen.value = false
  }
}

function showAllTripMemories() {
  if (selfCompanion.value) {
    selectCompanionById(selfCompanion.value.id)
    return
  }
  clearCompanionFilter()
}

function openManager() {
  actionError.value = ''
  managerOpen.value = true
}

function closeManager() {
  managerOpen.value = false
  editingId.value = null
}

function handleManagerKeydown(event) {
  if (event.key === 'Escape') closeManager()
}

async function uploadCompanionAvatar(event, companion) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || !companion || avatarUploadingId.value != null) return
  avatarUploadingId.value = companion.id
  actionError.value = ''
  try {
    const data = new FormData()
    data.append('photo', file)
    const uploaded = await uploadImage(data)
    await updateTripCompanion(props.id, companion.id, {
      name: companion.name,
      avatarUrl: uploaded.photoUrl,
      isSelf: false,
    })
    companions.value = await getTripCompanions(props.id)
  } catch (requestError) {
    actionError.value = requestError.message || '头像上传失败。'
  } finally {
    avatarUploadingId.value = null
  }
}

async function loadPage() {
  loading.value = true
  error.value = ''
  clearCompanionFilter()
  try {
    const [tripData, memoryData, companionData] = await Promise.all([
      getTrip(props.id),
      getTimeline(props.id),
      getTripCompanions(props.id),
    ])
    trip.value = tripData
    memories.value = Array.isArray(memoryData) ? memoryData : []
    companions.value = Array.isArray(companionData) ? companionData : []
  } catch (requestError) {
    error.value = requestError.message || '同行者信息暂时没有加载成功，请稍后再试。'
  } finally {
    loading.value = false
  }
}

async function addCompanion() {
  const name = draftName.value.trim()
  if (!name || saving.value) return
  saving.value = true
  actionError.value = ''
  try {
    await createTripCompanion(props.id, {
      name,
      avatarUrl: null,
      isSelf: false,
    })
    draftName.value = ''
    companions.value = await getTripCompanions(props.id)
  } catch (requestError) {
    actionError.value = requestError.message || '添加同行者失败。'
  } finally {
    saving.value = false
  }
}

function beginEdit(companion) {
  editingId.value = companion.id
  editingName.value = companion.name
  actionError.value = ''
}

async function saveEdit(companion) {
  const name = editingName.value.trim()
  if (!name || saving.value) return
  saving.value = true
  actionError.value = ''
  try {
    await updateTripCompanion(props.id, companion.id, {
      name,
      avatarUrl: companion.avatarUrl || null,
      isSelf: false,
    })
    editingId.value = null
    companions.value = await getTripCompanions(props.id)
    memories.value = await getTimeline(props.id)
  } catch (requestError) {
    actionError.value = requestError.message || '修改同行者失败。'
  } finally {
    saving.value = false
  }
}

async function toggleActive(companion) {
  if (saving.value) return
  const message = companion.active
    ? `暂不在新记忆中显示“${companion.name}”吗？已经记录的同行信息会保留。`
    : `重新启用“${companion.name}”吗？`
  if (!window.confirm(message)) return
  saving.value = true
  actionError.value = ''
  try {
    await setTripCompanionActive(props.id, companion.id, !companion.active)
    companions.value = await getTripCompanions(props.id)
    if (companion.active && String(selectedCompanionId.value) === String(companion.id)) {
      clearCompanionFilter()
    }
  } catch (requestError) {
    actionError.value = requestError.message || '更新同行者状态失败。'
  } finally {
    saving.value = false
  }
}

watch(() => props.id, loadPage, { immediate: true })
watch(managerOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  if (open) window.addEventListener('keydown', handleManagerKeydown)
  else window.removeEventListener('keydown', handleManagerKeydown)
})

onMounted(() => document.addEventListener('pointerdown', handleOutsidePointer))
onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', handleManagerKeydown)
  document.removeEventListener('pointerdown', handleOutsidePointer)
})
</script>

<template>
  <section class="companions-page">
    <MobilePageHeader title="同行的人" back-to="/trips" />
    <TripContextCard v-if="!loading && trip" :trip="trip" :memories="memories" variant="compact" />
    <TripViewNav v-if="!loading && trip" :trip-id="id" active="companions" />

    <header v-if="!loading && trip" class="companions-head">
      <h1>同行的人</h1>
      <p>记录这趟旅程里和你一起的人</p>
    </header>

    <p v-if="loading" class="muted">正在整理同行的记忆...</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!loading && !error && trip" class="companions-layout">
      <section class="companions-people" aria-label="这趟旅行的同行者">
        <div v-if="activeCompanions.length" class="companions-people-list">
          <article
            v-for="companion in orderedActiveCompanions"
            :key="companion.id"
            :class="['companion-memory-person', { active: String(selectedCompanionId) === String(companion.id) }]"
          >
            <span class="companion-avatar companion-avatar--large" aria-hidden="true">
              <img v-if="companion.avatarUrl" :src="companion.avatarUrl" alt="" />
              <template v-else>{{ companion.name.slice(0, 1) }}</template>
            </span>
            <span class="companion-row-copy">
              <strong>{{ companion.name }}</strong>
              <small>出现在 {{ companionMemoryCount(companion.id) }} 段记忆中</small>
            </span>
            <span v-if="companion.isSelf" class="companion-self-badge">我</span>
            <ChevronRight v-else :size="20" :stroke-width="1.7" aria-hidden="true" />
          </article>
        </div>
        <div v-else class="companions-empty companions-empty--compact">
          <h2>还没有记录同行的人。</h2>
          <p>之后编辑记忆时，可以把这一刻一起出现的人补充进来。</p>
        </div>
      </section>

      <section class="companions-related">
        <div class="companions-related-head">
          <div class="companion-related-selector">
            <span>相关记忆</span>
            <i aria-hidden="true">·</i>
            <span ref="companionSelector" class="companion-related-select-control">
              <button
                type="button"
                aria-haspopup="listbox"
                :aria-expanded="companionSelectorOpen"
                @click="companionSelectorOpen = !companionSelectorOpen"
              >
                <span class="companion-selected-label">
                  {{ selectedCompanion
                    ? (selectedCompanion.isSelf ? `${selectedCompanion.name}（我）` : selectedCompanion.name)
                    : '选择同行人' }}
                </span>
                <ChevronDown
                  :size="17"
                  :stroke-width="1.6"
                  :class="{ open: companionSelectorOpen }"
                  aria-hidden="true"
                />
              </button>
              <span v-if="companionSelectorOpen" class="companion-related-options" role="listbox" aria-label="选择要回看的同行者">
                <button
                  v-for="companion in orderedActiveCompanions"
                  :key="companion.id"
                  type="button"
                  role="option"
                  :aria-selected="String(selectedCompanionId) === String(companion.id)"
                  :class="{ active: String(selectedCompanionId) === String(companion.id) }"
                  @click="chooseCompanion(companion.id)"
                >
                  <span class="companion-option-avatar" aria-hidden="true">
                    <img v-if="companion.avatarUrl" :src="companion.avatarUrl" alt="" />
                    <template v-else>{{ companion.name.slice(0, 1) }}</template>
                  </span>
                  <span>
                    <strong>{{ companion.name }}</strong>
                    <small>{{ companion.isSelf ? '我的全部旅行记忆' : `${companionMemoryCount(companion.id)} 段相关记忆` }}</small>
                  </span>
                  <span v-if="companion.isSelf" class="companion-option-self">我</span>
                </button>
              </span>
            </span>
          </div>
          <button v-if="selectedCompanion && !selectedCompanion.isSelf" type="button" class="companion-text-btn" @click="showAllTripMemories">
            查看全部 <ChevronRight :size="15" aria-hidden="true" />
          </button>
        </div>

        <p v-if="relatedLoading" class="companions-related-loading">正在整理一起留下的记忆...</p>

        <div v-else-if="!selectedCompanion" class="companions-empty companions-related-empty">
          <h2>选择一位同行的人，再回看一起经历的片段。</h2>
        </div>

        <div v-else-if="relatedMemoryItems.length === 0" class="companions-empty companions-related-empty">
          <h2>还没有和{{ selectedCompanion.name }}一起的记忆。</h2>
          <p>之后编辑记忆时，可以把这位同行者补充进去。</p>
        </div>

        <div v-else class="companions-related-list">
          <RouterLink
            v-for="memory in relatedMemoryItems"
            :key="memory.id"
            :to="`/trips/${id}/memories/${memory.id}`"
            :class="['companions-related-card', { 'has-photo': normalizedPhotos(memory).length }]"
          >
            <MemoryPhotoGallery
              v-if="normalizedPhotos(memory).length"
              class="companions-related-photo"
              :photos="normalizedPhotos(memory)"
              :fallback-url="memory.photoUrl || ''"
              layout="favorite"
              alt="同行记忆照片"
            />
            <div v-else class="companions-related-photo-empty">记忆</div>

            <div class="companions-related-copy">
              <p :class="{ muted: !memory.content }">
                “{{ memory.content || '这一刻没有留下文字。' }}”
              </p>
              <div class="companions-related-meta">
                <span>
                  <MapPin :size="14" :stroke-width="1.7" aria-hidden="true" />
                  {{ memory.locationName || '地点还没有补充' }}
                </span>
                <span>
                  <Clock3 :size="14" :stroke-width="1.7" aria-hidden="true" />
                  {{ formatDateTime(memory.recordTime) }}
                </span>
              </div>
            </div>

            <span v-if="memoryPhotoCount(memory)" class="companions-photo-count">
              <Images :size="15" :stroke-width="1.7" aria-hidden="true" />
              {{ memoryPhotoCount(memory) }}
            </span>
          </RouterLink>
        </div>
      </section>

      <button type="button" class="companion-manage-entry" @click="openManager">
        <UserRoundCog :size="22" :stroke-width="1.7" aria-hidden="true" />
        管理名单
      </button>
    </div>

    <Teleport to="body">
      <div v-if="managerOpen" class="companion-manager-backdrop" @click.self="closeManager">
        <section class="companion-manager-dialog" role="dialog" aria-modal="true" aria-labelledby="companion-manager-title">
          <header class="companion-manager-dialog-head">
            <div>
              <h2 id="companion-manager-title">管理同行者</h2>
              <p>{{ activeCompanions.length }} 人可用于新记忆</p>
            </div>
            <button type="button" aria-label="关闭同行者管理" @click="closeManager">
              <X :size="21" :stroke-width="1.8" aria-hidden="true" />
            </button>
          </header>

          <div class="companion-manager-content">
            <form class="companion-add" @submit.prevent="addCompanion">
              <input v-model="draftName" maxlength="50" placeholder="输入名字或称呼" aria-label="同行者名字或称呼" />
              <button :disabled="saving || !draftName.trim()">添加</button>
            </form>

            <p v-if="actionError" class="error companion-action-error">{{ actionError }}</p>

            <div v-if="activeCompanions.length" class="companion-list">
              <article v-for="companion in activeCompanions" :key="companion.id" class="companion-row">
                <template v-if="editingId === companion.id">
                  <input v-model="editingName" maxlength="50" aria-label="修改同行者名字" @keyup.enter="saveEdit(companion)" />
                  <button class="ghost" :disabled="saving" @click="saveEdit(companion)">保存</button>
                  <button class="ghost" @click="editingId = null">取消</button>
                </template>
                <template v-else>
                  <span class="companion-avatar" aria-hidden="true">
                    <img v-if="companion.avatarUrl" :src="companion.avatarUrl" alt="" />
                    <template v-else>{{ companion.name.slice(0, 1) }}</template>
                  </span>
                  <span class="companion-row-copy">
                    <strong>{{ companion.name }}</strong>
                    <small>出现在 {{ companionMemoryCount(companion.id) }} 段记忆中</small>
                  </span>
                  <label class="companion-avatar-picker">
                    <span>
                      {{ String(avatarUploadingId) === String(companion.id)
                        ? '上传中...'
                        : (companion.avatarUrl ? '更换头像' : '添加头像') }}
                    </span>
                    <input
                      class="visually-hidden"
                      type="file"
                      accept="image/*"
                      :disabled="avatarUploadingId != null"
                      @change="uploadCompanionAvatar($event, companion)"
                    />
                  </label>
                  <button class="companion-text-btn" @click="beginEdit(companion)">改名</button>
                  <button class="companion-text-btn" @click="toggleActive(companion)">停用</button>
                </template>
              </article>
            </div>

            <details v-if="inactiveCompanions.length" class="inactive-companions">
              <summary>已停用 {{ inactiveCompanions.length }} 人</summary>
              <div class="companion-list">
                <article v-for="companion in inactiveCompanions" :key="companion.id" class="companion-row inactive">
                  <span class="companion-avatar" aria-hidden="true">
                    <img v-if="companion.avatarUrl" :src="companion.avatarUrl" alt="" />
                    <template v-else>{{ companion.name.slice(0, 1) }}</template>
                  </span>
                  <span class="companion-row-copy">
                    <strong>{{ companion.name }}</strong>
                    <small>过去出现在 {{ companionMemoryCount(companion.id) }} 段记忆中</small>
                  </span>
                  <button class="companion-text-btn" @click="toggleActive(companion)">重新启用</button>
                </article>
              </div>
            </details>

            <div class="companion-note">
              <h3>关于同行者</h3>
              <p>这里只记录旅途中真实人物的称呼，不会创建账号，也不会授予查看或编辑权限。</p>
              <p>停用后，过去记忆中已经留下的同行信息仍会保留。</p>
            </div>
          </div>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.companions-page {
  display: grid;
  gap: 20px;
}

.companions-head {
  display: grid;
  gap: 7px;
  padding: 14px 4px 4px;
}

.companions-head h1,
.companions-head p {
  margin: 0;
}

.companions-head h1 {
  color: #37251d;
  font-family: var(--tm-font-serif);
  font-size: 36px;
  font-weight: 600;
  line-height: 1.25;
}

.companions-head p {
  color: #a66b50;
  font-family: var(--tm-font-serif);
  font-size: 15px;
  line-height: 1.6;
}

.companions-layout {
  display: grid;
  width: min(100%, 860px);
  justify-self: center;
  gap: 28px;
}

.companions-people,
.companions-related {
  display: grid;
  gap: 14px;
}

.companions-people-list,
.companions-related-list {
  display: grid;
  gap: 12px;
}

.companion-memory-person {
  display: flex;
  width: 100%;
  min-height: 88px;
  align-items: center;
  gap: 16px;
  border: 1px solid rgba(231, 221, 211, .86);
  border-radius: 18px;
  background: rgba(255, 254, 251, .94);
  padding: 14px 18px;
  color: var(--tm-text);
  text-align: left;
  box-shadow: 0 9px 24px rgba(70, 50, 37, .06);
  transition: background .16s ease, box-shadow .16s ease;
}

.companion-memory-person.active {
  border-color: rgba(222, 207, 194, .92);
  background: #fffdfa;
  box-shadow: 0 11px 27px rgba(70, 50, 37, .075);
}

.companion-memory-person > svg {
  margin-left: auto;
  color: #8e7c71;
}

.companion-avatar {
  display: grid;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: #f2e6dc;
  color: var(--tm-accent);
  font-family: var(--tm-font-serif);
  font-weight: 600;
}

.companion-avatar--large {
  flex-basis: 58px;
  width: 58px;
  height: 58px;
  font-size: 21px;
}

.companion-avatar img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.companion-row-copy {
  display: grid;
  min-width: 0;
  margin-right: auto;
  gap: 4px;
}

.companion-row-copy strong {
  min-width: 0;
  overflow: hidden;
  font-family: var(--tm-font-serif);
  font-size: 20px;
  color: #31231d;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.companion-row-copy small {
  color: #756960;
  font-size: 12px;
}

.companion-self-badge {
  flex: 0 0 auto;
  min-width: 54px;
  margin-left: auto;
  border-radius: 999px;
  background: #f7e7df;
  color: #b45c3b;
  padding: 7px 14px;
  font-size: 13px;
  text-align: center;
}

.companions-related-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 4px 2px;
}

.companion-related-selector {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  color: #382820;
  font-family: var(--tm-font-serif);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.3;
  white-space: nowrap;
}

.companion-related-selector > i {
  color: #725f53;
  font-style: normal;
}

.companion-related-select-control {
  position: relative;
  display: inline-flex;
  min-width: 0;
  align-items: center;
  color: var(--tm-accent);
}

.companion-related-select-control > button {
  display: inline-flex;
  min-width: 0;
  max-width: 210px;
  min-height: 34px;
  align-items: center;
  gap: 5px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--tm-accent);
  padding: 2px 4px;
  font: inherit;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.companion-related-select-control > button svg {
  overflow: visible;
}

.companion-selected-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.companion-related-select-control > button:hover,
.companion-related-select-control > button[aria-expanded="true"] {
  background: rgba(247, 232, 222, .76);
}

.companion-related-select-control > button svg {
  position: static;
  flex: 0 0 auto;
  transition: transform .16s ease;
}

.companion-related-select-control > button svg.open {
  transform: rotate(180deg);
}

.companion-related-selector svg {
  color: var(--tm-accent);
}

.companion-related-options {
  position: absolute;
  z-index: 80;
  top: calc(100% + 8px);
  left: 0;
  display: grid;
  width: min(268px, calc(100vw - 48px));
  overflow: hidden;
  border: 1px solid rgba(227, 213, 201, .94);
  border-radius: 15px;
  background: rgba(255, 253, 249, .99);
  box-shadow: 0 18px 40px rgba(55, 39, 29, .16);
  padding: 6px;
}

.companion-related-options > button {
  display: grid;
  width: 100%;
  min-height: 54px;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #392a23;
  padding: 7px 9px;
  text-align: left;
}

.companion-related-options > button:hover,
.companion-related-options > button.active {
  background: #f8ede5;
}

.companion-option-avatar {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: #f1e4d9;
  color: var(--tm-accent);
  font-family: var(--tm-font-serif);
}

.companion-option-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.companion-related-options > button > span:nth-child(2) {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.companion-related-options strong {
  overflow: hidden;
  font-family: var(--tm-font-serif);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.companion-related-options small {
  color: #817269;
  font-size: 11px;
}

.companion-option-self {
  border-radius: 999px;
  background: #f5e3da;
  color: var(--tm-accent);
  padding: 4px 8px;
  font-size: 11px;
}

.companion-text-btn {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: 0;
  background: transparent;
  color: var(--tm-text-muted);
  padding: 4px;
  font-size: 12px;
}

.companions-related-card {
  display: grid;
  min-width: 0;
  grid-template-columns: 116px minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  overflow: hidden;
  border: 1px solid rgba(232, 222, 212, .9);
  border-radius: 16px;
  background: rgba(255, 254, 251, .96);
  padding: 12px;
  color: var(--tm-text);
  box-shadow: 0 8px 22px rgba(70, 50, 37, .055);
}

.companions-related-card:hover {
  border-color: rgba(181, 92, 50, .25);
  box-shadow: 0 11px 26px rgba(63, 49, 38, .075);
}

.companions-related-photo,
.companions-related-photo-empty {
  width: 116px;
  height: 82px;
  overflow: hidden;
  border-radius: 12px;
}

.companions-related-photo :deep(.gallery-favorite-preview) {
  height: 82px;
  border-radius: 12px;
}

.companions-related-photo :deep(.gallery-total-count) {
  display: none;
}

.companions-related-photo-empty {
  display: grid;
  place-items: center;
  background: linear-gradient(145deg, #f4e8dd, #ead8c9);
  color: #ad7457;
  font-family: var(--tm-font-serif);
}

.companions-related-copy {
  display: grid;
  min-width: 0;
  gap: 11px;
}

.companions-related-copy > p {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: #43342c;
  font-family: var(--tm-font-serif);
  font-size: 15px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.companions-related-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  color: #81736a;
  font-size: 12px;
}

.companions-related-meta span,
.companions-photo-count {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.companions-photo-count {
  align-self: start;
  border: 1px solid var(--tm-border);
  border-radius: 999px;
  padding: 6px 9px;
  color: #6f5d52;
  font-size: 12px;
}

.companion-manage-entry {
  display: inline-flex;
  width: 100%;
  min-height: 58px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid var(--tm-accent);
  border-radius: 17px;
  background: transparent;
  color: var(--tm-accent);
  font-family: var(--tm-font-serif);
  font-size: 19px;
  font-weight: 600;
}

.companion-manage-entry:hover {
  background: #f8eee7;
}

.companions-empty {
  padding: 24px;
  border: 1px solid var(--tm-border);
  border-radius: 16px;
  background: rgba(255, 253, 249, .78);
}

.companions-empty--compact {
  padding: 20px;
}

.companions-related-empty {
  display: grid;
  min-height: 112px;
  align-content: center;
}

.companions-related-loading {
  margin: 0;
  padding: 22px 4px;
  color: #81736a;
  font-size: 13px;
}

.companions-empty h2,
.companions-empty p {
  margin: 0;
}

.companions-empty h2 {
  font-family: var(--tm-font-serif);
  font-size: 20px;
  font-weight: 600;
}

.companions-empty p {
  margin-top: 8px;
  color: var(--tm-text-muted);
  line-height: 1.65;
}

.companion-manager-backdrop {
  position: fixed;
  z-index: 1200;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(39, 31, 26, .36);
  backdrop-filter: blur(4px);
}

.companion-manager-dialog {
  display: grid;
  width: min(100%, 620px);
  max-height: min(82vh, 760px);
  overflow: hidden;
  border: 1px solid var(--tm-border);
  border-radius: 20px;
  background: #fffdf9;
  box-shadow: 0 24px 60px rgba(47, 35, 27, .24);
}

.companion-manager-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--tm-border);
  padding: 18px 20px;
}

.companion-manager-dialog-head h2,
.companion-manager-dialog-head p {
  margin: 0;
}

.companion-manager-dialog-head h2 {
  font-family: var(--tm-font-serif);
  font-size: 24px;
  font-weight: 600;
}

.companion-manager-dialog-head p {
  margin-top: 4px;
  color: var(--tm-text-muted);
  font-size: 12px;
}

.companion-manager-dialog-head > button {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--tm-text-muted);
}

.companion-manager-dialog-head > button:hover {
  background: var(--tm-accent-soft);
}

.companion-manager-content {
  display: grid;
  gap: 16px;
  overflow-y: auto;
  padding: 18px 20px 22px;
  overscroll-behavior: contain;
}

.companion-add {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 9px;
  padding: 14px;
  border: 1px solid var(--tm-border);
  border-radius: 15px;
  background: #fcf8f3;
}

.companion-add input {
  min-width: 0;
}

.companion-avatar-picker {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  color: #8b6c5a;
  padding: 5px 7px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.companion-avatar-picker:hover {
  background: var(--tm-accent-soft);
  color: var(--tm-accent);
}

.companion-list {
  display: grid;
  gap: 9px;
}

.companion-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
  border: 1px solid var(--tm-border);
  border-radius: 13px;
  background: var(--tm-surface);
  padding: 11px 12px;
}

.companion-row input {
  min-width: 0;
}

.companion-row .companion-row-copy strong {
  font-family: var(--tm-font-sans);
  font-size: 15px;
}

.companion-self-inline {
  margin-left: 5px;
  color: var(--tm-accent);
  font-size: 11px;
}

.companion-row.inactive {
  opacity: .68;
}

.inactive-companions summary {
  cursor: pointer;
  color: var(--tm-text-muted);
  font-size: 13px;
}

.inactive-companions .companion-list {
  margin-top: 10px;
}

.companion-note {
  display: grid;
  gap: 8px;
  border-top: 1px solid var(--tm-border);
  padding-top: 14px;
}

.companion-note h3,
.companion-note p {
  margin: 0;
}

.companion-note h3 {
  font-size: 14px;
}

.companion-note p {
  color: var(--tm-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.companion-action-error {
  margin: 0;
  font-size: 12px;
}

@media (max-width: 640px) {
  .companions-page {
    gap: 15px;
  }

  .companions-page :deep(.mobile-page-header > strong) {
    visibility: hidden;
  }

  .companions-head {
    padding: 14px 3px 2px;
  }

  .companions-head h1 {
    font-size: 31px;
  }

  .companions-head p {
    font-size: 14px;
  }

  .companions-layout {
    gap: 24px;
  }

  .companion-memory-person {
    min-height: 72px;
    gap: 13px;
    padding: 11px 13px;
    border-radius: 16px;
  }

  .companion-avatar--large {
    flex-basis: 48px;
    width: 48px;
    height: 48px;
    font-size: 18px;
  }

  .companion-row-copy strong {
    font-size: 18px;
  }

  .companion-self-badge {
    min-width: 48px;
    padding: 6px 12px;
  }

  .companion-related-selector {
    gap: 4px;
    font-size: 21px;
  }

  .companion-related-select-control > button {
    max-width: 180px;
    min-height: 32px;
    padding-inline: 3px;
  }

  .companions-related-card {
    grid-template-columns: 86px minmax(0, 1fr) auto;
    gap: 11px;
    padding: 9px;
    border-radius: 15px;
  }

  .companions-related-photo,
  .companions-related-photo-empty,
  .companions-related-photo :deep(.gallery-favorite-preview) {
    width: 86px;
    height: 70px;
    border-radius: 10px;
  }

  .companions-related-copy {
    gap: 7px;
  }

  .companions-related-copy > p {
    font-size: 13px;
    line-height: 1.55;
  }

  .companions-related-meta {
    display: grid;
    gap: 4px;
    font-size: 11px;
  }

  .companions-photo-count {
    padding: 5px 7px;
    font-size: 11px;
  }

  .companion-manage-entry {
    min-height: 54px;
    font-size: 18px;
  }

  .companion-manager-backdrop {
    align-items: end;
    padding: 0;
  }

  .companion-manager-dialog {
    width: 100%;
    max-height: min(88svh, 780px);
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
    border-radius: 22px 22px 0 0;
    padding-bottom: env(safe-area-inset-bottom);
  }

  .companion-manager-dialog-head {
    padding: 16px;
  }

  .companion-manager-content {
    padding: 15px 16px 20px;
  }
}

@media (max-width: 370px) {
  .companions-related-card {
    grid-template-columns: 76px minmax(0, 1fr);
  }

  .companions-related-photo,
  .companions-related-photo-empty,
  .companions-related-photo :deep(.gallery-favorite-preview) {
    width: 76px;
    height: 66px;
  }

  .companions-photo-count {
    display: none;
  }
}
</style>
