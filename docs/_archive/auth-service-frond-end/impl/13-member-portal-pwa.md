# IMPL-13 — Portal del Miembro: PWA (nuevo repo)

> **ESTADO:** 📜 Histórico — paso de implementación ya completado. NO describe el estado actual del código; es el registro de cómo se construyó este módulo. Ver [../../STATUS.md](../../STATUS.md).

> **Módulo:** Portal del Miembro — Fase 2 (frontend independiente)
> **Complejidad:** ★★★★★
> **Prerequisito:** IMPL-12 completado (backends listos)
> **Resultado:** PWA mobile-first que permite al miembro ver su membresía y registrar asistencia

---

## Características clave

| Característica | Detalle |
|---|---|
| Framework | Vite + React 18 + TypeScript |
| PWA | `vite-plugin-pwa` — caché offline, service worker |
| Styling | Tailwind CSS |
| State | Zustand |
| Forms | React Hook Form + Zod |
| HTTP | Axios |
| Routing | React Router v6 |
| Auth social | Google Identity Services + Facebook Login SDK |
| Branding | Logo + nombre del gimnasio desde endpoint público / JWT |

---

## URL definitiva del portal

```
https://app.tudominio.com/gym/<qrToken>
```

El `qrToken` identifica la sucursal/gimnasio. Es permanente y manualmente revocable.

---

## Estructura del nuevo repositorio

```
member-portal/                          ← nombre del repo
├── public/
│   └── icons/                          ← íconos PWA (192x192, 512x512)
├── src/
│   ├── domain/
│   │   ├── entities/
│   │   │   ├── GimnasioPublico.ts      ← { idCompania, nombreCompania, logoUrl }
│   │   │   ├── MiembroSession.ts       ← datos del JWT cliente
│   │   │   ├── Membresia.ts            ← { estado, diasRestantes, accesosRestantes, ... }
│   │   │   └── Asistencia.ts           ← { fecha, horaEntrada, metodoRegistro }
│   │   └── ports/
│   │       ├── IGimnasioRepository.ts
│   │       ├── IAuthRepository.ts
│   │       ├── IMembresiaRepository.ts
│   │       └── IAsistenciaRepository.ts
│   ├── application/
│   │   ├── ResolverQrUseCase.ts
│   │   ├── LoginManualUseCase.ts
│   │   ├── LoginGoogleUseCase.ts
│   │   ├── LoginFacebookUseCase.ts
│   │   ├── ObtenerMembresiaUseCase.ts
│   │   ├── RegistrarCheckInUseCase.ts
│   │   ├── ObtenerHistorialUseCase.ts
│   │   └── ReactivarCongelamientoUseCase.ts
│   ├── infrastructure/
│   │   ├── http/
│   │   │   ├── axios.instance.ts       ← Axios con interceptor JWT
│   │   │   ├── GimnasioHttpRepository.ts
│   │   │   ├── AuthHttpRepository.ts
│   │   │   ├── MembresiaHttpRepository.ts
│   │   │   └── AsistenciaHttpRepository.ts
│   │   └── store/
│   │       ├── auth.store.ts           ← { token, miembro, gimnasio, initialized }
│   │       └── gimnasio.store.ts       ← { qrToken, idCompania, nombre, logoUrl }
│   ├── ui/
│   │   ├── guards/
│   │   │   └── ClienteGuard.tsx        ← requiere JWT tipo: 'cliente' + mismo id_compania
│   │   ├── layouts/
│   │   │   └── MemberLayout.tsx        ← header con logo del gimnasio + nombre del miembro
│   │   ├── pages/
│   │   │   ├── GymLandingPage.tsx      ← carga branding, redirige a login o dashboard
│   │   │   ├── LoginPage.tsx           ← login manual + botones Google + Facebook
│   │   │   ├── DashboardPage.tsx       ← tarjeta membresía + botón check-in
│   │   │   ├── HistorialPage.tsx       ← listado de asistencias
│   │   │   └── ErrorPage.tsx           ← QR inválido, sin membresía, etc.
│   │   └── components/
│   │       ├── MembershipCard.tsx
│   │       ├── CheckInButton.tsx
│   │       ├── AttendanceList.tsx
│   │       ├── GoogleLoginButton.tsx
│   │       └── FacebookLoginButton.tsx
│   ├── router/
│   │   └── index.tsx
│   ├── lib/
│   │   ├── jwt.ts                      ← decodificar JWT sin librerías pesadas
│   │   ├── api-error.ts
│   │   └── utils.ts
│   ├── App.tsx
│   └── main.tsx
├── index.html
├── vite.config.ts
├── tailwind.config.ts
└── .env.example
```

