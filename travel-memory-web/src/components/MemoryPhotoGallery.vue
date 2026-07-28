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
  eagerPreview: { type: Boolean, default: false },
})
const emit = defineEmits(['journey-layout-change'])

const open = ref(false)
const selectedIndex = ref(0)
const primaryOrientation = ref('landscape')
const primaryRatio = ref(1)
const failedPhotoUrls = ref(new Set())
const touchStart = ref(null)
const isSwitching = ref(false)
const switchRequest = ref(0)
const photoOrientations = ref({})
const photoRatios = ref({})
const journeyInspectedPhotoUrls = ref(new Set())
const journeyPreviewReady = ref(false)
const imageLoadRequests = new Map()
const photoMetadataRequests = new Map()
let journeyLayoutRequest = 0
let previousBodyOverflow = ''
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
// Journey keeps the reading page quiet: only the first two sorted photos
// participate in the preview, while the fullscreen gallery still uses all photos.
const journeyPreviewItems = computed(() => items.value.slice(0, 2))
const journeyFirstLandscape = computed(() => (
  journeyPreviewItems.value.find(
    photo => photoOrientations.value[photo.photoUrl] === 'landscape',
  ) || null
))
const journeyFirstPortrait = computed(() => (
  journeyPreviewItems.value.find(
    photo => photoOrientations.value[photo.photoUrl] === 'portrait',
  ) || null
))
const journeyUsesEditorialLayout = computed(() => (
  Boolean(journeyFirstLandscape.value) && Boolean(journeyFirstPortrait.value)
))
const journeySupportingLandscape = computed(() => {
  if (!journeyUsesEditorialLayout.value) return null
  return journeyFirstLandscape.value
})
const journeyRightItems = computed(() => {
  if (!journeyPreviewItems.value.length) return []
  return journeyUsesEditorialLayout.value
    ? [journeyFirstPortrait.value]
    : [journeyPreviewItems.value[0]]
})
const journeyRightOrientation = computed(() => {
  const photoUrl = journeyRightItems.value[0]?.photoUrl
  return photoUrl ? (photoOrientations.value[photoUrl] || 'unknown') : 'unknown'
})
const journeyRightStyle = computed(() => {
  const photoUrl = journeyRightItems.value[0]?.photoUrl
  const ratio = Number(photoUrl ? photoRatios.value[photoUrl] : 0)
  return ratio > 0
    ? { '--journey-right-aspect-ratio': String(ratio) }
    : null
})
const journeySupportingStyle = computed(() => {
  const photoUrl = journeySupportingLandscape.value?.photoUrl
  const ratio = Number(photoUrl ? photoRatios.value[photoUrl] : 0)
  return ratio > 0
    ? { '--journey-supporting-aspect-ratio': String(ratio) }
    : null
})
const journeyDisplayedPhotoCount = computed(() => (
  journeyRightItems.value.length + (journeySupportingLandscape.value ? 1 : 0)
))
const journeyHasHiddenPhotos = computed(() => (
  items.value.length > journeyDisplayedPhotoCount.value
))
const journeyLayoutReady = computed(() => (
  props.layout !== 'journey'
  || journeyPreviewItems.value.every(photo => journeyInspectedPhotoUrls.value.has(photo.photoUrl))
))
const journeyDisplayVariant = computed(() => {
  if (!journeyLayoutReady.value) return 'resolving'
  return journeyUsesEditorialLayout.value ? 'editorial-landscape-support' : 'single-right'
})
watch(
  [journeyDisplayVariant, journeyFirstPortrait, journeySupportingLandscape],
  ([variant, featuredPortrait, supportingLandscape]) => {
    if (props.layout !== 'journey') return
    emit('journey-layout-change', {
      variant,
      featuredPortraitUrl: featuredPortrait?.photoUrl || '',
      supportingLandscapeUrl: supportingLandscape?.photoUrl || '',
    })
  },
  { immediate: true },
)
const useBackdrop = computed(() => (
  props.backdrop && (
    props.backdropPortraitOnly
      ? primaryOrientation.value === 'portrait'
      : (!props.backdropTallOnly || primaryRatio.value < 1.15)
  )
))
watch(() => `${props.layout}|${items.value.map(photo => photo.photoUrl).join('|')}`, () => {
  if (selectedIndex.value >= items.value.length) selectedIndex.value = 0
  if (!items.value.length) open.value = false
  failedPhotoUrls.value = new Set()
  photoOrientations.value = {}
  photoRatios.value = {}
  journeyInspectedPhotoUrls.value = new Set()
  journeyPreviewReady.value = props.layout !== 'journey' || items.value.length === 0
  primaryOrientation.value = 'landscape'
  primaryRatio.value = 1
  void resolveJourneyPhotoMetadata()
}, { immediate: true })
function normalizeIndex(index) {
  const length = items.value.length
  if (!length) return 0
  return ((Number(index) || 0) % length + length) % length
}
function journeyItemIndex(photo) {
  return Math.max(0, items.value.findIndex(item => item.photoUrl === photo?.photoUrl))
}
function preloadPhoto(photo) {
  const photoUrl = String(photo?.photoUrl || '').trim()
  if (!photoUrl || failedPhotoUrls.value.has(photoUrl) || typeof Image === 'undefined') {
    return Promise.resolve(false)
  }
  if (imageLoadRequests.has(photoUrl)) return imageLoadRequests.get(photoUrl)

  const request = new Promise((resolve) => {
    const image = new Image()
    image.decoding = 'async'
    image.onload = async () => {
      try {
        if (typeof image.decode === 'function') await image.decode()
      } catch {
        // A decoded browser cache entry is still usable when decode() is unavailable or interrupted.
      }
      resolve(true)
    }
    image.onerror = () => resolve(false)
    image.src = photoUrl
  })
  imageLoadRequests.set(photoUrl, request)
  return request
}
function inspectPhotoMetadata(photo) {
  const photoUrl = String(photo?.photoUrl || '').trim()
  if (!photoUrl || typeof Image === 'undefined') return Promise.resolve(null)
  if (photoMetadataRequests.has(photoUrl)) return photoMetadataRequests.get(photoUrl)

  const request = new Promise((resolve) => {
    const image = new Image()
    image.decoding = 'async'
    image.onload = async () => {
      try {
        if (typeof image.decode === 'function') await image.decode()
      } catch {
        // Natural dimensions are available even when browser decoding is interrupted.
      }
      const width = image.naturalWidth
      const height = image.naturalHeight
      if (!width || !height) {
        resolve(null)
        return
      }
      const ratio = width / height
      resolve({
        photoUrl,
        ratio,
        orientation: ratio > 1.2 ? 'landscape' : (ratio < 0.8 ? 'portrait' : 'square'),
      })
    }
    image.onerror = () => resolve(null)
    image.src = photoUrl
  })
  photoMetadataRequests.set(photoUrl, request)
  return request
}
async function resolveJourneyPhotoMetadata() {
  const photos = journeyPreviewItems.value.slice()
  const requestId = ++journeyLayoutRequest
  if (props.layout !== 'journey' || photos.length === 0) {
    journeyInspectedPhotoUrls.value = new Set(photos.map(photo => photo.photoUrl))
    journeyPreviewReady.value = true
    return
  }

  const metadata = await Promise.all(photos.map(inspectPhotoMetadata))
  if (requestId !== journeyLayoutRequest) return

  const orientations = {}
  const ratios = {}
  metadata.forEach((result) => {
    if (!result) return
    orientations[result.photoUrl] = result.orientation
    ratios[result.photoUrl] = result.ratio
  })
  photoOrientations.value = orientations
  photoRatios.value = ratios
  journeyInspectedPhotoUrls.value = new Set(photos.map(photo => photo.photoUrl))

  const revealPreview = () => {
    if (requestId === journeyLayoutRequest) journeyPreviewReady.value = true
  }
  if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
    window.requestAnimationFrame(revealPreview)
  } else {
    revealPreview()
  }
}
function preloadAdjacent(index = selectedIndex.value) {
  if (items.value.length < 2) return
  const currentIndex = normalizeIndex(index)
  const nearbyIndexes = [
    (currentIndex + 1) % items.value.length,
    (currentIndex - 1 + items.value.length) % items.value.length,
  ]
  nearbyIndexes.forEach((nearbyIndex) => { void preloadPhoto(items.value[nearbyIndex]) })
}
async function selectPhoto(index) {
  const targetIndex = normalizeIndex(index)
  if (!items.value.length || targetIndex === selectedIndex.value) {
    preloadAdjacent(targetIndex)
    return
  }
  const requestId = ++switchRequest.value
  isSwitching.value = true
  const targetPhoto = items.value[targetIndex]
  const didLoad = await preloadPhoto(targetPhoto)
  if (requestId !== switchRequest.value) return
  if (!didLoad) markPhotoFailed(targetPhoto)
  selectedIndex.value = targetIndex
  isSwitching.value = false
  preloadAdjacent(targetIndex)
}
function show(index = 0) {
  selectedIndex.value = normalizeIndex(index)
  open.value = true
  preloadAdjacent(selectedIndex.value)
}
function selectPreview(index) {
  void selectPhoto(index)
  primaryOrientation.value = 'landscape'
  primaryRatio.value = 1
}
defineExpose({ open: show })
function closeGallery() { open.value = false }
function showPrevious() {
  void selectPhoto(selectedIndex.value - 1)
}
function showNext() {
  void selectPhoto(selectedIndex.value + 1)
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
function detectPhotoOrientation(photo, event) {
  const { naturalWidth: width, naturalHeight: height } = event.target
  const photoUrl = String(photo?.photoUrl || '').trim()
  if (!photoUrl || !width || !height) return

  const ratio = width / height
  const orientation = ratio > 1.2 ? 'landscape' : (ratio < 0.8 ? 'portrait' : 'square')
  if (photoOrientations.value[photoUrl] !== orientation) {
    photoOrientations.value = {
      ...photoOrientations.value,
      [photoUrl]: orientation,
    }
  }
  if (photoRatios.value[photoUrl] !== ratio) {
    photoRatios.value = {
      ...photoRatios.value,
      [photoUrl]: ratio,
    }
  }

  if (items.value[0]?.photoUrl === photoUrl) {
    primaryRatio.value = ratio
    primaryOrientation.value = orientation
  }
}

watch(open, (isOpen) => {
  if (isOpen) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = previousBodyOverflow
    previousBodyOverflow = ''
  }
  if (isOpen) {
    window.addEventListener('keydown', handleGalleryKeydown)
    preloadAdjacent(selectedIndex.value)
  }
  else window.removeEventListener('keydown', handleGalleryKeydown)
})

