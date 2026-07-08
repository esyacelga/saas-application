import axios from 'axios'
import { useAuthStore } from '@/infrastructure/store/auth.store'

const coreApi = axios.create({
  baseURL: import.meta.env.VITE_CORE_API_URL ?? 'http://localhost:8083/api/v1',
})

coreApi.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export default coreApi
