import axios from 'axios'
import { useAuthStore } from '@/infrastructure/store/auth.store'

const attendanceApi = axios.create({
  baseURL: import.meta.env.VITE_ATTENDANCE_API_URL ?? 'http://localhost:8082/api/v1',
})

attendanceApi.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export default attendanceApi
