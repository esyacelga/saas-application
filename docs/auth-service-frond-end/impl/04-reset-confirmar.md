# IMPL-04 — ResetConfirmPage (Confirmar nueva contraseña)

> **Pantalla:** P-04 Reset — paso 2  
> **Complejidad:** ★★☆☆☆  
> **Prerequisito:** IMPL-03 completado  
> **Resultado:** Formulario para ingresar la nueva contraseña usando el token del correo; incluye indicador de fortaleza reactivo

---

## Flujo

```
Email con enlace → /reset-password/confirm?token=ABC123
  ├── Token válido  → muestra formulario de nueva contraseña
  ├── Token inválido → muestra error "enlace caducado"
  └── Submit OK     → redirige a /login con toast de éxito
```

---

## Archivos que se crean en este paso

```
src/features/auth/
├── schemas/
│   └── reset-confirm.schema.ts
├── components/
│   └── PasswordStrength.tsx     ← indicador reactivo de fortaleza
└── pages/
    └── ResetConfirmPage.tsx
```

También se actualiza `src/router/index.tsx`.

---

## Vista de la pantalla

```
┌─────────────────────────────────────────────┐
│  🔒 Crear nueva contraseña                  │
│                                             │
│  Nueva contraseña                           │
│  [••••••••••••  👁]                         │
│                                             │
│  Confirmar contraseña                       │
│  [••••••••••••  👁]                         │
│                                             │
│  Requisitos:                                │
│  ✓ Mínimo 8 caracteres        (verde)       │
│  ✓ Una letra mayúscula        (verde)       │
│  ○ Un número                  (gris)        │
│                                             │
│  [ Cambiar mi contraseña ]                  │
└─────────────────────────────────────────────┘
```

---

## 1. Schema de validación

**`src/features/auth/schemas/reset-confirm.schema.ts`:**
```ts
import { z } from 'zod'

export const resetConfirmSchema = z
  .object({
    nueva_password: z
      .string()
      .min(8, 'La contraseña debe tener al menos 8 caracteres')
      .regex(/[A-Z]/, 'Debe incluir al menos una letra mayúscula')
      .regex(/[0-9]/, 'Debe incluir al menos un número'),
    confirmar_password: z
      .string()
      .min(1, 'Confirma tu contraseña'),
  })
  .refine(
    (data) => data.nueva_password === data.confirmar_password,
    {
      message: 'Las contraseñas no coinciden',
      path: ['confirmar_password'],
    }
  )

export type ResetConfirmForm = z.infer<typeof resetConfirmSchema>
```

---

## 2. Componente PasswordStrength

Muestra los requisitos de la contraseña actualizándose en tiempo real mientras el usuario escribe.

**`src/features/auth/components/PasswordStrength.tsx`:**
```tsx
import { Check, Circle } from 'lucide-react'

interface Props {
  password: string
}

const checks = [
  { label: 'Mínimo 8 caracteres', test: (p: string) => p.length >= 8 },
  { label: 'Una letra mayúscula', test: (p: string) => /[A-Z]/.test(p) },
  { label: 'Un número',           test: (p: string) => /[0-9]/.test(p) },
]

export function PasswordStrength({ password }: Props) {
  if (!password) return null

  return (
    <ul className="space-y-1.5 mt-2">
      {checks.map(({ label, test }) => {
        const ok = test(password)
        return (
          <li
            key={label}
            className={`flex items-center gap-2 text-xs transition-colors ${
              ok ? 'text-green-600' : 'text-slate-400'
            }`}
          >
            {ok
              ? <Check size={13} className="flex-shrink-0" />
              : <Circle size={13} className="flex-shrink-0" />
            }
            {label}
          </li>
        )
      })}
    </ul>
  )
}
```

---

## 3. ResetConfirmPage

