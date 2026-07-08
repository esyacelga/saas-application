import { Navigate } from 'react-router-dom'
import { useHasPermission } from '@/infrastructure/store/auth/auth.store'

interface Props {
  permiso: string
  children: React.ReactNode
}

export function PermissionGuard({ permiso, children }: Props) {
  const tiene = useHasPermission(permiso)
  if (!tiene) return <Navigate to="/admin/sin-acceso" replace />
  return <>{children}</>
}

export function IfPermission({ permiso, children }: Props) {
  const tiene = useHasPermission(permiso)
  return tiene ? <>{children}</> : null
}
