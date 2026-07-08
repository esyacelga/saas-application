import { create } from 'zustand'
import type { Compania, Plan, Caracteristica } from '@/domain/platform/entities/Plan.entity'

interface PlatformState {
  companias: Compania[]
  selectedCompania: Compania | null
  planes: Plan[]
  caracteristicas: Caracteristica[]

  setCompanias: (companias: Compania[]) => void
  setSelectedCompania: (compania: Compania | null) => void
  setPlanes: (planes: Plan[]) => void
  setCaracteristicas: (caracteristicas: Caracteristica[]) => void
  reset: () => void
}

export const usePlatformStore = create<PlatformState>((set) => ({
  companias: [],
  selectedCompania: null,
  planes: [],
  caracteristicas: [],

  setCompanias: (companias) => set({ companias }),
  setSelectedCompania: (selectedCompania) => set({ selectedCompania }),
  setPlanes: (planes) => set({ planes }),
  setCaracteristicas: (caracteristicas) => set({ caracteristicas }),
  reset: () => set({ companias: [], selectedCompania: null, planes: [], caracteristicas: [] }),
}))
