<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { Plus, Star, X } from '@lucide/vue'
import MemoryPhotoGallery from './MemoryPhotoGallery.vue'

const props = defineProps({
  photos: { type: Array, default: () => [] },
  selectedIndex: { type: Number, default: 0 },
  maxPhotos: { type: Number, default: 6 },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['select', 'remove', 'reorder', 'set-primary', 'retry', 'files-selected'])
const gallery = ref(null)
const failedKeys = ref(new Set())
const draggingIndex = ref(null)
let longPressTimer = null
let pointerId = null
let suppressClick = false

const selectedPhoto = computed(() => props.photos[props.selectedIndex] || props.photos[0] || null)
const galleryPhotos = computed(() => props.photos.map((photo, index) => ({
  id: photo.id || photo.key || index,
  photoUrl: photoSource(photo),
  sortOrder: index,
})).filter(photo => photo.photoUrl))

function photoSource(photo) {
  return photo?.preview || photo?.previewUrl || photo?.photoUrl || ''
}

function photoKey(photo, index) {
  return photo?.key || photo?.id || photoSource(photo) || index
}

function isFailed(photo, index) {
  return failedKeys.value.has(String(photoKey(photo, index)))
}

function markFailed(photo, index) {
  failedKeys.value = new Set(failedKeys.value).add(String(photoKey(photo, index)))
}

function uploadProgressValue(photo) {
  const progress = photo?.uploadProgress
  if (progress === null || progress === undefined || progress === '') return null
  const numericProgress = Number(progress)
  if (!Number.isFinite(numericProgress)) return null
  return Math.min(100, Math.max(0, Math.round(numericProgress)))
}

function hasUploadProgress(photo) {
  return uploadProgressValue(photo) !== null
}

function uploadProgressText(photo) {
  const progress = uploadProgressValue(photo)
  return progress === null ? '上传中…' : `上传中 ${progress}%`
}

function openGallery() {
  if (galleryPhotos.value.length) gallery.value?.open(Math.min(props.selectedIndex, galleryPhotos.value.length - 1))
}

function requestRemove(index) {
  if (props.disabled) return
  if (!window.confirm(`确定删除第 ${index + 1} 张照片吗？照片变更将在保存后生效。`)) return
  emit('remove', index)
}

function requestPrimary() {
  if (!props.disabled && props.selectedIndex > 0) emit('set-primary', props.selectedIndex)
}

function handleDragStart(event, index) {
  if (props.disabled) return event.preventDefault()
  draggingIndex.value = index
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(index))
}

function handleDrop(event, targetIndex) {
  event.preventDefault()
  const sourceIndex = draggingIndex.value ?? Number(event.dataTransfer.getData('text/plain'))
  if (Number.isInteger(sourceIndex) && sourceIndex !== targetIndex) emit('reorder', sourceIndex, targetIndex)
  draggingIndex.value = null
}

function beginLongPress(event, index) {
  if (props.disabled || event.pointerType === 'mouse') return
  clearLongPress()
  pointerId = event.pointerId
  const target = event.currentTarget
  longPressTimer = window.setTimeout(() => {
    draggingIndex.value = index
    suppressClick = true
    target?.setPointerCapture?.(pointerId)
  }, 420)
}

function moveLongPress(event) {
  if (draggingIndex.value == null || event.pointerId !== pointerId) return
  event.preventDefault()
  const target = document.elementFromPoint(event.clientX, event.clientY)?.closest?.('[data-photo-index]')
  const targetIndex = Number(target?.dataset?.photoIndex)
  if (Number.isInteger(targetIndex) && targetIndex !== draggingIndex.value) {
    emit('reorder', draggingIndex.value, targetIndex)
    draggingIndex.value = targetIndex
  }
}

function finishLongPress() {
  clearLongPress()
  draggingIndex.value = null
  pointerId = null
  window.setTimeout(() => { suppressClick = false }, 0)
}

function clearLongPress() {
  if (longPressTimer != null) window.clearTimeout(longPressTimer)
  longPressTimer = null
}