---

## Rutas de la aplicación

```
/gym/:qrToken                           ← GymLandingPage (pública)
/gym/:qrToken/login                     ← LoginPage (pública)
/gym/:qrToken/dashboard                 ← DashboardPage (ClienteGuard)
/gym/:qrToken/historial                 ← HistorialPage (ClienteGuard)
/*                                      ← ErrorPage (404)
```

---

## Flujo completo del usuario

```
1. Escanea QR impreso en el gimnasio
   → abre https://app.tudominio.com/gym/<qrToken>

2. GymLandingPage monta:
   a. Llama GET /auth/gimnasio/by-qr/{qrToken}
   b. Guarda { idCompania, nombre, logoUrl } en gimnasio.store
   c. Si hay JWT válido en localStorage y mismo id_compania → redirige a /dashboard
   d. Si JWT de otro gimnasio → borra localStorage → redirige a /login (auto-logout)
   e. Si sin sesión → redirige a /login

3. LoginPage muestra:
   - Logo del gimnasio + nombre
   - Campo "email o cédula" + contraseña
   - Botón [Continuar con Google]
   - Botón [Continuar con Facebook]
   - Link "Olvidé mi contraseña" → flujo reset en auth-service

4. Tras login exitoso:
   - Guarda accessToken + refreshToken en localStorage
   - Decodifica JWT → obtiene { id_persona, nombre, id_compania, nombre_compania, logo_url }
   - Redirige a /gym/:qrToken/dashboard

5. DashboardPage muestra:
   - Header: logo del gimnasio | nombre del miembro
   - MembershipCard: estado / días o pases restantes / fecha vencimiento
   - [Registrar entrada] → POST /asistencias/qr { qrToken }
   - Si ya registró hoy → botón deshabilitado + "Ya registraste tu entrada hoy"
   - Si membresía congelada → botón [Solicitar reactivación] en lugar del check-in

6. HistorialPage muestra las últimas asistencias del miembro
```

---

## Implementación paso a paso

### Paso 1 — Setup del repositorio

**`vite.config.ts`:**
```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg}'],
        runtimeCaching: [{
          urlPattern: /^https:\/\/.*\/api\/v1\/(auth|core|asistencias)\//,
          handler: 'NetworkFirst',
          options: { cacheName: 'api-cache', expiration: { maxAgeSeconds: 300 } },
        }],
      },
      manifest: {
        name: 'Mi Gimnasio',
        short_name: 'Gimnasio',
        theme_color: '#111827',
        background_color: '#111827',
        display: 'standalone',
        orientation: 'portrait',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
    }),
  ],
  resolve: { alias: { '@': '/src' } },
})
```

**`.env.example`:**
```
VITE_AUTH_API_URL=http://localhost:8080/api/v1
VITE_CORE_API_URL=http://localhost:8083/api/v1
VITE_ATTENDANCE_API_URL=http://localhost:8084/api/v1
VITE_GOOGLE_CLIENT_ID=
VITE_FACEBOOK_APP_ID=
```

---

### Paso 2 — Axios instance con interceptor JWT

**`src/infrastructure/http/axios.instance.ts`:**
```ts
import axios from 'axios'
import { useAuthStore } from '@/infrastructure/store/auth.store'

const authApi = axios.create({ baseURL: import.meta.env.VITE_AUTH_API_URL })
const coreApi = axios.create({ baseURL: import.meta.env.VITE_CORE_API_URL })
const attendanceApi = axios.create({ baseURL: import.meta.env.VITE_ATTENDANCE_API_URL })

const attachToken = (instance: typeof authApi) => {
  instance.interceptors.request.use(cfg => {
    const token = useAuthStore.getState().accessToken
    if (token) cfg.headers.Authorization = `Bearer ${token}`
    return cfg
  })
}

attachToken(authApi)
attachToken(coreApi)
attachToken(attendanceApi)

export { authApi, coreApi, attendanceApi }
```

