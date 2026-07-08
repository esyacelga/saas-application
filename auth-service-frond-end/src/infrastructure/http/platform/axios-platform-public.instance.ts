import axios from 'axios'
import { useLoaderStore } from '@/infrastructure/store/loader/loader.store'

const platformPublicApi = axios.create({
  baseURL: import.meta.env.VITE_API_PLATFORM_URL ?? 'http://localhost:8081/api/v1',
})

platformPublicApi.interceptors.request.use((config) => {
  useLoaderStore.getState().start()
  return config
})

platformPublicApi.interceptors.response.use(
  (response) => {
    useLoaderStore.getState().done()
    return response
  },
  (error) => {
    useLoaderStore.getState().done()
    return Promise.reject(error)
  },
)

export default platformPublicApi
