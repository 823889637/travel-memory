<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { createTripCompanion, getCompanionMemories, getTripCompanions, setTripCompanionActive, updateTripCompanion } from '../api/companion'
import { getTimeline } from '../api/memory'
import { getTrip, uploadImage } from '../api/trip'
import MemoryPhotoGallery from '../components/MemoryPhotoGallery.vue'
import { getTripDayNumber } from '../utils/tripDay'
import TripViewNav from '../components/TripViewNav.vue'
import MobilePageHeader from '../components/MobilePageHeader.vue'

const props = defineProps({ id: { type: String, required: true } })
const trip = ref(null)
const memories = ref([])
const companions = ref([])
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const draftName = ref('')
const draftAvatarUrl = ref('')
const draftIsSelf = ref(false)
const avatarUploading = ref(false)
const relatedMemories = ref([])
const saving = ref(false)
const editingId = ref(null)
const editingName = ref('')
const selectedCompanionId = ref(null)

const activeCompanions = computed(() => companions.value.filter(item => item.active))
const inactiveCompanions = computed(() => companions.value.filter(item => !item.active))
const selectedCompanion = computed(() => companions.value.find(
  item => String(item.id) === String(selectedCompanionId.value),
) || null)
const filteredMemories = computed(() => {
  if (!selectedCompanion.value) return memories.value
  return relatedMemories.value
})

function companionMemoryCount(companionId) {
  const summary = companions.value.find(item => String(item.id) === String(companionId))
  if (summary?.memoryCount != null) return summary.memoryCount
  return memories.value.filter(memory =>
    memory.companions?.some(companion => String(companion.id) === String(companionId)),
  ).length
}

const dayGroups = computed(() => {
  const groups = new Map()
  const sorted = [...filteredMemories.value].sort((left, right) => String(left.recordTime || '').localeCompare(String(right.recordTime || '')))
  sorted.forEach((memory) => {
    const date = memory.recordTime ? String(memory.recordTime).slice(0, 10) : '未知日期'
    if (!groups.has(date)) groups.set(date, [])
    groups.get(date).push(memory)
  })
  return [...groups.entries()].map(([date, items], index) => ({
    date,
    dayNumber: getTripDayNumber(trip.value?.startDate, date, index + 1),
    memories: items,
  }))
})

function formatTime(value) {
  return value ? String(value).slice(11, 16) : '--:--'
}

async function selectCompanion(companion) {
  if (String(selectedCompanionId.value) === String(companion.id)) {
    selectedCompanionId.value = null
    relatedMemories.value = []
    return
  }
  selectedCompanionId.value = companion.id
  actionError.value = ''
  try {
    relatedMemories.value = await getCompanionMemories(props.id, companion.id)
  } catch (err) {
    selectedCompanionId.value = null
    relatedMemories.value = []
    actionError.value = err.message || '同行者的相关记忆暂时没有加载成功。'
  }
}

async function uploadCompanionAvatar(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  avatarUploading.value = true
  actionError.value = ''
  try {
    const data = new FormData()
    data.append('photo', file)
    draftAvatarUrl.value = (await uploadImage(data)).photoUrl
  } catch (err) {
    actionError.value = err.message || '头像上传失败。'
  } finally {
    avatarUploading.value = false
  }
}

async function loadPage() {
  loading.value = true
  error.value = ''
  try {
    const [tripData, memoryData, companionData] = await Promise.all([
      getTrip(props.id),
      getTimeline(props.id),
      getTripCompanions(props.id),
    ])
    trip.value = tripData
    memories.value = memoryData
    companions.value = companionData
  } catch (err) {
    error.value = err.message || '同行者信息暂时没有加载成功，请稍后再试。'
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
      avatarUrl: draftAvatarUrl.value || null,
      isSelf: draftIsSelf.value,
    })
    draftName.value = ''
    draftAvatarUrl.value = ''
    draftIsSelf.value = false
    companions.value = await getTripCompanions(props.id)
  } catch (err) {
    actionError.value = err.message || '添加同行者失败。'
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
      isSelf: Boolean(companion.isSelf),
    })
    editingId.value = null
    companions.value = await getTripCompanions(props.id)
    memories.value = await getTimeline(props.id)
  } catch (err) {
    actionError.value = err.message || '修改同行者失败。'
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
  } catch (err) {
    actionError.value = err.message || '更新同行者状态失败。'
  } finally {
    saving.value = false
  }
}

