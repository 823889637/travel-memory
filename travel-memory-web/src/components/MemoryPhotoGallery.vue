<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  photos: { type: Array, default: () => [] },
  fallbackUrl: { type: String, default: '' },
  alt: { type: String, default: 'Memory photo' },
})

const open = ref(false)
const selectedIndex = ref(0)
const items = computed(() => {
  const valid = props.photos.filter(photo => photo?.photoUrl)
  return valid.length ? valid : (props.fallbackUrl ? [{ photoUrl: props.fallbackUrl }] : [])
})
const selected = computed(() => items.value[selectedIndex.value] || items.value[0])
watch(items, () => { if (selectedIndex.value >= items.value.length) selectedIndex.value = 0 })
function show(index = 0) { selectedIndex.value = index; open.value = true }
</script>

<template>
  <div v-if="items.length" class="gallery" @click.stop>
    <button type="button" class="gallery-main" @click="show()">
      <img :src="items[0].photoUrl" :alt="alt" />
      <span v-if="items.length > 1" class="gallery-count">{{ items.length }} photos</span>
    </button>
    <div v-if="items.length > 1" class="gallery-thumbs">
      <button v-for="(photo, index) in items.slice(1, 4)" :key="photo.id || photo.photoUrl" type="button" @click="show(index + 1)">
        <img :src="photo.photoUrl" :alt="`${alt} ${index + 2}`" />
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
.gallery-count { position: absolute; right: 8px; bottom: 8px; padding: 4px 7px; border-radius: 4px; background: rgba(30, 26, 22, .7); color: #fff; font-size: 12px; }
.gallery-thumbs { display: flex; gap: 6px; overflow: hidden; }
.gallery-thumbs button, .gallery-dialog-thumbs button { flex: 0 0 52px; height: 52px; padding: 0; overflow: hidden; background: #eee3d6; }
.gallery-thumbs img, .gallery-dialog-thumbs img { width: 100%; height: 100%; object-fit: cover; }
.gallery-dialog { position: fixed; z-index: 1000; inset: 0; display: grid; align-content: center; justify-items: center; gap: 14px; padding: 24px; background: rgba(20, 18, 16, .9); }
.gallery-full { max-width: min(92vw, 960px); max-height: 76vh; object-fit: contain; }
.gallery-close { position: fixed; top: 16px; right: 16px; width: 40px; height: 40px; border-radius: 50%; background: #fff; color: #222; font-size: 22px; }
.gallery-dialog-thumbs { display: flex; gap: 8px; max-width: 100%; overflow-x: auto; }
.gallery-dialog-thumbs button.active { outline: 2px solid #fff; }
</style>
