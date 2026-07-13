import axios from 'axios'

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
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const body = error.response?.data
    if (body && typeof body === 'object' && body.message) {
      return Promise.reject(new Error(body.message))
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，请检查图片大小或稍后重试'))
    }
    if (error.message === 'Network Error') {
      return Promise.reject(new Error('网络请求失败，请确认后端服务正常，或检查图片是否过大'))
    }
    return Promise.reject(error)
  }
)

export default request
