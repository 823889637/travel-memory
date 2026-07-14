export function formatLocalDateTime(date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return ''
  const offset = date.getTimezoneOffset()
  const localDate = new Date(date.getTime() - offset * 60 * 1000)
  return localDate.toISOString().slice(0, 16)
}

export function toDateTimeLocalValue(value) {
  if (!value) return ''

  if (value instanceof Date) return formatLocalDateTime(value)

  const text = String(value).trim()
  const localMatch = text.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}):(\d{2})/)
  if (localMatch) return `${localMatch[1]}T${localMatch[2]}:${localMatch[3]}`

  const parsed = new Date(text)
  return Number.isNaN(parsed.getTime()) ? '' : formatLocalDateTime(parsed)
}

export function formatDisplayDateTime(value) {
  const dateTimeValue = toDateTimeLocalValue(value)
  return dateTimeValue ? dateTimeValue.replace('T', ' ') : ''
}
