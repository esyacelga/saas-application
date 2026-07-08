import axios from 'axios'
import { useAuthStore } from '@/infrastructure/store/auth/auth.store'
import { useLoaderStore } from '@/infrastructure/store/loader/loader.store'

const coreApi = axios.create({
  baseURL: import.meta.env.VITE_API_CORE_URL ?? 'http://localhost:8083/api/v1',
})

coreApi.interceptors.request.use((config) => {
  useLoaderStore.getState().start()
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

coreApi.interceptors.response.use(
  (response) => {
    useLoaderStore.getState().done()
    return response
  },
  (error) => {
    useLoaderStore.getState().done()
    return Promise.reject(error)
  },
)

export default coreApi
