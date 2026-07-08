# IMPL-11 — PlatformLayout + PlatformUsuariosPage (Panel SaaS)

> **Pantalla:** P-10 Operadores de plataforma  
> **Complejidad:** ★★★☆☆  
> **Prerequisito:** IMPL-07 (patrón CRUD) y IMPL-06 (patrón layout)  
> **Resultado:** Panel oscuro tipo SaaS para operadores de plataforma; gestiona quién puede acceder al panel de control de la plataforma

---

## Diferencias con el panel Staff

| Aspecto | Panel Staff | Panel Plataforma |
|---|---|---|
| Audiencia | No técnicos (recepcionistas, etc.) | Técnicos (super_admin, soporte, viewer) |
| Tema visual | Sidebar negro con naranja | Sidebar muy oscuro, tipografía densa |
| JWT requerido | `tipo: 'staff'` | `tipo: 'plataforma'` |
| Guard | `AuthGuard` | `PlatformGuard` |
| Rutas | `/admin/*` | `/platform/*` |

---

## Archivos que se crean en este paso

```
src/
├── layouts/
│   └── PlatformLayout.tsx
└── features/auth/
    ├── schemas/
    │   └── operador.schema.ts
    ├── components/
    │   └── CrearOperadorModal.tsx
    └── pages/platform/
        └── PlatformUsuariosPage.tsx
```

También se actualiza `src/router/index.tsx`.

---

## Vista del PlatformLayout

```
┌──────────────────────────────────────────────────────────┐
│  Plataforma     │  (contenido oscuro bg-slate-950)        │
│  Gym Admin      │                                         │
│  ─────────────  │                                         │
│  [SU] Super A.  │  Contenido de la página (Outlet)        │
│  ─────────────  │                                         │
│ › Dashboard     │                                         │
│ › Operadores    │                                         │
│ › Actividad     │                                         │
│  ─────────────  │                                         │
│  nombre@...     │                                         │
│  Cerrar sesión  │                                         │
└──────────────────────────────────────────────────────────┘
```

---

## 1. PlatformLayout

**`src/layouts/PlatformLayout.tsx`:**
```tsx
import { Outlet, NavLink } from 'react-router-dom'
import {
  LayoutDashboard, Users, Activity,
  LogOut, ChevronRight, Dumbbell,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { useCurrentUser, useAuthStore } from '@/features/auth/store/auth.store'
import { logout as apiLogout } from '@/api/auth.api'
import type { JwtPayloadPlataforma } from '@/types'

const ROLE_BADGE_CLASS: Record<string, string> = {
  super_admin: 'bg-orange-500/20 text-orange-400 border border-orange-500/30',
  soporte:     'bg-blue-500/20 text-blue-400 border border-blue-500/30',
  viewer:      'bg-slate-600/40 text-slate-400 border border-slate-700',
}

const ROLE_LABEL: Record<string, string> = {
  super_admin: 'Super Admin',
  soporte:     'Soporte',
  viewer:      'Viewer',
}

const NAV_ITEMS = [
  { to: '/platform/dashboard',  label: 'Dashboard',   icon: <LayoutDashboard size={15} /> },
  { to: '/platform/usuarios',   label: 'Operadores',  icon: <Users size={15} /> },
  { to: '/platform/actividad',  label: 'Actividad',   icon: <Activity size={15} /> },
]

export function PlatformLayout() {
  const user = useCurrentUser() as JwtPayloadPlataforma | null
  const { logout } = useAuthStore()

  const handleLogout = async () => {
    await apiLogout().catch(() => {})
    logout()
  }

  const inicialNombre = user?.nombre?.[0]?.toUpperCase() ?? 'O'
  const rolLabel = user ? (ROLE_LABEL[user.rol_plataforma] ?? user.rol_plataforma) : ''
  const rolBadgeClass = user ? (ROLE_BADGE_CLASS[user.rol_plataforma] ?? '') : ''

  return (
    <div className="flex h-screen bg-slate-950 text-white overflow-hidden">
      {/* Sidebar compacto */}
      <aside className="w-52 flex-shrink-0 flex flex-col bg-gym-900 border-r border-gym-800">
        {/* Header */}
        <div className="px-4 py-5 border-b border-gym-800">
          <div className="flex items-center gap-2 mb-1">
            <Dumbbell size={16} className="text-orange-500" />
            <span className="text-white font-bold text-sm">Gym Admin</span>
          </div>
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
            Panel de Plataforma
          </p>
        </div>

        {/* Badge del rol del operador */}
        {user && (
          <div className="px-4 pt-3 pb-1">
            <span className={`inline-block text-xs px-2 py-0.5 rounded-full font-medium ${rolBadgeClass}`}>
              {rolLabel}
            </span>
          </div>
        )}

        {/* Navegación */}
        <nav className="flex-1 px-2 py-3 space-y-0.5 overflow-y-auto">
          {NAV_ITEMS.map(item => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2.5 px-3 py-2 rounded-md text-xs font-medium transition-colors group',
                  isActive
                    ? 'bg-orange-500/15 text-orange-400'
                    : 'text-slate-400 hover:bg-gym-800 hover:text-slate-200'
                )
              }
            >
              <span className="flex-shrink-0">{item.icon}</span>
              <span className="flex-1">{item.label}</span>
              <ChevronRight
                size={12}
                className="opacity-0 group-hover:opacity-40 transition-opacity"
              />
            </NavLink>
          ))}
        </nav>

        {/* Footer: usuario y logout */}
        <div className="px-4 py-4 border-t border-gym-800">
          <div className="flex items-center gap-2 mb-2">
            <div className="flex items-center justify-center w-7 h-7 rounded-full bg-gym-700 text-slate-300 text-xs font-bold flex-shrink-0">
              {inicialNombre}
            </div>
            <div className="min-w-0">
              <p className="text-slate-300 text-xs font-medium truncate">{user?.nombre}</p>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center gap-2 text-xs text-slate-500 hover:text-red-400 transition-colors py-1"
          >
            <LogOut size={13} />
            Cerrar sesión
          </button>
        </div>
      </aside>

      {/* Contenido */}
      <main className="flex-1 overflow-y-auto bg-slate-950">
        <Outlet />
      </main>
    </div>
  )
}
```

