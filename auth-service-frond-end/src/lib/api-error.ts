import { isAxiosError } from 'axios'

/**
 * Shape del sobre de error estandarizado (RFC 7807 + `codigo`).
 * Ver docs/gym-administrator/architecture/error-contract.md.
 * `detail` es el campo RFC 7807; `mensaje` es su alias en período de gracia;
 * `message`/`error` son formatos legacy de servicios aún no migrados.
 */
interface ApiErrorData {
  detail?: string
  mensaje?: string
  message?: string
  error?: string
  codigo?: string
}

export function getApiErrorStatus(err: unknown): number | null {
  if (isAxiosError(err)) return err.response?.status ?? null
  return null
}

export function getApiErrorMessage(err: unknown): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as ApiErrorData | undefined
    return (
      data?.detail ??
      data?.mensaje ??
      data?.message ??
      data?.error ??
      'Error desconocido'
    )
  }
  return 'Error desconocido'
}

/**
 * Código de negocio legible por máquina (`codigo`) del sobre estandarizado.
 * Úsalo para lógica/UI contextual (i18n, límites de plan) en lugar del texto
 * crudo del backend. Devuelve `null` si la respuesta no lo trae.
 */
export function getApiErrorCode(err: unknown): string | null {
  if (isAxiosError(err)) {
    return (err.response?.data as ApiErrorData | undefined)?.codigo ?? null
  }
  return null
}

/**
 * Extensión arbitraria del sobre RFC 7807 (los campos snake_case que cada error
 * añade además de los 5 estándar — p. ej. `fecha_envio_previo`, `plan_actual`).
 * Devuelve `null` si la respuesta no la trae.
 */
export function getApiErrorExtension(err: unknown, campo: string): unknown {
  if (isAxiosError(err)) {
    const data = err.response?.data as Record<string, unknown> | undefined
    return data?.[campo] ?? null
  }
  return null
}
