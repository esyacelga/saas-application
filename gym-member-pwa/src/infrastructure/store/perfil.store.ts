import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { MiPerfilResponse } from '@/infrastructure/http/CoreHttpRepository'

const STALE_MS = 5 * 60 * 1000

interface PerfilStore {
  data: MiPerfilResponse | null
  fetchedAt: number | null
  setData: (data: MiPerfilResponse) => void
  invalidate: () => void
}

export const usePerfilStore = create<PerfilStore>()(
  persist(
    (set) => ({
      data: null,
      fetchedAt: null,
      setData: (data) => set({ data, fetchedAt: Date.now() }),
      invalidate: () => set({ data: null, fetchedAt: null }),
    }),
    {
      name: 'gym-member-perfil',
      partialize: (s) => ({ data: s.data, fetchedAt: s.fetchedAt }),
    },
  ),
)

export const isPerfilStale = (fetchedAt: number | null): boolean =>
  !fetchedAt || Date.now() - fetchedAt > STALE_MS