<script setup>
import { ImagePlus, LoaderCircle } from '@lucide/vue'
import { ref } from 'vue'

const props = defineProps({
  previewUrl: { type: String, default: '' },
  locationLabel: { type: String, default: '' },
  uploading: { type: Boolean, default: false },
  alt: { type: String, default: '旅行封面预览' },
})
const emit = defineEmits(['select', 'error'])
const input = ref(null)

function choose(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) emit('select', file)
}
</script>

<template>
  <section class="trip-cover-form-field">
    <div v-if="previewUrl" class="trip-cover-form-preview">
      <img :src="previewUrl" :alt="alt" @error="emit('error')" />
      <span class="trip-cover-form-wash" aria-hidden="true"></span>
    </div>
    <div v-else class="trip-cover-form-placeholder">
      <strong>{{ locationLabel || '旅行封面' }}</strong>
      <small>选择一张真实照片，让这趟旅行更容易被认出。</small>
    </div>
    <button type="button" class="trip-cover-picker" :disabled="uploading" @click="input?.click()">
      <LoaderCircle v-if="uploading" :size="17" class="spin" aria-hidden="true" />
      <ImagePlus v-else :size="17" aria-hidden="true" />
      {{ uploading ? '上传中…' : (previewUrl ? '更换封面' : '添加封面') }}
    </button>
    <input ref="input" class="visually-hidden" type="file" accept="image/*" @change="choose" />
  </section>
</template>
