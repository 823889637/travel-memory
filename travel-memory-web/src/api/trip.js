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

export function setTripCoverUrl(tripId, photoUrl) {
  return request.put(`/api/trips/${tripId}/cover-url`, { photoUrl })
}

export function favoriteTrip(tripId, favorite) {
  return request.put(`/api/trips/${tripId}/favorite`, { favorite })
}

export function getTripRecap(tripId) {
  return request.get(`/api/trips/${tripId}/recap`)
}

export function getTripDraft() {
  return request.get('/api/trip-drafts')
}

export function saveTripDraft(data) {
  return request.put('/api/trip-drafts', data)
}

export function deleteTripDraft() {
  return request.delete('/api/trip-drafts')
}

export function uploadImage(data) {
  return request.post('/api/uploads/images', data)
}

export function deleteTrip(id) {
  return request.delete(`/api/trips/${id}`)
}
