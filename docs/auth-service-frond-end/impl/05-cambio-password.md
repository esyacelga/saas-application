# IMPL-05 — ChangePasswordPage (Cambio de contraseña obligatorio)

> **Pantalla:** P-05 Cambio de contraseña  
> **Complejidad:** ★★☆☆☆  
> **Prerequisito:** IMPL-04 completado (reutiliza `PasswordStrength`)  
> **Resultado:** Pantalla que aparece automáticamente tras el primer login; el usuario no puede navegar a otra parte hasta completarla

---

## Diferencias con ResetConfirmPage (P-04)

| Aspecto | ResetConfirmPage | ChangePasswordPage |
|---|---|---|
| Cuándo aparece | Desde enlace en email | Tras login con `requiere_cambio_pwd: true` |
| Autenticación | Sin JWT (token de reset) | Con JWT de sesión activo |
| Campos | nueva + confirmar | **actual** + nueva + confirmar |
| Endpoint | `POST /auth/password/reset` | `POST /auth/password/change` |
| Guarda sesión | No (redirige a login) | Sí (redirige a dashboard) |

---

## Archivos que se crean en este paso

```
src/features/auth/
├── schemas/
│   └── change-password.schema.ts
└── pages/
    └── ChangePasswordPage.tsx
```

También se actualiza `src/router/index.tsx`.

---

## Vista de la pantalla

```
┌───────────────────────────────────────────────┐
│  🔒 Antes de continuar...                     │
│                                               │
│  Por seguridad, necesitas crear una           │
│  contraseña nueva. Esto solo ocurre           │
│  la primera vez que ingresas.                 │
│                                               │
│  Contraseña actual                            │
│  [••••••••  👁]                              │
│                                               │
│  Nueva contraseña                             │
│  [••••••••  👁]                              │
│                                               │
│  ○ Mínimo 8 caracteres                        │
│  ○ Una letra mayúscula                        │
│  ○ Un número                                  │
│                                               │
│  Confirmar contraseña                         │
│  [••••••••  👁]                              │
│                                               │
│  [ Crear mi contraseña y entrar → ]           │
└───────────────────────────────────────────────┘
```

---

## 1. Schema de validación

**`src/features/auth/schemas/change-password.schema.ts`:**
```ts
import { z } from 'zod'

export const changePasswordSchema = z
  .object({
    password_actual: z
      .string()
      .min(1, 'Ingresa tu contraseña actual'),
    nueva_password: z
      .string()
      .min(8, 'La contraseña debe tener al menos 8 caracteres')
      .regex(/[A-Z]/, 'Debe incluir al menos una letra mayúscula')
      .regex(/[0-9]/, 'Debe incluir al menos un número'),
    confirmar_password: z
      .string()
      .min(1, 'Confirma tu nueva contraseña'),
  })
  .refine(
    (data) => data.nueva_password === data.confirmar_password,
    {
      message: 'Las contraseñas no coinciden',
      path: ['confirmar_password'],
    }
  )
  .refine(
    (data) => data.password_actual !== data.nueva_password,
    {
      message: 'La nueva contraseña debe ser diferente a la actual',
      path: ['nueva_password'],
    }
  )

export type ChangePasswordForm = z.infer<typeof changePasswordSchema>
```

---

## 2. ChangePasswordPage

