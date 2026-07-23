export function calculateUploadProgress(event, file) {
  const loaded = Number(event?.loaded)
  if (!Number.isFinite(loaded) || loaded < 0) return null

  const reportedTotal = Number(event?.total)
  const fileSize = Number(file?.size)
  const total = Number.isFinite(reportedTotal) && reportedTotal > 0
    ? reportedTotal
    : Number.isFinite(fileSize) && fileSize > 0
      ? fileSize
      : null

  if (total === null) return null
  return Math.min(99, Math.max(0, Math.round((loaded / total) * 100)))
}
