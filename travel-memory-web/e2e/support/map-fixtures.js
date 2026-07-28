const LOCATION_GROUP_DISTANCE_METERS = 10
const NAMED_LOCATION_GROUP_DISTANCE_METERS = 50
const LONG_ROUTE_BREAK_DISTANCE_METERS = 80_000
const DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000

function isValidCoordinate(latitude, longitude) {
  if (latitude == null || longitude == null
    || String(latitude).trim() === '' || String(longitude).trim() === '') {
    return false
  }
  const lat = Number(latitude)
  const lng = Number(longitude)
  return Number.isFinite(lat)
    && Number.isFinite(lng)
    && lat >= -90
    && lat <= 90
    && lng >= -180
    && lng <= 180
}

function recordDate(value) {
  return String(value || '').match(/^(\d{4}-\d{2}-\d{2})/)?.[1] || ''
}

function sortableTime(value) {
  const timestamp = Date.parse(String(value || '').replace(' ', 'T'))
  return Number.isFinite(timestamp) ? timestamp : Number.MAX_SAFE_INTEGER
}

function hasValidRecordTime(memory) {
  return Boolean(recordDate(memory.recordTime))
    && sortableTime(memory.recordTime) !== Number.MAX_SAFE_INTEGER
}

function compareMemories(left, right) {
  return sortableTime(left.recordTime) - sortableTime(right.recordTime)
    || sortableTime(left.createTime) - sortableTime(right.createTime)
    || Number(left.id || 0) - Number(right.id || 0)
}

function normalizedLocationName(value) {
  return String(value || '').trim().replace(/\s+/g, ' ').toLocaleLowerCase('zh-CN')
}

function distanceMeters(left, right) {
  const toRadians = value => Number(value) * Math.PI / 180
  const latitudeDelta = toRadians(Number(right.latitude) - Number(left.latitude))
  const longitudeDelta = toRadians(Number(right.longitude) - Number(left.longitude))
  const latitudeA = toRadians(left.latitude)
  const latitudeB = toRadians(right.latitude)
  const haversine = Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(latitudeA) * Math.cos(latitudeB) * Math.sin(longitudeDelta / 2) ** 2
  return 6371008.8 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
}

function isSameMapLocation(left, right) {
  const distance = distanceMeters(left, right)
  if (distance <= LOCATION_GROUP_DISTANCE_METERS) return true
  const leftName = normalizedLocationName(left.locationName)
  const rightName = normalizedLocationName(right.locationName)
  return Boolean(
    leftName
    && leftName === rightName
    && distance <= NAMED_LOCATION_GROUP_DISTANCE_METERS,
  )
}

function photoUrls(memory) {
  const orderedPhotos = Array.isArray(memory.photos)
    ? [...memory.photos].sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0))
    : []
  const candidates = [
    ...orderedPhotos.map(photo => photo?.photoUrl),
    memory.photoUrl,
  ]
  return [...new Set(candidates.map(value => String(value || '').trim()).filter(Boolean))]
}

function toUtcDay(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!match) return null
  return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

function tripDayNumber(startDate, date, fallback) {
  const start = toUtcDay(startDate)
  const current = toUtcDay(date)
  if (start == null || current == null) return fallback
  const calculated = Math.floor((current - start) / DAY_IN_MILLISECONDS) + 1
  return Number.isInteger(calculated) && calculated > 0 ? calculated : fallback
}

