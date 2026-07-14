const DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000

function toUtcDay(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!match) return null

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const timestamp = Date.UTC(year, month - 1, day)
  const parsed = new Date(timestamp)

  if (
    parsed.getUTCFullYear() !== year
    || parsed.getUTCMonth() !== month - 1
    || parsed.getUTCDate() !== day
  ) {
    return null
  }
  return timestamp
}

export function getTripDayNumber(startDate, recordDate, fallback = 1) {
  const start = toUtcDay(startDate)
  const current = toUtcDay(recordDate)
  if (start == null || current == null) return fallback

  const dayNumber = Math.floor((current - start) / DAY_IN_MILLISECONDS) + 1
  return Number.isInteger(dayNumber) && dayNumber > 0 ? dayNumber : fallback
}

export function getTripDurationDays(startDate, endDate) {
  const start = toUtcDay(startDate)
  const end = toUtcDay(endDate)
  if (start == null || end == null) return null

  const duration = Math.floor((end - start) / DAY_IN_MILLISECONDS) + 1
  return Number.isInteger(duration) && duration > 0 ? duration : null
}

export function getChronologicalTripDayNumber(startDate, recordDate, fallback = 1, previousDayNumber = 0) {
  const calculated = getTripDayNumber(startDate, recordDate, fallback)
  const nextChronologicalDay = Number.isInteger(previousDayNumber) && previousDayNumber > 0
    ? previousDayNumber + 1
    : 1

  return Math.max(calculated, nextChronologicalDay)
}
