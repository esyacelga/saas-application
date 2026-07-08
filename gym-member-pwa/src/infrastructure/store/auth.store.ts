import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { ClienteToken } from '@/domain/model/auth'
import { usePerfilStore } from './perfil.store'

function parseToken(token: string): ClienteToken | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    if (payload.tipo !== 'cliente') return null
    return payload as ClienteToken
  } catch {
    return null
  }
}

export interface GymInfo {
  id_compania: number | null
  logo_url: string | null
  nombre_compania: string | null
  nombre_sucursal: string | null
  id_sucursal: number | null
}

interface AuthStore {
  accessToken: string | null
  refreshToken: string | null
  user: ClienteToken | null
  initialized: boolean
  gymInfo: GymInfo | null
  setTokens: (access: string, refresh: string) => void
  setAccessToken: (access: string) => void
  clear: () => void
  setInitialized: () => void
  setGymInfo: (info: GymInfo | null) => void
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      initialized: false,
      gymInfo: null,

      setTokens: (access, refresh) =>
        set({ accessToken: access, refreshToken: refresh, user: parseToken(access) }),

      setAccessToken: (access) =>
        set({ accessToken: access, user: parseToken(access) }),

      clear: () => {
        usePerfilStore.getState().invalidate()
        set({ accessToken: null, refreshToken: null, user: null })
      },

      setInitialized: () => set({ initialized: true }),

      setGymInfo: (info) => set({ gymInfo: info }),
    }),
    {
      name: 'gym-member-auth',
      partialize: (s) => ({
        accessToken: s.accessToken,
        refreshToken: s.refreshToken,
        gymInfo: s.gymInfo,
      }),
    },
  ),
)

export const useIsAuthenticated = () => useAuthStore((s) => !!s.accessToken && !!s.user)
export const useCurrentUser = () => useAuthStore((s) => s.user)