onBeforeUnmount(() => {
  open.value = false
  selectedIndex.value = 0
  touchStart.value = null
  isSwitching.value = false
  switchRequest.value += 1
  imageLoadRequests.clear()
  photoMetadataRequests.clear()
  journeyLayoutRequest += 1
  failedPhotoUrls.value = new Set()
  photoOrientations.value = {}
  photoRatios.value = {}
  journeyInspectedPhotoUrls.value = new Set()
  document.body.style.overflow = previousBodyOverflow
  previousBodyOverflow = ''
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

    <div
      v-else-if="showPreview && layout === 'journey' && !journeyPreviewReady"
      :class="[
        'gallery-journey-loading-preview',
        {
          'has-measured-layout': journeyLayoutReady,
          'has-multiple': journeyLayoutReady && journeyUsesEditorialLayout,
        },
      ]"
      :style="journeyRightStyle"
      aria-label="正在准备照片布局"
    >
      <span
        v-if="journeyLayoutReady && journeyUsesEditorialLayout"
        class="gallery-journey-loading-support gallery-journey-layout-placeholder"
        :style="journeySupportingStyle"
      ></span>
      <span class="gallery-journey-right-main gallery-journey-layout-placeholder"></span>
    </div>

    <div
      v-else-if="showPreview && layout === 'journey' && journeyUsesEditorialLayout"
      class="gallery-journey-editorial-preview"
      :style="journeyRightStyle"
    >
      <button
        type="button"
        class="gallery-journey-tile gallery-journey-supporting-landscape"
        :style="journeySupportingStyle"
        :aria-label="`查看第 ${journeyItemIndex(journeySupportingLandscape) + 1} 张照片`"
        @click="show(journeyItemIndex(journeySupportingLandscape))"
      >
        <span v-if="hasPhotoFailed(journeySupportingLandscape)" class="gallery-photo-error">照片暂时无法显示</span>
        <img
          v-else
          :src="journeySupportingLandscape.photoUrl"
          :alt="`${alt} ${journeyItemIndex(journeySupportingLandscape) + 1}`"
          loading="eager"
          decoding="async"
          @load="detectPhotoOrientation(journeySupportingLandscape, $event)"
          @error="markPhotoFailed(journeySupportingLandscape)"
        />
      </button>

      <div class="gallery-journey-right-stack gallery-journey-right-count-1">
        <button
          v-for="photo in journeyRightItems"
          :key="photo.id || photo.photoUrl"
          type="button"
          :class="[
            'gallery-journey-tile',
            'gallery-journey-right-main',
            `gallery-journey-tile-${photoOrientations[photo.photoUrl] || 'unknown'}`,
          ]"
          :aria-label="`查看第 ${journeyItemIndex(photo) + 1} 张照片`"
          @click="show(journeyItemIndex(photo))"
        >
          <span v-if="hasPhotoFailed(photo)" class="gallery-photo-error">照片暂时无法显示</span>
          <img
            v-else
            :src="photo.photoUrl"
            :alt="`${alt} ${journeyItemIndex(photo) + 1}`"
            loading="eager"
            decoding="async"
            @load="detectPhotoOrientation(photo, $event)"
            @error="markPhotoFailed(photo)"
          />
        </button>
        <span v-if="journeyHasHiddenPhotos" class="gallery-total-count">共 {{ items.length }} 张</span>
      </div>
    </div>

    <div
      v-else-if="showPreview && layout === 'journey'"
      :class="['gallery-journey-single-preview', `is-${journeyRightOrientation}`]"
      :style="journeyRightStyle"
    >
      <div
        :class="[
          'gallery-journey-right-stack',
          'gallery-journey-right-count-1',
          `is-${journeyRightOrientation}`,
        ]"
      >
        <button
          v-for="photo in journeyRightItems"
          :key="photo.id || photo.photoUrl"
          type="button"
          :class="[
            'gallery-journey-tile',
            'gallery-journey-right-main',
            `gallery-journey-tile-${photoOrientations[photo.photoUrl] || 'unknown'}`,
          ]"
          :aria-label="`查看第 ${journeyItemIndex(photo) + 1} 张照片`"
          @click="show(journeyItemIndex(photo))"
        >
          <span v-if="hasPhotoFailed(photo)" class="gallery-photo-error">照片暂时无法显示</span>
          <img
            v-else
            :src="photo.photoUrl"
            :alt="`${alt} ${journeyItemIndex(photo) + 1}`"
            loading="eager"
            decoding="async"
            @load="detectPhotoOrientation(photo, $event)"
            @error="markPhotoFailed(photo)"
          />
        </button>
        <span v-if="journeyHasHiddenPhotos" class="gallery-total-count">共 {{ items.length }} 张</span>
      </div>
    </div>

    <button v-else-if="showPreview && layout === 'favorite'" type="button" class="gallery-favorite-preview" @click="show(0)">
      <span v-if="hasPhotoFailed(items[0])" class="gallery-photo-error">照片暂时无法显示</span>
      <img
        v-else
        :src="items[0].photoUrl"
        :alt="alt"
        :loading="eagerPreview ? 'eager' : 'lazy'"
        :fetchpriority="eagerPreview ? 'high' : 'auto'"
        decoding="async"
        @load="detectPrimaryOrientation"
        @error="markPhotoFailed(items[0])"
      />
      <span v-if="items.length > 1" class="gallery-total-count">共 {{ items.length }} 张</span>
    </button>

    <template v-else-if="showPreview">
      <button type="button" :class="['gallery-main', { 'gallery-main-with-backdrop': useBackdrop, 'gallery-main-dim-backdrop': useBackdrop && backdropDim }]" @click="show(selectedIndex)">
        <span v-if="useBackdrop" class="gallery-backdrop" :style="{ backgroundImage: `url(${previewSelected.photoUrl})` }"></span>
        <span v-if="hasPhotoFailed(previewSelected)" class="gallery-photo-error">照片暂时无法显示</span>
        <img v-else :class="`gallery-image-${fit}`" :src="previewSelected.photoUrl" :alt="alt" loading="lazy" decoding="async" @load="detectPrimaryOrientation" @error="markPhotoFailed(previewSelected)" />
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
          <img v-else :class="`gallery-thumb-image-${fit}`" :src="photo.photoUrl" :alt="`${alt} ${layout === 'detail' ? index + 1 : index + 2}`" loading="lazy" decoding="async" @error="markPhotoFailed(photo)" />
        </button>
      </div>
    </template>

    <Teleport to="body">
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
        <img v-else :src="selected.photoUrl" :alt="`${alt} ${selectedIndex + 1}`" class="gallery-full" loading="eager" decoding="async" fetchpriority="high" @error="markPhotoFailed(selected)" />
        <span v-if="isSwitching" class="gallery-switching" aria-live="polite">正在准备下一张照片</span>
        <button v-if="items.length > 1" type="button" class="gallery-arrow gallery-arrow-next" aria-label="下一张照片" @click="showNext">›</button>
        <div v-if="items.length > 1" class="gallery-dialog-thumbs">
          <button v-for="(photo, index) in items" :key="photo.id || photo.photoUrl" type="button" :class="{ active: index === selectedIndex }" @click="selectPhoto(index)">
            <span v-if="hasPhotoFailed(photo)" class="gallery-photo-error">无法显示</span>
            <img v-else :src="photo.photoUrl" :alt="`${alt} ${index + 1}`" loading="lazy" decoding="async" @error="markPhotoFailed(photo)" />
          </button>
        </div>
      </div>
    </Teleport>
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
.gallery-journey-editorial-preview {
  display: grid;
  grid-template-columns: minmax(0, .82fr) minmax(0, 1.18fr);
  gap: 10px;
  width: 100%;
}
.gallery-journey-single-preview {
  position: relative;
  display: grid;
  width: 100%;
  min-height: 360px;
  overflow: hidden;
  border-radius: 12px;
}
.gallery-journey-supporting-landscape {
  align-self: end;
  aspect-ratio: 16 / 9;
}
.gallery-journey-right-stack {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 360px;
  gap: 6px;
  overflow: hidden;
  border-radius: 12px;
}
.gallery-journey-right-main {
  width: 100%;
  height: 100%;
}
.gallery-journey-single-preview.is-landscape,
.gallery-journey-single-preview.is-square {
  min-height: 0;
  aspect-ratio: var(--journey-right-aspect-ratio, 16 / 9);
}
.gallery-journey-single-preview.is-landscape .gallery-journey-right-stack,
.gallery-journey-single-preview.is-square .gallery-journey-right-stack {
  min-height: 0;
  aspect-ratio: var(--journey-right-aspect-ratio, 16 / 9);
}
.gallery-journey-single-preview.is-landscape .gallery-journey-right-main,
.gallery-journey-single-preview.is-square .gallery-journey-right-main {
  min-height: 0;
}
.gallery-journey-single-preview.is-landscape .gallery-journey-right-main img,
.gallery-journey-single-preview.is-square .gallery-journey-right-main img {
  object-fit: contain;
}
.gallery-journey-layout-placeholder {
  border: 0;
  background:
    linear-gradient(110deg, rgba(232, 222, 210, .78) 8%, rgba(246, 240, 232, .9) 24%, rgba(232, 222, 210, .78) 40%);
  background-size: 220% 100%;
}
.gallery-journey-right-count-1 {
  grid-template-columns: 1fr;
}
.gallery-journey-right-count-2 {
  grid-template-columns: 1fr;
  grid-template-rows: repeat(2, minmax(0, 1fr));
}
.gallery-journey-right-count-3 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: minmax(0, 1.25fr) minmax(0, 1fr);
}
.gallery-journey-right-count-3 .gallery-journey-tile:first-child {
  grid-column: 1 / -1;
}
.gallery-journey-right-count-4,
.gallery-journey-right-count-5 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-auto-rows: minmax(0, 1fr);
}
.gallery-journey-right-count-5 .gallery-journey-tile:first-child {
  grid-column: 1 / -1;
}
.gallery-journey-preview { position: relative; display: grid; width: 100%; gap: 10px; overflow: hidden; border-radius: 8px; }
.gallery-journey-count-1 { display: block; }
.gallery-journey-count-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); height: min(42vw, 500px); }
.gallery-journey-count-2.gallery-journey-variant-landscape-pair {
  grid-template-columns: 1fr;
  grid-template-rows: repeat(2, minmax(0, 1fr));
  height: min(54vw, 560px);
}
.gallery-journey-count-2.gallery-journey-variant-portrait-featured {
  grid-template-columns: minmax(0, .82fr) minmax(0, 1.18fr);
}
.gallery-journey-count-2.gallery-journey-variant-portrait-featured .is-journey-featured-portrait {
  grid-column: 2;
}
.gallery-journey-count-2.gallery-journey-variant-portrait-featured .gallery-journey-tile:not(.is-journey-featured-portrait) {
  grid-column: 1;
  grid-row: 1;
}
.gallery-journey-count-3 {
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  grid-template-rows: repeat(2, minmax(0, 1fr));
  height: min(48vw, 540px);
}
.gallery-journey-count-3.gallery-journey-variant-portrait-featured {
  grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr);
}
.gallery-journey-count-3 .gallery-journey-tile-1 { grid-row: 1 / span 2; }
.gallery-journey-count-3.gallery-journey-variant-portrait-featured .gallery-journey-tile-1 {
  grid-row: auto;
}
.gallery-journey-count-3.gallery-journey-variant-portrait-featured .is-journey-featured-portrait {
  grid-column: 2;
  grid-row: 1 / span 2;
}
.gallery-journey-count-3.gallery-journey-variant-portrait-featured .gallery-journey-tile:not(.is-journey-featured-portrait) {
  grid-column: 1;
}
.gallery-journey-count-4 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  height: min(52vw, 560px);
}
.gallery-journey-count-5,
.gallery-journey-count-6 {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  grid-template-rows: minmax(0, 1.45fr) repeat(2, minmax(0, 1fr));
  height: min(72vw, 620px);
}
.gallery-journey-count-5 .gallery-journey-tile-1,
.gallery-journey-count-6 .gallery-journey-tile-1 {
  grid-column: 1 / -1;
}
.gallery-journey-count-5 .gallery-journey-tile-2,
.gallery-journey-count-5 .gallery-journey-tile-4,
.gallery-journey-count-6 .gallery-journey-tile-5 {
  grid-column: 1 / span 3;
}
.gallery-journey-count-5 .gallery-journey-tile-3,
.gallery-journey-count-5 .gallery-journey-tile-5,
.gallery-journey-count-6 .gallery-journey-tile-6 {
  grid-column: 4 / span 3;
}
.gallery-journey-count-6 .gallery-journey-tile-2 { grid-column: 1 / span 2; }
.gallery-journey-count-6 .gallery-journey-tile-3 { grid-column: 3 / span 2; }
.gallery-journey-count-6 .gallery-journey-tile-4 { grid-column: 5 / span 2; }
.gallery-journey-count-5 .gallery-journey-tile-2,
.gallery-journey-count-5 .gallery-journey-tile-3,
.gallery-journey-count-6 .gallery-journey-tile-2,
.gallery-journey-count-6 .gallery-journey-tile-3,
.gallery-journey-count-6 .gallery-journey-tile-4 {
  grid-row: 2;
}
.gallery-journey-count-5 .gallery-journey-tile-4,
.gallery-journey-count-5 .gallery-journey-tile-5,
.gallery-journey-count-6 .gallery-journey-tile-5,
.gallery-journey-count-6 .gallery-journey-tile-6 {
  grid-row: 3;
}
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
.gallery-dialog { position: fixed; z-index: 1000; inset: 0; display: grid; width: 100%; height: 100dvh; min-height: 100svh; grid-template-columns: minmax(48px, 1fr) minmax(0, 1080px) minmax(48px, 1fr); grid-template-rows: auto minmax(0, 1fr) auto; gap: 14px 18px; padding: max(20px, env(safe-area-inset-top)) 24px max(18px, env(safe-area-inset-bottom)); overflow: hidden; overscroll-behavior: contain; background: rgba(19, 17, 15, .96); color: #fff; }
.gallery-dialog-bar { grid-column: 1 / -1; display: grid; min-height: 42px; grid-template-columns: 44px minmax(0, 1fr) 44px; align-items: center; color: rgba(255,255,255,.76); font-size: 13px; text-align: center; }
.gallery-dialog-bar-spacer { width: 44px; }
.gallery-full { grid-column: 2; grid-row: 2; align-self: center; justify-self: center; max-width: 100%; max-height: min(76vh, 100%); object-fit: contain; }
.gallery-switching { grid-column: 2; grid-row: 2; align-self: end; justify-self: center; z-index: 1; margin-bottom: 14px; padding: 7px 11px; border-radius: 999px; background: rgba(0,0,0,.56); color: rgba(255,255,255,.92); font-size: 12px; line-height: 18px; pointer-events: none; }
.gallery-full-error { grid-column: 2; grid-row: 2; align-self: center; justify-self: center; padding: 22px; color: rgba(255,255,255,.74); text-align: center; }
.gallery-close { display: grid; width: 40px; height: 40px; place-items: center; padding: 0; border: 1px solid rgba(255,255,255,.28); border-radius: 50%; background: rgba(255,255,255,.08); color: #fff; font-size: 25px; line-height: 1; }
.gallery-arrow { align-self: center; display: grid; width: 44px; height: 56px; place-items: center; padding: 0; border: 1px solid rgba(255,255,255,.2); border-radius: 50%; background: rgba(255,255,255,.07); color: #fff; font-size: 38px; line-height: 1; }
.gallery-arrow-previous { grid-column: 1; grid-row: 2; justify-self: end; }
.gallery-arrow-next { grid-column: 3; grid-row: 2; justify-self: start; }
.gallery-dialog-thumbs { grid-column: 1 / -1; grid-row: 3; display: flex; min-width: 0; gap: 8px; max-width: min(100%, 760px); justify-self: center; overflow-x: auto; padding: 3px; overscroll-behavior-inline: contain; }
.gallery-dialog-thumbs button { border-radius: 5px; opacity: .56; }
.gallery-dialog-thumbs button.active { outline: 2px solid #fff; opacity: 1; }
@media (max-width: 640px) {
  .gallery-timeline-preview { border-radius: 0 7px 7px 0; }
  .gallery-favorite-preview { height: min(52vw, 210px); border-radius: 0; }
  .gallery-layout-journey.gallery-orientation-landscape .gallery-image-contain { max-height: min(58vh, 340px); }
  .gallery-layout-journey.gallery-orientation-portrait .gallery-image-contain { width: min(360px, 100%); max-height: 68vh; }
  .gallery-layout-journey.gallery-orientation-square .gallery-image-contain { width: 78%; max-height: min(58vh, 360px); }
  .gallery-journey-preview { gap: 5px; }
  .gallery-journey-count-2 { height: clamp(240px, 84vw, 380px); }
  .gallery-journey-count-2.gallery-journey-variant-landscape-pair { height: clamp(300px, 96vw, 430px); }
  .gallery-journey-count-3 { height: clamp(270px, 78vw, 360px); }
  .gallery-journey-count-4 { height: clamp(290px, 82vw, 380px); }
  .gallery-journey-count-5,
  .gallery-journey-count-6 { height: clamp(360px, 112vw, 470px); }
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
  .gallery-dialog { grid-template-columns: 42px minmax(0, 1fr) 42px; gap: 10px 4px; padding: max(12px, env(safe-area-inset-top)) 8px max(10px, env(safe-area-inset-bottom)); }
  .gallery-full { max-height: min(72vh, 100%); }
  .gallery-arrow { width: 38px; height: 48px; border: 0; background: rgba(0,0,0,.22); font-size: 32px; }
  .gallery-dialog-thumbs button { flex-basis: 46px; height: 46px; }
}
</style>
