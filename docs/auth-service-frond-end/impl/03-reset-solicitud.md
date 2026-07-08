# IMPL-03 — ResetRequestPage (Solicitar recuperación de contraseña)

> **Pantalla:** P-03 Reset — paso 1  
> **Complejidad:** ★★☆☆☆  
> **Prerequisito:** IMPL-01 completado (PublicLayout)  
> **Resultado:** Formulario para solicitar el enlace de recuperación por correo

---

## Archivos que se crean en este paso

```
src/features/auth/
├── schemas/
│   └── reset-request.schema.ts
└── pages/
    └── ResetRequestPage.tsx
```

También se actualiza `src/router/index.tsx`.

---

## Vista de la pantalla

```
┌─────────────────────────────────────────────┐
│  ← Volver al inicio de sesión               │
│                                             │
│  🔑 Recuperar contraseña                    │
│  Te enviaremos un enlace a tu correo        │
│  para que puedas restablecer tu clave.      │
│                                             │
│  Correo electrónico                         │
│  [tu@correo.com___________________________] │
│                                             │
│  ID del gimnasio (opcional)            (?)  │
│  [_____]                                    │
│                                             │
│  [ Enviar enlace de recuperación ]          │
│                                             │
│  ─────────────────────────────────────────  │
│  💡 Si no recibes el correo en 5 minutos,   │
│     revisa tu carpeta de spam.              │
└─────────────────────────────────────────────┘

  (estado éxito después del submit):
┌─────────────────────────────────────────────┐
│  ✅ ¡Correo enviado!                        │
│  Revisa tu bandeja de entrada en            │
│  tu@correo.com                              │
│                                             │
│  El enlace expira en 15 minutos.            │
│                                             │
│  [ Volver al inicio de sesión ]             │
└─────────────────────────────────────────────┘
```

---

## 1. Schema de validación

**`src/features/auth/schemas/reset-request.schema.ts`:**
```ts
import { z } from 'zod'

export const resetRequestSchema = z.object({
  correo: z
    .string()
    .min(1, 'El correo es requerido')
    .email('Ingresa un correo válido'),
  id_compania: z.coerce
    .number()
    .int()
    .positive()
    .optional()
    .or(z.literal('')),
})

export type ResetRequestForm = z.infer<typeof resetRequestSchema>
```

---

## 2. ResetRequestPage

