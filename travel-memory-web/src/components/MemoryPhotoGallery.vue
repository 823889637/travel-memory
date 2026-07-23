<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  photos: { type: Array, default: () => [] },
  fallbackUrl: { type: String, default: '' },
  fit: { type: String, default: 'cover' },
  countLabel: { type: String, default: 'photos' },
  maxHeight: { type: String, default: '' },
  layout: { type: String, default: '' },
  backdrop: { type: Boolean, default: false },
  backdropTallOnly: { type: Boolean, default: true },
  backdropPortraitOnly: { type: Boolean, default: false },
  backdropDim: { type: Boolean, default: false },
  alt: { type: String, default: 'Memory photo' },
  showPreview: { type: Boolean, default: true },
})

const open = ref(false)
const selectedIndex = ref(0)
const primaryOrientation = ref('landscape')
const primaryRatio = ref(1)
const failedPhotoUrls = ref(new Set())
const touchStart = ref(null)
const items = computed(() => {
  const normalizeItems = (source) => {
    const seenUrls = new Set()
    return source
      .map((photo, index) => ({ ...photo, _sourceIndex: index }))
      .filter((photo) => {
        const url = String(photo?.photoUrl || '').trim()
        if (!url || seenUrls.has(url)) return false
        seenUrls.add(url)
        photo.photoUrl = url
        return true
      })
      .sort((left, right) => {
        const leftOrder = left.sortOrder == null ? Number.NaN : Number(left.sortOrder)
        const rightOrder = right.sortOrder == null ? Number.NaN : Number(right.sortOrder)
        const normalizedLeft = Number.isFinite(leftOrder) ? leftOrder : 1_000_000 + left._sourceIndex
        const normalizedRight = Number.isFinite(rightOrder) ? rightOrder : 1_000_000 + right._sourceIndex
        return normalizedLeft - normalizedRight || left._sourceIndex - right._sourceIndex
      })
      .slice(0, 6)
  }
  const normalizedPhotos = normalizeItems(props.photos)
  return normalizedPhotos.length
    ? normalizedPhotos
    : normalizeItems(props.fallbackUrl ? [{ photoUrl: props.fallbackUrl }] : [])
})
const selected = computed(() => items.value[selectedIndex.value] || items.value[0])
const previewSelected = computed(() => props.layout === 'detail' ? selected.value : items.value[0])
const previewItems = computed(() => items.value.slice(0, 4))
const hiddenPhotoCount = computed(() => Math.max(0, items.value.length - previewItems.value.length))
const journeyPreviewItems = computed(() => items.value.slice(0, 3))
const hiddenJourneyPhotoCount = computed(() => Math.max(0, items.value.length - journeyPreviewItems.value.length))
const useBackdrop = computed(() => (
  props.backdrop && (
    props.backdropPortraitOnly
      ? primaryOrientation.value === 'portrait'
      : (!props.backdropTallOnly || primaryRatio.value < 1.15)
  )
))
watch(items, () => {
  if (selectedIndex.value >= items.value.length) selectedIndex.value = 0
  if (!items.value.length) open.value = false
  failedPhotoUrls.value = new Set()
  primaryOrientation.value = 'landscape'
  primaryRatio.value = 1
})
function show(index = 0) { selectedIndex.value = index; open.value = true }
function selectPreview(index) {
  selectedIndex.value = index
  primaryOrientation.value = 'landscape'
  primaryRatio.value = 1
}
defineExpose({ open: show })
function closeGallery() { open.value = false }
function showPrevious() {
  selectedIndex.value = (selectedIndex.value - 1 + items.value.length) % items.value.length
}
function showNext() {
  selectedIndex.value = (selectedIndex.value + 1) % items.value.length
}
function handleTouchStart(event) {
  const touch = event.changedTouches?.[0]
  touchStart.value = touch ? { x: touch.clientX, y: touch.clientY } : null
}
function handleTouchEnd(event) {
  const touch = event.changedTouches?.[0]
  const start = touchStart.value
  touchStart.value = null
  if (!touch || !start || items.value.length < 2) return

  const deltaX = touch.clientX - start.x
  const deltaY = touch.clientY - start.y
  if (Math.abs(deltaX) < 48 || Math.abs(deltaX) <= Math.abs(deltaY)) return
  if (deltaX > 0) showPrevious()
  else showNext()
}
function handleGalleryKeydown(event) {
  if (!open.value) return
  if (event.key === 'Escape') closeGallery()
  if (event.key === 'ArrowLeft' && items.value.length > 1) showPrevious()
  if (event.key === 'ArrowRight' && items.value.length > 1) showNext()
}
function markPhotoFailed(photo) {
  const nextFailedUrls = new Set(failedPhotoUrls.value)
  nextFailedUrls.add(photo.photoUrl)
  failedPhotoUrls.value = nextFailedUrls
}
function hasPhotoFailed(photo) {
  return failedPhotoUrls.value.has(photo?.photoUrl)
}
function detectPrimaryOrientation(event) {
  const { naturalWidth: width, naturalHeight: height } = event.target
  if (!width || !height) return
  const ratio = width / height
  primaryRatio.value = ratio
  primaryOrientation.value = ratio > 1.2 ? 'landscape' : (ratio < 0.8 ? 'portrait' : 'square')
}

