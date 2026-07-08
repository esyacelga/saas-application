import { create } from 'zustand'
import type { AuthStorePort } from '@/domain/auth/ports/AuthStore.port'
import type { JwtPayloadStaff, JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { decodeJwt } from '@/lib/jwt'

// Import diferido para evitar dependencia circular (auth ← theme ← auth)
function syncTheme(sub: string | undefined) {
  import('@/infrastructure/store/theme/theme.store').then(({ useThemeStore }) => {
    useThemeStore.getState().syncUser(sub)
  })
}

export const useAuthStore = create<AuthStorePort>((set) => ({
  accessToken: null,
  user: null,
  initialized: false,

  setSession: (token) => {
    const payload = decodeJwt(token) as unknown as JwtPayloadStaff | JwtPayloadPlataforma
    set({ accessToken: token, user: payload })
    syncTheme(payload.sub)
  },

  setAccessToken: (token) => {
    const payload = decodeJwt(token) as unknown as JwtPayloadStaff | JwtPayloadPlataforma
    set({ accessToken: token, user: payload })
    syncTheme(payload.sub)
  },

  logout: () => {
    set({ accessToken: null, user: null })
    syncTheme(undefined)
  },

  setInitialized: () => set({ initialized: true }),
}))

export const useIsAuthenticated = () =>
  useAuthStore((s) => s.accessToken !== null && s.accessToken !== undefined )

export const useCurrentUser = () =>
  useAuthStore((s) => s.user)

export const useHasPermission = (permiso: string) =>
  useAuthStore((s) => {
    const user = s.user
    if (!user || user.tipo !== 'staff') return false
    return (user as JwtPayloadStaff).permisos.includes(permiso)
  })

export const useIsPlatformUser = () =>
  useAuthStore((s) => s.user?.tipo === 'plataforma')
