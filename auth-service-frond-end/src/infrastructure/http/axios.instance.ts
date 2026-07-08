import axios, { type AxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/infrastructure/store/auth/auth.store'
import { getStoredRefreshToken, clearStoredRefreshToken } from '@/lib/refresh-token-storage'
import { useLoaderStore } from '@/infrastructure/store/loader/loader.store'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_AUTH_URL ?? import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  withCredentials: true,
})

api.interceptors.request.use((config) => {
  if (!config.url?.includes('/auth/refresh')) {
    useLoaderStore.getState().start()
  }
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let isRefreshing = false
let pendingQueue: Array<{ resolve: (t: string) => void; reject: (e: unknown) => void }> = []

const flushQueue = (token: string | null, error: unknown = null) => {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (token) resolve(token)
    else reject(error)
  })
  pendingQueue = []
}

api.interceptors.response.use(
  (response) => {
    useLoaderStore.getState().done()
    return response
  },
  async (error) => {
    useLoaderStore.getState().done()
    const originalRequest: AxiosRequestConfig & { _retry?: boolean } = error.config
    const isAuthEndpoint =
      originalRequest.url?.includes('/auth/refresh') ||
      originalRequest.url?.includes('/auth/login') ||
      originalRequest.url?.includes('/auth/platform/login')

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push({
            resolve: (token) => {
              if (originalRequest.headers) originalRequest.headers['Authorization'] = `Bearer ${token}`
              resolve(api(originalRequest))
            },
            reject,
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const rt = getStoredRefreshToken()
        const { data } = await api.post<{ access_token: string }>(
          '/auth/refresh',
          rt ? { refresh_token: rt } : undefined,
        )
        useAuthStore.getState().setAccessToken(data.access_token)
        flushQueue(data.access_token)
        if (originalRequest.headers) originalRequest.headers['Authorization'] = `Bearer ${data.access_token}`
        return api(originalRequest)
      } catch (refreshError) {
        flushQueue(null, refreshError)
        clearStoredRefreshToken()
        useAuthStore.getState().logout()
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)

export default api