---

## 2. Schema operador

**`src/features/auth/schemas/operador.schema.ts`:**
```ts
import { z } from 'zod'

export const crearOperadorSchema = z.object({
  nombre: z
    .string()
    .min(2, 'El nombre debe tener al menos 2 caracteres')
    .max(100),
  correo: z
    .string()
    .min(1, 'El correo es requerido')
    .email('Correo no válido'),
  password: z
    .string()
    .min(8, 'La contraseña debe tener al menos 8 caracteres'),
  rol: z.enum(['super_admin', 'soporte', 'viewer'], {
    required_error: 'Selecciona un rol',
  }),
})

export type CrearOperadorFormData = z.infer<typeof crearOperadorSchema>
```

---

## 3. CrearOperadorModal

**`src/features/auth/components/CrearOperadorModal.tsx`:**
```tsx
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { Loader2, Eye, EyeOff } from 'lucide-react'
import { isAxiosError } from 'axios'
import {
  Dialog, DialogContent, DialogHeader,
  DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { crearOperadorPlataforma } from '@/api/auth.api'
import { crearOperadorSchema, type CrearOperadorFormData } from '../schemas/operador.schema'

interface Props {
  open: boolean
  onClose: () => void
  onCreado: () => void
}

const ROLES_OPCIONES = [
  {
    value: 'super_admin',
    label: 'Super Admin',
    desc: 'Acceso total a la plataforma',
  },
  {
    value: 'soporte',
    label: 'Soporte',
    desc: 'Puede ver y gestionar gimnasios',
  },
  {
    value: 'viewer',
    label: 'Viewer',
    desc: 'Solo lectura',
  },
]

const inputClass =
  'w-full border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition'

export function CrearOperadorModal({ open, onClose, onCreado }: Props) {
  const [showPassword, setShowPassword] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CrearOperadorFormData>({ resolver: zodResolver(crearOperadorSchema) })

  const handleClose = () => { reset(); onClose() }

  const onSubmit = async (data: CrearOperadorFormData) => {
    try {
      await crearOperadorPlataforma(data)
      toast.success('Operador creado correctamente.')
      reset()
      onCreado()
    } catch (err) {
      if (isAxiosError(err)) {
        if (err.response?.status === 409) {
          setError('correo', { message: 'Este correo ya está registrado.' })
        } else {
          toast.error('Error al crear el operador. Intenta de nuevo.')
        }
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Nuevo operador</DialogTitle>
        </DialogHeader>

        <form
          id="crear-operador-form"
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4 py-2"
          noValidate
        >
          {/* Nombre */}
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">Nombre</label>
            <input
              type="text"
              autoFocus
              placeholder="Ana García"
              {...register('nombre')}
              className={inputClass}
            />
            {errors.nombre && (
              <p className="text-xs text-red-600">{errors.nombre.message}</p>
            )}
          </div>

          {/* Correo */}
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">Correo</label>
            <input
              type="email"
              placeholder="operador@empresa.com"
              {...register('correo')}
              className={inputClass}
            />
            {errors.correo && (
              <p className="text-xs text-red-600">{errors.correo.message}</p>
            )}
          </div>

          {/* Contraseña */}
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">Contraseña</label>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                placeholder="••••••••"
                {...register('password')}
                className={`${inputClass} pr-10`}
              />
              <button
                type="button"
                onClick={() => setShowPassword(v => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                tabIndex={-1}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {errors.password && (
              <p className="text-xs text-red-600">{errors.password.message}</p>
            )}
          </div>

          {/* Rol — radio buttons con descripción */}
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">Rol</label>
            <div className="space-y-2">
              {ROLES_OPCIONES.map(opcion => (
                <label
                  key={opcion.value}
                  className="flex items-start gap-3 p-3 border border-slate-200 rounded-lg cursor-pointer hover:bg-slate-50 transition-colors has-[:checked]:border-orange-400 has-[:checked]:bg-orange-50"
                >
                  <input
                    type="radio"
                    value={opcion.value}
                    {...register('rol')}
                    className="mt-0.5 accent-orange-500"
                  />
                  <div>
                    <p className="text-sm font-medium text-slate-800">{opcion.label}</p>
                    <p className="text-xs text-slate-400">{opcion.desc}</p>
                  </div>
                </label>
              ))}
            </div>
            {errors.rol && (
              <p className="text-xs text-red-600">{errors.rol.message}</p>
            )}
          </div>
        </form>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            Cancelar
          </Button>
          <Button
            type="submit"
            form="crear-operador-form"
            disabled={isSubmitting}
            className="bg-orange-500 hover:bg-orange-600 text-white"
          >
            {isSubmitting && <Loader2 size={14} className="mr-2 animate-spin" />}
            {isSubmitting ? 'Creando...' : 'Crear operador'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

---

## 4. PlatformUsuariosPage

**`src/features/auth/pages/platform/PlatformUsuariosPage.tsx`:**
```tsx
import { useState, useEffect, useCallback } from 'react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { UserCog, UserPlus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { useCurrentUser } from '@/features/auth/store/auth.store'
import {
  getOperadoresPlataforma,
  desactivarOperadorPlataforma,
} from '@/api/auth.api'
import type { OperadorPlataforma } from '@/api/types/auth.types'
import type { JwtPayloadPlataforma } from '@/types'
import { CrearOperadorModal } from '../../components/CrearOperadorModal'

