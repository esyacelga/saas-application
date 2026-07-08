import { create } from 'zustand'

interface LoaderStore {
  activeRequests: number
  start: () => void
  done: () => void
  isLoading: () => boolean
}

export const useLoaderStore = create<LoaderStore>((set, get) => ({
  activeRequests: 0,
  start: () => set((s) => ({ activeRequests: s.activeRequests + 1 })),
  done: () => set((s) => ({ activeRequests: Math.max(0, s.activeRequests - 1) })),
  isLoading: () => get().activeRequests > 0,
}))
