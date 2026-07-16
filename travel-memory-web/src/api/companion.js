import request from './request'

export function getTripCompanions(tripId) {
  return request.get(`/api/trips/${tripId}/companions`)
}

export function createTripCompanion(tripId, data) {
  return request.post(`/api/trips/${tripId}/companions`, typeof data === 'string' ? { name: data } : data)
}

export function updateTripCompanion(tripId, companionId, data) {
  return request.put(`/api/trips/${tripId}/companions/${companionId}`, typeof data === 'string' ? { name: data } : data)
}

export function getCompanionMemories(tripId, companionId) {
  return request.get(`/api/trips/${tripId}/companions/${companionId}/memories`)
}

export function setTripCompanionActive(tripId, companionId, active) {
  return request.put(`/api/trips/${tripId}/companions/${companionId}/active`, { active })
}
