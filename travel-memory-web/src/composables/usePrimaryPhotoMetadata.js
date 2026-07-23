import { onScopeDispose, ref, watch } from 'vue'
import { readPhotoMetadata } from '../api/memory'

function photoUrlOf(photo) {
  return photo?.result?.photoUrl || photo?.photoUrl || ''
}

function embeddedMetadataOf(photo) {
  const metadata = photo?.metadata || photo?.result
  if (!metadata || (!('hasExifTime' in metadata) && !('hasExifLocation' in metadata))) return null
  return metadata
}

export function usePrimaryPhotoMetadata(getPrimaryPhoto) {
  const metadataCache = new Map()
  const readingTarget = ref('')
  const timeFeedback = ref('')
  const timeFeedbackType = ref('')
  const locationFeedback = ref('')
  const locationFeedbackType = ref('')

  async function loadPrimaryMetadata() {
    const photo = getPrimaryPhoto()
    const photoUrl = photoUrlOf(photo)
    if (!photoUrl) throw new Error('请先添加一张主图。')
    if (metadataCache.has(photoUrl)) return metadataCache.get(photoUrl)

    const embedded = embeddedMetadataOf(photo)
    if (embedded) {
      metadataCache.set(photoUrl, embedded)
      return embedded
    }

    const metadata = await readPhotoMetadata(photoUrl)
    metadataCache.set(photoUrl, metadata)
    return metadata
  }

  async function readTimeFromPrimary() {
    readingTarget.value = 'time'
    timeFeedback.value = ''
    timeFeedbackType.value = ''
    try {
      const metadata = await loadPrimaryMetadata()
      if (!metadata?.hasExifTime || !metadata.photoTakenTime) {
        timeFeedback.value = '当前主图没有可读取的拍摄时间。'
        timeFeedbackType.value = 'empty'
        return null
      }
      timeFeedback.value = '已从当前主图重新读取拍摄时间，你可以修改。'
      timeFeedbackType.value = 'success'
      return metadata.photoTakenTime
    } catch {
      timeFeedback.value = '暂时无法读取主图信息，请稍后重试。'
      timeFeedbackType.value = 'error'
      return null
    } finally {
      readingTarget.value = ''
    }
  }

  async function readLocationFromPrimary() {
    readingTarget.value = 'location'
    locationFeedback.value = ''
    locationFeedbackType.value = ''
    try {
      const metadata = await loadPrimaryMetadata()
      if (!metadata?.hasExifLocation || metadata.latitude == null || metadata.longitude == null) {
        locationFeedback.value = '当前主图没有可读取的位置信息。'
        locationFeedbackType.value = 'empty'
        return null
      }
      locationFeedback.value = '已从当前主图重新读取照片位置，请确认地点名称。'
      locationFeedbackType.value = 'success'
      return {
        latitude: metadata.latitude,
        longitude: metadata.longitude,
      }
    } catch {
      locationFeedback.value = '暂时无法读取主图信息，请稍后重试。'
      locationFeedbackType.value = 'error'
      return null
    } finally {
      readingTarget.value = ''
    }
  }

  watch(
    () => photoUrlOf(getPrimaryPhoto()),
    () => {
      timeFeedback.value = ''
      timeFeedbackType.value = ''
      locationFeedback.value = ''
      locationFeedbackType.value = ''
    },
  )

  onScopeDispose(() => metadataCache.clear())

  return {
    locationFeedback,
    locationFeedbackType,
    readLocationFromPrimary,
    readingTarget,
    readTimeFromPrimary,
    timeFeedback,
    timeFeedbackType,
  }
}
