const KEY = 'auth_rt'

export const getStoredRefreshToken = (): string | null =>
  sessionStorage.getItem(KEY)

export const storeRefreshToken = (token: string): void =>
  sessionStorage.setItem(KEY, token)

export const clearStoredRefreshToken = (): void =>
  sessionStorage.removeItem(KEY)
