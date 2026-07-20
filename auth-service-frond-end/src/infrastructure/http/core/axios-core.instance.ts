import axios from 'axios'
import { useAuthStore } from '@/infrastructure/store/auth/auth.store'
import { useLoaderStore } from '@/infrastructure/store/loader/loader.store'
import { useLimitPlanModalStore } from '@/infrastructure/store/plan/useLimitPlanModalStore'

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

    // Detectar límite de plan alcanzado — solo esta condición exacta abre el modal.
    // Otros 403 (permisos de rol, token inválido) se propagan sin cambios.
    if (
      error.response?.status === 403 &&
      typeof error.response.data?.codigo === 'string' &&
      error.response.data.codigo === 'limite_plan_alcanzado'
    ) {
      // El sobre estandarizado (RFC 7807 + codigo) emite la metadata en snake_case:
      // `plan_actual` (antes `planActual`). Ver error-contract.md, riesgo #1.
      const { recurso, actual, maximo, plan_actual: planActual } = error.response.data as {
        recurso: string
        actual: number
        maximo: number
        plan_actual: string
      }
      useLimitPlanModalStore.getState().abrirModal({ recurso, actual, maximo, planActual })
    }

    return Promise.reject(error)
  },
)

export default coreApi
