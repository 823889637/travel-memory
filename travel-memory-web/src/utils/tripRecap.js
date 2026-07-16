import { getChronologicalTripDayNumber, getTripDurationDays } from './tripDay'

function timeValue(value) {
  if (!value) return Number.MAX_SAFE_INTEGER
  const parsed = new Date(value).getTime()
  return Number.isNaN(parsed) ? Number.MAX_SAFE_INTEGER : parsed
}

function dateKey(value) {
  return value ? String(value).slice(0, 10) : '未知日期'
}

function photoCount(memory) {
  return Number(memory?.photoCount || (memory?.photoUrl ? 1 : 0))
}

function selectRepresentative(memories) {
  return memories.find(memory => memory.isFavorite === 1 && memory.photoUrl)
    || memories.find(memory => memory.photoUrl)
    || memories[0]
    || null
}

export function buildTripRecap(trip, sourceMemories = []) {
  const memories = [...sourceMemories].sort((left, right) => {
    const byRecordTime = timeValue(left.recordTime) - timeValue(right.recordTime)
    return byRecordTime || timeValue(left.createTime) - timeValue(right.createTime)
  })
  const groups = new Map()
  const placeCounts = new Map()

  memories.forEach((memory, index) => {
    const key = dateKey(memory.recordTime)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(memory)

    const place = String(memory.locationName || '').trim()
    if (place) {
      const current = placeCounts.get(place) || { name: place, count: 0, firstIndex: index }
      current.count += 1
      placeCounts.set(place, current)
    }
  })

  let previousDayNumber = 0
  const days = Array.from(groups.entries()).map(([date, dayMemories], index) => {
    const locations = [...new Set(dayMemories.map(item => String(item.locationName || '').trim()).filter(Boolean))]
    const dayNumber = getChronologicalTripDayNumber(trip?.startDate, date, index + 1, previousDayNumber)
    previousDayNumber = dayNumber
    return {
      date,
      dayNumber,
      memories: dayMemories,
      memoryCount: dayMemories.length,
      photoCount: dayMemories.reduce((total, memory) => total + photoCount(memory), 0),
      earliestTime: dayMemories[0]?.recordTime || null,
      latestTime: dayMemories[dayMemories.length - 1]?.recordTime || null,
      locations,
      representative: selectRepresentative(dayMemories),
    }
  })

  const sortedPlaces = [...placeCounts.values()]
    .sort((left, right) => right.count - left.count || left.firstIndex - right.firstIndex)
  const places = sortedPlaces.slice(0, 8)

  return {
    days,
    places,
    favorites: memories.filter(memory => memory.isFavorite === 1),
    memoryCount: memories.length,
    photoCount: memories.reduce((total, memory) => total + photoCount(memory), 0),
    favoriteCount: memories.filter(memory => memory.isFavorite === 1).length,
    locatedCount: memories.filter(memory => memory.latitude != null && memory.longitude != null).length,
    placeCount: sortedPlaces.length,
    recordedDayCount: days.length,
    durationDays: getTripDurationDays(trip?.startDate, trip?.endDate),
  }
}
