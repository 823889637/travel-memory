import axios from 'axios'

let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

function notifyUnauthorized(config) {
  const url = config?.url || ''
  if (url.startsWith('/api/auth/')) return
  unauthorizedHandler?.()
}

const request = axios.create({
  baseURL: '',
  timeout: 60000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) {
        return body.data
      }
      if (body.code === 401) notifyUnauthorized(response.config)
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const body = error.response?.data
    if (error.response?.status === 401) notifyUnauthorized(error.config)
    if (body && typeof body === 'object' && body.message) {
      return Promise.reject(new Error(body.message))
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，请稍后重试'))
    }
    if (error.message === 'Network Error') {
      return Promise.reject(new Error('网络请求失败，请检查网络连接或确认服务是否可用'))
    }
    return Promise.reject(error)
  }
)

export default request
