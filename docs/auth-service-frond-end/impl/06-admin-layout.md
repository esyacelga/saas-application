# IMPL-06 — AdminLayout + SinAccesoPage

> **Tipo:** Layout + página de error de permisos  
> **Complejidad:** ★★★☆☆  
> **Prerequisito:** IMPL-05 completado  
> **Resultado:** Sidebar responsivo con tema gym, navegación por permisos, hamburger en móvil; todas las pantallas internas de staff lo usan

---

## Qué resuelve este layout

- **Desktop (≥1024px):** sidebar siempre visible a la izquierda (64px de ancho)
- **Tablet/Móvil (<1024px):** sidebar oculto, botón hamburger en header superior, abre un drawer con overlay oscuro
- Navegación filtrada por permisos del JWT (solo ve lo que puede hacer)
- Avatar del usuario + botón de logout en la parte inferior del sidebar

---

## Archivos que se crean en este paso

```
src/
├── layouts/
│   └── AdminLayout.tsx
└── features/auth/pages/
    └── SinAccesoPage.tsx
```

También se actualiza `src/router/index.tsx`.

---

## Vista del layout

```
Desktop (lg+):
┌──────────────┬────────────────────────────────────────┐
│  🏋 Gym Admin │  [PageHeader del contenido]            │
│  Sucursal 1  │                                        │
│──────────────│  Contenido de la página actual         │
│ ■ Dashboard  │  (Outlet)                               │
│ ■ Usuarios   │                                        │
│ ■ Roles      │                                        │
│ ■ Cuentas App│                                        │
│ ■ Bitácora   │                                        │
│──────────────│                                        │
│ [JM] Juan M. │                                        │
│ Cerrar sesión│                                        │
└──────────────┴────────────────────────────────────────┘

Móvil:
┌──────────────────────────────────────┐
│ ☰  Gym Admin                         │  ← header fijo
├──────────────────────────────────────┤
│  Contenido de la página (Outlet)     │
└──────────────────────────────────────┘

(con sidebar abierto):
┌────────────────┐────────────────────┐
│  🏋 Gym Admin  │ overlay semiopaco  │
│  Sucursal 1  ✕ │                    │
│──────────────  │                    │
│ Dashboard      │                    │
│ Usuarios       │                    │
│ Roles          │                    │
│ Cuentas App    │                    │
└────────────────┘────────────────────┘
```

---

## 1. AdminLayout

**`src/layouts/AdminLayout.tsx`:**
```tsx
import { useState } from 'react'
import { Outlet, NavLink } from 'react-router-dom'
import {
  Menu, X,
  LayoutDashboard, Users, Shield,
  Smartphone, ScrollText, LogOut, Dumbbell,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  useCurrentUser,
  useHasPermission,
  useAuthStore,
} from '@/features/auth/store/auth.store'
import { logout as apiLogout } from '@/api/auth.api'
import type { JwtPayloadStaff } from '@/types'

interface NavItem {
  to: string
  label: string
  icon: React.ReactNode
  permiso?: string
}

const ALL_NAV_ITEMS: NavItem[] = [
  {
    to: '/admin/dashboard',
    label: 'Dashboard',
    icon: <LayoutDashboard size={20} />,
  },
  {
    to: '/admin/usuarios',
    label: 'Usuarios',
    icon: <Users size={20} />,
    permiso: 'usuarios:leer',
  },
  {
    to: '/admin/roles',
    label: 'Roles y permisos',
    icon: <Shield size={20} />,
    permiso: 'roles:leer',
  },
  {
    to: '/admin/clientes/app',
    label: 'Cuentas App',
    icon: <Smartphone size={20} />,
  },
  {
    to: '/admin/bitacora',
    label: 'Bitácora',
    icon: <ScrollText size={20} />,
    permiso: 'usuarios:leer',
  },
]

export function AdminLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const user = useCurrentUser() as JwtPayloadStaff | null
  const { logout } = useAuthStore()

  const tienePermiso = (permiso: string) => useHasPermission(permiso)

  const navItems = ALL_NAV_ITEMS.filter(
    item => !item.permiso || tienePermiso(item.permiso)
  )

  const inicialNombre = user?.nombre?.[0]?.toUpperCase() ?? 'U'

  const handleLogout = async () => {
    await apiLogout().catch(() => {})
    logout()
  }

  const closeSidebar = () => setSidebarOpen(false)

  const SidebarContent = () => (
    <>
      {/* Logo / Header del sidebar */}
      <div className="flex items-center gap-3 px-5 py-5 border-b border-gym-800">
        <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-orange-500 flex-shrink-0">
          <Dumbbell size={20} className="text-white" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-white font-bold text-sm leading-tight">Gym Admin</p>
          {user && (
            <p className="text-slate-400 text-xs leading-tight truncate">
              Sucursal {user.id_sucursal}
            </p>
          )}
        </div>
        {/* Botón cerrar — solo en móvil */}
        <button
          onClick={closeSidebar}
          className="text-slate-400 hover:text-white transition-colors lg:hidden"
          aria-label="Cerrar menú"
        >
          <X size={20} />
        </button>
      </div>

      {/* Navegación */}
      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
        {navItems.map(item => (
          <NavLink
            key={item.to}
            to={item.to}
            onClick={closeSidebar}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-orange-500 text-white'
                  : 'text-slate-300 hover:bg-gym-800 hover:text-white'
              )
            }
          >
            <span className="flex-shrink-0">{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* Footer: usuario + logout */}
      <div className="px-3 py-4 border-t border-gym-800">
        <div className="flex items-center gap-3 px-3 py-2 rounded-lg">
          <div className="flex items-center justify-center w-8 h-8 rounded-full bg-orange-500 text-white text-sm font-bold flex-shrink-0">
            {inicialNombre}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-white text-sm font-medium truncate">
              {user?.nombre ?? 'Usuario'}
            </p>
            <p className="text-slate-400 text-xs truncate">Staff</p>
          </div>
        </div>
        <button
          onClick={handleLogout}
          className="flex items-center gap-3 w-full px-3 py-2.5 mt-1 rounded-lg text-sm font-medium text-slate-400 hover:bg-red-500/15 hover:text-red-400 transition-colors"
        >
          <LogOut size={18} />
          Cerrar sesión
        </button>
      </div>
    </>
  )

  return (
    <div className="flex h-screen overflow-hidden bg-slate-50">
      {/* Overlay móvil (click para cerrar) */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-20 bg-black/60 lg:hidden"
          onClick={closeSidebar}
          aria-hidden="true"
        />
      )}

      {/* Sidebar en móvil (drawer) */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-30 flex flex-col bg-gym-950',
          'w-72 shadow-sidebar',
          'transition-transform duration-300 ease-in-out',
          'lg:hidden',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <SidebarContent />
      </aside>

      {/* Sidebar en desktop (siempre visible) */}
      <aside className="hidden lg:flex lg:flex-col lg:w-64 bg-gym-950 flex-shrink-0">
        <SidebarContent />
      </aside>

      {/* Área principal */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Header solo en móvil/tablet */}
        <header className="flex items-center gap-4 px-4 py-3 bg-white border-b border-slate-200 shadow-sm lg:hidden flex-shrink-0">
          <button
            onClick={() => setSidebarOpen(true)}
            className="p-2 rounded-lg text-slate-600 hover:bg-slate-100 transition-colors"
            aria-label="Abrir menú"
          >
            <Menu size={22} />
          </button>
          <div className="flex items-center gap-2">
            <Dumbbell size={18} className="text-orange-500" />
            <span className="font-semibold text-slate-900 text-sm">Gym Admin</span>
          </div>
        </header>

        {/* Contenido de la página */}
        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
```

