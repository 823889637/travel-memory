import request from './request'

export function getTrips() {
  return request.get('/api/trips')
}

export function getTrip(id) {
  return request.get(`/api/trips/${id}`)
}

export function createTrip(data) {
  return request.post('/api/trips', data)
}

export function updateTrip(id, data) {
  return request.put(`/api/trips/${id}`, data)
}

export function setTripCover(tripId, memoryId) {
  return request.put(`/api/trips/${tripId}/cover`, { memoryId })
}

export function clearTripCover(tripId) {
  return request.delete(`/api/trips/${tripId}/cover`)
}

export function deleteTrip(id) {
  return request.delete(`/api/trips/${id}`)
}
