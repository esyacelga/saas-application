import axios from 'axios'
import { useAuthStore } from '@/infrastructure/store/auth/auth.store'
import { useLoaderStore } from '@/infrastructure/store/loader/loader.store'

const attendanceApi = axios.create({
  baseURL: import.meta.env.VITE_API_ATTENDANCE_URL ?? 'http://localhost:8084/api/v1',
})

attendanceApi.interceptors.request.use((config) => {
  useLoaderStore.getState().start()
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

attendanceApi.interceptors.response.use(
  (response) => {
    useLoaderStore.getState().done()
    return response
  },
  (error) => {
    useLoaderStore.getState().done()
    return Promise.reject(error)
  },
)

export default attendanceApi
