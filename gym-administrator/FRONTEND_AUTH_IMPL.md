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
