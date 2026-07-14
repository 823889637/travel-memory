<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { createTripCompanion, getTripCompanions } from '../api/companion'

const props = defineProps({
  tripId: { type: [String, Number], required: true },
  modelValue: { type: Array, default: () => [] },
  showHeading: { type: Boolean, default: true },
})
const emit = defineEmits(['update:modelValue'])
const companions = ref([])
const loading = ref(false)
const error = ref('')
const addOpen = ref(false)
const newName = ref('')
const adding = ref(false)
const addError = ref('')
const selectable = computed(() => companions.value.filter(item => item.active || props.modelValue.includes(item.id)))

function toggle(id) {
  const selected = new Set(props.modelValue)
  if (selected.has(id)) selected.delete(id)
  else selected.add(id)
  emit('update:modelValue', [...selected])
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    companions.value = await getTripCompanions(props.tripId)
  } catch (err) {
    error.value = err.message || '同行者名单暂时没有加载成功。'
  } finally {
    loading.value = false
  }
}

function initial(name) {
  return String(name || '?').trim().slice(0, 1).toUpperCase()
}

async function createCompanion() {
  const name = newName.value.trim()
  if (!name) {
    addError.value = '请输入同行者名称。'
    return
  }
  adding.value = true
  addError.value = ''
  try {
    const created = await createTripCompanion(props.tripId, name)
    companions.value = [...companions.value, created]
    emit('update:modelValue', [...new Set([...props.modelValue, created.id])])
    newName.value = ''
    addOpen.value = false
  } catch (err) {
    addError.value = err.message || '同行者暂时没有添加成功。'
  } finally {
    adding.value = false
  }
}

watch(() => props.tripId, load, { immediate: true })
</script>

<template>
  <section class="companion-selector">
    <div v-if="showHeading" class="companion-selector-head">
      <div>
        <strong>和谁一起</strong>
        <p>只记录这一刻在场的人，留空也可以。</p>
      </div>
      <RouterLink :to="`/trips/${tripId}/companions`">管理名单</RouterLink>
    </div>
    <p v-if="loading" class="companion-selector-status">正在读取同行者...</p>
    <p v-else-if="error" class="companion-selector-error">{{ error }}</p>
    <div v-else class="companion-options">
      <button
        v-for="companion in selectable"
        :key="companion.id"
        type="button"
        :class="['companion-option', { selected: modelValue.includes(companion.id) }]"
        :aria-pressed="modelValue.includes(companion.id)"
        @click="toggle(companion.id)"
      >
        <span class="companion-avatar" aria-hidden="true">{{ modelValue.includes(companion.id) ? '✓' : initial(companion.name) }}</span>
        <small>{{ companion.name }}</small>
      </button>
      <button type="button" class="companion-option companion-add" aria-label="添加同行者" @click="addOpen = true">
        <span class="companion-avatar" aria-hidden="true">＋</span>
        <small>添加</small>
      </button>
    </div>

    <div v-if="addOpen" class="companion-add-backdrop" role="presentation" @click.self="addOpen = false">
      <form class="companion-add-dialog" role="dialog" aria-modal="true" aria-labelledby="companion-add-title" @submit.prevent="createCompanion">
        <h3 id="companion-add-title">添加同行者</h3>
        <p>这里只记录旅行中的人物称呼，不会创建账号。</p>
        <label for="memory-companion-name">名称</label>
        <input id="memory-companion-name" v-model="newName" maxlength="50" autocomplete="off" placeholder="例如：小雨" />
        <p v-if="addError" class="companion-selector-error" role="alert">{{ addError }}</p>
        <div class="companion-add-actions">
          <button type="button" :disabled="adding" @click="addOpen = false">取消</button>
          <button type="submit" :disabled="adding">{{ adding ? '添加中…' : '添加并选中' }}</button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.companion-selector { display: grid; gap: 11px; min-width: 0; }
.companion-selector-head { display: flex; align-items: start; justify-content: space-between; gap: 12px; }
.companion-selector-head strong { font-size: 15px; }
.companion-selector-head p { margin: 5px 0 0; color: var(--tm-text-muted); font-size: 12px; }
.companion-selector-head a { flex: 0 0 auto; color: var(--tm-accent); font-size: 12px; }
.companion-options { display: flex; gap: 12px; overflow-x: auto; padding: 3px 2px 8px; }
.companion-option { display: grid; flex: 0 0 62px; justify-items: center; gap: 6px; padding: 0; border: 0; background: transparent; color: var(--tm-text-muted); }
.companion-avatar { display: grid; width: 48px; height: 48px; place-items: center; border: 1px solid var(--tm-border); border-radius: 50%; background: var(--tm-surface); color: #765846; font-size: 15px; font-weight: 700; }
.companion-option small { width: 100%; overflow: hidden; color: inherit; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.companion-option.selected .companion-avatar { border: 2px solid var(--tm-accent); background: var(--tm-accent-soft); color: var(--tm-accent); box-shadow: 0 0 0 3px rgba(180,87,49,.09); }
.companion-add .companion-avatar { border-style: dashed; font-size: 22px; font-weight: 400; }
.companion-selector-status, .companion-selector-error { margin: 0; font-size: 12px; }
.companion-selector-status { color: var(--tm-text-muted); }
.companion-selector-error { color: #c0402c; }
.companion-add-backdrop { position: fixed; z-index: 70; inset: 0; display: grid; place-items: center; padding: 20px; background: rgba(42,34,29,.34); }
.companion-add-dialog { display: grid; gap: 11px; width: min(360px, 100%); padding: 20px; border: 1px solid var(--tm-border); border-radius: 12px; background: #fffdf9; box-shadow: 0 22px 60px rgba(44,33,26,.2); }
.companion-add-dialog h3, .companion-add-dialog p { margin: 0; }
.companion-add-dialog p { color: var(--tm-text-muted); font-size: 12px; line-height: 1.6; }
.companion-add-dialog label { font-size: 13px; font-weight: 700; }
.companion-add-dialog input { height: 44px; padding: 0 11px; border: 1px solid var(--tm-border); border-radius: 8px; font: inherit; }
.companion-add-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.companion-add-actions button { min-height: 42px; border: 1px solid var(--tm-border); border-radius: 8px; background: #fff; color: var(--tm-text); }
.companion-add-actions button[type="submit"] { border-color: var(--tm-accent); background: var(--tm-accent); color: #fff; }
</style>
