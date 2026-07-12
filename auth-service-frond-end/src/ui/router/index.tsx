import { createBrowserRouter, Navigate } from 'react-router-dom'
import { PublicLayout } from '@/ui/layouts/PublicLayout'
import { AuthGuard } from './guards/AuthGuard'
import { PlatformGuard } from './guards/PlatformGuard'
import { LoginPage } from '@/ui/features/auth/pages/LoginPage'
import { AutoRegistroPage } from '@/ui/features/auth/pages/AutoRegistroPage'
import { PlatformLoginPage } from '@/ui/features/auth/pages/PlatformLoginPage'
import { ResetRequestPage } from '@/ui/features/auth/pages/ResetRequestPage'
import { ResetConfirmPage } from '@/ui/features/auth/pages/ResetConfirmPage'
import { ChangePasswordPage } from '@/ui/features/auth/pages/ChangePasswordPage'
import { AdminLayout } from '@/ui/layouts/AdminLayout'
import { SinAccesoPage } from '@/ui/features/auth/pages/SinAccesoPage'
import { UsuariosPage } from '@/ui/features/auth/pages/UsuariosPage'
import { BitacoraPage } from '@/ui/features/auth/pages/BitacoraPage'
import { ClientesAppPage } from '@/ui/features/auth/pages/ClientesAppPage'
import { RolesPage } from '@/ui/features/auth/pages/RolesPage'
import { PlatformLayout } from '@/ui/layouts/PlatformLayout'
import { PlatformUsuariosPage } from '@/ui/features/auth/pages/platform/PlatformUsuariosPage'
import { PlatformRolesPage } from '@/ui/features/auth/pages/platform/PlatformRolesPage'
import { PlanesPage } from '@/ui/features/platform/pages/PlanesPage'
import { CaracteristicasPage } from '@/ui/features/platform/pages/CaracteristicasPage'
import { CompaniasPage } from '@/ui/features/platform/pages/CompaniasPage'
import { CompaniaDetallePage } from '@/ui/features/platform/pages/CompaniaDetallePage'
import { PlatformActividadPage } from '@/ui/features/platform/pages/PlatformActividadPage'
import { PlatformDashboardPage } from '@/ui/features/platform/pages/PlatformDashboardPage'
import { PersonasPage } from '@/ui/features/platform/pages/PersonasPage'
import { PersonaDetallePage } from '@/ui/features/platform/pages/PersonaDetallePage'
import { PagosPendientesPage } from '@/ui/features/platform/pages/PagosPendientesPage'
import { ClientesPage } from '@/ui/features/core/pages/ClientesPage'
import { TiposMembresiaPage } from '@/ui/features/core/pages/TiposMembresiaPage'
import { ConfiguracionPage } from '@/ui/features/admin/pages/ConfiguracionPage'
import { DashboardPage } from '@/ui/features/admin/pages/DashboardPage'
import { MiSuscripcionPage } from '@/ui/features/admin/pages/MiSuscripcionPage'

export const router = createBrowserRouter([
  // Rutas públicas (layout con branding gym)
  {
    element: <PublicLayout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/registro', element: <AutoRegistroPage /> },
      { path: '/platform/login', element: <PlatformLoginPage /> },
      { path: '/reset-password', element: <ResetRequestPage /> },
      { path: '/reset-password/confirm', element: <ResetConfirmPage /> },
    ],
  },

  // Rutas protegidas con JWT
  {
    element: <AuthGuard />,
    children: [
      // Cambio de contraseña (sin sidebar — flujo previo al panel)
      { path: '/change-password', element: <ChangePasswordPage /> },

      // Panel admin con sidebar
      {
        element: <AdminLayout />,
        children: [
          { path: '/admin', element: <Navigate to="/admin/dashboard" replace /> },
          { path: '/admin/dashboard', element: <DashboardPage /> },
          { path: '/admin/sin-acceso', element: <SinAccesoPage /> },
          { path: '/admin/usuarios', element: <UsuariosPage /> },
          { path: '/admin/bitacora', element: <BitacoraPage /> },
          { path: '/admin/clientes/app', element: <ClientesAppPage /> },
          { path: '/admin/roles', element: <RolesPage /> },
          { path: '/admin/clientes', element: <ClientesPage /> },
          { path: '/admin/tipos-membresia', element: <TiposMembresiaPage /> },
          { path: '/admin/configuracion', element: <ConfiguracionPage /> },
          { path: '/admin/mi-suscripcion', element: <MiSuscripcionPage /> },
        ],
      },
    ],
  },

  // Panel plataforma
  {
    element: <PlatformGuard />,
    children: [
      {
        element: <PlatformLayout />,
        children: [
          { path: '/platform', element: <Navigate to="/platform/dashboard" replace /> },
          { path: '/platform/dashboard', element: <PlatformDashboardPage /> },
          { path: '/platform/usuarios', element: <PlatformUsuariosPage /> },
          { path: '/platform/roles', element: <PlatformRolesPage /> },
          { path: '/platform/planes', element: <PlanesPage /> },
          { path: '/platform/caracteristicas', element: <CaracteristicasPage /> },
          { path: '/platform/companias', element: <CompaniasPage /> },
          { path: '/platform/companias/:id', element: <CompaniaDetallePage /> },
          { path: '/platform/actividad', element: <PlatformActividadPage /> },
          { path: '/platform/personas', element: <PersonasPage /> },
          { path: '/platform/personas/:id', element: <PersonaDetallePage /> },
          { path: '/platform/pagos-pendientes', element: <PagosPendientesPage /> },
        ],
      },
    ],
  },

  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '*', element: <Navigate to="/login" replace /> },
])
