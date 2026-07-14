<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { getTripCompanions } from '../api/companion'

const props = defineProps({
  tripId: { type: [String, Number], required: true },
  modelValue: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue'])
const companions = ref([])
const loading = ref(false)
const error = ref('')
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

watch(() => props.tripId, load, { immediate: true })
</script>

<template>
  <section class="companion-selector">
    <div class="companion-selector-head">
      <div>
        <strong>和谁一起</strong>
        <p>只记录这一刻在场的人，留空也可以。</p>
      </div>
      <RouterLink :to="`/trips/${tripId}/companions`">管理名单</RouterLink>
    </div>
    <p v-if="loading" class="companion-selector-status">正在读取同行者...</p>
    <p v-else-if="error" class="companion-selector-error">{{ error }}</p>
    <div v-else-if="selectable.length" class="companion-options">
      <button
        v-for="companion in selectable"
        :key="companion.id"
        type="button"
        :class="['companion-option', { selected: modelValue.includes(companion.id) }]"
        :aria-pressed="modelValue.includes(companion.id)"
        @click="toggle(companion.id)"
      >
        <span aria-hidden="true">{{ modelValue.includes(companion.id) ? '✓' : '' }}</span>
        {{ companion.name }}
      </button>
    </div>
    <p v-else class="companion-selector-status">同行者名单还是空的，可以先保存这段记忆。</p>
  </section>
</template>

<style scoped>
.companion-selector { display: grid; gap: 11px; padding: 15px; border: 1px solid var(--tm-border); border-radius: 14px; background: rgba(251, 249, 244, .82); }
.companion-selector-head { display: flex; align-items: start; justify-content: space-between; gap: 12px; }
.companion-selector-head strong { font-size: 15px; }
.companion-selector-head p { margin: 5px 0 0; color: var(--tm-text-muted); font-size: 12px; }
.companion-selector-head a { flex: 0 0 auto; color: var(--tm-accent); font-size: 12px; }
.companion-options { display: flex; flex-wrap: wrap; gap: 8px; }
.companion-option { display: inline-flex; align-items: center; gap: 6px; padding: 7px 11px; border: 1px solid var(--tm-border); border-radius: 999px; background: var(--tm-surface); color: var(--tm-text-muted); font-size: 13px; }
.companion-option span { display: grid; width: 16px; height: 16px; place-items: center; border: 1px solid var(--tm-border); border-radius: 50%; font-size: 10px; }
.companion-option.selected { border-color: var(--tm-accent); background: var(--tm-accent-soft); color: var(--tm-accent); }
.companion-option.selected span { border-color: var(--tm-accent); background: var(--tm-accent); color: #fff; }
.companion-selector-status, .companion-selector-error { margin: 0; font-size: 12px; }
.companion-selector-status { color: var(--tm-text-muted); }
.companion-selector-error { color: #c0402c; }
</style>
