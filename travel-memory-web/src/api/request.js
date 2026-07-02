import axios from 'axios'

const request = axios.create({
  baseURL: '',
  timeout: 15000,
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
      return Promise.reject(new Error('请求超时，请检查后端服务是否正常运行'))
    }
    if (error.message === 'Network Error') {
      return Promise.reject(new Error('网络请求失败，请确认后端服务已启动'))
    }
    return Promise.reject(error)
  }
)

export default request