**`src/features/auth/pages/ResetRequestPage.tsx`:**
```tsx
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Loader2, KeyRound, CheckCircle2, HelpCircle, AlertCircle, ArrowLeft } from 'lucide-react'
import { isAxiosError } from 'axios'
import { requestPasswordReset } from '@/api/auth.api'
import { resetRequestSchema, type ResetRequestForm } from '../schemas/reset-request.schema'

export function ResetRequestPage() {
  const [enviado, setEnviado] = useState(false)
  const [correoEnviado, setCorreoEnviado] = useState('')

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ResetRequestForm>({ resolver: zodResolver(resetRequestSchema) })

  const onSubmit = async (data: ResetRequestForm) => {
    try {
      const id_compania = data.id_compania === '' ? undefined : data.id_compania as number | undefined
      await requestPasswordReset({
        correo: data.correo,
        id_compania,
        tipo: 'staff',
      })
      setCorreoEnviado(data.correo)
      setEnviado(true)
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 404) {
          // Por seguridad, no revelar si el correo existe o no
          // Igual mostramos éxito
          setCorreoEnviado(data.correo)
          setEnviado(true)
        } else if (status === 429) {
          setError('root', {
            message: 'Demasiadas solicitudes. Espera unos minutos antes de intentarlo de nuevo.',
          })
        } else {
          toast.error('Error de conexión. Intenta de nuevo.')
        }
      }
    }
  }

  // Estado de éxito: correo enviado
  if (enviado) {
    return (
      <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6 text-center">
        <div className="flex items-center justify-center w-16 h-16 rounded-full bg-green-100 mx-auto">
          <CheckCircle2 size={32} className="text-green-600" />
        </div>
        <div>
          <h2 className="text-xl font-bold text-slate-900">¡Correo enviado!</h2>
          <p className="text-slate-500 text-sm mt-2 leading-relaxed">
            Revisa tu bandeja de entrada en{' '}
            <span className="font-medium text-slate-700">{correoEnviado}</span>.
            <br />
            El enlace de recuperación expira en <strong>15 minutos</strong>.
          </p>
        </div>
        <p className="text-xs text-slate-400 bg-slate-50 rounded-lg p-3">
          💡 ¿No ves el correo? Revisa tu carpeta de spam o correo no deseado.
        </p>
        <Link
          to="/login"
          className="block w-full text-center bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          Volver al inicio de sesión
        </Link>
      </div>
    )
  }

  // Formulario de solicitud
  return (
    <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6">
      {/* Volver */}
      <Link
        to="/login"
        className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 transition-colors -mb-2"
      >
        <ArrowLeft size={15} />
        Volver al inicio de sesión
      </Link>

      {/* Encabezado */}
      <div className="flex items-start gap-3">
        <div className="flex items-center justify-center w-11 h-11 rounded-xl bg-orange-100 flex-shrink-0 mt-0.5">
          <KeyRound size={22} className="text-orange-600" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-slate-900">Recuperar contraseña</h1>
          <p className="text-slate-500 text-sm mt-0.5 leading-snug">
            Te enviaremos un enlace para que puedas restablecer tu contraseña.
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

        {/* Campo: correo */}
        <div className="space-y-1.5">
          <label htmlFor="correo" className="block text-sm font-medium text-slate-700">
            Correo electrónico
          </label>
          <input
            id="correo"
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

        {/* Campo: ID del gimnasio (opcional, ayuda a filtrar si hay varios gyms) */}
        <div className="space-y-1.5">
          <label htmlFor="id_compania" className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
            ID del gimnasio
            <span className="text-slate-400 font-normal">(opcional)</span>
            <span
              title="Si perteneces a más de un gimnasio, ingresa el ID para identificar tu cuenta correctamente."
              className="text-slate-400 cursor-help"
            >
              <HelpCircle size={14} />
            </span>
          </label>
          <input
            id="id_compania"
            type="number"
            placeholder="Ej: 1"
            min={1}
            {...register('id_compania')}
            className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
          />
        </div>

        {/* Botón */}
        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 active:bg-orange-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm mt-2"
        >
          {isSubmitting && <Loader2 size={16} className="animate-spin" />}
          {isSubmitting ? 'Enviando...' : 'Enviar enlace de recuperación'}
        </button>
      </form>

      {/* Nota de spam */}
      <p className="text-xs text-slate-400 text-center">
        💡 Si no recibes el correo en 5 minutos, revisa tu carpeta de spam.
      </p>
    </div>
  )
}
```

---

## 3. Actualizar el router

**`src/router/index.tsx`** — agregar dentro del bloque `PublicLayout`:
```tsx
import { ResetRequestPage } from '@/features/auth/pages/ResetRequestPage'

// Dentro del children de PublicLayout:
{ path: '/reset-password', element: <ResetRequestPage /> },
```

---

## Cómo probar

1. Ir a `localhost:5173/reset-password` o hacer clic en "¿Olvidaste tu contraseña?" desde LoginPage
2. El link "← Volver al inicio de sesión" debe funcionar
3. Enviar formulario vacío → debe mostrar error de validación en el campo correo
4. Con correo inválido → mensaje de validación
5. Con correo válido + submit → debe aparecer el estado de éxito con la pantalla verde (`✅ ¡Correo enviado!`)
6. El botón "Volver al inicio de sesión" en el estado éxito → regresa a `/login`

**Siguiente paso:** [IMPL-04 — Reset Confirmar](./IMPL_04_RESET_CONFIRMAR.md)
