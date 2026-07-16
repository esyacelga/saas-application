import { create } from 'zustand'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import { useAuthStore } from '@/infrastructure/store/auth/auth.store'
import type { JwtPayloadStaff } from '@/domain/auth/entities/User.entity'

const STORAGE_PREFIX = 'gym-onboarding'

function storageKey(idCompania: number): string {
  return `${STORAGE_PREFIX}-${idCompania}`
}

interface OnboardingState {
  totalTipos: number
  tiposActivos: number
  totalClientes: number
  checklistOculto: boolean
  cargado: boolean
  hidratarDesdeApi: (idCompania: number) => Promise<void>
  marcarOculto: () => void
}

export const useOnboardingStore = create<OnboardingState>((set, get) => ({
  totalTipos: 0,
  tiposActivos: 0,
  totalClientes: 0,
  checklistOculto: false,
  cargado: false,

  hidratarDesdeApi: async (idCompania: number) => {
    try {
      const storedOculto = localStorage.getItem(storageKey(idCompania))
      const checklistOculto = storedOculto === 'true'

      const [tipos, clientesRes] = await Promise.all([
        coreRepository.getTiposMembresia(),
        coreRepository.getClientes({ limit: 1 }),
      ])

      const totalTipos = tipos.length
      const tiposActivos = tipos.filter(t => t.activo).length
      const totalClientes = clientesRes.total

      set({ totalTipos, tiposActivos, totalClientes, cargado: true, checklistOculto })

      if (totalTipos > 0 && totalClientes > 0 && !checklistOculto) {
        get().marcarOculto()
      }
    } catch (err) {
      console.error('[OnboardingStore] hidratarDesdeApi failed:', err)
      set({ cargado: true })
    }
  },

  marcarOculto: () => {
    const user = useAuthStore.getState().user
    if (!user || user.tipo !== 'staff') return
    const idCompania = (user as JwtPayloadStaff).id_compania
    try {
      localStorage.setItem(storageKey(idCompania), 'true')
    } catch {}
    set({ checklistOculto: true })
  },
}))
