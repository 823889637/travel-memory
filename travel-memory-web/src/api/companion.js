import request from './request'

export function getTripCompanions(tripId) {
  return request.get(`/api/trips/${tripId}/companions`)
}

export function createTripCompanion(tripId, name) {
  return request.post(`/api/trips/${tripId}/companions`, { name })
}

export function updateTripCompanion(tripId, companionId, name) {
  return request.put(`/api/trips/${tripId}/companions/${companionId}`, { name })
}

export function setTripCompanionActive(tripId, companionId, active) {
  return request.put(`/api/trips/${tripId}/companions/${companionId}/active`, { active })
}
