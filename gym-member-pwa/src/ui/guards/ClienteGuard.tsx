import { Navigate } from 'react-router-dom'
import { useAuthStore, useIsAuthenticated } from '@/infrastructure/store/auth.store'

export function ClienteGuard({ children }: { children: React.ReactNode }) {
  const initialized = useAuthStore((s) => s.initialized)
  const authenticated = useIsAuthenticated()

  if (!initialized) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-slate-700 border-t-accent-500" />
      </div>
    )
  }

  if (!authenticated) return <Navigate to="/login" replace />

  return <>{children}</>
}
