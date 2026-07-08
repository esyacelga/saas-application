# IMPL-02 — PlatformLoginPage (Operadores SaaS)

> **Pantalla:** P-02 Login Plataforma  
> **Complejidad:** ★★☆☆☆  
> **Prerequisito:** IMPL-01 completado  
> **Resultado:** Pantalla de login oscura para operadores de plataforma; redirige a `/platform/dashboard`

---

## Diferencias con LoginPage (P-01)

| Aspecto | LoginPage (staff) | PlatformLoginPage |
|---|---|---|
| Audiencia | Recepcionistas, instructores | Operadores técnicos SaaS |
| Campos | correo + password + **id_compania** | correo + password (sin id_compania) |
| Visual | Split claro/oscuro | Todo oscuro (`bg-gym-950`) |
| Endpoint | `POST /auth/login` | `POST /auth/platform/login` |
| Redirección | `/admin/dashboard` | `/platform/dashboard` |

---

## Archivos que se crean en este paso

```
src/features/auth/
├── schemas/
│   └── platform-login.schema.ts
└── pages/
    └── PlatformLoginPage.tsx
```

También se actualiza `src/router/index.tsx`.

---

## Vista de la pantalla

```
┌─────────────────────────────────────────┐
│  (fondo bg-gym-950, pantalla completa)  │
│                                         │
│     🏋  Gym Admin                       │
│         Panel de Plataforma             │
│                                         │
│  ┌──────────────────────────────────┐   │
│  │  Acceso Plataforma               │   │
│  │  Solo para operadores autorizados│   │
│  │                                  │   │
│  │  [correo___________________]     │   │
│  │  [contraseña_______  👁]        │   │
│  │                                  │   │
│  │  [ Iniciar sesión → ]            │   │
│  └──────────────────────────────────┘   │
│                                         │
│  ← Volver al login del gimnasio         │
└─────────────────────────────────────────┘
```

---

## 1. Schema de validación

**`src/features/auth/schemas/platform-login.schema.ts`:**
```ts
import { z } from 'zod'

export const platformLoginSchema = z.object({
  correo: z
    .string()
    .min(1, 'El correo es requerido')
    .email('Ingresa un correo válido'),
  password: z
    .string()
    .min(1, 'La contraseña es requerida'),
})

export type PlatformLoginForm = z.infer<typeof platformLoginSchema>
```

---

## 2. PlatformLoginPage