watch(() => props.id, loadPage, { immediate: true })
</script>

<template>
  <section class="companions-page">
    <MobilePageHeader title="同行的人" back-to="/trips" />
    <header v-if="!loading && trip" class="companions-head">
      <div>
        <p class="trip-list-kicker">同行的人</p>
        <h1>{{ trip?.title || '这次旅行' }}</h1>
        <p>记下每个瞬间当时和谁一起，不把同行者变成账号或权限关系。</p>
      </div>
      <RouterLink :to="`/trips/${id}/memories/new`"><button class="secondary">新增记忆</button></RouterLink>
    </header>

    <TripViewNav v-if="!loading && trip" :trip-id="id" active="companions" />

    <p v-if="loading" class="muted">正在整理同行的记忆...</p>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!loading && !error && trip" class="companions-layout">
      <div class="companions-memories">
        <div class="companions-related-head">
          <div>
            <p class="trip-list-kicker">相关记忆</p>
            <h2>{{ selectedCompanion ? selectedCompanion.name : '全部同行记忆' }}</h2>
          </div>
          <button v-if="selectedCompanion" type="button" class="companion-text-btn" @click="selectedCompanionId = null; relatedMemories = []">查看全部</button>
        </div>
        <div v-if="dayGroups.length === 0" class="companions-empty">
          <h2>{{ selectedCompanion ? `还没有和${selectedCompanion.name}一起的记忆。` : '还没有可以一起回看的片段。' }}</h2>
          <p>{{ selectedCompanion ? '之后编辑 Memory 时，可以把这位同行者补充进去。' : '创建 Memory 后，可以标记这一刻有哪些同行者在场。' }}</p>
        </div>

        <section v-for="group in dayGroups" :key="group.date" class="companions-day">
          <div class="companions-day-head">
            <h2>第 {{ group.dayNumber }} 天</h2>
            <span>{{ group.date }}</span>
          </div>
          <div class="companions-memory-list">
            <article v-for="memory in group.memories" :key="memory.id" class="companions-memory-card">
              <time>{{ formatTime(memory.recordTime) }}</time>
              <MemoryPhotoGallery
                v-if="memory.photoUrl"
                :photos="memory.photos"
                :fallback-url="memory.photoUrl"
                layout="recap"
                alt="旅行记忆照片"
              />
              <div v-else class="companions-photo-empty">没有照片</div>
              <div class="companions-memory-copy">
                <p :class="{ muted: !memory.content }">{{ memory.content || '这一刻没有留下文字' }}</p>
                <span>{{ memory.locationName || '地点还没有补充' }}</span>
                <div v-if="memory.companions?.length" class="memory-companion-line">
                  <strong>和谁一起</strong>
                  <span v-for="companion in memory.companions" :key="companion.id">{{ companion.name }}</span>
                </div>
                <p v-else class="memory-companion-empty">这一刻还没有记录同行者。</p>
              </div>
              <RouterLink class="companions-edit-link" :to="`/trips/${id}/memories/${memory.id}/edit`">编辑</RouterLink>
            </article>
          </div>
        </section>
      </div>

      <aside class="companion-manager">
        <div class="companion-manager-head">
          <div>
            <h2>这趟旅行的同行者</h2>
            <p>{{ activeCompanions.length }} 人可用于新记忆</p>
          </div>
        </div>

        <form class="companion-add" @submit.prevent="addCompanion">
          <input v-model="draftName" maxlength="50" placeholder="输入名字或称呼" />
          <button :disabled="saving || !draftName.trim()">添加</button>
          <label class="companion-avatar-picker">
            <span>{{ avatarUploading ? '上传中…' : (draftAvatarUrl ? '已选择头像' : '添加头像') }}</span>
            <input class="visually-hidden" type="file" accept="image/*" :disabled="avatarUploading" @change="uploadCompanionAvatar" />
          </label>
          <label class="companion-self-toggle"><input v-model="draftIsSelf" type="checkbox" /> 这是我</label>
        </form>
        <p v-if="actionError" class="error companion-action-error">{{ actionError }}</p>

        <div v-if="activeCompanions.length" class="companion-list">
          <article v-for="companion in activeCompanions" :key="companion.id" class="companion-row">
            <template v-if="editingId === companion.id">
              <input v-model="editingName" maxlength="50" @keyup.enter="saveEdit(companion)" />
              <button class="ghost" :disabled="saving" @click="saveEdit(companion)">保存</button>
              <button class="ghost" @click="editingId = null">取消</button>
            </template>
            <template v-else>
              <button
                type="button"
                :class="['companion-select', { active: String(selectedCompanionId) === String(companion.id) }]"
                :aria-pressed="String(selectedCompanionId) === String(companion.id)"
                @click="selectCompanion(companion)"
              >
                <span class="companion-avatar" aria-hidden="true">
                  <img v-if="companion.avatarUrl" :src="companion.avatarUrl" alt="" />
                  <template v-else>{{ companion.name.slice(0, 1) }}</template>
                </span>
                <span class="companion-row-copy">
                  <strong>{{ companion.name }} <small v-if="companion.isSelf" class="companion-self-badge">你</small></strong>
                  <small>出现在 {{ companionMemoryCount(companion.id) }} 段记忆中</small>
                </span>
              </button>
              <button class="companion-text-btn" @click="beginEdit(companion)">改名</button>
              <button class="companion-text-btn" @click="toggleActive(companion)">停用</button>
            </template>
          </article>
        </div>

        <details v-if="inactiveCompanions.length" class="inactive-companions">
          <summary>已停用 {{ inactiveCompanions.length }} 人</summary>
          <div class="companion-list">
            <article v-for="companion in inactiveCompanions" :key="companion.id" class="companion-row inactive">
              <span class="companion-avatar" aria-hidden="true"><img v-if="companion.avatarUrl" :src="companion.avatarUrl" alt="" /><template v-else>{{ companion.name.slice(0, 1) }}</template></span>
              <span class="companion-row-copy">
                <strong>{{ companion.name }}</strong>
                <small>已停用 · 出现在 {{ companionMemoryCount(companion.id) }} 段记忆中</small>
              </span>
              <button class="companion-text-btn" @click="toggleActive(companion)">重新启用</button>
            </article>
          </div>
        </details>

        <div class="companion-note">
          <h3>关于同行者</h3>
          <p>这里记录的是旅行中的真实人物称呼，不会创建账号，也不会授予查看或编辑权限。</p>
          <p>停用后，过去 Memory 中已经留下的同行信息仍会保留。</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.companions-page { display: grid; gap: 20px; }