---

### Paso 3 — Auth store

**`src/infrastructure/store/auth.store.ts`:**
```ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface MiembroSession {
  idPersona: number
  idCompania: number
  nombre: string
  nombreCompania: string
  logoUrl: string | null
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  miembro: MiembroSession | null
  initialized: boolean
  login: (tokens: { accessToken: string; refreshToken: string }, miembro: MiembroSession) => void
  logout: () => void
  setInitialized: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      miembro: null,
      initialized: false,
      login: (tokens, miembro) => set({ ...tokens, miembro }),
      logout: () => set({ accessToken: null, refreshToken: null, miembro: null }),
      setInitialized: () => set({ initialized: true }),
    }),
    { name: 'member-auth' }
  )
)
```

---

### Paso 4 — Gimnasio store

**`src/infrastructure/store/gimnasio.store.ts`:**
```ts
import { create } from 'zustand'

interface GimnasioState {
  qrToken: string | null
  idCompania: number | null
  nombre: string | null
  logoUrl: string | null
  setGimnasio: (data: { qrToken: string; idCompania: number; nombre: string; logoUrl: string | null }) => void
  clear: () => void
}

export const useGimnasioStore = create<GimnasioState>()((set) => ({
  qrToken: null,
  idCompania: null,
  nombre: null,
  logoUrl: null,
  setGimnasio: (data) => set(data),
  clear: () => set({ qrToken: null, idCompania: null, nombre: null, logoUrl: null }),
}))
```

---

### Paso 5 — GymLandingPage (resolver QR + auto-logout multi-gimnasio)

**`src/ui/pages/GymLandingPage.tsx`:**
```tsx
import { useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/infrastructure/store/auth.store'
import { useGimnasioStore } from '@/infrastructure/store/gimnasio.store'
import { resolverQr } from '@/infrastructure/http/GimnasioHttpRepository'

export default function GymLandingPage() {
  const { qrToken } = useParams<{ qrToken: string }>()
  const navigate = useNavigate()
  const { miembro, logout } = useAuthStore()
  const { setGimnasio } = useGimnasioStore()

  useEffect(() => {
    if (!qrToken) { navigate('/error'); return }

    resolverQr(qrToken).then(gimnasio => {
      setGimnasio({ qrToken, ...gimnasio })

      // Sesión activa de OTRO gimnasio → auto-logout
      if (miembro && miembro.idCompania !== gimnasio.idCompania) {
        logout()
        navigate(`/gym/${qrToken}/login`)
        return
      }

      // Sesión activa del MISMO gimnasio → ir al dashboard
      if (miembro && miembro.idCompania === gimnasio.idCompania) {
        navigate(`/gym/${qrToken}/dashboard`)
        return
      }

      // Sin sesión → login
      navigate(`/gym/${qrToken}/login`)
    }).catch(() => navigate('/error'))
  }, [qrToken])

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-950">
      <div className="w-8 h-8 border-2 border-white border-t-transparent rounded-full animate-spin" />
    </div>
  )
}
```

---

### Paso 6 — LoginPage

