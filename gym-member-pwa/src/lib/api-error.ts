import type { AxiosError } from 'axios'

interface ApiErrorData {
  mensaje?: string
  message?: string
  error?: string
  codigo?: string
}

export function getApiErrorMessage(err: unknown, fallback = 'Error inesperado'): string {
  const e = err as AxiosError<ApiErrorData>
  return (
    e?.response?.data?.mensaje ??
    e?.response?.data?.message ??
    e?.response?.data?.error ??
    e?.message ??
    fallback
  )
}

export function getApiErrorCode(err: unknown): string | null {
  const e = err as AxiosError<ApiErrorData>
  return e?.response?.data?.codigo ?? null
}