watch(open, (isOpen) => {
  document.body.style.overflow = isOpen ? 'hidden' : ''
  if (isOpen) window.addEventListener('keydown', handleGalleryKeydown)
  else window.removeEventListener('keydown', handleGalleryKeydown)
})

onBeforeUnmount(() => {
  open.value = false
  selectedIndex.value = 0
  touchStart.value = null
  failedPhotoUrls.value = new Set()
  document.body.style.overflow = ''
  window.removeEventListener('keydown', handleGalleryKeydown)
})
</script>

<template>
  <div v-if="items.length" :class="['gallery', `gallery-orientation-${primaryOrientation}`, { [`gallery-layout-${layout}`]: layout }]" :style="maxHeight ? { '--gallery-main-max-height': maxHeight } : null" @click.stop>
    <div v-if="showPreview && layout === 'timeline'" :class="['gallery-timeline-preview', `gallery-timeline-count-${previewItems.length}`]">
      <button
        v-for="(photo, index) in previewItems"
        :key="photo.id || photo.photoUrl"
        type="button"
        :class="['gallery-preview-tile', `gallery-preview-tile-${index + 1}`]"
        :aria-label="`查看第 ${index + 1} 张照片`"
        @click="show(index)"
      >
        <span v-if="hasPhotoFailed(photo)" class="gallery-photo-error">照片暂时无法显示</span>
        <img v-else :src="photo.photoUrl" :alt="`${alt} ${index + 1}`" loading="lazy" @error="markPhotoFailed(photo)" />
        <span v-if="index === previewItems.length - 1 && hiddenPhotoCount" class="gallery-more-overlay">+{{ hiddenPhotoCount }}</span>
      </button>
      <span v-if="items.length > 1 && !hiddenPhotoCount" class="gallery-total-count">{{ items.length }} 张</span>
    </div>

    <div v-else-if="showPreview && layout === 'journey'" :class="['gallery-journey-preview', `gallery-journey-count-${journeyPreviewItems.length}`]">
      <button
        v-for="(photo, index) in journeyPreviewItems"
        :key="photo.id || photo.photoUrl"
        type="button"
        :class="['gallery-journey-tile', `gallery-journey-tile-${index + 1}`]"
        :aria-label="`查看第 ${index + 1} 张照片`"
        @click="show(index)"
      >
        <span v-if="hasPhotoFailed(photo)" class="gallery-photo-error">照片暂时无法显示</span>
        <img
          v-else
          :src="photo.photoUrl"
          :alt="`${alt} ${index + 1}`"
          loading="lazy"
          @load="index === 0 && detectPrimaryOrientation($event)"
          @error="markPhotoFailed(photo)"
        />
        <span v-if="index === journeyPreviewItems.length - 1 && hiddenJourneyPhotoCount" class="gallery-more-overlay">+{{ hiddenJourneyPhotoCount }}</span>
      </button>
      <span v-if="items.length > 1 && !hiddenJourneyPhotoCount" class="gallery-total-count">{{ items.length }} 张</span>
    </div>

    <button v-else-if="showPreview && layout === 'favorite'" type="button" class="gallery-favorite-preview" @click="show(0)">
      <span v-if="hasPhotoFailed(items[0])" class="gallery-photo-error">照片暂时无法显示</span>
      <img v-else :src="items[0].photoUrl" :alt="alt" loading="lazy" @load="detectPrimaryOrientation" @error="markPhotoFailed(items[0])" />
      <span v-if="items.length > 1" class="gallery-total-count">共 {{ items.length }} 张</span>
    </button>

    <template v-else-if="showPreview">
      <button type="button" :class="['gallery-main', { 'gallery-main-with-backdrop': useBackdrop, 'gallery-main-dim-backdrop': useBackdrop && backdropDim }]" @click="show(selectedIndex)">
        <span v-if="useBackdrop" class="gallery-backdrop" :style="{ backgroundImage: `url(${previewSelected.photoUrl})` }"></span>
        <span v-if="hasPhotoFailed(previewSelected)" class="gallery-photo-error">照片暂时无法显示</span>
        <img v-else :class="`gallery-image-${fit}`" :src="previewSelected.photoUrl" :alt="alt" loading="lazy" @load="detectPrimaryOrientation" @error="markPhotoFailed(previewSelected)" />
        <span v-if="items.length > 1" class="gallery-count">{{ layout === 'detail' ? `${selectedIndex + 1} / ${items.length}` : `${items.length} ${countLabel}` }}</span>
      </button>
      <div v-if="items.length > 1" class="gallery-thumbs">
        <button
          v-for="(photo, index) in (layout === 'detail' ? items : items.slice(1, 4))"
          :key="photo.id || photo.photoUrl"
          type="button"
          :class="{ active: layout === 'detail' && index === selectedIndex }"
          :aria-current="layout === 'detail' && index === selectedIndex ? 'true' : undefined"
          :aria-label="`查看第 ${layout === 'detail' ? index + 1 : index + 2} 张照片`"
          @click="layout === 'detail' ? selectPreview(index) : show(index + 1)"
        >
          <span v-if="hasPhotoFailed(photo)" class="gallery-photo-error">无法显示</span>
          <img v-else :class="`gallery-thumb-image-${fit}`" :src="photo.photoUrl" :alt="`${alt} ${layout === 'detail' ? index + 1 : index + 2}`" loading="lazy" @error="markPhotoFailed(photo)" />
        </button>
      </div>
    </template>

    <div
      v-if="open"
      class="gallery-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="照片浏览"
      @click.self="closeGallery"
      @touchstart.passive="handleTouchStart"
      @touchend="handleTouchEnd"
    >
      <div class="gallery-dialog-bar">
        <button type="button" class="gallery-close" aria-label="关闭照片浏览" @click="closeGallery">×</button>
        <span>{{ selectedIndex + 1 }} / {{ items.length }}</span>
        <span class="gallery-dialog-bar-spacer" aria-hidden="true"></span>
      </div>
      <button v-if="items.length > 1" type="button" class="gallery-arrow gallery-arrow-previous" aria-label="上一张照片" @click="showPrevious">‹</button>
      <span v-if="hasPhotoFailed(selected)" class="gallery-full-error">这张照片暂时无法显示</span>
      <img v-else :src="selected.photoUrl" :alt="`${alt} ${selectedIndex + 1}`" class="gallery-full" @error="markPhotoFailed(selected)" />
      <button v-if="items.length > 1" type="button" class="gallery-arrow gallery-arrow-next" aria-label="下一张照片" @click="showNext">›</button>
      <div v-if="items.length > 1" class="gallery-dialog-thumbs">
        <button v-for="(photo, index) in items" :key="photo.id || photo.photoUrl" type="button" :class="{ active: index === selectedIndex }" @click="selectedIndex = index">
          <span v-if="hasPhotoFailed(photo)" class="gallery-photo-error">无法显示</span>
          <img v-else :src="photo.photoUrl" :alt="`${alt} ${index + 1}`" @error="markPhotoFailed(photo)" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gallery { display: grid; gap: 6px; min-width: 0; }