**`src/ui/pages/LoginPage.tsx`:**
```tsx
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useParams, useNavigate } from 'react-router-dom'
import { useGimnasioStore } from '@/infrastructure/store/gimnasio.store'
import { useAuthStore } from '@/infrastructure/store/auth.store'
import GoogleLoginButton from '@/ui/components/GoogleLoginButton'
import FacebookLoginButton from '@/ui/components/FacebookLoginButton'

const loginSchema = z.object({
  login: z.string().min(1, 'Requerido'),
  password: z.string().min(1, 'Requerido'),
})
type LoginForm = z.infer<typeof loginSchema>

export default function LoginPage() {
  const { qrToken } = useParams<{ qrToken: string }>()
  const navigate = useNavigate()
  const { idCompania, nombre, logoUrl } = useGimnasioStore()
  const { login: storeLogin } = useAuthStore()
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  })

  const onSubmit = async (data: LoginForm) => {
    // Llamar LoginManualUseCase
    // storeLogin(tokens, miembro)
    // navigate(`/gym/${qrToken}/dashboard`)
  }

  return (
    <div className="min-h-screen bg-gray-950 flex flex-col items-center justify-center px-4">
      {/* Branding del gimnasio */}
      <div className="mb-8 text-center">
        {logoUrl
          ? <img src={logoUrl} alt={nombre ?? ''} className="h-16 mx-auto mb-3 object-contain" />
          : <div className="w-16 h-16 bg-gray-800 rounded-full mx-auto mb-3" />
        }
        <h1 className="text-white text-xl font-semibold">{nombre}</h1>
      </div>

      {/* Formulario */}
      <form onSubmit={handleSubmit(onSubmit)} className="w-full max-w-sm space-y-4">
        <div>
          <input
            {...register('login')}
            placeholder="Email o cédula"
            className="w-full bg-gray-800 text-white rounded-lg px-4 py-3 outline-none focus:ring-2 focus:ring-white/20"
          />
          {errors.login && <p className="text-red-400 text-sm mt-1">{errors.login.message}</p>}
        </div>
        <div>
          <input
            {...register('password')}
            type="password"
            placeholder="Contraseña"
            className="w-full bg-gray-800 text-white rounded-lg px-4 py-3 outline-none focus:ring-2 focus:ring-white/20"
          />
        </div>
        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full bg-white text-gray-900 font-semibold py-3 rounded-lg disabled:opacity-50"
        >
          {isSubmitting ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>

      {/* Separador */}
      <div className="flex items-center gap-3 w-full max-w-sm my-4">
        <div className="flex-1 h-px bg-gray-700" />
        <span className="text-gray-500 text-sm">o</span>
        <div className="flex-1 h-px bg-gray-700" />
      </div>

      {/* OAuth */}
      <div className="w-full max-w-sm space-y-3">
        <GoogleLoginButton idCompania={idCompania!} qrToken={qrToken!} />
        <FacebookLoginButton idCompania={idCompania!} qrToken={qrToken!} />
      </div>

      {/* Reset password */}
      <a href="/forgot-password" className="text-gray-400 text-sm mt-6 hover:text-white">
        Olvidé mi contraseña
      </a>
    </div>
  )
}
```

---

### Paso 7 — ClienteGuard

**`src/ui/guards/ClienteGuard.tsx`:**
```tsx
import { Navigate, Outlet, useParams } from 'react-router-dom'
import { useAuthStore } from '@/infrastructure/store/auth.store'
import { useGimnasioStore } from '@/infrastructure/store/gimnasio.store'

export default function ClienteGuard() {
  const { qrToken } = useParams<{ qrToken: string }>()
  const { miembro } = useAuthStore()
  const { idCompania } = useGimnasioStore()

  // Sin sesión → login
  if (!miembro) return <Navigate to={`/gym/${qrToken}/login`} replace />

  // Sesión de otro gimnasio → login (caso raro: acceso directo sin pasar por GymLandingPage)
  if (idCompania && miembro.idCompania !== idCompania) {
    return <Navigate to={`/gym/${qrToken}/login`} replace />
  }

  return <Outlet />
}
```

---

### Paso 8 — DashboardPage

```
┌── MemberLayout ─────────────────────────────────────┐
│  [Logo Gym]  FitZone Ecuador          Juan Pérez  ☰  │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌── MembershipCard ──────────────────────────────┐ │
│  │  MEMBRESÍA ACTIVA                  ✅          │ │
│  │  Plan mensual                                  │ │
│  │  Días restantes: 18                            │ │
│  │  Vence: 02 Jul 2026                            │ │
│  └────────────────────────────────────────────────┘ │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │          [REGISTRAR ENTRADA]                │    │
│  └─────────────────────────────────────────────┘    │
│  (o: "Ya registraste tu entrada hoy ✓")             │
│                                                     │
│  ┌── Últimas asistencias ─────────────────────────┐ │
│  │  Hoy              10:32 AM                    │ │
│  │  Jue 12 Jun       09:15 AM                    │ │
│  │  Mié 11 Jun       17:40 PM                    │ │
│  │                  [Ver todo →]                 │ │
│  └────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

**`src/ui/pages/DashboardPage.tsx`:**
```tsx
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useGimnasioStore } from '@/infrastructure/store/gimnasio.store'
import MembershipCard from '@/ui/components/MembershipCard'
import CheckInButton from '@/ui/components/CheckInButton'
import AttendanceList from '@/ui/components/AttendanceList'
// import usecases...

