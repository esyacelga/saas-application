import { create } from 'zustand'
import { useAuthStore } from '@/infrastructure/store/auth/auth.store'

export type AppTheme = 'light' | 'dark' | 'dark-blue' | 'slate-carbon' | 'mint-pastel' | 'ocean-blue'

const DEFAULT_THEME: AppTheme = 'light'
const STORAGE_PREFIX = 'gym-theme'

function storageKey(sub: string | undefined): string {
  return sub ? `${STORAGE_PREFIX}-${sub}` : `${STORAGE_PREFIX}-guest`
}

function loadTheme(sub: string | undefined): AppTheme {
  try {
    const stored = localStorage.getItem(storageKey(sub))
    if (stored) return stored as AppTheme
  } catch {}
  return DEFAULT_THEME
}

function saveTheme(sub: string | undefined, theme: AppTheme): void {
  try {
    localStorage.setItem(storageKey(sub), theme)
  } catch {}
}

interface ThemeState {
  theme: AppTheme
  setTheme: (theme: AppTheme) => void
  syncUser: (sub: string | undefined) => void
}

export const useThemeStore = create<ThemeState>((set) => ({
  theme: loadTheme(undefined),

  setTheme: (theme) => {
    const sub = useAuthStore.getState().user?.sub
    saveTheme(sub, theme)
    set({ theme })
    document.body.dataset.layout = theme
  },

  syncUser: (sub) => {
    const theme = loadTheme(sub)
    set({ theme })
    document.body.dataset.layout = theme
  },
}))