.gallery-main { position: relative; display: block; overflow: hidden; width: 100%; padding: 0; background: #eee3d6; }
.gallery-main img { display: block; width: 100%; height: 100%; min-height: 140px; max-height: 340px; object-fit: cover; }
.gallery-main img.gallery-image-contain { height: auto; min-height: 0; max-height: var(--gallery-main-max-height, 260px); object-fit: contain; }
.gallery-main.gallery-main-with-backdrop { display: grid; place-items: center; height: var(--gallery-main-max-height, 260px); background: #e8dfd4; }
.gallery-backdrop { position: absolute; inset: -28px; background-position: center; background-size: cover; filter: blur(24px) saturate(0.9); opacity: 0.32; transform: scale(1.08); }
.gallery-main.gallery-main-dim-backdrop .gallery-backdrop { filter: blur(26px) brightness(0.68) saturate(0.78); opacity: 0.22; }
.gallery-main.gallery-main-with-backdrop img.gallery-image-contain { position: relative; z-index: 1; width: 100%; height: 100%; min-height: 0; max-height: none; object-fit: contain; }
.gallery-layout-journey .gallery-main { background: transparent; box-shadow: none; }
.gallery-layout-journey.gallery-orientation-landscape .gallery-image-contain { width: 100%; height: auto; max-height: min(58vh, 520px); border-radius: 12px; box-shadow: 0 12px 26px rgba(43, 38, 34, 0.1); }
.gallery-layout-journey.gallery-orientation-portrait .gallery-main { display: flex; justify-content: center; }
.gallery-layout-journey.gallery-orientation-portrait .gallery-image-contain { width: min(420px, 100%); height: auto; max-height: 68vh; border-radius: 12px; box-shadow: 0 10px 22px rgba(43, 38, 34, 0.1); }
.gallery-layout-journey.gallery-orientation-square .gallery-main { display: flex; justify-content: center; }
.gallery-layout-journey.gallery-orientation-square .gallery-image-contain { width: 76%; height: auto; max-height: min(62vh, 480px); border-radius: 12px; box-shadow: 0 10px 22px rgba(43, 38, 34, 0.08); }
.gallery-layout-journey { display: block; width: 100%; }
.gallery-journey-preview { position: relative; display: grid; width: 100%; gap: 10px; overflow: hidden; border-radius: 8px; }
.gallery-journey-count-1 { display: block; }
.gallery-journey-count-2 { grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr); height: min(42vw, 500px); }
.gallery-journey-count-3 { grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr); grid-template-rows: repeat(2, minmax(0, 1fr)); height: min(44vw, 520px); }
.gallery-journey-count-3 .gallery-journey-tile-1 { grid-row: 1 / span 2; }
.gallery-journey-tile { position: relative; display: block; min-width: 0; min-height: 0; overflow: hidden; padding: 0; border: 0; border-radius: 8px; background: #eee7de; }
.gallery-journey-tile img { display: block; width: 100%; height: 100%; object-fit: cover; }
.gallery-journey-count-1 .gallery-journey-tile { display: grid; max-height: 580px; place-items: center; background: transparent; }
.gallery-journey-count-1 .gallery-journey-tile img { width: auto; max-width: 100%; height: auto; max-height: 580px; object-fit: contain; }
.gallery-layout-journey.gallery-orientation-landscape .gallery-journey-count-1 .gallery-journey-tile img { width: 100%; }
.gallery-layout-timeline { display: block; width: 100%; height: auto; aspect-ratio: 16 / 9; }
.gallery-timeline-preview { position: relative; display: grid; width: 100%; height: 100%; gap: 3px; overflow: hidden; border-radius: 0 8px 8px 0; background: #eee7de; }
.gallery-timeline-count-1 { grid-template-columns: 1fr; }
.gallery-timeline-count-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.gallery-timeline-count-3 { grid-template-columns: 2fr 1fr; grid-template-rows: repeat(2, minmax(0, 1fr)); }
.gallery-timeline-count-3 .gallery-preview-tile-1 { grid-row: 1 / span 2; }
.gallery-timeline-count-4 { grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-rows: repeat(2, minmax(0, 1fr)); }
.gallery-preview-tile { position: relative; display: block; min-width: 0; min-height: 0; overflow: hidden; padding: 0; border: 0; border-radius: 0; background: #eee7de; }
.gallery-preview-tile img, .gallery-favorite-preview img { display: block; width: 100%; height: 100%; object-fit: cover; }
.gallery-more-overlay { position: absolute; inset: 0; display: grid; place-items: center; background: rgba(25, 22, 19, .54); color: #fff; font-size: 25px; font-weight: 800; }
.gallery-total-count { position: absolute; right: 8px; bottom: 8px; z-index: 2; padding: 4px 7px; border-radius: 4px; background: rgba(30, 26, 22, .72); color: #fff; font-size: 12px; line-height: 1; }
.gallery-layout-favorite { display: block; width: 100%; }
.gallery-favorite-preview { position: relative; display: block; overflow: hidden; width: 100%; height: 124px; padding: 0; border: 0; border-radius: 0 8px 8px 0; background: #eee7de; }
.gallery-layout-favorite.map-memory-photo .gallery-favorite-preview { height: 100%; }
.gallery-photo-error { display: grid; width: 100%; height: 100%; min-height: 52px; place-items: center; padding: 8px; background: #eee7de; color: #887b70; font-size: 11px; line-height: 1.4; text-align: center; }
.gallery-layout-recap .gallery-main { height: 145px; border-radius: 7px; background: #f1ebe2; }
.gallery-layout-recap .gallery-main img { width: 100%; height: 100%; min-height: 0; max-height: none; object-fit: cover; }
.gallery-layout-recap .gallery-thumbs { display: none; }
.gallery-layout-detail .gallery-main { display: grid; place-items: center; min-height: 360px; border-radius: 8px; background: #eee8df; }
.gallery-layout-detail .gallery-main img.gallery-image-contain { width: 100%; height: auto; min-height: 0; max-height: var(--gallery-main-max-height, min(66vh, 620px)); object-fit: contain; }
.gallery-layout-detail.gallery-orientation-portrait .gallery-main img.gallery-image-contain { width: auto; max-width: 100%; }
.gallery-layout-detail .gallery-thumbs { overflow-x: auto; padding: 3px 2px; scrollbar-width: thin; }
.gallery-layout-detail .gallery-thumbs button { flex-basis: 62px; height: 62px; border: 2px solid transparent; opacity: .72; }
.gallery-layout-detail .gallery-thumbs button.active { border-color: var(--tm-accent); opacity: 1; }
.gallery-count { position: absolute; right: 8px; bottom: 8px; padding: 4px 7px; border-radius: 4px; background: rgba(30, 26, 22, .7); color: #fff; font-size: 12px; }
.gallery-thumbs { display: flex; gap: 6px; overflow: hidden; }
.gallery-thumbs button, .gallery-dialog-thumbs button { flex: 0 0 52px; height: 52px; padding: 0; overflow: hidden; background: #eee3d6; }
.gallery-thumbs img, .gallery-dialog-thumbs img { width: 100%; height: 100%; object-fit: cover; }
.gallery-thumbs img.gallery-thumb-image-contain { object-fit: contain; }
.gallery-dialog { position: fixed; z-index: 1000; inset: 0; display: grid; grid-template-columns: minmax(48px, 1fr) minmax(0, 1080px) minmax(48px, 1fr); grid-template-rows: auto minmax(0, 1fr) auto; gap: 14px 18px; padding: 20px 24px 18px; background: rgba(19, 17, 15, .96); color: #fff; }
.gallery-dialog-bar { grid-column: 1 / -1; display: grid; min-height: 42px; grid-template-columns: 44px minmax(0, 1fr) 44px; align-items: center; color: rgba(255,255,255,.76); font-size: 13px; text-align: center; }
.gallery-dialog-bar-spacer { width: 44px; }
.gallery-full { grid-column: 2; grid-row: 2; align-self: center; justify-self: center; max-width: 100%; max-height: 76vh; object-fit: contain; }
.gallery-full-error { grid-column: 2; grid-row: 2; align-self: center; justify-self: center; padding: 22px; color: rgba(255,255,255,.74); text-align: center; }
.gallery-close { display: grid; width: 40px; height: 40px; place-items: center; padding: 0; border: 1px solid rgba(255,255,255,.28); border-radius: 50%; background: rgba(255,255,255,.08); color: #fff; font-size: 25px; line-height: 1; }
.gallery-arrow { align-self: center; display: grid; width: 44px; height: 56px; place-items: center; padding: 0; border: 1px solid rgba(255,255,255,.2); border-radius: 50%; background: rgba(255,255,255,.07); color: #fff; font-size: 38px; line-height: 1; }
.gallery-arrow-previous { grid-column: 1; grid-row: 2; justify-self: end; }
.gallery-arrow-next { grid-column: 3; grid-row: 2; justify-self: start; }
.gallery-dialog-thumbs { grid-column: 1 / -1; grid-row: 3; display: flex; gap: 8px; max-width: min(100%, 760px); justify-self: center; overflow-x: auto; padding: 3px; }
.gallery-dialog-thumbs button { border-radius: 5px; opacity: .56; }
.gallery-dialog-thumbs button.active { outline: 2px solid #fff; opacity: 1; }
@media (max-width: 640px) {
  .gallery-timeline-preview { border-radius: 0 7px 7px 0; }
  .gallery-favorite-preview { height: min(52vw, 210px); border-radius: 0; }
  .gallery-layout-journey.gallery-orientation-landscape .gallery-image-contain { max-height: min(58vh, 340px); }
  .gallery-layout-journey.gallery-orientation-portrait .gallery-image-contain { width: min(360px, 100%); max-height: 68vh; }
  .gallery-layout-journey.gallery-orientation-square .gallery-image-contain { width: 78%; max-height: min(58vh, 360px); }
  .gallery-journey-preview { gap: 5px; }
  .gallery-journey-count-2, .gallery-journey-count-3 { height: min(78vw, 360px); }
  .gallery-journey-count-1 .gallery-journey-tile, .gallery-journey-count-1 .gallery-journey-tile img { max-height: min(68vh, 520px); }
  .gallery-layout-recap .gallery-main { height: min(52vw, 210px); }
  .gallery-layout-detail .gallery-main { min-height: 0; border-radius: 0; }
  .gallery-layout-detail.gallery-orientation-portrait .gallery-main {
    height: min(58vh, 560px);
    min-height: min(58vh, 560px);
  }
  .gallery-layout-detail.gallery-orientation-portrait .gallery-main img.gallery-image-contain {
    width: 100%;
    height: 100%;
    max-height: none;
  }
  .gallery-layout-detail .gallery-thumbs button { flex-basis: 58px; height: 58px; }
  .gallery-dialog { grid-template-columns: 42px minmax(0, 1fr) 42px; gap: 10px 4px; padding: 12px 8px max(10px, env(safe-area-inset-bottom)); }
  .gallery-full { max-height: 72vh; }
  .gallery-arrow { width: 38px; height: 48px; border: 0; background: rgba(0,0,0,.22); font-size: 32px; }
  .gallery-dialog-thumbs button { flex-basis: 46px; height: 46px; }
}
</style>
