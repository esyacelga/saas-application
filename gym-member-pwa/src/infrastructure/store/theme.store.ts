import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type ThemeId = 'acero' | 'volcan' | 'bosque' | 'coral' | 'violeta' | 'aurora'

export interface ThemeMeta {
  id: ThemeId
  name: string
  hex: string
  gender: 'M' | 'F'
}

export const THEMES: ThemeMeta[] = [
  { id: 'acero',   name: 'Acero',   hex: '#2563eb', gender: 'M' },
  { id: 'volcan',  name: 'Volcán',  hex: '#ea580c', gender: 'M' },
  { id: 'bosque',  name: 'Bosque',  hex: '#16a34a', gender: 'M' },
  { id: 'coral',   name: 'Coral',   hex: '#e11d48', gender: 'F' },
  { id: 'violeta', name: 'Violeta', hex: '#9333ea', gender: 'F' },
  { id: 'aurora',  name: 'Aurora',  hex: '#0891b2', gender: 'F' },
]

interface ThemeStore {
  theme: ThemeId
  userCustomized: boolean
  setTheme: (theme: ThemeId) => void
  initTheme: (sexo: string | null) => void
}

export const useThemeStore = create<ThemeStore>()(
  persist(
    (set, get) => ({
      theme: 'acero',
      userCustomized: false,

      setTheme: (theme) => set({ theme, userCustomized: true }),

      initTheme: (sexo) => {
        if (get().userCustomized) return
        const defaultTheme: ThemeId = sexo === 'F' ? 'coral' : 'acero'
        set({ theme: defaultTheme })
      },
    }),
    { name: 'gym-member-theme' },
  ),
)