---

## 2. SinAccesoPage

Pantalla amigable cuando el usuario navega a una ruta sin el permiso necesario.

**`src/features/auth/pages/SinAccesoPage.tsx`:**
```tsx
import { Link } from 'react-router-dom'
import { Lock } from 'lucide-react'

export function SinAccesoPage() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] py-16 px-4 text-center">
      <div className="flex items-center justify-center w-16 h-16 rounded-full bg-slate-100 mb-4">
        <Lock size={28} className="text-slate-400" />
      </div>
      <h2 className="text-xl font-bold text-slate-800">Sin acceso</h2>
      <p className="text-slate-500 text-sm mt-2 max-w-xs leading-relaxed">
        No tienes permisos para ver esta sección.
        Si crees que es un error, comunícate con el administrador del sistema.
      </p>
      <Link
        to="/admin/dashboard"
        className="mt-6 inline-flex items-center gap-2 bg-orange-500 hover:bg-orange-600 text-white font-semibold text-sm px-5 py-2.5 rounded-lg transition-colors"
      >
        Volver al inicio
      </Link>
    </div>
  )
}
```

---

## 3. Actualizar el router

**`src/router/index.tsx`** — agregar el bloque completo del panel staff:
```tsx
import { AdminLayout } from '@/layouts/AdminLayout'
import { SinAccesoPage } from '@/features/auth/pages/SinAccesoPage'

// Reemplazar el bloque AuthGuard existente:
{
  element: <AuthGuard />,
  children: [
    // Cambio de contraseña (fuera del AdminLayout — sin sidebar)
    { path: '/change-password', element: <ChangePasswordPage /> },

    // Panel admin con sidebar
    {
      element: <AdminLayout />,
      children: [
        { path: '/admin', element: <Navigate to="/admin/dashboard" replace /> },
        {
          path: '/admin/dashboard',
          element: (
            <div className="p-6">
              <h1 className="text-2xl font-bold text-slate-900">Dashboard</h1>
              <p className="text-slate-500 mt-1">
                Módulo en construcción — próximas pantallas aquí.
              </p>
            </div>
          ),
        },
        { path: '/admin/sin-acceso', element: <SinAccesoPage /> },
        // Las siguientes rutas se añaden en IMPL-07 a IMPL-10
      ],
    },
  ],
},
```

---

## Cómo probar

1. Hacer login como usuario staff → debe redirigir a `/admin/dashboard` con el sidebar visible
2. **Desktop:** sidebar visible permanentemente a la izquierda con fondo oscuro y naranja en el ítem activo
3. **Móvil (reducir ventana):** sidebar desaparece, aparece botón ☰ en header superior
4. Clic en ☰ → sidebar aparece como drawer con overlay oscuro detrás
5. Clic fuera del drawer → se cierra
6. Navegar entre ítems → el ítem activo se pone naranja
7. Ir a `localhost:5173/admin/sin-acceso` → pantalla de sin acceso con botón "Volver al inicio"
8. Botón "Cerrar sesión" → logout + redirige a `/login`

**Siguiente paso:** [IMPL-07 — Usuarios](./07-usuarios.md)