.companions-head { display: flex; align-items: end; justify-content: space-between; gap: 18px; padding: 10px 2px 4px; }
.companions-head h1, .companions-head p { margin: 0; }
.companions-head h1 { margin: 5px 0 8px; font-family: Georgia, "Microsoft YaHei", serif; font-size: 34px; }
.companions-head > div > p:last-child { color: var(--tm-text-muted); line-height: 1.6; }
.companions-layout { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(270px, .7fr); gap: 20px; align-items: start; }
.companions-memories { display: grid; gap: 24px; }
.companions-day { display: grid; gap: 10px; }
.companions-day-head { display: flex; align-items: baseline; gap: 10px; border-bottom: 1px solid var(--tm-border); padding-bottom: 8px; }
.companions-day-head h2, .companions-day-head span { margin: 0; }
.companions-day-head h2 { font-size: 21px; }
.companions-day-head span { color: var(--tm-text-muted); font-size: 13px; }
.companions-memory-list { display: grid; gap: 9px; }
.companions-memory-card { display: grid; grid-template-columns: 54px 155px minmax(0, 1fr) auto; gap: 14px; align-items: center; border: 1px solid var(--tm-border); border-radius: var(--tm-radius-md); background: var(--tm-surface); padding: 10px; }
.companions-memory-card time { color: var(--tm-text-muted); font-size: 13px; font-weight: 700; }
.companions-photo-empty { display: grid; min-height: 105px; place-items: center; background: var(--tm-accent-soft); color: var(--tm-text-muted); font-size: 12px; }
.companions-memory-copy { display: grid; gap: 9px; min-width: 0; }
.companions-memory-copy > p, .companions-memory-copy > span { margin: 0; }
.companions-memory-copy > p { line-height: 1.55; }
.companions-memory-copy > span { color: var(--tm-text-muted); font-size: 13px; }
.memory-companion-line { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.memory-companion-line strong { margin-right: 4px; font-size: 12px; }
.memory-companion-line span { padding: 4px 8px; border-radius: 999px; background: var(--tm-accent-soft); color: var(--tm-accent); font-size: 12px; }
.memory-companion-empty { color: var(--tm-text-muted); font-size: 12px; }
.companions-edit-link { align-self: start; color: var(--tm-text-muted); font-size: 12px; padding: 4px; }
.companion-manager { position: sticky; top: 82px; display: grid; gap: 15px; border: 1px solid var(--tm-border); border-radius: var(--tm-radius-md); background: rgba(255, 253, 249, .9); padding: 18px; box-shadow: 0 10px 26px rgba(63, 49, 38, .05); }
.companion-manager-head h2, .companion-manager-head p { margin: 0; }
.companion-manager-head h2 { font-size: 18px; }
.companion-manager-head p { margin-top: 5px; color: var(--tm-text-muted); font-size: 12px; }
.companion-add { display: grid; grid-template-columns: 1fr auto; gap: 7px; }
.companion-list { display: grid; }
.companion-row { display: flex; align-items: center; gap: 8px; min-width: 0; border-top: 1px solid var(--tm-border); padding: 11px 0; }
.companion-row-copy { display: grid; min-width: 0; margin-right: auto; gap: 3px; }
.companion-row-copy strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.companion-row-copy small { color: var(--tm-text-muted); font-size: 11px; }
.companion-row input { min-width: 0; }
.companion-avatar { display: grid; flex: 0 0 34px; height: 34px; place-items: center; border-radius: 50%; background: var(--tm-accent-soft); color: var(--tm-accent); font-weight: 800; }
.companion-text-btn { padding: 3px; background: transparent; color: var(--tm-text-muted); font-size: 12px; }
.companion-row.inactive { opacity: .68; }
.inactive-companions summary { cursor: pointer; color: var(--tm-text-muted); font-size: 13px; }
.companion-note { display: grid; gap: 8px; border-top: 1px solid var(--tm-border); padding-top: 14px; }
.companion-note h3, .companion-note p { margin: 0; }
.companion-note h3 { font-size: 14px; }
.companion-note p { color: var(--tm-text-muted); font-size: 12px; line-height: 1.6; }
.companion-action-error { margin: 0; font-size: 12px; }
.companions-empty { padding: 28px; border: 1px solid var(--tm-border); background: var(--tm-surface); }
.companions-empty h2, .companions-empty p { margin: 0; }
.companions-empty p { margin-top: 8px; color: var(--tm-text-muted); }
@media (max-width: 820px) { .companions-layout { grid-template-columns: 1fr; } .companion-manager { position: static; order: -1; } }
@media (max-width: 640px) { .companions-head { align-items: start; flex-direction: column; } .companions-memory-card { grid-template-columns: 45px minmax(0, 1fr); } .companions-memory-card :deep(.gallery), .companions-photo-empty { grid-column: 2; } .companions-memory-copy { grid-column: 2; } .companions-edit-link { grid-column: 2; } .companion-list { gap: 8px; } .companion-row { flex-wrap: wrap; border: 1px solid var(--tm-border); border-radius: 12px; background: var(--tm-surface); padding: 11px; } }
</style>
