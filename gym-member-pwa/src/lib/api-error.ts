import type { AxiosError } from 'axios'

interface ApiErrorData {
  detail?: string
  mensaje?: string
  message?: string
  error?: string
  codigo?: string
}

export function getApiErrorMessage(err: unknown, fallback = 'Error inesperado'): string {
  const e = err as AxiosError<ApiErrorData>
  return (
    // `detail` es el campo RFC 7807 del sobre estandarizado; el resto son alias
    // (mensaje) y formatos legacy de servicios aún no migrados.
    e?.response?.data?.detail ??
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