export default function DashboardPage() {
  const { qrToken } = useParams<{ qrToken: string }>()
  const [membresia, setMembresia] = useState(null)
  const [asistencias, setAsistencias] = useState([])
  const [yaRegistroHoy, setYaRegistroHoy] = useState(false)

  // Cargar datos al montar...

  return (
    <div className="min-h-screen bg-gray-950 pb-10">
      <MembershipCard membresia={membresia} />
      <div className="px-4 mt-6">
        <CheckInButton
          qrToken={qrToken!}
          disabled={yaRegistroHoy || membresia?.estado === 'congelada'}
          estado={membresia?.estado}
          onSuccess={() => setYaRegistroHoy(true)}
        />
      </div>
      <div className="px-4 mt-8">
        <AttendanceList asistencias={asistencias} />
      </div>
    </div>
  )
}
```

---

### Paso 9 — CheckInButton

**`src/ui/components/CheckInButton.tsx`:**
```tsx
interface Props {
  qrToken: string
  disabled: boolean
  estado: 'activa' | 'vencida' | 'congelada' | undefined
  onSuccess: () => void
}

export default function CheckInButton({ qrToken, disabled, estado, onSuccess }: Props) {
  const [loading, setLoading] = useState(false)

  const handleClick = async () => {
    setLoading(true)
    try {
      await registrarCheckIn(qrToken)   // POST /asistencias/qr
      onSuccess()
    } catch (e) {
      // mostrar toast de error
    } finally {
      setLoading(false)
    }
  }

  if (estado === 'congelada') {
    return (
      <div className="space-y-3">
        <p className="text-yellow-400 text-center text-sm">Tu membresía está congelada</p>
        <button
          onClick={handleReactivar}
          className="w-full bg-yellow-500 text-gray-900 font-semibold py-4 rounded-2xl"
        >
          Solicitar reactivación
        </button>
      </div>
    )
  }

  return (
    <button
      onClick={handleClick}
      disabled={disabled || loading}
      className="w-full bg-white text-gray-900 font-bold py-5 rounded-2xl text-lg 
                 disabled:opacity-40 disabled:cursor-not-allowed active:scale-95 transition-transform"
    >
      {loading ? 'Registrando…' : disabled ? '✓ Entrada registrada hoy' : 'Registrar entrada'}
    </button>
  )
}
```

---

### Paso 10 — Google y Facebook Login Buttons

**`src/ui/components/GoogleLoginButton.tsx`:**
```tsx
import { useEffect } from 'react'

interface Props { idCompania: number; qrToken: string }

export default function GoogleLoginButton({ idCompania, qrToken }: Props) {
  useEffect(() => {
    // Cargar Google Identity Services SDK
    const script = document.createElement('script')
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    document.body.appendChild(script)

    script.onload = () => {
      window.google.accounts.id.initialize({
        client_id: import.meta.env.VITE_GOOGLE_CLIENT_ID,
        callback: async ({ credential }) => {
          // POST /auth/app/oauth/google { idToken: credential, idCompania }
          // storeLogin(response) → navigate al dashboard
        },
      })
      window.google.accounts.id.renderButton(
        document.getElementById('google-signin')!,
        { theme: 'filled_black', size: 'large', width: 400, text: 'continue_with' }
      )
    }
    return () => document.body.removeChild(script)
  }, [])

  return <div id="google-signin" className="w-full" />
}
```

**`src/ui/components/FacebookLoginButton.tsx`:**
```tsx
interface Props { idCompania: number; qrToken: string }