function selectThumbnail(index) {
  if (!suppressClick) emit('select', index)
}

function handleFileChange(event) {
  emit('files-selected', event)
}

onBeforeUnmount(clearLongPress)
</script>

<template>
  <div class="memory-photo-editor">
    <div v-if="selectedPhoto" class="memory-photo-main">
      <button type="button" class="memory-photo-main-view" aria-label="打开照片浏览" @click="openGallery">
        <span v-if="isFailed(selectedPhoto, selectedIndex)" class="memory-photo-load-error">照片暂时无法显示</span>
        <img
          v-else
          :src="photoSource(selectedPhoto)"
          :alt="`当前预览：第 ${selectedIndex + 1} 张照片`"
          @error="markFailed(selectedPhoto, selectedIndex)"
        />
      </button>
      <button type="button" class="memory-photo-primary" :disabled="disabled || selectedIndex === 0" @click="requestPrimary">
        <Star :size="14" :fill="selectedIndex === 0 ? 'currentColor' : 'none'" aria-hidden="true" />
        {{ selectedIndex === 0 ? '主图' : '设为主图' }}
      </button>
      <span v-if="selectedPhoto.uploading" class="memory-photo-uploading" role="status">{{ uploadProgressText(selectedPhoto) }}</span>
      <span v-if="selectedPhoto.uploading && hasUploadProgress(selectedPhoto)" class="memory-photo-progress" aria-hidden="true">
        <span :style="{ width: `${uploadProgressValue(selectedPhoto)}%` }"></span>
      </span>
    </div>

    <label v-else :class="['memory-photo-empty', { disabled }]" :aria-disabled="disabled">
      <input
        class="memory-photo-picker-input"
        type="file"
        accept="image/*"
        multiple
        :disabled="disabled"
        aria-label="添加照片"
        @change="handleFileChange"
      />
      <Plus :size="30" aria-hidden="true" />
      <strong>添加照片</strong>
      <small>最多 {{ maxPhotos }} 张，第一张作为主图</small>
    </label>

    <div v-if="photos.length" class="memory-photo-thumbs" aria-label="照片顺序">
      <div
        v-for="(photo, index) in photos"
        :key="photoKey(photo, index)"
        class="memory-photo-thumb-wrap"
        :data-photo-index="index"
      >
        <button
          type="button"
          draggable="true"
          :class="['memory-photo-thumb', { selected: index === selectedIndex, dragging: index === draggingIndex }]"
          :aria-label="`选择第 ${index + 1} 张照片${index === 0 ? '，当前主图' : ''}`"
          :aria-pressed="index === selectedIndex"
          @click="selectThumbnail(index)"
          @dragstart="handleDragStart($event, index)"
          @dragover.prevent
          @drop="handleDrop($event, index)"
          @dragend="draggingIndex = null"
          @pointerdown="beginLongPress($event, index)"
          @pointermove="moveLongPress"
          @pointerup="finishLongPress"
          @pointercancel="finishLongPress"
        >
          <span class="memory-photo-order">{{ index + 1 }}</span>
          <span v-if="isFailed(photo, index)" class="memory-photo-load-error">无法显示</span>
          <img v-else :src="photoSource(photo)" :alt="`第 ${index + 1} 张照片缩略图`" @error="markFailed(photo, index)" />
          <span v-if="photo.uploading" class="memory-photo-thumb-uploading" role="status">{{ uploadProgressValue(photo) === null ? '上传中' : `${uploadProgressValue(photo)}%` }}</span>
          <span v-if="photo.uploading && hasUploadProgress(photo)" class="memory-photo-progress memory-photo-thumb-progress" aria-hidden="true">
            <span :style="{ width: `${uploadProgressValue(photo)}%` }"></span>
          </span>
        </button>
        <button type="button" class="memory-photo-delete" :disabled="disabled" :aria-label="`删除第 ${index + 1} 张照片`" @click="requestRemove(index)">
          <X :size="12" aria-hidden="true" />
        </button>
        <button v-if="photo.error" type="button" class="memory-photo-retry" :disabled="disabled" @click="emit('retry', photo)">重试</button>
      </div>
      <label v-if="photos.length < maxPhotos" :class="['memory-photo-add', { disabled }]" :aria-disabled="disabled">
        <input
          class="memory-photo-picker-input"
          type="file"
          accept="image/*"
          multiple
          :disabled="disabled"
          aria-label="添加照片"
          @change="handleFileChange"
        />
        <Plus :size="28" aria-hidden="true" />
        <span>添加照片</span>
      </label>
    </div>

    <p v-if="photos.length > 1" class="memory-photo-sort-hint">长按照片可调整顺序，第一张作为主图。</p>
    <p v-if="photos.length >= maxPhotos" class="memory-photo-limit">已达到 {{ maxPhotos }} 张上限</p>

    <MemoryPhotoGallery ref="gallery" :photos="galleryPhotos" :show-preview="false" alt="记忆照片" />
  </div>
