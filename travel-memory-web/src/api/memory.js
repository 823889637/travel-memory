import request from './request'

export function getTimeline(tripId) {
  return request.get('/api/memories/timeline', { params: { tripId } })
}

export function searchMemories(tripId, keyword) {
  return request.get('/api/memories/search', { params: { tripId, keyword } })
}

export function createMemory(data) {
  return request.post('/api/memories', data)
}

export function uploadPhoto(data, config = {}) {
  return request.post('/api/memories/photo', data, config)
}

export function reverseGeocode(latitude, longitude) {
  return request.get('/api/location/reverse-geocode', { params: { latitude, longitude } })
}

export function searchLocations(keyword, options = {}) {
  return request.get('/api/location/search', {
    params: {
      keyword,
      latitude: options.latitude ?? undefined,
      longitude: options.longitude ?? undefined,
      city: options.city ?? undefined,
    },
  })
}

export function searchCities(keyword) {
  return request.get('/api/location/cities', { params: { keyword } })
}

export function normalizeCoordinate(data) {
  return request.post('/api/location/normalize', data)
}

export function getMemory(id) {
  return request.get(`/api/memories/${id}`)
}

export function updateMemory(id, data) {
  return request.put(`/api/memories/${id}`, data)
}

export function favoriteMemory(id, favorite) {
  return request.put(`/api/memories/${id}/favorite`, null, { params: { favorite } })
}

export function uploadMemoryPhoto(id, data) {
  return request.post(`/api/memories/${id}/photo`, data)
}

export function addMemoryPhoto(id, data) {
  return request.post(`/api/memories/${id}/photos`, data)
}

export function deleteMemoryPhoto(id, photoId) {
  return request.delete(`/api/memories/${id}/photos/${photoId}`)
}

export function reorderMemoryPhotos(id, photoIds) {
  return request.put(`/api/memories/${id}/photos/order`, { photoIds })
}

export function deleteMemory(id) {
  return request.delete(`/api/memories/${id}`)
}

export function getMemoryDraft(tripId, memoryId = null) {
  return request.get('/api/memory-drafts', { params: { tripId, memoryId: memoryId ?? undefined } })
}

export function saveMemoryDraft(data) {
  return request.put('/api/memory-drafts', data)
}

export function deleteMemoryDraft(tripId, memoryId = null) {
  return request.delete('/api/memory-drafts', { params: { tripId, memoryId: memoryId ?? undefined } })
}
