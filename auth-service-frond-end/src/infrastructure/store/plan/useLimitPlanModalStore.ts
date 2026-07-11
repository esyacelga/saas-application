import { create } from 'zustand'

interface LimitePlanState {
  abierto: boolean
  recurso: string | null       // "clientes" | "staff" | "sucursales"
  actual: number | null
  maximo: number | null
  planActual: string | null    // "FREE" | "TRIAL"
  refetchWidget: number
}

interface LimitePlanActions {
  abrirModal: (payload: { recurso: string; actual: number; maximo: number; planActual: string }) => void
  cerrarModal: () => void
  triggerRefetch: () => void
}

type LimitePlanStore = LimitePlanState & LimitePlanActions

export const useLimitPlanModalStore = create<LimitePlanStore>((set) => ({
  abierto: false,
  recurso: null,
  actual: null,
  maximo: null,
  planActual: null,
  refetchWidget: 0,

  abrirModal: (payload) =>
    set({ abierto: true, ...payload }),

  // No reseteamos los campos de payload para permitir animación de salida del modal
  cerrarModal: () => set({ abierto: false }),

  triggerRefetch: () =>
    set((s) => ({ refetchWidget: s.refetchWidget + 1 })),
}))
