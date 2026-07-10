export function normalizePhotoUrl(value) {
  const normalized = String(value ?? '').trim()
  return normalized || ''
}

export function hasExplicitTripCover(trip) {
  return Boolean(normalizePhotoUrl(trip?.coverPhotoUrl))
}

export function resolveTripCoverUrl(trip, memories = []) {
  const explicitCoverUrl = normalizePhotoUrl(trip?.coverPhotoUrl)
  if (explicitCoverUrl) {
    return explicitCoverUrl
  }

  const effectiveCoverUrl = normalizePhotoUrl(trip?.effectiveCoverPhotoUrl)
  if (effectiveCoverUrl) {
    return effectiveCoverUrl
  }

  return normalizePhotoUrl(memories.find((memory) => normalizePhotoUrl(memory?.photoUrl))?.photoUrl)
}
