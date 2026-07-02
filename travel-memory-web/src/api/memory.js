import request from './request'

export function getTimeline(tripId) {
  return request.get('/api/memories/timeline', { params: { tripId } })
}

export function createMemory(data) {
  return request.post('/api/memories', data)
}

export function getMemory(id) {
  return request.get(`/api/memories/${id}`)
}

export function updateMemory(id, data) {
  return request.put(`/api/memories/${id}`, data)
}

export function uploadMemoryPhoto(id, data) {
  return request.post(`/api/memories/${id}/photo`, data)
}

export function deleteMemory(id) {
  return request.delete(`/api/memories/${id}`)
}