export default function FacebookLoginButton({ idCompania, qrToken }: Props) {
  useEffect(() => {
    // Cargar Facebook SDK
    window.fbAsyncInit = () => {
      FB.init({ appId: import.meta.env.VITE_FACEBOOK_APP_ID, version: 'v19.0' })
    }
    const script = document.createElement('script')
    script.src = 'https://connect.facebook.net/es_LA/sdk.js'
    script.async = true
    document.body.appendChild(script)
    return () => document.body.removeChild(script)
  }, [])

  const handleFacebookLogin = () => {
    FB.login(response => {
      if (response.authResponse) {
        const { accessToken } = response.authResponse
        // POST /auth/app/oauth/facebook { accessToken, idCompania }
        // storeLogin(response) → navigate al dashboard
      }
    }, { scope: 'email,public_profile' })
  }

  return (
    <button
      onClick={handleFacebookLogin}
      className="w-full flex items-center justify-center gap-3 bg-[#1877F2] text-white 
                 font-semibold py-3 rounded-lg"
    >
      <svg viewBox="0 0 24 24" className="w-5 h-5 fill-white">
        <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
      </svg>
      Continuar con Facebook
    </button>
  )
}
```

---

### Paso 11 — Router

**`src/router/index.tsx`:**
```tsx
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import GymLandingPage from '@/ui/pages/GymLandingPage'
import LoginPage from '@/ui/pages/LoginPage'
import DashboardPage from '@/ui/pages/DashboardPage'
import HistorialPage from '@/ui/pages/HistorialPage'
import ErrorPage from '@/ui/pages/ErrorPage'
import ClienteGuard from '@/ui/guards/ClienteGuard'

const router = createBrowserRouter([
  { path: '/gym/:qrToken', element: <GymLandingPage /> },
  { path: '/gym/:qrToken/login', element: <LoginPage /> },
  {
    path: '/gym/:qrToken',
    element: <ClienteGuard />,
    children: [
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'historial', element: <HistorialPage /> },
    ],
  },
  { path: '*', element: <ErrorPage /> },
])

export default function Router() {
  return <RouterProvider router={router} />
}
```

---

## Comportamiento offline

| Situación | Comportamiento |
|---|---|
| Sin internet al abrir QR | Muestra última membresía en caché (estado como cuando se cargó) |
| Sin internet al pulsar check-in | Muestra error amigable: "Sin conexión — intenta cuando tengas señal" |
| Reconexión | La PWA actualiza automáticamente el caché via `NetworkFirst` |

El service worker cachea:
- Shell de la app (JS/CSS/HTML)
- Respuesta de `/gimnasio/by-qr/{token}` (5 min TTL)
- Respuesta de la membresía activa (5 min TTL)

---

## Variables de entorno del nuevo repo

```bash
# .env.local
VITE_AUTH_API_URL=https://api.tudominio.com/api/v1
VITE_CORE_API_URL=https://core.tudominio.com/api/v1
VITE_ATTENDANCE_API_URL=https://attendance.tudominio.com/api/v1
VITE_GOOGLE_CLIENT_ID=<google-client-id>.apps.googleusercontent.com
VITE_FACEBOOK_APP_ID=<facebook-app-id>
```

---

## Checklist de implementación

### Backend (IMPL-12) — debe estar listo antes de comenzar

- [ ] `logo_url` en tabla `companias`
- [ ] `GET /auth/gimnasio/by-qr/{qrToken}` funcional
- [ ] JWT cliente incluye `nombre_compania` + `logo_url`
- [ ] `POST /asistencias/qr` acepta JWT tipo `cliente`
- [ ] `POST /auth/app/oauth/google` funcional
- [ ] `POST /auth/app/oauth/facebook` funcional

### PWA — orden de implementación

- [ ] 1. Setup Vite + PWA plugin + Tailwind + Zustand + React Router
- [ ] 2. Axios instances + auth store + gimnasio store
- [ ] 3. `GymLandingPage` — resolver QR + auto-logout multi-gimnasio
- [ ] 4. `LoginPage` — formulario manual
- [ ] 5. `ClienteGuard` + router
- [ ] 6. `DashboardPage` — tarjeta membresía + check-in button
- [ ] 7. `HistorialPage` — lista de asistencias
- [ ] 8. `GoogleLoginButton` + `FacebookLoginButton`
- [ ] 9. Modo offline (service worker config)
- [ ] 10. Flujo membresía congelada → reactivación