**`src/features/auth/pages/PlatformLoginPage.tsx`:**
```tsx
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Navigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Eye, EyeOff, Loader2, AlertCircle, Dumbbell } from 'lucide-react'
import { isAxiosError } from 'axios'
import { loginPlatform } from '@/api/auth.api'
import { useAuthStore, useIsPlatformUser } from '../store/auth.store'
import { platformLoginSchema, type PlatformLoginForm } from '../schemas/platform-login.schema'

export function PlatformLoginPage() {
  const navigate = useNavigate()
  const { setSession } = useAuthStore()
  const isPlatformUser = useIsPlatformUser()
  const [showPassword, setShowPassword] = useState(false)

  // Si ya tiene sesión de plataforma activa, redirigir
  if (isPlatformUser) return <Navigate to="/platform/dashboard" replace />

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<PlatformLoginForm>({ resolver: zodResolver(platformLoginSchema) })

  const onSubmit = async (data: PlatformLoginForm) => {
    try {
      const response = await loginPlatform(data)
      setSession(response.access_token)
      navigate('/platform/dashboard', { replace: true })
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 401) {
          setError('root', { message: 'Correo o contraseña incorrectos.' })
        } else if (status === 403) {
          setError('root', { message: 'Tu cuenta de operador está desactivada.' })
        } else if (status === 429) {
          setError('root', {
            message: 'Demasiados intentos fallidos. Espera unos minutos.',
          })
        } else {
          toast.error('Error de conexión. Intenta de nuevo.')
        }
      }
    }
  }

  return (
    <div className="min-h-screen bg-gym-950 flex flex-col items-center justify-center p-6">
      {/* Header con logo */}
      <div className="flex items-center gap-3 mb-8">
        <div className="w-11 h-11 rounded-xl bg-orange-500 flex items-center justify-center shadow-lg shadow-orange-500/30">
          <Dumbbell size={24} className="text-white" />
        </div>
        <div>
          <p className="text-white font-bold text-base leading-tight">Gym Admin</p>
          <p className="text-slate-500 text-xs leading-tight">Panel de Plataforma</p>
        </div>
      </div>

      {/* Card del formulario */}
      <div className="w-full max-w-md bg-gym-900 border border-gym-800 rounded-2xl p-8 space-y-6 shadow-2xl">
        {/* Título */}
        <div>
          <h1 className="text-xl font-bold text-white">Acceso Plataforma</h1>
          <p className="text-slate-500 text-sm mt-1">
            Solo para operadores autorizados
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {/* Error general */}
          {errors.root && (
            <div className="flex items-start gap-3 bg-red-500/10 border border-red-500/30 text-red-400 text-sm p-3 rounded-lg">
              <AlertCircle size={16} className="mt-0.5 flex-shrink-0" />
              <span>{errors.root.message}</span>
            </div>
          )}

          {/* Campo: correo */}
          <div className="space-y-1.5">
            <label htmlFor="correo" className="block text-sm font-medium text-slate-300">
              Correo electrónico
            </label>
            <input
              id="correo"
              type="email"
              autoComplete="email"
              placeholder="operador@empresa.com"
              {...register('correo')}
              className="w-full bg-gym-800 border border-gym-700 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
            />
            {errors.correo && (
              <p className="text-xs text-red-400">{errors.correo.message}</p>
            )}
          </div>

          {/* Campo: contraseña */}
          <div className="space-y-1.5">
            <label htmlFor="password" className="block text-sm font-medium text-slate-300">
              Contraseña
            </label>
            <div className="relative">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="••••••••"
                {...register('password')}
                className="w-full bg-gym-800 border border-gym-700 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition pr-10"
              />
              <button
                type="button"
                onClick={() => setShowPassword(v => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors"
                tabIndex={-1}
                aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {errors.password && (
              <p className="text-xs text-red-400">{errors.password.message}</p>
            )}
          </div>

          {/* Botón */}
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 active:bg-orange-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm mt-2"
          >
            {isSubmitting && <Loader2 size={16} className="animate-spin" />}
            {isSubmitting ? 'Iniciando sesión...' : 'Iniciar sesión'}
          </button>
        </form>
      </div>

      {/* Link de regreso */}
      <Link
        to="/login"
        className="mt-6 text-sm text-slate-500 hover:text-slate-300 transition-colors"
      >
        ← Volver al login del gimnasio
      </Link>
    </div>
  )
}
```

---

## 3. Actualizar el router

**`src/router/index.tsx`** — agregar la ruta dentro del bloque `PublicLayout`:
```tsx
import { PlatformLoginPage } from '@/features/auth/pages/PlatformLoginPage'

// Dentro del children de PublicLayout:
{ path: '/platform/login', element: <PlatformLoginPage /> },
```

> **Nota:** La ruta `/platform/login` usa `PublicLayout` pero la página renderiza su propio fondo oscuro de pantalla completa. El `PublicLayout` nunca se ve porque `PlatformLoginPage` ocupa el viewport completo con `min-h-screen`.
>
> Alternativa limpia: crear un `PlatformPublicLayout` sin wrapper, pero para este sprint el enfoque actual es suficiente.

---

## Cómo probar

1. Ir a `localhost:5173/platform/login`
2. Debe mostrar pantalla completamente oscura con el formulario centrado
3. Ingresar credenciales inválidas → error en rojo con fondo semitransparente
4. El link "← Volver al login del gimnasio" → debe navegar a `/login`
5. Con credenciales válidas de operador → redirige a `/platform/dashboard` (404 hasta IMPL-11)

**Siguiente paso:** [IMPL-03 — Reset Password Solicitud](./IMPL_03_RESET_SOLICITUD.md)
