import axios from 'axios'
import { useAuthStore } from '@/infrastructure/store/auth/auth.store'
import { useLoaderStore } from '@/infrastructure/store/loader/loader.store'

/**
 * Axios instance dedicated to platform-service (port 8081).
 * Uses VITE_API_PLATFORM_URL. Falls back to VITE_API_BASE_URL for local dev
 * if VITE_API_PLATFORM_URL is not set.
 *
 * This instance shares the same JWT from the auth store but targets a
 * different microservice base URL. Refresh-token logic lives only in the
 * auth axios instance — if platform-service returns 401 it means the token
 * itself is invalid (the auth-service already handles refresh via its own
 * interceptor on the auth instance).
 */
const platformApi = axios.create({
  baseURL: import.meta.env.VITE_API_PLATFORM_URL ?? 'http://localhost:8081/api/v1',
})

platformApi.interceptors.request.use((config) => {
  useLoaderStore.getState().start()
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

platformApi.interceptors.response.use(
  (response) => {
    useLoaderStore.getState().done()
    return response
  },
  (error) => {
    useLoaderStore.getState().done()
    return Promise.reject(error)
  },
)

export default platformApi
