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

export function uploadPhoto(data) {
  return request.post('/api/memories/photo', data)
}

export function reverseGeocode(latitude, longitude) {
  return request.get('/api/location/reverse-geocode', { params: { latitude, longitude } })
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

export function deleteMemory(id) {
  return request.delete(`/api/memories/${id}`)
}