**`src/features/auth/pages/ChangePasswordPage.tsx`:**
```tsx
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Navigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Eye, EyeOff, Loader2, ShieldCheck, AlertCircle } from 'lucide-react'
import { isAxiosError } from 'axios'
import { changePassword } from '@/api/auth.api'
import { useIsAuthenticated, useAuthStore } from '../store/auth.store'
import { changePasswordSchema, type ChangePasswordForm } from '../schemas/change-password.schema'
import { PasswordStrength } from '../components/PasswordStrength'

// Toggle de visibilidad reutilizable dentro de esta pantalla
function PasswordInput({
  id,
  label,
  autoComplete,
  registration,
  error,
}: {
  id: string
  label: string
  autoComplete: string
  registration: ReturnType<ReturnType<typeof useForm>['register']>
  error?: string
}) {
  const [show, setShow] = useState(false)
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          type={show ? 'text' : 'password'}
          autoComplete={autoComplete}
          placeholder="••••••••"
          {...registration}
          className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition pr-10"
        />
        <button
          type="button"
          onClick={() => setShow(v => !v)}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
          tabIndex={-1}
        >
          {show ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}

export function ChangePasswordPage() {
  const navigate = useNavigate()
  const isAuthenticated = useIsAuthenticated()
  const { logout } = useAuthStore()

  // Solo usuarios autenticados llegan aquí
  if (!isAuthenticated) return <Navigate to="/login" replace />

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ChangePasswordForm>({ resolver: zodResolver(changePasswordSchema) })

  const nuevaPassword = watch('nueva_password', '')

  const onSubmit = async (data: ChangePasswordForm) => {
    try {
      await changePassword({
        password_actual: data.password_actual,
        nueva_password: data.nueva_password,
      })
      toast.success('¡Contraseña actualizada! Bienvenido al sistema.')
      navigate('/admin/dashboard', { replace: true })
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 401) {
          setError('password_actual', {
            message: 'La contraseña actual es incorrecta.',
          })
        } else if (status === 422) {
          setError('nueva_password', {
            message: 'La contraseña no cumple los requisitos de seguridad.',
          })
        } else {
          toast.error('Error de conexión. Intenta de nuevo.')
        }
      }
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6">
        {/* Encabezado */}
        <div className="flex items-start gap-3">
          <div className="flex items-center justify-center w-11 h-11 rounded-xl bg-orange-100 flex-shrink-0 mt-0.5">
            <ShieldCheck size={22} className="text-orange-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-900">Antes de continuar...</h1>
            <p className="text-slate-500 text-sm mt-0.5 leading-snug">
              Por seguridad, crea una contraseña nueva.
              Solo ocurre en tu primer inicio de sesión.
            </p>
          </div>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {/* Error general */}
          {errors.root && (
            <div className="flex items-start gap-3 bg-red-50 border border-red-200 text-red-700 text-sm p-3 rounded-lg">
              <AlertCircle size={16} className="text-red-500 mt-0.5 flex-shrink-0" />
              <span>{errors.root.message}</span>
            </div>
          )}

          {/* Contraseña actual */}
          <PasswordInput
            id="password_actual"
            label="Contraseña actual"
            autoComplete="current-password"
            registration={register('password_actual')}
            error={errors.password_actual?.message}
          />

          {/* Separador visual */}
          <div className="border-t border-slate-100 pt-2" />

          {/* Nueva contraseña */}
          <div className="space-y-1.5">
            <label htmlFor="nueva_password" className="block text-sm font-medium text-slate-700">
              Nueva contraseña
            </label>
            <div className="relative">
              <input
                id="nueva_password"
                type="password"
                autoComplete="new-password"
                placeholder="••••••••"
                {...register('nueva_password')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
              />
            </div>
            {errors.nueva_password && (
              <p className="text-xs text-red-600">{errors.nueva_password.message}</p>
            )}
            <PasswordStrength password={nuevaPassword} />
          </div>

          {/* Confirmar contraseña */}
          <PasswordInput
            id="confirmar_password"
            label="Confirmar nueva contraseña"
            autoComplete="new-password"
            registration={register('confirmar_password')}
            error={errors.confirmar_password?.message}
          />

          {/* Botón */}
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 active:bg-orange-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm mt-2"
          >
            {isSubmitting && <Loader2 size={16} className="animate-spin" />}
            {isSubmitting ? 'Guardando...' : 'Crear mi contraseña y entrar'}
          </button>
        </form>

        {/* Opción de cerrar sesión si el usuario no quiere continuar */}
        <div className="text-center">
          <button
            onClick={() => {
              logout()
              navigate('/login', { replace: true })
            }}
            className="text-xs text-slate-400 hover:text-slate-600 transition-colors"
          >
            Cancelar y cerrar sesión
          </button>
        </div>
      </div>
    </div>
  )
}
```

---

## 3. Actualizar el router

**`src/router/index.tsx`** — agregar fuera del `PublicLayout` pero dentro del `AuthGuard`:
```tsx
import { ChangePasswordPage } from '@/features/auth/pages/ChangePasswordPage'

// Esta ruta requiere JWT (AuthGuard) pero NO el AdminLayout
// ya que el usuario debe completar el cambio antes de entrar al panel
{
  element: <AuthGuard />,
  children: [
    { path: '/change-password', element: <ChangePasswordPage /> },
    // Aquí irá el AdminLayout en IMPL-06
  ],
},
```

---

## Cómo probar

1. Para simular el flujo real: hacer login con usuario cuyo `requiere_cambio_pwd = true` → el LoginPage redirige automáticamente a `/change-password`
2. Para probar directamente: ir a `localhost:5173/change-password` (se necesita tener sesión activa, si no redirige a `/login`)
3. Ingresar contraseña actual incorrecta → error en el campo "Contraseña actual"
4. Ingresar nueva contraseña igual a la actual → error "debe ser diferente"
5. Las contraseñas no coinciden → error en confirmar
6. Flujo completo válido → toast "¡Contraseña actualizada!" + redirige a `/admin/dashboard`
7. "Cancelar y cerrar sesión" → cierra la sesión y va a `/login`

**Siguiente paso:** [IMPL-06 — AdminLayout](./06-admin-layout.md)
