import { isAxiosError } from 'axios'

export function getApiErrorStatus(err: unknown): number | null {
  if (isAxiosError(err)) return err.response?.status ?? null
  return null
}

export function getApiErrorMessage(err: unknown): string {
  if (isAxiosError(err)) {
    return (err.response?.data as { mensaje?: string })?.mensaje ?? 'Error desconocido'
  }
  return 'Error desconocido'
}
