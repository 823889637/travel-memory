<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import MemoryPhotoGallery from './MemoryPhotoGallery.vue'

const props = defineProps({
  photos: { type: Array, default: () => [] },
  selectedIndex: { type: Number, default: 0 },
  maxPhotos: { type: Number, default: 6 },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['add', 'select', 'remove', 'reorder', 'set-primary', 'retry'])
const gallery = ref(null)
const mainOrientation = ref('landscape')
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

function detectOrientation(event) {
  const ratio = event.target.naturalWidth / event.target.naturalHeight
  mainOrientation.value = ratio < 0.8 ? 'portrait' : (ratio > 1.2 ? 'landscape' : 'square')
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

onBeforeUnmount(clearLongPress)
</script>

<template>
  <div class="memory-photo-editor">
    <div v-if="selectedPhoto" :class="['memory-photo-main', `is-${mainOrientation}`]">
      <span
        v-if="mainOrientation === 'portrait'"
        class="memory-photo-main-backdrop"
        :style="{ backgroundImage: `url(${photoSource(selectedPhoto)})` }"
        aria-hidden="true"
      ></span>
      <button type="button" class="memory-photo-main-view" aria-label="打开照片浏览" @click="openGallery">
        <span v-if="isFailed(selectedPhoto, selectedIndex)" class="memory-photo-load-error">照片暂时无法显示</span>
        <img
          v-else
          :src="photoSource(selectedPhoto)"
          :alt="`当前预览：第 ${selectedIndex + 1} 张照片`"
          @load="detectOrientation"
          @error="markFailed(selectedPhoto, selectedIndex)"
        />
      </button>
      <button type="button" class="memory-photo-primary" :disabled="disabled || selectedIndex === 0" @click="requestPrimary">
        {{ selectedIndex === 0 ? '当前主图' : '设为主图' }}
      </button>
      <span v-if="selectedPhoto.uploading" class="memory-photo-uploading">上传中…</span>
    </div>

    <button v-else type="button" class="memory-photo-empty" :disabled="disabled" @click="emit('add')">
      <span aria-hidden="true">＋</span>
      <strong>添加照片</strong>
      <small>最多 {{ maxPhotos }} 张，第一张作为主图</small>
    </button>

    <button
      v-if="selectedPhoto && photos.length < maxPhotos"
      type="button"
      class="memory-photo-add"
      :disabled="disabled"
      @click="emit('add')"
    >
      <span aria-hidden="true">＋</span>
      添加照片
    </button>

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
        </button>
        <button type="button" class="memory-photo-delete" :disabled="disabled" :aria-label="`删除第 ${index + 1} 张照片`" @click="requestRemove(index)">×</button>
        <button v-if="photo.error" type="button" class="memory-photo-retry" :disabled="disabled" @click="emit('retry', photo)">重试</button>
      </div>
    </div>

    <p v-if="photos.length" class="memory-photo-sort-hint">长按拖动可调整顺序，第一张将作为主图。</p>
    <p v-if="photos.length >= maxPhotos" class="memory-photo-limit">已达到 {{ maxPhotos }} 张上限</p>

    <MemoryPhotoGallery ref="gallery" :photos="galleryPhotos" :show-preview="false" alt="记忆照片" />
  </div>
</template>

<style scoped>
.memory-photo-editor { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) 122px; gap: 10px; min-width: 0; }
.memory-photo-main { position: relative; min-width: 0; height: 250px; overflow: hidden; border-radius: 10px; background: #eee7de; }
.memory-photo-main-view { position: relative; z-index: 1; display: grid; width: 100%; height: 100%; place-items: center; overflow: hidden; padding: 0; border: 0; background: transparent; }
.memory-photo-main img { position: relative; z-index: 1; display: block; width: 100%; height: 100%; object-fit: cover; }
.memory-photo-main.is-portrait img { object-fit: contain; }
.memory-photo-main-backdrop { position: absolute; inset: -20px; background-position: center; background-size: cover; filter: blur(22px) brightness(.72); opacity: .2; transform: scale(1.08); }
.memory-photo-primary, .memory-photo-uploading { position: absolute; z-index: 2; top: 10px; padding: 6px 9px; border: 1px solid rgba(255,255,255,.72); border-radius: 6px; background: rgba(255,253,249,.9); color: #99502f; font-size: 12px; }
.memory-photo-primary { left: 10px; }
.memory-photo-primary:disabled { color: #6f5546; opacity: 1; }
.memory-photo-uploading { right: 10px; }
.memory-photo-add, .memory-photo-empty { display: grid; min-width: 0; place-items: center; align-content: center; gap: 7px; border: 1px dashed #d9b9a5; border-radius: 10px; background: #fffdfa; color: #9b5a3a; }
.memory-photo-add { min-height: 122px; font-size: 13px; }
.memory-photo-add span, .memory-photo-empty > span { font-size: 30px; font-weight: 300; }
.memory-photo-empty { grid-column: 1 / -1; min-height: 210px; }
.memory-photo-empty strong { color: #4b382d; font-size: 16px; }
.memory-photo-empty small { color: #8b7b70; }
.memory-photo-thumbs { grid-column: 1 / -1; display: flex; gap: 8px; overflow-x: auto; padding: 3px 2px 7px; scrollbar-width: thin; }
.memory-photo-thumb-wrap { position: relative; flex: 0 0 68px; }
.memory-photo-thumb { position: relative; display: block; width: 68px; height: 68px; overflow: hidden; padding: 0; border: 2px solid transparent; border-radius: 8px; background: #eee7df; touch-action: pan-x; }
.memory-photo-thumb.selected { border-color: #b65b36; box-shadow: 0 0 0 2px rgba(182,91,54,.12); }
.memory-photo-thumb.dragging { opacity: .52; }
.memory-photo-thumb img { display: block; width: 100%; height: 100%; object-fit: cover; }
.memory-photo-order { position: absolute; z-index: 2; top: 3px; left: 3px; display: grid; width: 18px; height: 18px; place-items: center; border-radius: 50%; background: rgba(255,253,249,.92); color: #7e5c49; font-size: 10px; font-weight: 700; }
.memory-photo-thumb-wrap:first-child .memory-photo-order { background: #b65b36; color: #fff; }
.memory-photo-delete { position: absolute; z-index: 3; top: -5px; right: -5px; display: grid; width: 20px; height: 20px; place-items: center; padding: 0; border: 1px solid #fff; border-radius: 50%; background: #5a4b42; color: #fff; font-size: 14px; line-height: 1; }
.memory-photo-retry { width: 100%; margin-top: 4px; padding: 3px; border: 1px solid #dca88e; border-radius: 5px; background: #fff; color: #ad4f2c; font-size: 10px; }
.memory-photo-sort-hint, .memory-photo-limit { grid-column: 1 / -1; margin: 0; color: #8a7c71; font-size: 12px; }
.memory-photo-limit { color: #a75636; }
.memory-photo-load-error { display: grid; height: 100%; place-items: center; padding: 8px; color: #9c5b42; font-size: 11px; }
@media (max-width: 640px) {
  .memory-photo-editor { grid-template-columns: minmax(0, 1fr) 92px; }
  .memory-photo-main { height: 205px; }
  .memory-photo-add { min-height: 92px; }
  .memory-photo-thumb-wrap { flex-basis: 62px; }
  .memory-photo-thumb { width: 62px; height: 62px; }
}
</style>