**`src/features/auth/pages/ResetConfirmPage.tsx`:**
```tsx
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Eye, EyeOff, Loader2, Lock, AlertCircle, CheckCircle2 } from 'lucide-react'
import { isAxiosError } from 'axios'
import { confirmPasswordReset } from '@/api/auth.api'
import { resetConfirmSchema, type ResetConfirmForm } from '../schemas/reset-confirm.schema'
import { PasswordStrength } from '../components/PasswordStrength'

export function ResetConfirmPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ResetConfirmForm>({ resolver: zodResolver(resetConfirmSchema) })

  const passwordValue = watch('nueva_password', '')

  // Token no presente en la URL
  if (!token) {
    return (
      <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6 text-center">
        <div className="flex items-center justify-center w-16 h-16 rounded-full bg-red-100 mx-auto">
          <AlertCircle size={32} className="text-red-500" />
        </div>
        <div>
          <h2 className="text-xl font-bold text-slate-900">Enlace no válido</h2>
          <p className="text-slate-500 text-sm mt-2">
            Este enlace de recuperación no es válido o ya expiró.
            Solicita uno nuevo desde la pantalla de inicio de sesión.
          </p>
        </div>
        <Link
          to="/reset-password"
          className="block w-full text-center bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          Solicitar nuevo enlace
        </Link>
      </div>
    )
  }

  const onSubmit = async (data: ResetConfirmForm) => {
    try {
      await confirmPasswordReset({ token, nueva_password: data.nueva_password })
      toast.success('¡Contraseña cambiada! Ingresa con tu nueva contraseña.')
      navigate('/login', { replace: true })
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 400 || status === 404) {
          setError('root', {
            message: 'El enlace de recuperación expiró o ya fue usado. Solicita uno nuevo.',
          })
        } else {
          toast.error('Error de conexión. Intenta de nuevo.')
        }
      }
    }
  }

  return (
    <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6">
      {/* Encabezado */}
      <div className="flex items-start gap-3">
        <div className="flex items-center justify-center w-11 h-11 rounded-xl bg-orange-100 flex-shrink-0 mt-0.5">
          <Lock size={22} className="text-orange-600" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-slate-900">Crear nueva contraseña</h1>
          <p className="text-slate-500 text-sm mt-0.5">
            Elige una contraseña segura para tu cuenta.
          </p>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {/* Error general (token expirado) */}
        {errors.root && (
          <div className="flex items-start gap-3 bg-red-50 border border-red-200 text-red-700 text-sm p-3 rounded-lg">
            <AlertCircle size={16} className="text-red-500 mt-0.5 flex-shrink-0" />
            <div className="space-y-1">
              <span>{errors.root.message}</span>
              <Link
                to="/reset-password"
                className="block text-red-600 underline text-xs"
              >
                Solicitar nuevo enlace
              </Link>
            </div>
          </div>
        )}

        {/* Campo: nueva contraseña */}
        <div className="space-y-1.5">
          <label htmlFor="nueva_password" className="block text-sm font-medium text-slate-700">
            Nueva contraseña
          </label>
          <div className="relative">
            <input
              id="nueva_password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="••••••••"
              {...register('nueva_password')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition pr-10"
            />
            <button
              type="button"
              onClick={() => setShowPassword(v => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
              tabIndex={-1}
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {errors.nueva_password && (
            <p className="text-xs text-red-600">{errors.nueva_password.message}</p>
          )}
          {/* Indicador reactivo de fortaleza */}
          <PasswordStrength password={passwordValue} />
        </div>

        {/* Campo: confirmar contraseña */}
        <div className="space-y-1.5">
          <label htmlFor="confirmar_password" className="block text-sm font-medium text-slate-700">
            Confirmar contraseña
          </label>
          <div className="relative">
            <input
              id="confirmar_password"
              type={showConfirm ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="••••••••"
              {...register('confirmar_password')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition pr-10"
            />
            <button
              type="button"
              onClick={() => setShowConfirm(v => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
              tabIndex={-1}
            >
              {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {errors.confirmar_password && (
            <p className="text-xs text-red-600">{errors.confirmar_password.message}</p>
          )}
        </div>

        {/* Botón */}
        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 active:bg-orange-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm mt-2"
        >
          {isSubmitting && <Loader2 size={16} className="animate-spin" />}
          {isSubmitting ? 'Guardando...' : 'Cambiar mi contraseña'}
        </button>
      </form>
    </div>
  )
}
```

---

## 4. Actualizar el router

**`src/router/index.tsx`** — agregar dentro del bloque `PublicLayout`:
```tsx
import { ResetConfirmPage } from '@/features/auth/pages/ResetConfirmPage'

// Dentro del children de PublicLayout:
{ path: '/reset-password/confirm', element: <ResetConfirmPage /> },
```

---

## Cómo probar

1. Ir a `localhost:5173/reset-password/confirm` (sin token) → debe mostrar "Enlace no válido" con botón para solicitar uno nuevo
2. Ir a `localhost:5173/reset-password/confirm?token=test123`
3. Escribir en el campo "Nueva contraseña" → los requisitos deben activarse en tiempo real:
   - Escribe `abc` → solo "Mínimo 8 caracteres" en gris
   - Escribe `Abcdefg1` → los tres requisitos en verde
4. Enviar con contraseñas que no coinciden → error "Las contraseñas no coinciden" en confirmar
5. Enviar con token inválido + contraseña válida → error con link para solicitar nuevo enlace
6. Con token válido + contraseña válida → toast de éxito + redirige a `/login`

**Siguiente paso:** [IMPL-05 — Cambio de Contraseña](./05-cambio-password.md)
