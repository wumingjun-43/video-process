import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    console.log('[REQUEST]', config.method?.toUpperCase(), config.url, config.params, config.data)
    const token = localStorage.getItem('token')
    // 非登录接口和非匿名接口都带上 token
    if (token && !config.url.startsWith('/auth/login') && !config.url.startsWith('/auth/face-login')) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // FormData 请求：让浏览器自动设置 Content-Type 和 boundary
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  (error) => {
    console.error('[REQUEST ERROR]', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    console.log('[RESPONSE]', response.config.url, code, data)
    if (code === 200) {
      return data
    }
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      console.error('[RESPONSE ERROR]', error.config?.url, status, data)
      if (status === 401) {
        const authStore = useAuthStore()
        authStore.logout()
        router.push('/login')
        ElMessage.warning('登录已过期，请重新登录')
      } else if (status === 403) {
        ElMessage.error('权限不足')
      } else if (status >= 500) {
        ElMessage.error('服务器内部错误')
      } else {
        const msg = data?.message || '请求失败'
        ElMessage.error(msg)
      }
    } else {
      console.error('[NETWORK ERROR]', error.message)
      ElMessage.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default request