function analyzeTrip(trip, memories) {
  const located = memories.filter(memory => isValidCoordinate(memory.latitude, memory.longitude))
  const replayable = located.filter(hasValidRecordTime).sort(compareMemories)
  const timed = memories.filter(hasValidRecordTime).sort(compareMemories)
  const groups = []

  located.forEach((memory) => {
    const group = groups.find(candidate => candidate.some(member => isSameMapLocation(member, memory)))
    if (group) group.push(memory)
    else groups.push([memory])
  })

  const days = new Map()
  replayable.forEach((memory) => {
    const date = recordDate(memory.recordTime)
    if (!days.has(date)) {
      days.set(date, {
        date,
        dayNumber: tripDayNumber(trip.startDate, date, days.size + 1),
        count: 0,
      })
    }
    days.get(date).count += 1
  })

  const multiPhotoMemory = replayable
    .map(memory => ({ memory, photoCount: photoUrls(memory).length }))
    .filter(candidate => candidate.photoCount >= 2)
    .sort((left, right) => left.photoCount - right.photoCount)[0]
  const replayableById = new Map(replayable.map(memory => [String(memory.id), memory]))
  const continuousGroups = []
  let currentContinuousGroup = []
  let previousPoint = null
  let longBreakCount = 0

  timed.forEach((memory) => {
    const point = replayableById.get(String(memory.id))
    if (!point) {
      if (currentContinuousGroup.length) continuousGroups.push(currentContinuousGroup)
      currentContinuousGroup = []
      previousPoint = null
      return
    }

    if (!previousPoint) {
      currentContinuousGroup = [point]
      previousPoint = point
      return
    }

    const distance = distanceMeters(previousPoint, point)
    if (distance > LONG_ROUTE_BREAK_DISTANCE_METERS) {
      longBreakCount += 1
      if (currentContinuousGroup.length) continuousGroups.push(currentContinuousGroup)
      currentContinuousGroup = [point]
    } else {
      currentContinuousGroup.push(point)
    }
    previousPoint = point
  })
  if (currentContinuousGroup.length) continuousGroups.push(currentContinuousGroup)
  const defaultContinuousGroup = continuousGroups.find(group => group.length >= 2)
    || continuousGroups[0]
    || []

  return {
    tripId: String(trip.id),
    tripTitle: trip.title || `Trip ${trip.id}`,
    replayCount: replayable.length,
    locatedCount: located.length,
    groupedSize: Math.max(0, ...groups.map(group => group.length)),
    longBreakCount,
    defaultContinuousCount: defaultContinuousGroup.length,
    singleStationDay: [...days.values()].find(day => day.count === 1) || null,
    multiPhotoMemory: multiPhotoMemory
      ? {
          memoryId: String(multiPhotoMemory.memory.id),
          photoCount: multiPhotoMemory.photoCount,
        }
      : null,
  }
}

function preferTrip(entries, preferredTripId) {
  if (!preferredTripId) return entries
  return [...entries].sort((left, right) => {
    const leftPreferred = left.tripId === String(preferredTripId)
    const rightPreferred = right.tripId === String(preferredTripId)
    return Number(rightPreferred) - Number(leftPreferred)
  })
}

async function fetchApi(page, path) {
  return page.evaluate(async (requestPath) => {
    const response = await fetch(requestPath, { credentials: 'include' })
    const body = await response.json().catch(() => null)
    if (!response.ok || body?.code !== 200) {
      throw new Error(body?.message || `Request failed: ${requestPath} (${response.status})`)
    }
    return body.data
  }, path)
}

export async function discoverMapFixtures(page, preferredTripId = '') {
  const trips = await fetchApi(page, '/api/trips')
  const catalog = []

  for (const trip of Array.isArray(trips) ? trips : []) {
    const memories = await fetchApi(
      page,
      `/api/memories/timeline?tripId=${encodeURIComponent(trip.id)}`,
    )
    catalog.push(analyzeTrip(trip, Array.isArray(memories) ? memories : []))
  }

  const preferred = preferTrip(catalog, preferredTripId)
  const replayCandidates = preferred
    .filter(entry => entry.replayCount >= 2)
    .sort((left, right) => {
      if (preferredTripId) {
        const preferredDifference = Number(right.tripId === String(preferredTripId))
          - Number(left.tripId === String(preferredTripId))
        if (preferredDifference) return preferredDifference
      }
      return left.replayCount - right.replayCount
    })
  const route = replayCandidates[0] || null
  const groupedEntry = preferred.find(entry => entry.groupedSize >= 2) || null
  const galleryEntry = preferred.find(entry => entry.multiPhotoMemory) || null
  const singleDayEntry = preferred.find(
    entry => entry.replayCount >= 2 && entry.singleStationDay,
  ) || null
  const longRouteEntry = preferred.find(
    entry => entry.longBreakCount > 0
      && entry.defaultContinuousCount > 0
      && entry.defaultContinuousCount < entry.replayCount,
  ) || null

  return {
    route,
    replay: route,
    grouped: groupedEntry
      ? { ...groupedEntry, groupSize: groupedEntry.groupedSize }
      : null,
    gallery: galleryEntry
      ? { ...galleryEntry, ...galleryEntry.multiPhotoMemory }
      : null,
    singleStationDay: singleDayEntry
      ? {
          ...singleDayEntry,
          date: singleDayEntry.singleStationDay.date,
          displayDate: singleDayEntry.singleStationDay.date.replaceAll('-', '.'),
          dayNumber: singleDayEntry.singleStationDay.dayNumber,
        }
      : null,
    longRoute: longRouteEntry,
    catalog: catalog.map(entry => ({
      tripId: entry.tripId,
      tripTitle: entry.tripTitle,
      replayCount: entry.replayCount,
      locatedCount: entry.locatedCount,
      longBreakCount: entry.longBreakCount,
    })),
  }
}
