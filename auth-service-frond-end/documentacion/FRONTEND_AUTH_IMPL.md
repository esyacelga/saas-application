# Módulo de Autenticación — Frontend (React + Vite)
# Documento de Implementación Técnica

> **Módulo:** Autenticación, Usuarios, Roles y Permisos
> **Spec funcional:** FRONTEND_AUTH_SPEC.md
> **Stack:** React 18 + Vite + TypeScript
> **Estado:** Listo para implementar
> **Fecha:** Mayo 2026

---

## Tabla de Contenidos

1. [Stack y dependencias](#1-stack-y-dependencias)
2. [Estructura de carpetas](#2-estructura-de-carpetas)
3. [Variables de entorno](#3-variables-de-entorno)
4. [TypeScript — Tipos del módulo](#4-typescript--tipos-del-módulo)
5. [Capa de API — Axios](#5-capa-de-api--axios)
6. [Store de autenticación — Zustand](#6-store-de-autenticación--zustand)
7. [Router y guards de ruta](#7-router-y-guards-de-ruta)
8. [Layouts](#8-layouts)
9. [Implementación por pantalla](#9-implementación-por-pantalla)
10. [Patrones de formulario](#10-patrones-de-formulario)
11. [Patrones de tabla de datos](#11-patrones-de-tabla-de-datos)
12. [Manejo global de errores](#12-manejo-global-de-errores)
13. [Inicialización del proyecto](#13-inicialización-del-proyecto)

---

## 1. Stack y dependencias

### Core

| Paquete | Versión | Propósito |
|---|---|---|
| `react` | 18.x | UI |
| `react-dom` | 18.x | Renderizado en browser |
| `typescript` | 5.x | Tipado estático |
| `vite` | 5.x | Build tool y dev server |

### Routing

| Paquete | Propósito |
|---|---|
| `react-router-dom` v6 | Routing declarativo, loaders, guards |

### HTTP

| Paquete | Propósito |
|---|---|
| `axios` | Cliente HTTP con interceptores para refresh automático |

### Estado global

| Paquete | Propósito |
|---|---|
| `zustand` | Store del usuario autenticado y sus permisos |

> **Por qué Zustand:** El estado de auth es simple (un objeto de usuario + tokens en memoria). Zustand evita el boilerplate de Redux sin perder tipado. Context nativo no escala bien cuando el estado se comparte en muchos componentes.

### Formularios y validación

| Paquete | Propósito |
|---|---|
| `react-hook-form` | Manejo de formularios con mínimo re-render |
| `zod` | Esquemas de validación + inferencia de tipos |
| `@hookform/resolvers` | Conector entre zod y react-hook-form |

### UI

| Paquete | Propósito |
|---|---|
| `tailwindcss` | Utility-first CSS |
| `shadcn/ui` | Componentes accesibles sobre Radix UI + Tailwind |
| `lucide-react` | Iconos (ya incluido con shadcn) |

> **Por qué shadcn/ui:** Los componentes son código propio (no una librería cerrada). Se copian al repositorio y se pueden modificar libremente. Ideal para un panel administrativo donde se necesita control total.

### Feedback y UX

| Paquete | Propósito |
|---|---|
| `sonner` | Toast notifications (compatible con shadcn) |

### Tablas

| Paquete | Propósito |
|---|---|
| `@tanstack/react-table` | Tablas con paginación, filtros y ordenamiento |

### Utilidades

| Paquete | Propósito |
|---|---|
| `date-fns` | Formateo de fechas en español |
| `clsx` + `tailwind-merge` | Composición condicional de clases CSS |

---

## 2. Estructura de carpetas

```
src/
│
├── api/                          # Capa de comunicación con el backend
│   ├── axios.instance.ts         # Instancia Axios con interceptores
│   ├── auth.api.ts               # Endpoints del auth-service
│   └── types/                    # Tipos de request/response por módulo
│       └── auth.types.ts
│
├── features/                     # Módulos de la aplicación
│   └── auth/                     # Todo lo relacionado con autenticación
│       ├── components/           # Componentes reutilizables del módulo
│       │   ├── LoginForm.tsx
│       │   ├── PermissionGate.tsx
│       │   └── UserBadge.tsx
│       ├── hooks/                # Hooks específicos del módulo
│       │   ├── useAuth.ts
│       │   └── usePermission.ts
│       ├── pages/                # Una página por pantalla del spec
│       │   ├── LoginPage.tsx           # P-01
│       │   ├── PlatformLoginPage.tsx   # P-02
│       │   ├── ResetRequestPage.tsx    # P-03
│       │   ├── ResetConfirmPage.tsx    # P-04
│       │   ├── ChangePasswordPage.tsx  # P-05
│       │   ├── UsuariosPage.tsx        # P-06
│       │   ├── RolesPage.tsx           # P-07
│       │   ├── ClientesAppPage.tsx     # P-08
│       │   ├── BitacoraPage.tsx        # P-09
│       │   └── platform/
│       │       └── PlatformUsuariosPage.tsx  # P-10
│       ├── schemas/              # Esquemas Zod de cada formulario
│       │   ├── login.schema.ts
│       │   ├── usuario.schema.ts
│       │   └── rol.schema.ts
│       └── store/
│           └── auth.store.ts     # Zustand store de sesión
│
├── router/
│   ├── index.tsx                 # Definición de todas las rutas
│   └── guards/
│       ├── AuthGuard.tsx         # JWT válido + tipo staff
│       ├── PlatformGuard.tsx     # JWT válido + tipo plataforma
│       └── PermissionGuard.tsx   # Permiso específico del JWT
│
├── layouts/
│   ├── PublicLayout.tsx          # Wrapper para páginas de login
│   ├── AdminLayout.tsx           # Panel staff: sidebar + header
│   └── PlatformLayout.tsx        # Panel plataforma: sidebar propio
│
├── components/                   # Componentes UI compartidos
│   ├── ui/                       # Componentes generados por shadcn
│   ├── DataTable.tsx             # Tabla genérica con TanStack
│   ├── PageHeader.tsx
│   ├── ConfirmDialog.tsx
│   └── FormField.tsx             # Wrapper campo + label + error
│
├── hooks/
│   └── useDebounce.ts
│
├── lib/
│   ├── utils.ts                  # cn() helper para clases Tailwind
│   └── jwt.ts                    # Decodificador de JWT sin librería externa
│
├── types/
│   └── index.ts                  # Tipos globales compartidos
│
├── main.tsx
└── App.tsx
```

---

## 3. Variables de entorno

Archivo `.env.local` (nunca en git):

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_APP_NAME=Gym Administrator
```

Acceso en el código:

```ts
// Solo variables con prefijo VITE_ son expuestas al cliente
const baseUrl = import.meta.env.VITE_API_BASE_URL
```

Archivo `.env.example` (sí en git, sin valores reales):

```env
VITE_API_BASE_URL=
VITE_APP_NAME=
```

---

## 4. TypeScript — Tipos del módulo

### `src/types/index.ts`

```ts
// Los tres tipos de JWT que emite el auth-service
export type TipoToken = 'plataforma' | 'staff' | 'cliente'

export interface JwtPayloadPlataforma {
  sub: string
  tipo: 'plataforma'
  rol_plataforma: 'super_admin' | 'soporte' | 'viewer'
  nombre: string
  iat: number
  exp: number
}

export interface JwtPayloadStaff {
  sub: string
  tipo: 'staff'
  id_compania: number
  id_sucursal: number
  id_rol: number
  nombre: string
  permisos: string[]   // formato "modulo:accion"
  iat: number
  exp: number
}

// El JWT de cliente es solo para la app móvil, pero el tipo existe por completitud
export interface JwtPayloadCliente {
  sub: string
  tipo: 'cliente'
  id_compania: number
  id_persona: number
  nombre: string
  iat: number
  exp: number
}

export type JwtPayload = JwtPayloadPlataforma | JwtPayloadStaff | JwtPayloadCliente
```

### `src/api/types/auth.types.ts`

```ts
// --- Requests ---

export interface LoginStaffRequest {
  correo: string
  password: string
  id_compania: number
}

export interface LoginPlatformRequest {
  correo: string
  password: string
}

export interface RefreshRequest {
  refresh_token: string
}

export interface ResetPasswordRequestBody {
  correo: string
  id_compania?: number
  tipo: 'staff' | 'cliente'
}

export interface ResetPasswordConfirmBody {
  token: string
  nueva_password: string
}

export interface CrearUsuarioRequest {
  nombre: string
  correo: string
  id_rol: number
  id_sucursal: number
  password_temporal: string
}

export interface CrearRolRequest {
  nombre: string
  descripcion?: string
}

export interface AsignarPermisosRequest {
  id_permisos: number[]
}

export interface CrearPersonaRequest {
  ci: string
  nombre: string
  telefono?: string
  correo?: string
  fecha_nacimiento?: string
}

export interface CrearAppUsuarioRequest {
  id_persona: number
  login: string
  password: string
}

export interface CrearOperadorPlataformaRequest {
  nombre: string
  correo: string
  password: string
  rol: 'super_admin' | 'soporte' | 'viewer'
}

// --- Responses ---

export interface LoginStaffResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  requiere_cambio_pwd: boolean
  usuario: {
    id: number
    nombre: string
    correo: string
    id_rol: number
    nombre_rol: string
  }
}

export interface LoginPlatformResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  usuario: {
    id: number
    nombre: string
    rol_plataforma: 'super_admin' | 'soporte' | 'viewer'
  }
}

export interface RefreshResponse {
  access_token: string
  expires_in: number
}

export interface UsuarioStaff {
  id: number
  nombre: string
  correo: string
  id_rol: number
  nombre_rol: string
  activo: boolean
  ultimo_acceso: string | null
}

export interface PermisosUsuario {
  usuario: { id: number; nombre: string }
  rol: { id: number; nombre: string }
  permisos: string[]
}

export interface Rol {
  id: number
  nombre: string
  descripcion: string | null
}

export interface Permiso {
  id: number
  nombre: string
  modulo: string
  descripcion: string | null
}

export interface RolConPermisos {
  rol: Rol
  permisos: Permiso[]
}

export interface Persona {
  id: number
  ci: string
  nombre: string
  telefono: string | null
  correo: string | null
  foto_url: string | null
  fecha_nacimiento: string | null
}

export interface OperadorPlataforma {
  id: number
  nombre: string
  correo: string
  rol_plataforma: 'super_admin' | 'soporte' | 'viewer'
  activo: boolean
  ultimo_acceso: string | null
}

export interface BitacoraEntry {
  id: number
  id_usuario: number
  nombre_usuario: string
  modulo: string
  accion: string
  entidad_id: number | null
  ip: string | null
  fecha: string
}

export interface PaginatedResponse<T> {
  total: number
  pagina: number
  datos: T[]
}
```

---

## 5. Capa de API — Axios

### `src/api/axios.instance.ts`

Esta es la pieza más crítica del módulo. Maneja dos cosas: adjuntar el token a cada request y renovarlo automáticamente cuando expira.

```ts
import axios, { AxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/features/auth/store/auth.store'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  withCredentials: true,    // necesario para enviar la cookie httpOnly del refresh token
})

// --- Interceptor de request: adjunta el access token ---
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// --- Interceptor de response: maneja 401 con refresh silencioso ---

let isRefreshing = false
// Cola de requests que llegaron mientras el refresh estaba en progreso
let pendingQueue: Array<{
  resolve: (token: string) => void
  reject: (err: unknown) => void
}> = []

const flushQueue = (token: string | null, error: unknown = null) => {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (token) resolve(token)
    else reject(error)
  })
  pendingQueue = []
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest: AxiosRequestConfig & { _retry?: boolean } = error.config

    // Solo intentar refresh si es 401 y no es ya un retry ni el endpoint de refresh/login
    const isAuthEndpoint =
      originalRequest.url?.includes('/auth/refresh') ||
      originalRequest.url?.includes('/auth/login') ||
      originalRequest.url?.includes('/auth/platform/login')

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      if (isRefreshing) {
        // Otro request ya está haciendo el refresh — encolar este y esperar
        return new Promise((resolve, reject) => {
          pendingQueue.push({
            resolve: (token) => {
              if (originalRequest.headers) {
                originalRequest.headers.Authorization = `Bearer ${token}`
              }
              resolve(api(originalRequest))
            },
            reject,
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        // El refresh_token viaja en httpOnly cookie automáticamente (withCredentials: true)
        const { data } = await api.post<{ access_token: string }>('/auth/refresh')
        const newToken = data.access_token

        useAuthStore.getState().setAccessToken(newToken)
        flushQueue(newToken)

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newToken}`
        }
        return api(originalRequest)
      } catch (refreshError) {
        flushQueue(null, refreshError)
        // Refresh falló → cerrar sesión y redirigir a login
        useAuthStore.getState().logout()
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)

export default api
```

### `src/api/auth.api.ts`

```ts
import api from './axios.instance'
import type {
  LoginStaffRequest, LoginStaffResponse,
  LoginPlatformRequest, LoginPlatformResponse,
  ResetPasswordRequestBody, ResetPasswordConfirmBody,
  CrearUsuarioRequest, UsuarioStaff, PermisosUsuario,
  CrearRolRequest, Rol, Permiso, RolConPermisos, AsignarPermisosRequest,
  CrearPersonaRequest, Persona,
  CrearAppUsuarioRequest,
  CrearOperadorPlataformaRequest, OperadorPlataforma,
  BitacoraEntry, PaginatedResponse,
} from './types/auth.types'

// --- Autenticación ---

export const loginStaff = (body: LoginStaffRequest) =>
  api.post<LoginStaffResponse>('/auth/login', body).then(r => r.data)

export const loginPlatform = (body: LoginPlatformRequest) =>
  api.post<LoginPlatformResponse>('/auth/platform/login', body).then(r => r.data)

export const logout = () =>
  api.post('/auth/logout')

export const requestPasswordReset = (body: ResetPasswordRequestBody) =>
  api.post('/auth/password/reset-request', body).then(r => r.data)

export const confirmPasswordReset = (body: ResetPasswordConfirmBody) =>
  api.post('/auth/password/reset', body).then(r => r.data)

// --- Usuarios staff ---

export const getUsuarios = () =>
  api.get<UsuarioStaff[]>('/usuarios').then(r => r.data)

export const crearUsuario = (body: CrearUsuarioRequest) =>
  api.post<UsuarioStaff>('/usuarios', body).then(r => r.data)

export const getPermisosUsuario = (id: number) =>
  api.get<PermisosUsuario>(`/usuarios/${id}/permisos`).then(r => r.data)

export const desactivarUsuario = (id: number) =>
  api.put(`/usuarios/${id}/desactivar`)

export const activarUsuario = (id: number) =>
  api.put(`/usuarios/${id}/activar`)

// --- Roles ---

export const getRoles = () =>
  api.get<Rol[]>('/roles').then(r => r.data)

export const crearRol = (body: CrearRolRequest) =>
  api.post<Rol>('/roles', body).then(r => r.data)

export const getRolPermisos = (id: number) =>
  api.get<RolConPermisos>(`/roles/${id}/permisos`).then(r => r.data)

export const actualizarRolPermisos = (id: number, body: AsignarPermisosRequest) =>
  api.put(`/roles/${id}/permisos`, body)

export const eliminarRol = (id: number) =>
  api.delete(`/roles/${id}`)

// --- Permisos ---

export const getPermisos = () =>
  api.get<Permiso[]>('/permisos').then(r => r.data)

// --- Personas ---

export const buscarPersonaPorCI = (ci: string) =>
  api.get<Persona>(`/personas/ci/${ci}`).then(r => r.data)

export const crearPersona = (body: CrearPersonaRequest) =>
  api.post<Persona>('/personas', body).then(r => r.data)

// --- Usuarios app ---

export const crearUsuarioApp = (body: CrearAppUsuarioRequest) =>
  api.post('/app-usuarios', body).then(r => r.data)

export const activarUsuarioApp = (id: number) =>
  api.put(`/app-usuarios/${id}/activar`)

export const desactivarUsuarioApp = (id: number) =>
  api.put(`/app-usuarios/${id}/desactivar`)

// --- Bitácora ---

export interface BitacoraParams {
  modulo?: string
  desde?: string
  hasta?: string
  id_usuario?: number
  pagina?: number
}

export const getBitacora = (params: BitacoraParams = {}) =>
  api.get<PaginatedResponse<BitacoraEntry>>('/bitacora', { params }).then(r => r.data)

// --- Operadores de plataforma ---

export const getOperadoresPlataforma = () =>
  api.get<OperadorPlataforma[]>('/platform/usuarios').then(r => r.data)

export const crearOperadorPlataforma = (body: CrearOperadorPlataformaRequest) =>
  api.post<OperadorPlataforma>('/platform/usuarios', body).then(r => r.data)

export const desactivarOperadorPlataforma = (id: number) =>
  api.put(`/platform/usuarios/${id}/desactivar`)
```

---

## 6. Store de autenticación — Zustand

### `src/features/auth/store/auth.store.ts`

El store guarda el access token **solo en memoria** (se pierde al cerrar el tab, que es el comportamiento correcto). El refresh token viaja en httpOnly cookie gestionada por el backend.

```ts
import { create } from 'zustand'
import { JwtPayloadStaff, JwtPayloadPlataforma } from '@/types'
import { decodeJwt } from '@/lib/jwt'

interface AuthState {
  accessToken: string | null
  // El payload decodificado — evita decodificar en cada render
  user: JwtPayloadStaff | JwtPayloadPlataforma | null
  // Flag que indica si ya se intentó restaurar la sesión al montar la app
  initialized: boolean

  setSession: (token: string) => void
  setAccessToken: (token: string) => void
  logout: () => void
  setInitialized: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  initialized: false,

  setSession: (token) => {
    const payload = decodeJwt(token) as JwtPayloadStaff | JwtPayloadPlataforma
    set({ accessToken: token, user: payload })
  },

  setAccessToken: (token) => {
    const payload = decodeJwt(token) as JwtPayloadStaff | JwtPayloadPlataforma
    set({ accessToken: token, user: payload })
  },

  logout: () => {
    set({ accessToken: null, user: null })
  },

  setInitialized: () => set({ initialized: true }),
}))

// Selector helpers — evitan re-renders innecesarios
export const useIsAuthenticated = () =>
  useAuthStore((s) => s.accessToken !== null)

export const useCurrentUser = () =>
  useAuthStore((s) => s.user)

export const useHasPermission = (permiso: string) =>
  useAuthStore((s) => {
    const user = s.user
    if (!user || user.tipo !== 'staff') return false
    return (user as JwtPayloadStaff).permisos.includes(permiso)
  })

export const useIsPlatformUser = () =>
  useAuthStore((s) => s.user?.tipo === 'plataforma')
```

### `src/lib/jwt.ts`

Decodifica el payload del JWT sin verificar firma (la verificación la hace el backend). Solo se usa para leer los claims en el cliente.

```ts
export function decodeJwt(token: string): Record<string, unknown> {
  try {
    const payload = token.split('.')[1]
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decoded)
  } catch {
    return {}
  }
}

export function isTokenExpired(token: string): boolean {
  const payload = decodeJwt(token)
  if (!payload.exp) return true
  return Date.now() >= (payload.exp as number) * 1000
}
```

### Inicialización al montar la app

Al abrir la app, el access token en memoria es `null` (se perdió). Se intenta un refresh silencioso usando la cookie. Si funciona, la sesión se restaura sin que el usuario note nada.

```ts
// src/App.tsx
import { useEffect } from 'react'
import { useAuthStore } from '@/features/auth/store/auth.store'
import api from '@/api/axios.instance'
import type { RefreshResponse } from '@/api/types/auth.types'
import { RouterProvider } from 'react-router-dom'
import { router } from '@/router'

export function App() {
  const { setSession, setInitialized } = useAuthStore()

  useEffect(() => {
    // Intento silencioso de refresh al montar
    api.post<RefreshResponse>('/auth/refresh')
      .then(({ data }) => setSession(data.access_token))
      .catch(() => { /* sin sesión previa — normal */ })
      .finally(() => setInitialized())
  }, [])

  return <RouterProvider router={router} />
}
```

---

## 7. Router y guards de ruta

### `src/router/guards/AuthGuard.tsx`

```tsx
import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/features/auth/store/auth.store'

export function AuthGuard() {
  const { accessToken, user, initialized } = useAuthStore()

  // Espera a que se intente el refresh silencioso antes de decidir
  if (!initialized) return null   // o un spinner de pantalla completa

  if (!accessToken || user?.tipo !== 'staff') {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}
```

### `src/router/guards/PlatformGuard.tsx`

```tsx
import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/features/auth/store/auth.store'

export function PlatformGuard() {
  const { accessToken, user, initialized } = useAuthStore()

  if (!initialized) return null

  if (!accessToken || user?.tipo !== 'plataforma') {
    return <Navigate to="/platform/login" replace />
  }

  return <Outlet />
}
```

### `src/router/guards/PermissionGuard.tsx`

```tsx
import { Navigate } from 'react-router-dom'
import { useHasPermission } from '@/features/auth/store/auth.store'

interface Props {
  permiso: string
  children: React.ReactNode
}

// Uso en componentes: <PermissionGuard permiso="usuarios:crear">...</PermissionGuard>
// Redirige a /admin/sin-acceso si no tiene el permiso
export function PermissionGuard({ permiso, children }: Props) {
  const tiene = useHasPermission(permiso)
  if (!tiene) return <Navigate to="/admin/sin-acceso" replace />
  return <>{children}</>
}

// Versión de renderizado condicional (sin redirect — para botones/secciones)
export function IfPermission({ permiso, children }: Props) {
  const tiene = useHasPermission(permiso)
  return tiene ? <>{children}</> : null
}
```

### `src/router/index.tsx`

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
import { UsuariosPage } from '@/features/auth/pages/UsuariosPage'
import { RolesPage } from '@/features/auth/pages/RolesPage'
import { ClientesAppPage } from '@/features/auth/pages/ClientesAppPage'
import { BitacoraPage } from '@/features/auth/pages/BitacoraPage'
import { PlatformUsuariosPage } from '@/features/auth/pages/platform/PlatformUsuariosPage'

export const router = createBrowserRouter([
  // --- Rutas públicas ---
  {
    element: <PublicLayout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/platform/login', element: <PlatformLoginPage /> },
      { path: '/reset-password', element: <ResetRequestPage /> },
      { path: '/reset-password/confirm', element: <ResetConfirmPage /> },
    ],
  },

  // --- Panel Staff (requiere JWT tipo staff) ---
  {
    element: <AuthGuard />,
    children: [
      { path: '/change-password', element: <ChangePasswordPage /> },
      {
        element: <AdminLayout />,
        children: [
          { path: '/admin', element: <Navigate to="/admin/dashboard" replace /> },
          // dashboard vendrá en otro módulo — placeholder por ahora
          { path: '/admin/dashboard', element: <div>Dashboard</div> },
          { path: '/admin/usuarios', element: <UsuariosPage /> },
          { path: '/admin/roles', element: <RolesPage /> },
          { path: '/admin/clientes/app', element: <ClientesAppPage /> },
          { path: '/admin/bitacora', element: <BitacoraPage /> },
          { path: '/admin/sin-acceso', element: <SinAccesoPage /> },
        ],
      },
    ],
  },

  // --- Panel Plataforma (requiere JWT tipo plataforma) ---
  {
    element: <PlatformGuard />,
    children: [
      {
        element: <PlatformLayout />,
        children: [
          { path: '/platform', element: <Navigate to="/platform/dashboard" replace /> },
          { path: '/platform/dashboard', element: <div>Platform Dashboard</div> },
          { path: '/platform/usuarios', element: <PlatformUsuariosPage /> },
        ],
      },
    ],
  },

  // Raíz → redirige a login
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '*', element: <Navigate to="/login" replace /> },
])
```

---

## 8. Layouts

### `src/layouts/PublicLayout.tsx`

Wrapper centrado para las páginas de login y recuperación de contraseña.

```tsx
import { Outlet } from 'react-router-dom'

export function PublicLayout() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <Outlet />
      </div>
    </div>
  )
}
```

### `src/layouts/AdminLayout.tsx`

Layout del panel staff: sidebar con navegación filtrada por permisos + header con nombre del usuario y botón de logout.

```tsx
import { Outlet, NavLink } from 'react-router-dom'
import { useCurrentUser, useHasPermission, useAuthStore } from '@/features/auth/store/auth.store'
import { logout as apiLogout } from '@/api/auth.api'

export function AdminLayout() {
  const user = useCurrentUser()
  const { logout } = useAuthStore()
  const puedeVerUsuarios = useHasPermission('usuarios:leer')
  const puedeVerRoles = useHasPermission('roles:leer')

  const handleLogout = async () => {
    await apiLogout().catch(() => {})   // logout best-effort
    logout()
  }

  return (
    <div className="flex h-screen">
      {/* Sidebar */}
      <aside className="w-64 bg-white border-r flex flex-col">
        <div className="p-4 border-b">
          <span className="font-semibold text-gray-900">Gym Administrator</span>
        </div>
        <nav className="flex-1 p-4 space-y-1">
          <NavLink to="/admin/dashboard">Dashboard</NavLink>
          {/* Solo muestra las opciones con permiso */}
          {puedeVerUsuarios && <NavLink to="/admin/usuarios">Usuarios</NavLink>}
          {puedeVerRoles && <NavLink to="/admin/roles">Roles</NavLink>}
          <NavLink to="/admin/clientes/app">Cuentas App</NavLink>
          {puedeVerUsuarios && <NavLink to="/admin/bitacora">Bitácora</NavLink>}
        </nav>
        <div className="p-4 border-t">
          <p className="text-sm text-gray-600">{user?.nombre}</p>
          <button onClick={handleLogout} className="text-sm text-red-600 mt-1">
            Cerrar sesión
          </button>
        </div>
      </aside>

      {/* Contenido */}
      <main className="flex-1 overflow-auto bg-gray-50">
        <Outlet />
      </main>
    </div>
  )
}
```

---

## 9. Implementación por pantalla

### P-01 — LoginPage

**Schema Zod:**
```ts
// src/features/auth/schemas/login.schema.ts
import { z } from 'zod'

export const loginStaffSchema = z.object({
  correo: z.string().email('Ingresa un correo válido'),
  password: z.string().min(1, 'La contraseña es requerida'),
  id_compania: z.coerce.number().int().positive('Ingresa el ID del gimnasio'),
})

export type LoginStaffForm = z.infer<typeof loginStaffSchema>
```

**Página:**
```tsx
// src/features/auth/pages/LoginPage.tsx
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Navigate } from 'react-router-dom'
import { toast } from 'sonner'
import { loginStaff } from '@/api/auth.api'
import { useAuthStore, useIsAuthenticated } from '../store/auth.store'
import { loginStaffSchema, type LoginStaffForm } from '../schemas/login.schema'
import { isAxiosError } from 'axios'

export function LoginPage() {
  const navigate = useNavigate()
  const { setSession } = useAuthStore()
  const isAuthenticated = useIsAuthenticated()

  // Si ya tiene sesión activa, redirigir
  if (isAuthenticated) return <Navigate to="/admin/dashboard" replace />

  const { register, handleSubmit, formState: { errors, isSubmitting }, setError } =
    useForm<LoginStaffForm>({ resolver: zodResolver(loginStaffSchema) })

  const onSubmit = async (data: LoginStaffForm) => {
    try {
      const response = await loginStaff(data)
      setSession(response.access_token)

      if (response.requiere_cambio_pwd) {
        navigate('/change-password', { replace: true })
      } else {
        navigate('/admin/dashboard', { replace: true })
      }
    } catch (err) {
      if (isAxiosError(err)) {
        if (err.response?.status === 401) {
          // Mismo mensaje para usuario/pass/gym incorrectos — no revelar cuál
          setError('root', {
            message: 'Correo, contraseña o ID de gimnasio incorrectos.'
          })
        } else if (err.response?.status === 403) {
          setError('root', { message: 'Tu cuenta está desactivada. Contacta al administrador.' })
        } else {
          toast.error('Ocurrió un error. Por favor intenta de nuevo.')
        }
      }
    }
  }

  return (
    <div className="bg-white rounded-xl shadow p-8 space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Iniciar sesión</h1>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {errors.root && (
          <p className="text-sm text-red-600 bg-red-50 p-3 rounded-md">
            {errors.root.message}
          </p>
        )}

        <div>
          <label className="block text-sm font-medium text-gray-700">Correo</label>
          <input type="email" {...register('correo')}
            className="mt-1 block w-full border rounded-md px-3 py-2" />
          {errors.correo && <p className="text-sm text-red-600 mt-1">{errors.correo.message}</p>}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">Contraseña</label>
          <input type="password" {...register('password')}
            className="mt-1 block w-full border rounded-md px-3 py-2" />
          {errors.password && <p className="text-sm text-red-600 mt-1">{errors.password.message}</p>}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700">ID del gimnasio</label>
          <input type="number" {...register('id_compania')}
            className="mt-1 block w-full border rounded-md px-3 py-2" />
          {errors.id_compania && <p className="text-sm text-red-600 mt-1">{errors.id_compania.message}</p>}
        </div>

        <button type="submit" disabled={isSubmitting}
          className="w-full bg-blue-600 text-white py-2 rounded-md hover:bg-blue-700 disabled:opacity-50">
          {isSubmitting ? 'Ingresando...' : 'Ingresar'}
        </button>
      </form>

      <div className="text-center text-sm space-y-2">
        <a href="/reset-password" className="text-blue-600 hover:underline block">
          ¿Olvidaste tu contraseña?
        </a>
        <a href="/platform/login" className="text-gray-500 hover:underline block">
          Acceso operadores de plataforma
        </a>
      </div>
    </div>
  )
}
```

---

### P-06 — UsuariosPage — Patrón para pantallas de listado con modal

Este patrón se replica en RolesPage y PlatformUsuariosPage. Se define una vez aquí con detalle.

```tsx
// src/features/auth/pages/UsuariosPage.tsx
import { useState, useEffect } from 'react'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { getUsuarios, desactivarUsuario, activarUsuario } from '@/api/auth.api'
import { IfPermission } from '@/router/guards/PermissionGuard'
import type { UsuarioStaff } from '@/api/types/auth.types'
// Componentes shadcn (se añaden con: npx shadcn-ui@latest add button dialog table badge)
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { CrearUsuarioModal } from '../components/CrearUsuarioModal'
import { PermisosPanel } from '../components/PermisosPanel'

export function UsuariosPage() {
  const [usuarios, setUsuarios] = useState<UsuarioStaff[]>([])
  const [loading, setLoading] = useState(true)
  const [crearOpen, setCrearOpen] = useState(false)
  const [permisosUsuarioId, setPermisosUsuarioId] = useState<number | null>(null)
  const [confirmToggle, setConfirmToggle] = useState<UsuarioStaff | null>(null)

  const cargar = async () => {
    setLoading(true)
    try {
      const data = await getUsuarios()
      setUsuarios(data)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { cargar() }, [])

  const toggleActivo = async (usuario: UsuarioStaff) => {
    try {
      if (usuario.activo) await desactivarUsuario(usuario.id)
      else await activarUsuario(usuario.id)
      toast.success(`Usuario ${usuario.activo ? 'desactivado' : 'activado'} correctamente.`)
      cargar()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error('No puedes desactivar al último administrador activo.')
      } else {
        toast.error('Ocurrió un error.')
      }
    } finally {
      setConfirmToggle(null)
    }
  }

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Usuarios</h1>
        <IfPermission permiso="usuarios:crear">
          <Button onClick={() => setCrearOpen(true)}>Nuevo usuario</Button>
        </IfPermission>
      </div>

      {loading ? (
        <div>Cargando...</div>   // reemplazar con skeleton de shadcn
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left border-b">
              <th className="pb-2">Nombre</th>
              <th className="pb-2">Correo</th>
              <th className="pb-2">Rol</th>
              <th className="pb-2">Último acceso</th>
              <th className="pb-2">Estado</th>
              <th className="pb-2">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {usuarios.map(u => (
              <tr key={u.id} className="border-b">
                <td className="py-3">{u.nombre}</td>
                <td>{u.correo}</td>
                <td>{u.nombre_rol}</td>
                <td>{u.ultimo_acceso ? new Date(u.ultimo_acceso).toLocaleDateString('es') : '—'}</td>
                <td>
                  <Badge variant={u.activo ? 'default' : 'secondary'}>
                    {u.activo ? 'Activo' : 'Inactivo'}
                  </Badge>
                </td>
                <td className="space-x-2">
                  <button className="text-blue-600 text-sm"
                    onClick={() => setPermisosUsuarioId(u.id)}>
                    Ver permisos
                  </button>
                  <IfPermission permiso="usuarios:editar">
                    <button className="text-sm text-gray-600"
                      onClick={() => setConfirmToggle(u)}>
                      {u.activo ? 'Desactivar' : 'Activar'}
                    </button>
                  </IfPermission>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* Modal crear usuario */}
      <CrearUsuarioModal
        open={crearOpen}
        onClose={() => setCrearOpen(false)}
        onCreado={() => { setCrearOpen(false); cargar() }}
      />

      {/* Panel lateral de permisos */}
      {permisosUsuarioId !== null && (
        <PermisosPanel
          usuarioId={permisosUsuarioId}
          onClose={() => setPermisosUsuarioId(null)}
        />
      )}

      {/* Diálogo de confirmación */}
      <ConfirmDialog
        open={confirmToggle !== null}
        title={confirmToggle?.activo ? 'Desactivar usuario' : 'Activar usuario'}
        description={`¿Confirmas ${confirmToggle?.activo ? 'desactivar' : 'activar'} a ${confirmToggle?.nombre}?`}
        onConfirm={() => confirmToggle && toggleActivo(confirmToggle)}
        onCancel={() => setConfirmToggle(null)}
      />
    </div>
  )
}
```

---

### P-07b — Editor de permisos del rol

Este es el componente más complejo del módulo. Agrupa permisos por módulo con checkboxes.

```tsx
// src/features/auth/components/RolPermisosEditor.tsx
import { useState, useEffect } from 'react'
import { toast } from 'sonner'
import { getRolPermisos, getPermisos, actualizarRolPermisos } from '@/api/auth.api'
import type { Permiso } from '@/api/types/auth.types'

interface Props {
  rolId: number
  rolNombre: string
  onClose: () => void
}

export function RolPermisosEditor({ rolId, rolNombre, onClose }: Props) {
  const [todosPermisos, setTodosPermisos] = useState<Permiso[]>([])
  const [seleccionados, setSeleccionados] = useState<Set<number>>(new Set())
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    Promise.all([getPermisos(), getRolPermisos(rolId)]).then(([todos, rolData]) => {
      setTodosPermisos(todos)
      setSeleccionados(new Set(rolData.permisos.map(p => p.id)))
    })
  }, [rolId])

  // Agrupar por módulo
  const porModulo = todosPermisos.reduce<Record<string, Permiso[]>>((acc, p) => {
    if (!acc[p.modulo]) acc[p.modulo] = []
    acc[p.modulo].push(p)
    return acc
  }, {})

  const toggle = (id: number) => {
    setSeleccionados(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const guardar = async () => {
    setGuardando(true)
    try {
      await actualizarRolPermisos(rolId, { id_permisos: Array.from(seleccionados) })
      toast.success('Permisos actualizados.')
      onClose()
    } catch {
      toast.error('Ocurrió un error al guardar.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="fixed inset-y-0 right-0 w-96 bg-white shadow-xl flex flex-col">
      <div className="p-4 border-b flex justify-between items-center">
        <h2 className="font-semibold">Permisos: {rolNombre}</h2>
        <button onClick={onClose} className="text-gray-500">✕</button>
      </div>

      <div className="flex-1 overflow-auto p-4 space-y-6">
        {Object.entries(porModulo).map(([modulo, permisos]) => (
          <div key={modulo}>
            <h3 className="text-xs font-semibold uppercase text-gray-500 mb-2">{modulo}</h3>
            <div className="space-y-2">
              {permisos.map(p => (
                <label key={p.id} className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={seleccionados.has(p.id)}
                    onChange={() => toggle(p.id)}
                  />
                  <span className="text-sm">
                    <span className="font-mono text-xs text-blue-700 mr-1">{p.nombre}</span>
                    {p.descripcion}
                  </span>
                </label>
              ))}
            </div>
          </div>
        ))}
      </div>

      <div className="p-4 border-t flex gap-2">
        <button onClick={onClose} className="flex-1 border rounded-md py-2 text-sm">
          Cancelar
        </button>
        <button onClick={guardar} disabled={guardando}
          className="flex-1 bg-blue-600 text-white rounded-md py-2 text-sm disabled:opacity-50">
          {guardando ? 'Guardando...' : 'Guardar cambios'}
        </button>
      </div>
    </div>
  )
}
```

---

## 10. Patrones de formulario

Todo formulario del módulo sigue este mismo patrón:

```
Schema Zod → useForm con zodResolver → onSubmit con try/catch →
  Error de validación local → setError en el campo
  Error 409 del backend → setError en el campo afectado
  Error 500 / red → toast.error
  Éxito → toast.success + callback del padre
```

**Componente reutilizable `ConfirmDialog`:**

```tsx
// src/components/ConfirmDialog.tsx
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface Props {
  open: boolean
  title: string
  description: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ open, title, description, onConfirm, onCancel }: Props) {
  return (
    <Dialog open={open} onOpenChange={onCancel}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-gray-600">{description}</p>
        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>Cancelar</Button>
          <Button variant="destructive" onClick={onConfirm}>Confirmar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

---

## 11. Patrones de tabla de datos

Para la BitacoraPage (paginación + filtros) se usa TanStack Table:

```tsx
// src/components/DataTable.tsx — wrapper genérico reutilizable en todo el proyecto
import {
  useReactTable, getCoreRowModel, flexRender,
  type ColumnDef,
} from '@tanstack/react-table'

interface Props<T> {
  data: T[]
  columns: ColumnDef<T>[]
  loading?: boolean
}

export function DataTable<T>({ data, columns, loading }: Props<T>) {
  const table = useReactTable({ data, columns, getCoreRowModel: getCoreRowModel() })

  if (loading) return <div>Cargando...</div>

  return (
    <table className="w-full text-sm">
      <thead>
        {table.getHeaderGroups().map(hg => (
          <tr key={hg.id} className="border-b">
            {hg.headers.map(h => (
              <th key={h.id} className="text-left pb-2 font-medium text-gray-600">
                {flexRender(h.column.columnDef.header, h.getContext())}
              </th>
            ))}
          </tr>
        ))}
      </thead>
      <tbody>
        {table.getRowModel().rows.map(row => (
          <tr key={row.id} className="border-b hover:bg-gray-50">
            {row.getVisibleCells().map(cell => (
              <td key={cell.id} className="py-3">
                {flexRender(cell.column.columnDef.cell, cell.getContext())}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
```

---

## 12. Manejo global de errores

### Errores HTTP — tabla de decisiones

| Código | Contexto | Qué hacer |
|---|---|---|
| 400 | Formulario | Mostrar error en el campo específico (`setError`) |
| 401 | Login | Mensaje genérico en el formulario (no revelar campo) |
| 401 | Endpoint protegido | Interceptor Axios intenta refresh → si falla, logout |
| 403 | Login | "Cuenta desactivada" en el formulario |
| 403 | Endpoint protegido | `toast.error('Sin permiso para esta acción')` |
| 404 | Búsqueda (CI persona) | Mostrar inline "No encontrado" — no es un error de UX |
| 409 | Creación de recurso | Mostrar error en el campo duplicado |
| 429 | Login | Mostrar countdown + deshabilitar formulario |
| 500 | Cualquiera | `toast.error` genérico + botón reintentar si aplica |
| Red caída | Cualquiera | `toast.error` genérico |

### Helper para extraer errores de Axios

```ts
// src/lib/api-error.ts
import { isAxiosError } from 'axios'

export function getApiErrorStatus(err: unknown): number | null {
  if (isAxiosError(err)) return err.response?.status ?? null
  return null
}

export function getApiErrorMessage(err: unknown): string {
  if (isAxiosError(err)) return err.response?.data?.mensaje ?? 'Error desconocido'
  return 'Error desconocido'
}
```

---

## 13. Inicialización del proyecto

Comandos para crear el proyecto desde cero:

```bash
# 1. Crear proyecto Vite + React + TypeScript
npm create vite@latest gym-admin-frontend -- --template react-ts
cd gym-admin-frontend

# 2. Dependencias principales
npm install react-router-dom axios zustand react-hook-form zod @hookform/resolvers sonner date-fns @tanstack/react-table

# 3. Tailwind CSS
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p

# 4. shadcn/ui (requiere configuración previa de Tailwind)
npx shadcn-ui@latest init
# Respuestas: TypeScript: yes, style: default, base color: slate, CSS variables: yes

# 5. Componentes shadcn que usará el módulo de auth
npx shadcn-ui@latest add button dialog badge input label table

# 6. Alias de imports (@/) — agregar en vite.config.ts y tsconfig.json
npm install -D @types/node
```

**`vite.config.ts`** después del setup:
```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
})
```

**`tsconfig.json`** — agregar en `compilerOptions`:
```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": { "@/*": ["./src/*"] }
  }
}
```

---

## Resumen de archivos a crear

| Archivo | Qué contiene |
|---|---|
| `src/api/axios.instance.ts` | Instancia Axios con interceptores de auth y refresh |
| `src/api/auth.api.ts` | Todas las llamadas al auth-service |
| `src/api/types/auth.types.ts` | Tipos de request/response |
| `src/types/index.ts` | Tipos de JWT y globales |
| `src/lib/jwt.ts` | Decodificador de JWT |
| `src/lib/api-error.ts` | Helpers de manejo de errores |
| `src/lib/utils.ts` | Helper `cn()` de Tailwind |
| `src/features/auth/store/auth.store.ts` | Zustand store de sesión |
| `src/features/auth/schemas/*.ts` | Esquemas Zod de formularios |
| `src/features/auth/pages/*.tsx` | 15 páginas del spec funcional |
| `src/features/auth/components/*.tsx` | Modales, paneles y componentes del módulo |
| `src/router/index.tsx` | Definición de rutas |
| `src/router/guards/*.tsx` | AuthGuard, PlatformGuard, PermissionGuard |
| `src/layouts/*.tsx` | PublicLayout, AdminLayout, PlatformLayout |
| `src/components/DataTable.tsx` | Tabla genérica con TanStack |
| `src/components/ConfirmDialog.tsx` | Diálogo de confirmación reutilizable |
| `src/App.tsx` | Montaje con refresh silencioso al iniciar |

---

*Frontend Auth Implementation v1.0 · Gym Administrator · Mayo 2026*

---

## 14. Sistema de Diseño — Tokens y Tema Gimnasio

> **Audiencia:** Módulo staff (recepcionistas, instructores, admins) → usuarios NO técnicos, interfaz intuitiva y visual fuerte.  
> **Audiencia:** Módulo plataforma (operadores SaaS) → usuarios técnicos, densidad alta de información.

### 14.1 Paleta de colores

| Token CSS / Tailwind | Hex | Uso |
|---|---|---|
| `gym-950` | `#09090b` | Fondo sidebar principal |
| `gym-900` | `#111827` | Header sidebar |
| `gym-800` | `#1f2937` | Ítem activo sidebar |
| `gym-700` | `#374151` | Hover ítem sidebar |
| `gym-orange` / `orange-500` | `#f97316` | CTA primario, links activos, íconos de acento |
| `gym-orange-dark` / `orange-600` | `#ea580c` | Hover de botón primario |
| `gym-orange-muted` / `orange-100` | `#ffedd5` | Badge "activo", fondos suaves de acento |
| `slate-50` | `#f8fafc` | Fondo del área de contenido |
| `slate-100` | `#f1f5f9` | Fondo de filas alternas / cards |
| `slate-700` | `#334155` | Texto secundario |
| `green-600` | `#16a34a` | Badge "Activo" |
| `red-600` | `#dc2626` | Destructivo, badge "Inactivo", errores |
| `amber-500` | `#f59e0b` | Advertencia, badge roles especiales |

### 14.2 Tipografía

```ts
// Fuente: Inter (Google Fonts)
// En index.html:
// <link rel="preconnect" href="https://fonts.googleapis.com">
// <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
```

| Rol | Clase Tailwind | Uso |
|---|---|---|
| Display | `text-3xl font-extrabold tracking-tight` | Título en LoginPage |
| Heading 1 | `text-2xl font-bold` | Título de página (`PageHeader`) |
| Heading 2 | `text-lg font-semibold` | Título de modal/panel |
| Heading 3 | `text-xs font-semibold uppercase tracking-wider text-slate-500` | Agrupador de sección |
| Body | `text-sm text-slate-700` | Texto de tabla, labels |
| Caption | `text-xs text-slate-500` | Metadatos, fechas |
| Error | `text-sm text-red-600` | Mensajes de validación |

### 14.3 Tailwind config extendido

```ts
// tailwind.config.ts
import type { Config } from 'tailwindcss'

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        gym: {
          950: '#09090b',
          900: '#111827',
          800: '#1f2937',
          700: '#374151',
          orange: '#f97316',
          'orange-dark': '#ea580c',
          'orange-light': '#ffedd5',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        card: '0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)',
        sidebar: '4px 0 6px -1px rgb(0 0 0 / 0.3)',
      },
    },
  },
  plugins: [],
} satisfies Config
```

### 14.4 Componentes base de UI reutilizables

**Botón primario (gym-orange)** — reemplaza el default azul de shadcn:

```tsx
// En src/components/ui/button.tsx — modificar la variante "default"
// Cambiar: bg-primary → bg-orange-500 hover:bg-orange-600 text-white
// Esto afecta todos los <Button> sin variante especificada
```

Agregar en `globals.css`:
```css
@layer base {
  :root {
    --primary: 24 95% 53%;          /* orange-500 en HSL */
    --primary-foreground: 0 0% 100%;
  }
}

body {
  font-family: 'Inter', system-ui, sans-serif;
}
```

---

## 15. Diseño Responsivo — Estrategia y Breakpoints

### 15.1 Breakpoints

| Breakpoint | Tailwind | Comportamiento |
|---|---|---|
| Mobile | `< 768px` | Sidebar oculto, drawer hamburger, tablas scrollables |
| Tablet | `md: 768px` | Sidebar colapsado (solo íconos), contenido expandido |
| Desktop | `lg: 1024px` | Sidebar completo (íconos + texto), layout horizontal |

### 15.2 AdminLayout responsivo

El layout más crítico de la app. Usuarios no técnicos acceden desde tablets y móviles (ej. recepcionista con tablet en el mostrador).

```tsx
// src/layouts/AdminLayout.tsx — versión responsiva completa
import { useState } from 'react'
import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { Menu, X, LayoutDashboard, Users, Shield, Smartphone, ScrollText, LogOut, Dumbbell } from 'lucide-react'
import { useCurrentUser, useHasPermission, useAuthStore } from '@/features/auth/store/auth.store'
import { logout as apiLogout } from '@/api/auth.api'
import { cn } from '@/lib/utils'

interface NavItem {
  to: string
  label: string
  icon: React.ReactNode
  permiso?: string
}

const NAV_ITEMS: NavItem[] = [
  { to: '/admin/dashboard',    label: 'Dashboard',      icon: <LayoutDashboard size={20} /> },
  { to: '/admin/usuarios',     label: 'Usuarios',       icon: <Users size={20} />,      permiso: 'usuarios:leer' },
  { to: '/admin/roles',        label: 'Roles',          icon: <Shield size={20} />,     permiso: 'roles:leer' },
  { to: '/admin/clientes/app', label: 'Cuentas App',    icon: <Smartphone size={20} /> },
  { to: '/admin/bitacora',     label: 'Bitácora',       icon: <ScrollText size={20} />, permiso: 'usuarios:leer' },
]

export function AdminLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const user = useCurrentUser()
  const { logout } = useAuthStore()

  const handleLogout = async () => {
    await apiLogout().catch(() => {})
    logout()
  }

  const navItems = NAV_ITEMS.filter(item =>
    !item.permiso || useHasPermission(item.permiso)
  )

  return (
    <div className="flex h-screen overflow-hidden bg-slate-50">

      {/* Overlay móvil */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-20 bg-black/60 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside className={cn(
        'fixed inset-y-0 left-0 z-30 flex flex-col bg-gym-950 transition-transform duration-300 ease-in-out',
        'w-72 lg:w-64',
        'lg:relative lg:translate-x-0',
        sidebarOpen ? 'translate-x-0' : '-translate-x-full'
      )}>
        {/* Logo / Marca */}
        <div className="flex items-center gap-3 px-5 py-5 border-b border-gym-800">
          <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-orange-500">
            <Dumbbell size={20} className="text-white" />
          </div>
          <div>
            <p className="text-white font-bold text-sm leading-tight">Gym Admin</p>
            <p className="text-slate-400 text-xs leading-tight truncate max-w-[140px]">
              {user && 'id_compania' in user ? `Sucursal ${(user as any).id_sucursal}` : ''}
            </p>
          </div>
          {/* Cerrar en móvil */}
          <button
            onClick={() => setSidebarOpen(false)}
            className="ml-auto text-slate-400 hover:text-white lg:hidden"
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
              onClick={() => setSidebarOpen(false)}
              className={({ isActive }) => cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-orange-500 text-white'
                  : 'text-slate-300 hover:bg-gym-800 hover:text-white'
              )}
            >
              {item.icon}
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Footer del sidebar: usuario + logout */}
        <div className="px-3 py-4 border-t border-gym-800">
          <div className="flex items-center gap-3 px-3 py-2 rounded-lg">
            <div className="flex items-center justify-center w-8 h-8 rounded-full bg-orange-500 text-white text-sm font-bold flex-shrink-0">
              {user?.nombre?.[0]?.toUpperCase() ?? 'U'}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-white text-sm font-medium truncate">{user?.nombre}</p>
              <p className="text-slate-400 text-xs truncate">
                {'id_rol' in (user ?? {}) ? (user as any).nombre_rol ?? 'Staff' : 'Staff'}
              </p>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center gap-3 w-full px-3 py-2.5 mt-1 rounded-lg text-sm font-medium text-slate-300 hover:bg-red-600/20 hover:text-red-400 transition-colors"
          >
            <LogOut size={20} />
            Cerrar sesión
          </button>
        </div>
      </aside>

      {/* Área principal */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Header móvil/tablet */}
        <header className="flex items-center gap-4 px-4 py-3 bg-white border-b shadow-sm lg:hidden">
          <button
            onClick={() => setSidebarOpen(true)}
            className="p-2 rounded-lg text-slate-600 hover:bg-slate-100"
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

## 16. PublicLayout — Branding de Gimnasio

Las pantallas de login son la primera impresión. Deben transmitir energía, fuerza y profesionalismo.

```tsx
// src/layouts/PublicLayout.tsx — con tema gimnasio
import { Outlet } from 'react-router-dom'
import { Dumbbell } from 'lucide-react'

export function PublicLayout() {
  return (
    <div className="min-h-screen flex">
      {/* Panel izquierdo — branding (oculto en móvil) */}
      <div className="hidden lg:flex lg:w-1/2 relative bg-gym-950 flex-col items-center justify-center p-12 overflow-hidden">
        {/* Gradiente decorativo */}
        <div className="absolute inset-0 bg-gradient-to-br from-orange-600/30 via-transparent to-transparent" />
        <div className="absolute top-0 right-0 w-96 h-96 rounded-full bg-orange-500/10 blur-3xl" />
        <div className="absolute bottom-0 left-0 w-64 h-64 rounded-full bg-orange-500/5 blur-3xl" />

        {/* Contenido branding */}
        <div className="relative z-10 text-center space-y-6">
          <div className="flex items-center justify-center w-20 h-20 rounded-2xl bg-orange-500 mx-auto shadow-lg shadow-orange-500/30">
            <Dumbbell size={40} className="text-white" />
          </div>
          <div>
            <h1 className="text-4xl font-extrabold text-white tracking-tight">Gym Admin</h1>
            <p className="text-slate-400 mt-2 text-lg">Gestiona tu gimnasio con eficiencia</p>
          </div>
          <div className="flex gap-8 justify-center text-center pt-4">
            {[
              { value: 'Usuarios', label: 'Gestionados' },
              { value: 'Roles', label: 'Personalizados' },
              { value: '100%', label: 'Seguro' },
            ].map(stat => (
              <div key={stat.label}>
                <p className="text-orange-400 font-bold text-xl">{stat.value}</p>
                <p className="text-slate-500 text-xs mt-1">{stat.label}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Panel derecho — formulario */}
      <div className="flex-1 flex items-center justify-center p-6 bg-slate-50">
        {/* Logo visible solo en móvil */}
        <div className="w-full max-w-md space-y-6">
          <div className="flex items-center gap-3 lg:hidden">
            <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-orange-500">
              <Dumbbell size={22} className="text-white" />
            </div>
            <span className="text-xl font-bold text-slate-900">Gym Admin</span>
          </div>

          <Outlet />
        </div>
      </div>
    </div>
  )
}
```

---

## 17. Especificaciones Visuales por Pantalla

### P-01 — LoginPage (staff) — Versión con tema gym

Diseño para usuarios no técnicos (recepcionistas, instructores). Mensajes claros, campo de "ID de gimnasio" con tooltip explicativo.

```tsx
// src/features/auth/pages/LoginPage.tsx — versión UI completa
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Navigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Eye, EyeOff, HelpCircle, Loader2 } from 'lucide-react'
import { useState } from 'react'
import { loginStaff } from '@/api/auth.api'
import { useAuthStore, useIsAuthenticated } from '../store/auth.store'
import { loginStaffSchema, type LoginStaffForm } from '../schemas/login.schema'
import { isAxiosError } from 'axios'

export function LoginPage() {
  const navigate = useNavigate()
  const { setSession } = useAuthStore()
  const isAuthenticated = useIsAuthenticated()
  const [showPassword, setShowPassword] = useState(false)

  if (isAuthenticated) return <Navigate to="/admin/dashboard" replace />

  const { register, handleSubmit, formState: { errors, isSubmitting }, setError } =
    useForm<LoginStaffForm>({ resolver: zodResolver(loginStaffSchema) })

  const onSubmit = async (data: LoginStaffForm) => {
    try {
      const response = await loginStaff(data)
      setSession(response.access_token)
      if (response.requiere_cambio_pwd) {
        navigate('/change-password', { replace: true })
      } else {
        navigate('/admin/dashboard', { replace: true })
      }
    } catch (err) {
      if (isAxiosError(err)) {
        if (err.response?.status === 401) {
          setError('root', { message: 'Correo, contraseña o ID de gimnasio incorrectos.' })
        } else if (err.response?.status === 403) {
          setError('root', { message: 'Tu cuenta está desactivada. Contacta al administrador.' })
        } else if (err.response?.status === 429) {
          setError('root', { message: 'Demasiados intentos. Espera unos minutos e intenta de nuevo.' })
        } else {
          toast.error('Error de conexión. Verifica tu internet e intenta de nuevo.')
        }
      }
    }
  }

  return (
    <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6">
      {/* Encabezado */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Bienvenido</h1>
        <p className="text-slate-500 text-sm mt-1">Ingresa tus datos para acceder al panel</p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {/* Error general */}
        {errors.root && (
          <div className="flex items-start gap-3 bg-red-50 border border-red-200 text-red-700 text-sm p-3 rounded-lg">
            <span className="text-red-500 mt-0.5">⚠</span>
            <span>{errors.root.message}</span>
          </div>
        )}

        {/* Correo */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            Correo electrónico
          </label>
          <input
            type="email"
            autoComplete="email"
            placeholder="tu@correo.com"
            {...register('correo')}
            className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
          />
          {errors.correo && (
            <p className="text-xs text-red-600">{errors.correo.message}</p>
          )}
        </div>

        {/* Contraseña con toggle */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">Contraseña</label>
          <div className="relative">
            <input
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="••••••••"
              {...register('password')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition pr-10"
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

        {/* ID Gimnasio con tooltip explicativo */}
        <div className="space-y-1.5">
          <label className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
            ID del gimnasio
            <span
              title="Es el número que te proporcionó el administrador de tu gimnasio al registrarte."
              className="text-slate-400 cursor-help"
            >
              <HelpCircle size={14} />
            </span>
          </label>
          <input
            type="number"
            placeholder="Ej: 1"
            {...register('id_compania')}
            className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
          />
          {errors.id_compania && (
            <p className="text-xs text-red-600">{errors.id_compania.message}</p>
          )}
        </div>

        {/* Botón submit */}
        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          {isSubmitting && <Loader2 size={16} className="animate-spin" />}
          {isSubmitting ? 'Ingresando...' : 'Ingresar'}
        </button>
      </form>

      {/* Links secundarios */}
      <div className="text-center space-y-2 text-sm">
        <Link to="/reset-password" className="block text-orange-600 hover:text-orange-700 font-medium">
          ¿Olvidaste tu contraseña?
        </Link>
        <Link to="/platform/login" className="block text-slate-400 hover:text-slate-600 text-xs">
          Acceso operadores de plataforma
        </Link>
      </div>
    </div>
  )
}
```

---

### P-02 — PlatformLoginPage (operadores SaaS)

Interfaz más técnica/sobria. Fondo oscuro, sin panel lateral de branding.

```tsx
// src/features/auth/pages/PlatformLoginPage.tsx
// src/layouts/PlatformLayout.tsx usa un fondo diferente para este portal
// Agregar en el router: la ruta /platform/login NO usa PublicLayout gym,
// usa PlatformPublicLayout (fondo oscuro total)

export function PlatformLoginPage() {
  // misma lógica de form que LoginPage pero sin id_compania
  // Visual: fondo gym-950, card con borde gym-800, texto blanco
  return (
    <div className="min-h-screen bg-gym-950 flex items-center justify-center p-6">
      <div className="w-full max-w-md bg-gym-900 border border-gym-800 rounded-2xl p-8 space-y-6">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-orange-500 flex items-center justify-center">
            {/* Dumbbell icon */}
          </div>
          <div>
            <p className="text-white font-bold">Gym Admin</p>
            <p className="text-slate-500 text-xs">Panel de Plataforma</p>
          </div>
        </div>

        <div>
          <h1 className="text-xl font-bold text-white">Acceso Plataforma</h1>
          <p className="text-slate-400 text-sm mt-1">Solo para operadores autorizados</p>
        </div>

        {/* Mismo patrón de form pero con clases dark */}
        {/* input: bg-gym-800 border-gym-700 text-white placeholder-slate-500 focus:ring-orange-500 */}
        {/* button: misma clase orange */}
      </div>
    </div>
  )
}
```

---

### P-03 y P-04 — Reset de contraseña

Flujo en dos pasos. Diseño tranquilizador (no intimidar al usuario no técnico).

**Pantalla P-03 — Solicitar reset:**
```
┌─────────────────────────────────────┐
│  ← Volver al inicio                 │
│                                     │
│  🔑 Recuperar contraseña            │
│  Te enviaremos un enlace a tu       │
│  correo para restablecer tu clave   │
│                                     │
│  [Correo electrónico ____________]  │
│  [ID del gimnasio (opcional) ____]  │
│                                     │
│  [   Enviar enlace de acceso    ]   │  ← orange-500
│                                     │
│  💡 Si no recibes el correo en      │
│     5 minutos, revisa tu carpeta    │
│     de spam                         │
└─────────────────────────────────────┘
```

**Pantalla P-04 — Confirmar reset (llega del email):**
```
┌─────────────────────────────────────┐
│  ✅ Enlace válido                   │
│                                     │
│  Nueva contraseña                   │
│  [••••••••••  👁]                   │
│                                     │
│  Confirmar contraseña               │
│  [••••••••••  👁]                   │
│                                     │
│  Requisitos de contraseña:          │
│  ✅ Mínimo 8 caracteres             │
│  ✅ Al menos una mayúscula          │
│  ❌ Al menos un número              │
│                                     │
│  [    Cambiar mi contraseña     ]   │
└─────────────────────────────────────┘
```

Implementar indicador de fortaleza de contraseña reactivo (actualiza en tiempo real con `watch` de RHF):

```tsx
// Indicador de fortaleza — componente simple
function PasswordStrength({ password }: { password: string }) {
  const checks = [
    { label: 'Mínimo 8 caracteres', ok: password.length >= 8 },
    { label: 'Una letra mayúscula', ok: /[A-Z]/.test(password) },
    { label: 'Un número',           ok: /[0-9]/.test(password) },
  ]
  return (
    <ul className="space-y-1 mt-2">
      {checks.map(c => (
        <li key={c.label} className={`flex items-center gap-2 text-xs ${c.ok ? 'text-green-600' : 'text-slate-400'}`}>
          <span>{c.ok ? '✓' : '○'}</span>
          {c.label}
        </li>
      ))}
    </ul>
  )
}
```

---

### P-05 — ChangePasswordPage (primer login)

El usuario llega aquí automáticamente si `requiere_cambio_pwd = true`. Debe sentirse guiado, no bloqueado.

```
┌─────────────────────────────────────────┐
│  🔒 Antes de continuar...               │
│                                         │
│  Por seguridad, debes crear una         │
│  contraseña nueva para tu cuenta.       │
│  Esto solo ocurre la primera vez.       │
│                                         │
│  Nueva contraseña      [_________ 👁]   │
│  Confirmar contraseña  [_________ 👁]   │
│                                         │
│  [   Crear mi contraseña y entrar   ]   │
└─────────────────────────────────────────┘
```

---

### P-06 — UsuariosPage — Versión visual completa

```tsx
// src/features/auth/pages/UsuariosPage.tsx — reemplazar skeleton visual
// Agregar PageHeader y skeleton de tabla durante carga

// Skeleton durante carga (reemplaza "Cargando..."):
function TableSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex gap-4 py-3 border-b animate-pulse">
          <div className="h-4 bg-slate-200 rounded w-1/4" />
          <div className="h-4 bg-slate-200 rounded w-1/3" />
          <div className="h-4 bg-slate-200 rounded w-1/6" />
          <div className="h-4 bg-slate-200 rounded w-1/8" />
          <div className="h-5 bg-slate-200 rounded-full w-16" />
        </div>
      ))}
    </div>
  )
}

// Estado vacío (sin usuarios):
function EmptyState({ onAdd }: { onAdd: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <Users size={48} className="text-slate-300 mb-4" />
      <h3 className="text-slate-700 font-medium">No hay usuarios registrados</h3>
      <p className="text-slate-400 text-sm mt-1 mb-4">Agrega el primer usuario del sistema</p>
      <Button onClick={onAdd}>Nuevo usuario</Button>
    </div>
  )
}
```

**Badge de estado:** Reemplazar clases genéricas:
```tsx
// Activo → verde, Inactivo → rojo con texto descriptivo
<Badge className={u.activo
  ? 'bg-green-100 text-green-700 border-green-200'
  : 'bg-red-100 text-red-700 border-red-200'
}>
  {u.activo ? '● Activo' : '● Inactivo'}
</Badge>
```

**`PageHeader` — componente reutilizable para todas las páginas internas:**
```tsx
// src/components/PageHeader.tsx
interface Props {
  title: string
  description?: string
  action?: React.ReactNode
}

export function PageHeader({ title, description, action }: Props) {
  return (
    <div className="flex items-start justify-between px-6 py-5 border-b bg-white">
      <div>
        <h1 className="text-xl font-bold text-slate-900">{title}</h1>
        {description && <p className="text-slate-500 text-sm mt-0.5">{description}</p>}
      </div>
      {action && <div>{action}</div>}
    </div>
  )
}
```

Uso en UsuariosPage:
```tsx
<PageHeader
  title="Usuarios"
  description="Gestiona los miembros del equipo y sus accesos"
  action={
    <IfPermission permiso="usuarios:crear">
      <Button onClick={() => setCrearOpen(true)}>
        <UserPlus size={16} className="mr-2" /> Nuevo usuario
      </Button>
    </IfPermission>
  }
/>
```

---

### P-09 — BitacoraPage — Tabla paginada con filtros

```
┌── PageHeader ──────────────────────────────────────────┐
│ Bitácora de actividad                                   │
│ Registro de todas las acciones del sistema              │
└────────────────────────────────────────────────────────┘

┌── Filtros (colapsables en móvil) ──────────────────────┐
│ [Módulo ▾]  [Desde: ____]  [Hasta: ____]  [Buscar 🔍] │
└────────────────────────────────────────────────────────┘

┌── Tabla ───────────────────────────────────────────────┐
│ Usuario │ Módulo │ Acción │ Entidad │ IP │ Fecha        │
│─────────┼────────┼────────┼─────────┼────┼─────────────│
│ Ana M.  │ USERS  │ crear  │ 42      │... │ 23 may, 14:30│
└────────────────────────────────────────────────────────┘

← Anterior   Pág 1 de 12   Siguiente →
```

Los módulos se muestran como `Badge` con color por módulo:
```tsx
const MODULE_COLORS: Record<string, string> = {
  usuarios: 'bg-blue-100 text-blue-700',
  roles:    'bg-purple-100 text-purple-700',
  auth:     'bg-orange-100 text-orange-700',
  personas: 'bg-teal-100 text-teal-700',
}
```

---

## 18. Estados de Carga y Vacíos — Especificación Completa

| Pantalla | Estado carga | Estado vacío |
|---|---|---|
| UsuariosPage | `TableSkeleton` (5 filas animadas) | Ícono Users + CTA "Nuevo usuario" |
| RolesPage | `TableSkeleton` (3 filas) | Ícono Shield + CTA "Nuevo rol" |
| BitacoraPage | `TableSkeleton` (8 filas) | Ícono ScrollText + "No hay registros para el período" |
| ClientesAppPage | `TableSkeleton` (5 filas) | Ícono Smartphone + "Ningún cliente registrado" |
| PlatformUsuariosPage | `TableSkeleton` (3 filas) | Ícono UserCog + CTA (solo super_admin) |

**Spinner de pantalla completa** (durante `!initialized` en los guards):
```tsx
// Mostrar mientras se intenta el refresh silencioso al abrir la app
export function FullScreenLoader() {
  return (
    <div className="min-h-screen bg-gym-950 flex items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        <div className="w-14 h-14 rounded-2xl bg-orange-500 flex items-center justify-center animate-pulse">
          <Dumbbell size={28} className="text-white" />
        </div>
        <p className="text-slate-500 text-sm">Cargando...</p>
      </div>
    </div>
  )
}
```

Integrar en los guards:
```tsx
// AuthGuard.tsx
if (!initialized) return <FullScreenLoader />
```

---

## 19. PlatformLayout — Tema SaaS Técnico

Para operadores de plataforma (super_admin, soporte, viewer). Interfaz más densa y técnica.

```tsx
// src/layouts/PlatformLayout.tsx
import { Outlet, NavLink } from 'react-router-dom'
import { LayoutDashboard, Users, ChevronRight, Activity } from 'lucide-react'
import { useCurrentUser, useAuthStore } from '@/features/auth/store/auth.store'
import { logout as apiLogout } from '@/api/auth.api'
import type { JwtPayloadPlataforma } from '@/types'

const ROLE_BADGE: Record<string, string> = {
  super_admin: 'bg-orange-500/20 text-orange-400 border border-orange-500/30',
  soporte:     'bg-blue-500/20 text-blue-400 border border-blue-500/30',
  viewer:      'bg-slate-600/40 text-slate-400 border border-slate-600/30',
}

const ROLE_LABEL: Record<string, string> = {
  super_admin: 'Super Admin',
  soporte:     'Soporte',
  viewer:      'Viewer',
}

export function PlatformLayout() {
  const user = useCurrentUser() as JwtPayloadPlataforma | null
  const { logout } = useAuthStore()

  const handleLogout = async () => {
    await apiLogout().catch(() => {})
    logout()
  }

  return (
    <div className="flex h-screen bg-gym-950 text-slate-100">
      {/* Sidebar compacto — solo íconos + texto, sin branding grande */}
      <aside className="w-56 flex flex-col bg-gym-900 border-r border-gym-800">
        {/* Header plataforma */}
        <div className="px-4 py-4 border-b border-gym-800">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
            Plataforma
          </p>
          <p className="text-white font-bold text-sm mt-0.5">Gym Admin</p>
        </div>

        {/* Badge del rol */}
        {user && (
          <div className="px-4 pt-3">
            <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${ROLE_BADGE[user.rol_plataforma] ?? ''}`}>
              {ROLE_LABEL[user.rol_plataforma]}
            </span>
          </div>
        )}

        {/* Nav */}
        <nav className="flex-1 px-2 py-3 space-y-0.5">
          {[
            { to: '/platform/dashboard', label: 'Dashboard',  icon: <LayoutDashboard size={16} /> },
            { to: '/platform/usuarios',  label: 'Operadores', icon: <Users size={16} /> },
            { to: '/platform/bitacora',  label: 'Actividad',  icon: <Activity size={16} /> },
          ].map(item => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center gap-2.5 px-3 py-2 rounded-md text-xs font-medium transition-colors ${
                  isActive
                    ? 'bg-orange-500/15 text-orange-400'
                    : 'text-slate-400 hover:bg-gym-800 hover:text-slate-200'
                }`
              }
            >
              {item.icon}
              {item.label}
              <ChevronRight size={12} className="ml-auto opacity-40" />
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        <div className="px-4 py-3 border-t border-gym-800 text-xs">
          <p className="text-slate-300 font-medium truncate">{user?.nombre}</p>
          <button
            onClick={handleLogout}
            className="text-slate-500 hover:text-red-400 mt-1 transition-colors"
          >
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

## 20. UX para Usuarios No Técnicos — Reglas de Diseño

Estas reglas aplican a **todas las pantallas del módulo staff** (AdminLayout):

### 20.1 Mensajes de error — lenguaje humano

| ❌ Técnico (evitar) | ✅ Humano (usar) |
|---|---|
| "Error 409: Conflict" | "Este correo ya está registrado" |
| "Unauthorized" | "Datos incorrectos. Verifica tu correo y contraseña" |
| "Network error" | "Sin conexión. Revisa tu internet e intenta de nuevo" |
| "Validation failed" | "Completa todos los campos correctamente" |
| "403 Forbidden" | "No tienes permiso para realizar esta acción" |

### 20.2 Confirmaciones destructivas

Siempre usar `ConfirmDialog` con texto descriptivo de consecuencias:
```
¿Desactivar a "Juan Pérez"?
Al desactivarlo, no podrá ingresar al sistema. 
Puedes reactivarlo en cualquier momento.

[Cancelar]  [Sí, desactivar]
```

### 20.3 Targets táctiles (mobile-first)

- Botones: mínimo `h-10` (40px) — usar `py-2.5`
- Links de navegación: mínimo `py-2.5` en sidebar
- Checkboxes: usar `label` envolvente con área de clic amplia
- Acciones de tabla: botones con texto, no solo íconos

### 20.4 Feedback inmediato

| Acción | Feedback |
|---|---|
| Submit de form | Botón muestra spinner + texto "Guardando..." inmediatamente |
| Éxito | Toast verde en esquina superior derecha, 4 segundos |
| Error recuperable | Toast rojo con botón "Reintentar" si aplica |
| Acción destructiva | Toast con opción "Deshacer" si el backend lo soporta |

### 20.5 Página sin acceso (PermissionGuard redirect)

```tsx
// src/features/auth/pages/SinAccesoPage.tsx
export function SinAccesoPage() {
  return (
    <div className="flex flex-col items-center justify-center h-full py-24 text-center px-4">
      <div className="w-16 h-16 rounded-full bg-slate-100 flex items-center justify-center mb-4">
        <Lock size={28} className="text-slate-400" />
      </div>
      <h2 className="text-xl font-bold text-slate-800">Sin acceso</h2>
      <p className="text-slate-500 text-sm mt-2 max-w-xs">
        No tienes permisos para ver esta sección. 
        Contacta al administrador si crees que es un error.
      </p>
      <Link to="/admin/dashboard" className="mt-6 text-orange-600 text-sm font-medium hover:underline">
        Volver al inicio
      </Link>
    </div>
  )
}
```

---

## 21. Configuración de Sonner (Toast notifications)

Agregar en `App.tsx` el `Toaster` con posición y tema consistente:

```tsx
import { Toaster } from 'sonner'

export function App() {
  // ... useEffect de refresh ...
  return (
    <>
      <RouterProvider router={router} />
      <Toaster
        position="top-right"
        richColors
        toastOptions={{
          classNames: {
            toast: 'font-sans text-sm',
          },
        }}
      />
    </>
  )
}
```

---

## Resumen de cambios UI/UX sobre la implementación técnica base

| Archivo | Cambio UI/UX |
|---|---|
| `tailwind.config.ts` | Colores gym, fuente Inter |
| `src/index.css` | Variables HSL para shadcn, import fuente |
| `src/layouts/PublicLayout.tsx` | Panel split: branding oscuro izq + formulario der |
| `src/layouts/AdminLayout.tsx` | Sidebar oscuro responsive con hamburger móvil |
| `src/layouts/PlatformLayout.tsx` | Sidebar compacto dark SaaS con badge de rol |
| `src/features/auth/pages/LoginPage.tsx` | Eye toggle, tooltip ID gimnasio, botón orange |
| `src/features/auth/pages/SinAccesoPage.tsx` | Página amigable sin acceso |
| `src/components/PageHeader.tsx` | Header consistente para páginas internas |
| `src/components/FullScreenLoader.tsx` | Loader de inicialización con branding |
| Todas las páginas internas | `TableSkeleton` + `EmptyState` por módulo |
| `src/App.tsx` | `Toaster` configurado |

*Frontend UI/UX Design v1.0 · Gym Administrator · Mayo 2026*
