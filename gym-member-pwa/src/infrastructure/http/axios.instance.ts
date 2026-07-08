import axios from 'axios'
import { useAuthStore } from '@/infrastructure/store/auth.store'

const api = axios.create({
  baseURL: import.meta.env.VITE_AUTH_API_URL ?? 'http://localhost:8080/api/v1',
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let isRefreshing = false
let queue: Array<(token: string) => void> = []

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error)
    }
    original._retry = true

    if (isRefreshing) {
      return new Promise((resolve) => {
        queue.push((token) => {
          original.headers.Authorization = `Bearer ${token}`
          resolve(api(original))
        })
      })
    }

    isRefreshing = true
    try {
      const refreshToken = useAuthStore.getState().refreshToken
      if (!refreshToken) throw new Error('no refresh token')

      const { data } = await axios.post(
        `${import.meta.env.VITE_AUTH_API_URL ?? 'http://localhost:8080/api/v1'}/auth/refresh`,
        { refresh_token: refreshToken },
      )
      useAuthStore.getState().setAccessToken(data.access_token)
      queue.forEach((cb) => cb(data.access_token))
      queue = []
      original.headers.Authorization = `Bearer ${data.access_token}`
      return api(original)
    } catch {
      useAuthStore.getState().clear()
      return Promise.reject(error)
    } finally {
      isRefreshing = false
    }
  },
)

export default api
