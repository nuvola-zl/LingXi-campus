import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { getApiBaseURL } from '@/utils/apiConfig'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  baseURL: getApiBaseURL(),
  timeout: 30000,
})

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers = config.headers || {}
      config.headers['Authorization'] = token
    }
    const hasContentType = !!(config.headers && (config.headers as any)['Content-Type'])
    if (!hasContentType && !(config.data instanceof FormData)) {
      config.headers = config.headers || {}
      config.headers['Content-Type'] = 'application/json;charset=utf-8'
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response): any => {
    let res: any = response.data
    if (typeof res === 'string') {
      res = res ? JSON.parse(res) : res
    }
    return res
  },
  (error) => {
    if (error?.response) {
      const status = error.response.status
      const errorData = error.response.data
      if (status === 500) {
        ElMessage.error('服务端内部错误，请稍后重试')
      } else if (status === 401) {
        const userStore = useUserStore()
        ElMessage.error('请先登录')
        userStore.logout()
        setTimeout(() => {
          window.dispatchEvent(new CustomEvent('open-login-dialog'))
        }, 100)
      } else if (status === 403) {
        ElMessage.error('权限不足')
      } else {
        ElMessage.error(errorData?.message || errorData?.msg || `请求失败 (${status})`)
      }
    } else if (error?.message?.includes('timeout')) {
      ElMessage.error('请求超时，请检查网络连接')
    }
    return Promise.reject(error)
  },
)

export default request