const ROLE_BADGE: Record<string, string> = {
  super_admin: 'bg-orange-500/20 text-orange-400',
  soporte:     'bg-blue-500/20 text-blue-400',
  viewer:      'bg-slate-600/40 text-slate-400',
}

const ROLE_LABEL: Record<string, string> = {
  super_admin: 'Super Admin',
  soporte:     'Soporte',
  viewer:      'Viewer',
}

function EstadoBadge({ activo }: { activo: boolean }) {
  return (
    <span className={`inline-flex items-center gap-1.5 text-xs font-medium px-2 py-0.5 rounded-full ${
      activo ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'
    }`}>
      <span className={`w-1.5 h-1.5 rounded-full ${activo ? 'bg-green-500' : 'bg-red-500'}`} />
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

function TableSkeleton() {
  return (
    <div className="space-y-0">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="flex gap-4 px-6 py-4 border-b border-slate-800 animate-pulse">
          <div className="h-4 bg-slate-800 rounded w-1/4" />
          <div className="h-4 bg-slate-800 rounded w-1/3" />
          <div className="h-4 bg-slate-800 rounded w-20" />
          <div className="h-4 bg-slate-800 rounded w-16" />
          <div className="h-4 bg-slate-800 rounded w-24" />
        </div>
      ))}
    </div>
  )
}

