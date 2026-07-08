import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/infrastructure/store/auth/auth.store'
import { FullScreenLoader } from '@/ui/components/FullScreenLoader'

export function AuthGuard() {
  const { accessToken, user, initialized } = useAuthStore()
  if (!initialized) return <FullScreenLoader />
  if (!accessToken || user?.tipo !== 'staff') return <Navigate to="/login" replace />
  return <Outlet />
}
