<script setup>
import { computed, ref, watch } from 'vue'

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
})

const open = ref(false)
const selectedIndex = ref(0)
const primaryOrientation = ref('landscape')
const primaryRatio = ref(1)
const items = computed(() => {
  const valid = props.photos.filter(photo => photo?.photoUrl)
  return valid.length ? valid : (props.fallbackUrl ? [{ photoUrl: props.fallbackUrl }] : [])
})
const selected = computed(() => items.value[selectedIndex.value] || items.value[0])
const useBackdrop = computed(() => (
  props.backdrop && (
    props.backdropPortraitOnly
      ? primaryOrientation.value === 'portrait'
      : (!props.backdropTallOnly || primaryRatio.value < 1.15)
  )
))
watch(items, () => {
  if (selectedIndex.value >= items.value.length) selectedIndex.value = 0
  primaryOrientation.value = 'landscape'
  primaryRatio.value = 1
})
function show(index = 0) { selectedIndex.value = index; open.value = true }
function detectPrimaryOrientation(event) {
  const { naturalWidth: width, naturalHeight: height } = event.target
  if (!width || !height) return
  const ratio = width / height
  primaryRatio.value = ratio
  primaryOrientation.value = ratio > 1.2 ? 'landscape' : (ratio < 0.8 ? 'portrait' : 'square')
}
</script>

<template>
  <div v-if="items.length" :class="['gallery', `gallery-orientation-${primaryOrientation}`, { [`gallery-layout-${layout}`]: layout }]" :style="maxHeight ? { '--gallery-main-max-height': maxHeight } : null" @click.stop>
    <button type="button" :class="['gallery-main', { 'gallery-main-with-backdrop': useBackdrop, 'gallery-main-dim-backdrop': useBackdrop && backdropDim }]" @click="show()">
      <span v-if="useBackdrop" class="gallery-backdrop" :style="{ backgroundImage: `url(${items[0].photoUrl})` }"></span>
      <img :class="`gallery-image-${fit}`" :src="items[0].photoUrl" :alt="alt" @load="detectPrimaryOrientation" />
      <span v-if="items.length > 1" class="gallery-count">{{ items.length }} {{ countLabel }}</span>
    </button>
    <div v-if="items.length > 1" class="gallery-thumbs">
      <button v-for="(photo, index) in items.slice(1, 4)" :key="photo.id || photo.photoUrl" type="button" @click="show(index + 1)">
        <img :class="`gallery-thumb-image-${fit}`" :src="photo.photoUrl" :alt="`${alt} ${index + 2}`" />
      </button>
    </div>
    <div v-if="open" class="gallery-dialog" role="dialog" aria-modal="true" @click.self="open = false">
      <button type="button" class="gallery-close" aria-label="Close photos" @click="open = false">x</button>
      <img :src="selected.photoUrl" :alt="alt" class="gallery-full" />
      <div v-if="items.length > 1" class="gallery-dialog-thumbs">
        <button v-for="(photo, index) in items" :key="photo.id || photo.photoUrl" type="button" :class="{ active: index === selectedIndex }" @click="selectedIndex = index">
          <img :src="photo.photoUrl" :alt="`${alt} ${index + 1}`" />
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
.gallery-layout-timeline .gallery-main { display: grid; place-items: center; height: 190px; border-radius: 10px; background: #f4f0e8; box-shadow: none; }
.gallery-layout-timeline .gallery-image-contain { position: relative; z-index: 1; min-height: 0; max-height: none; object-fit: contain; }
.gallery-layout-timeline.gallery-orientation-landscape .gallery-image-contain { width: 100%; height: 100%; }
.gallery-layout-timeline.gallery-orientation-portrait .gallery-image-contain { width: auto; max-width: 100%; height: 100%; }
.gallery-layout-timeline.gallery-orientation-square .gallery-image-contain { width: 85%; height: 85%; }
.gallery-layout-recap .gallery-main { height: 145px; border-radius: 7px; background: #f1ebe2; }
.gallery-layout-recap .gallery-main img { width: 100%; height: 100%; min-height: 0; max-height: none; object-fit: cover; }
.gallery-layout-recap .gallery-thumbs { display: none; }
.gallery-layout-timeline .gallery-thumbs button { flex-basis: 42px; height: 42px; background: #f4f0e8; }
.gallery-layout-timeline .gallery-thumbs { display: none; }
.gallery-count { position: absolute; right: 8px; bottom: 8px; padding: 4px 7px; border-radius: 4px; background: rgba(30, 26, 22, .7); color: #fff; font-size: 12px; }
.gallery-thumbs { display: flex; gap: 6px; overflow: hidden; }
.gallery-thumbs button, .gallery-dialog-thumbs button { flex: 0 0 52px; height: 52px; padding: 0; overflow: hidden; background: #eee3d6; }
.gallery-thumbs img, .gallery-dialog-thumbs img { width: 100%; height: 100%; object-fit: cover; }
.gallery-thumbs img.gallery-thumb-image-contain { object-fit: contain; }
.gallery-dialog { position: fixed; z-index: 1000; inset: 0; display: grid; align-content: center; justify-items: center; gap: 14px; padding: 24px; background: rgba(20, 18, 16, .9); }
.gallery-full { max-width: min(92vw, 960px); max-height: 76vh; object-fit: contain; }
.gallery-close { position: fixed; top: 16px; right: 16px; width: 40px; height: 40px; border-radius: 50%; background: #fff; color: #222; font-size: 22px; }
.gallery-dialog-thumbs { display: flex; gap: 8px; max-width: 100%; overflow-x: auto; }
.gallery-dialog-thumbs button.active { outline: 2px solid #fff; }
@media (max-width: 640px) {
  .gallery-layout-timeline .gallery-main { height: 118px; }
  .gallery-layout-timeline.gallery-orientation-portrait .gallery-main { height: 118px; }
  .gallery-layout-journey.gallery-orientation-landscape .gallery-image-contain { max-height: min(58vh, 340px); }
  .gallery-layout-journey.gallery-orientation-portrait .gallery-image-contain { width: min(360px, 100%); max-height: 68vh; }
  .gallery-layout-journey.gallery-orientation-square .gallery-image-contain { width: 78%; max-height: min(58vh, 360px); }
  .gallery-layout-recap .gallery-main { height: min(52vw, 210px); }
}
</style>