</template>

<style scoped>
.memory-photo-editor { position: relative; display: grid; gap: 10px; min-width: 0; }
.memory-photo-main { position: relative; min-width: 0; aspect-ratio: 16 / 9; overflow: hidden; border-radius: 16px; background: #eee7de; box-shadow: 0 9px 24px rgba(66, 47, 35, .08); }
.memory-photo-main-view { position: relative; z-index: 1; display: grid; width: 100%; height: 100%; place-items: center; overflow: hidden; padding: 0; border: 0; background: transparent; }
.memory-photo-main img { position: relative; z-index: 1; display: block; width: 100%; height: 100%; object-fit: cover; }
.memory-photo-primary, .memory-photo-uploading { position: absolute; z-index: 2; top: 12px; display: inline-flex; min-height: 36px; align-items: center; gap: 5px; padding: 0 11px; border: 1px solid rgba(255,255,255,.78); border-radius: 999px; background: rgba(255,253,249,.92); color: #99502f; font-size: 12px; box-shadow: 0 5px 16px rgba(55, 39, 29, .12); backdrop-filter: blur(8px); touch-action: manipulation; }
.memory-photo-primary { right: 12px; }
.memory-photo-primary:disabled { color: #8a664f; opacity: 1; }
.memory-photo-uploading { left: 12px; }
.memory-photo-progress { position: absolute; z-index: 3; right: 0; bottom: 0; left: 0; height: 4px; overflow: hidden; background: rgba(255, 253, 249, .55); }
.memory-photo-progress > span { display: block; height: 100%; background: #bf6038; transition: width .16s ease-out; }
.memory-photo-thumb-uploading { position: absolute; z-index: 4; right: 4px; bottom: 5px; padding: 3px 5px; border-radius: 5px; background: rgba(44, 31, 24, .7); color: #fff; font-size: 10px; line-height: 1; }
.memory-photo-thumb-progress { z-index: 4; height: 3px; }
.memory-photo-add, .memory-photo-empty { position: relative; display: grid; min-width: 0; place-items: center; align-content: center; gap: 7px; overflow: hidden; border: 1px dashed #d9b9a5; border-radius: 14px; background: rgba(255,253,249,.72); color: #9b5a3a; cursor: pointer; touch-action: manipulation; }
.memory-photo-add.disabled, .memory-photo-empty.disabled { cursor: not-allowed; opacity: .55; }
.memory-photo-picker-input { position: absolute; z-index: 2; inset: 0; display: block; width: 100%; height: 100%; margin: 0; cursor: pointer; opacity: 0; }
.memory-photo-picker-input:disabled { cursor: not-allowed; }
.memory-photo-add { flex: 0 0 calc((100% - 20px) / 3); min-height: 104px; padding: 0; font: inherit; font-size: 12px; }
.memory-photo-empty { min-height: 218px; }
.memory-photo-empty strong { color: #4b382d; font-size: 16px; }
.memory-photo-empty small { color: #8b7b70; }
.memory-photo-thumbs { display: flex; gap: 10px; overflow-x: auto; padding: 2px 2px 7px; scroll-snap-type: x proximity; scrollbar-width: thin; }
.memory-photo-thumb-wrap { position: relative; flex: 0 0 calc((100% - 20px) / 3); min-width: 0; scroll-snap-align: start; }
.memory-photo-thumb { position: relative; display: block; width: 100%; aspect-ratio: 4 / 3; overflow: hidden; padding: 0; border: 2px solid transparent; border-radius: 14px; background: #eee7df; touch-action: pan-x; }
.memory-photo-thumb.selected { border-color: #b65b36; box-shadow: 0 0 0 2px rgba(182,91,54,.12); }
.memory-photo-thumb.dragging { opacity: .52; }
.memory-photo-thumb img { display: block; width: 100%; height: 100%; object-fit: cover; }
.memory-photo-order { position: absolute; z-index: 2; top: 3px; left: 3px; display: grid; width: 18px; height: 18px; place-items: center; border-radius: 50%; background: rgba(255,253,249,.92); color: #7e5c49; font-size: 10px; font-weight: 700; }
.memory-photo-thumb-wrap:first-child .memory-photo-order { background: #b65b36; color: #fff; }
.memory-photo-delete { position: absolute; z-index: 3; top: 5px; right: 5px; display: grid; width: 28px; height: 28px; place-items: center; padding: 0; border: 1px solid rgba(255,255,255,.8); border-radius: 50%; background: rgba(62, 50, 43, .78); color: #fff; line-height: 1; opacity: 0; pointer-events: none; touch-action: manipulation; backdrop-filter: blur(5px); transition: opacity .15s ease; }
.memory-photo-thumb.selected + .memory-photo-delete,
.memory-photo-delete:focus-visible { opacity: 1; pointer-events: auto; }
.memory-photo-delete::before { content: ''; position: absolute; inset: -6px; }
@media (hover: hover) and (pointer: fine) {
  .memory-photo-thumb-wrap:hover > .memory-photo-delete { opacity: 1; pointer-events: auto; }
}
.memory-photo-retry { width: 100%; margin-top: 4px; padding: 3px; border: 1px solid #dca88e; border-radius: 5px; background: #fff; color: #ad4f2c; font-size: 10px; }
.memory-photo-sort-hint, .memory-photo-limit { margin: 0; color: #8a7c71; font-size: 12px; }
.memory-photo-limit { color: #a75636; }
.memory-photo-load-error { display: grid; height: 100%; place-items: center; padding: 8px; color: #9c5b42; font-size: 11px; }
@media (max-width: 640px) {
  .memory-photo-editor { gap: 9px; }
  .memory-photo-main {
    border-radius: 18px;
    box-shadow: 0 10px 25px rgba(66, 47, 35, .075);
  }
  .memory-photo-primary,
  .memory-photo-uploading {
    top: 10px;
    min-height: 36px;
    padding-inline: 10px;
  }
  .memory-photo-primary { right: 10px; }
  .memory-photo-uploading { left: 10px; }
  .memory-photo-thumbs {
    gap: 8px;
    padding: 2px 1px 6px;
    scrollbar-width: none;
  }
  .memory-photo-thumbs::-webkit-scrollbar { display: none; }
  .memory-photo-thumb-wrap,
  .memory-photo-add {
    flex: 0 0 calc((100% - 32px) / 5);
    min-width: 56px;
  }
  .memory-photo-add {
    min-height: 0;
    aspect-ratio: 4 / 3;
    gap: 2px;
    border-radius: 11px;
    font-size: 10px;
  }
  .memory-photo-add :deep(svg) { width: 20px; height: 20px; }
  .memory-photo-thumb { border-radius: 11px; }
  .memory-photo-delete {
    top: 3px;
    right: 3px;
    width: 28px;
    height: 28px;
  }
  .memory-photo-empty {
    min-height: 196px;
    border-radius: 18px;
  }
  .memory-photo-sort-hint, .memory-photo-limit { padding-inline: 2px; font-size: 11px; }
}
</style>