export function PlatformUsuariosPage() {
  const currentUser = useCurrentUser() as JwtPayloadPlataforma | null
  const esSuperAdmin = currentUser?.rol_plataforma === 'super_admin'

  const [operadores, setOperadores] = useState<OperadorPlataforma[]>([])
  const [loading, setLoading] = useState(true)
  const [crearOpen, setCrearOpen] = useState(false)
  const [confirmDesactivar, setConfirmDesactivar] = useState<OperadorPlataforma | null>(null)

  const cargar = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getOperadoresPlataforma()
      setOperadores(data)
    } catch {
      toast.error('No se pudieron cargar los operadores.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { cargar() }, [cargar])

  const handleDesactivar = async (op: OperadorPlataforma) => {
    try {
      await desactivarOperadorPlataforma(op.id)
      toast.success(`Operador ${op.nombre} desactivado.`)
      cargar()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error('No puedes desactivar al último super admin activo.')
      } else {
        toast.error('Error al desactivar el operador.')
      }
    } finally {
      setConfirmDesactivar(null)
    }
  }

  const formatFecha = (fecha: string | null) => {
    if (!fecha) return '—'
    try {
      return format(new Date(fecha), "d MMM, HH:mm", { locale: es })
    } catch {
      return '—'
    }
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header de página — estilo plataforma */}
      <div className="flex items-start justify-between px-6 py-5 border-b border-slate-800">
        <div>
          <h1 className="text-lg font-bold text-white">Operadores</h1>
          <p className="text-slate-500 text-sm mt-0.5">
            Usuarios con acceso al panel de plataforma
          </p>
        </div>
        {esSuperAdmin && (
          <Button
            onClick={() => setCrearOpen(true)}
            className="bg-orange-500 hover:bg-orange-600 text-white text-sm"
          >
            <UserPlus size={15} className="mr-2" />
            Nuevo operador
          </Button>
        )}
      </div>

      {/* Tabla */}
      <div className="flex-1 overflow-auto">
        {loading ? (
          <TableSkeleton />
        ) : operadores.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <UserCog size={40} className="text-slate-700 mb-3" />
            <p className="text-slate-500 text-sm">No hay operadores registrados.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-800">
                  {['Nombre', 'Correo', 'Rol', 'Estado', 'Último acceso', 'Acciones'].map(h => (
                    <th
                      key={h}
                      className="px-6 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {operadores.map(op => (
                  <tr
                    key={op.id}
                    className="hover:bg-slate-800/30 transition-colors"
                  >
                    <td className="px-6 py-4 font-medium text-slate-200 whitespace-nowrap">
                      {op.nombre}
                    </td>
                    <td className="px-6 py-4 text-slate-400 whitespace-nowrap font-mono text-xs">
                      {op.correo}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${ROLE_BADGE[op.rol_plataforma] ?? ''}`}>
                        {ROLE_LABEL[op.rol_plataforma] ?? op.rol_plataforma}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <EstadoBadge activo={op.activo} />
                    </td>
                    <td className="px-6 py-4 text-slate-500 whitespace-nowrap text-xs">
                      {formatFecha(op.ultimo_acceso)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {esSuperAdmin && op.activo && (
                        <button
                          onClick={() => setConfirmDesactivar(op)}
                          className="text-xs text-red-500 hover:text-red-400 transition-colors"
                        >
                          Desactivar
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal crear */}
      <CrearOperadorModal
        open={crearOpen}
        onClose={() => setCrearOpen(false)}
        onCreado={() => { setCrearOpen(false); cargar() }}
      />

      {/* Confirmación desactivar */}
      <ConfirmDialog
        open={confirmDesactivar !== null}
        title="Desactivar operador"
        description={`¿Desactivar a ${confirmDesactivar?.nombre}? Perderá el acceso al panel de plataforma inmediatamente.`}
        onConfirm={() => confirmDesactivar && handleDesactivar(confirmDesactivar)}
        onCancel={() => setConfirmDesactivar(null)}
        destructive
      />
    </div>
  )
}
```

---

## 5. Actualizar el router — bloque completo final

**`src/router/index.tsx`** — versión final con todas las rutas:
```tsx
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AuthGuard } from './guards/AuthGuard'
import { PlatformGuard } from './guards/PlatformGuard'

import { PublicLayout } from '@/layouts/PublicLayout'
import { AdminLayout } from '@/layouts/AdminLayout'
import { PlatformLayout } from '@/layouts/PlatformLayout'

import { LoginPage } from '@/features/auth/pages/LoginPage'
import { PlatformLoginPage } from '@/features/auth/pages/PlatformLoginPage'
import { ResetRequestPage } from '@/features/auth/pages/ResetRequestPage'
import { ResetConfirmPage } from '@/features/auth/pages/ResetConfirmPage'
import { ChangePasswordPage } from '@/features/auth/pages/ChangePasswordPage'
import { SinAccesoPage } from '@/features/auth/pages/SinAccesoPage'
import { UsuariosPage } from '@/features/auth/pages/UsuariosPage'
import { RolesPage } from '@/features/auth/pages/RolesPage'
import { ClientesAppPage } from '@/features/auth/pages/ClientesAppPage'
import { BitacoraPage } from '@/features/auth/pages/BitacoraPage'
import { PlatformUsuariosPage } from '@/features/auth/pages/platform/PlatformUsuariosPage'

export const router = createBrowserRouter([
  // ─── Rutas públicas ───────────────────────────────────────
  {
    element: <PublicLayout />,
    children: [
      { path: '/login',                    element: <LoginPage /> },
      { path: '/platform/login',           element: <PlatformLoginPage /> },
      { path: '/reset-password',           element: <ResetRequestPage /> },
      { path: '/reset-password/confirm',   element: <ResetConfirmPage /> },
    ],
  },

  // ─── Panel Staff ──────────────────────────────────────────
  {
    element: <AuthGuard />,
    children: [
      { path: '/change-password', element: <ChangePasswordPage /> },
      {
        element: <AdminLayout />,
        children: [
          { path: '/admin', element: <Navigate to="/admin/dashboard" replace /> },
          {
            path: '/admin/dashboard',
            element: (
              <div className="p-6">
                <h1 className="text-2xl font-bold text-slate-900">Dashboard</h1>
                <p className="text-slate-500 mt-1">Módulo en construcción.</p>
              </div>
            ),
          },
          { path: '/admin/usuarios',      element: <UsuariosPage /> },
          { path: '/admin/roles',         element: <RolesPage /> },
          { path: '/admin/clientes/app',  element: <ClientesAppPage /> },
          { path: '/admin/bitacora',      element: <BitacoraPage /> },
          { path: '/admin/sin-acceso',    element: <SinAccesoPage /> },
        ],
      },
    ],
  },

  // ─── Panel Plataforma ─────────────────────────────────────
  {
    element: <PlatformGuard />,
    children: [
      {
        element: <PlatformLayout />,
        children: [
          { path: '/platform', element: <Navigate to="/platform/dashboard" replace /> },
          {
            path: '/platform/dashboard',
            element: (
              <div className="p-6">
                <h1 className="text-lg font-bold text-white">Dashboard</h1>
                <p className="text-slate-500 text-sm mt-1">Módulo en construcción.</p>
              </div>
            ),
          },
          { path: '/platform/usuarios', element: <PlatformUsuariosPage /> },
        ],
      },
    ],
  },

  // ─── Fallbacks ────────────────────────────────────────────
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '*', element: <Navigate to="/login" replace /> },
])
```

---

## Cómo probar

1. Ir a `localhost:5173/platform/login` → pantalla oscura
2. Login con credenciales de operador de plataforma → redirige a `/platform/dashboard`
3. **PlatformLayout:** sidebar compacto oscuro con badge de rol visible (Super Admin = naranja, Soporte = azul, Viewer = gris)
4. Navegar a `/platform/usuarios`
5. Como **super_admin:** botón "Nuevo operador" visible; como **viewer/soporte:** botón oculto
6. Crear operador con radio buttons de rol (Super Admin / Soporte / Viewer) → cards visuales
7. Correo duplicado → error en campo
8. Desactivar operador → diálogo de confirmación
9. Intentar desactivar el único super_admin activo → error toast descriptivo

---

## Resumen de la implementación completa

| # | Documento | Pantallas | Complejidad |
|---|---|---|---|
| 00 | Base Setup | — (infraestructura) | ★ |
| 01 | Login Staff | P-01 + PublicLayout | ★★ |
| 02 | Login Plataforma | P-02 | ★★ |
| 03 | Reset Solicitud | P-03 | ★★ |
| 04 | Reset Confirmar | P-04 + PasswordStrength | ★★ |
| 05 | Cambio Password | P-05 | ★★ |
| 06 | AdminLayout | Layout + SinAcceso | ★★★ |
| 07 | Usuarios | P-06 + modales | ★★★ |
| 08 | Bitácora | P-09 + DataTable | ★★★ |
| 09 | Clientes App | P-08 (flujo 3 pasos) | ★★★★ |
| 10 | Roles y Permisos | P-07 + editor checkbox | ★★★★★ |
| 11 | Plataforma | PlatformLayout + P-10 | ★★★ |

*Frontend UI/UX Implementation Guide v1.0 · Gym Administrator · Mayo 2026*
