const PI = Math.PI
const A = 6378245.0
const EE = 0.00669342162296594323

export function isValidWgs84Coordinate(latitude, longitude) {
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

function isInChina(latitude, longitude) {
  return longitude >= 72.004
    && longitude <= 137.8347
    && latitude >= 0.8293
    && latitude <= 55.8271
}

function transformLatitude(x, y) {
  let result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
    + 0.2 * Math.sqrt(Math.abs(x))
  result += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
  result += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0
  result += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0
  return result
}

function transformLongitude(x, y) {
  let result = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y
    + 0.1 * Math.sqrt(Math.abs(x))
  result += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
  result += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0
  result += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0
  return result
}

// Memory coordinates are stored as WGS84. AMap needs GCJ-02 for points in China.
export function wgs84ToGcj02(latitude, longitude) {
  if (!isValidWgs84Coordinate(latitude, longitude)) {
    return null
  }

  const lat = Number(latitude)
  const lng = Number(longitude)
  if (!isInChina(lat, lng)) {
    return { latitude: lat, longitude: lng }
  }

  let deltaLat = transformLatitude(lng - 105.0, lat - 35.0)
  let deltaLng = transformLongitude(lng - 105.0, lat - 35.0)
  const radLat = lat / 180.0 * PI
  const magic = 1 - EE * Math.sin(radLat) * Math.sin(radLat)
  const sqrtMagic = Math.sqrt(magic)
  deltaLat = (deltaLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
  deltaLng = (deltaLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI)

  return { latitude: lat + deltaLat, longitude: lng + deltaLng }
}
