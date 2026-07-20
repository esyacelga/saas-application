# IMPL-09 — ClientesAppPage (Cuentas de la app móvil)

> **ESTADO:** 📜 Histórico — paso de implementación ya completado. NO describe el estado actual del código; es el registro de cómo se construyó este módulo. Ver [../../STATUS.md](../../../STATUS.md).

> **Pantalla:** P-08 Clientes App  
> **Complejidad:** ★★★★☆  
> **Prerequisito:** IMPL-07 completado  
> **Resultado:** Flujo de 3 pasos para registrar la cuenta app de un cliente: buscar por CI → ver/crear persona → crear cuenta app

---

## Flujo de trabajo

El flujo es secuencial, no paralelo. Cada paso depende del anterior:

```
Paso 1: Ingresar CI
  ├── CI existe en BD → muestra datos de la persona (solo ver)
  │     └── ¿Ya tiene cuenta app?
  │           ├── Sí → mostrar estado de la cuenta (activo/inactivo)
  │           └── No → Paso 3: Crear cuenta app
  └── CI no existe → Paso 2: Crear persona nueva
        └── Persona creada → Paso 3: Crear cuenta app
```

---

## Archivos que se crean en este paso

```
src/features/auth/
├── schemas/
│   └── persona.schema.ts
├── components/
│   ├── BuscarPersonaStep.tsx      ← paso 1: input de CI
│   ├── CrearPersonaStep.tsx       ← paso 2: formulario persona nueva
│   └── CrearAppUsuarioStep.tsx    ← paso 3: crear cuenta app
└── pages/
    └── ClientesAppPage.tsx        ← orquesta los 3 pasos
```

También se actualiza `src/router/index.tsx`.

---

## Vista de la pantalla

```
┌── PageHeader ────────────────────────────────────────────────┐
│ Cuentas App                                                   │
│ Registra o gestiona las cuentas de clientes en la app móvil  │
└──────────────────────────────────────────────────────────────┘

PASO 1 — Buscar cliente por CI:
┌────────────────────────────────────┐
│  Buscar cliente por cédula (CI)    │
│                                    │
│  [0912345678_______] [Buscar →]    │
└────────────────────────────────────┘

PASO 1b — CI encontrado (persona existe):
┌──────────────────────────────────────────┐
│  ✓ Cliente encontrado                    │
│                                          │
│  Nombre: Juan Pérez                      │
│  CI: 0912345678                          │
│  Correo: juan@mail.com                   │
│  Teléfono: 099 123 4567                  │
│                                          │
│  Estado cuenta app: ● Sin cuenta         │
│                                          │
│  [ Crear cuenta app para este cliente ]  │
└──────────────────────────────────────────┘

PASO 2 — CI no encontrado, crear persona:
┌────────────────────────────────────┐
│  ← Buscar otro CI                  │
│  Nuevo cliente                     │
│  CI: 0912345678 (precargado)       │
│                                    │
│  Nombre completo [______________]  │
│  Teléfono       [______________]   │
│  Correo         [______________]   │
│  Fecha nacim.   [______________]   │
│                                    │
│  [ Registrar cliente ]             │
└────────────────────────────────────┘

PASO 3 — Crear cuenta app:
┌────────────────────────────────────┐
│  ← Cambiar cliente                 │
│  Crear cuenta app                  │
│  Cliente: Juan Pérez               │
│                                    │
│  Usuario (login) [______________]  │
│  Contraseña      [______________]  │
│                                    │
│  [ Crear cuenta ]                  │
└────────────────────────────────────┘
```

---

## 1. Schemas

**`src/features/auth/schemas/persona.schema.ts`:**
```ts
import { z } from 'zod'

export const crearPersonaSchema = z.object({
  ci: z
    .string()
    .min(6, 'La cédula debe tener al menos 6 caracteres')
    .max(20, 'Cédula demasiado larga'),
  nombre: z
    .string()
    .min(2, 'El nombre debe tener al menos 2 caracteres')
    .max(150, 'Nombre demasiado largo'),
  telefono: z.string().max(20).optional().or(z.literal('')),
  correo: z.string().email('Correo no válido').optional().or(z.literal('')),
  fecha_nacimiento: z.string().optional().or(z.literal('')),
})

export type CrearPersonaFormData = z.infer<typeof crearPersonaSchema>

export const crearAppUsuarioSchema = z.object({
  login: z
    .string()
    .min(3, 'El usuario debe tener al menos 3 caracteres')
    .max(50, 'Usuario demasiado largo')
    .regex(/^[a-zA-Z0-9._-]+$/, 'Solo letras, números, puntos, guiones y _'),
  password: z
    .string()
    .min(6, 'La contraseña debe tener al menos 6 caracteres'),
})

export type CrearAppUsuarioFormData = z.infer<typeof crearAppUsuarioSchema>
```

---

## 2. Paso 1 — BuscarPersonaStep

**`src/features/auth/components/BuscarPersonaStep.tsx`:**
```tsx
import { useState } from 'react'
import { Loader2, Search, AlertCircle, CheckCircle2, UserPlus, Smartphone } from 'lucide-react'
import { isAxiosError } from 'axios'
import { buscarPersonaPorCI } from '@/api/auth.api'
import type { Persona } from '@/api/types/auth.types'

interface Props {
  onPersonaEncontrada: (persona: Persona) => void
  onPersonaNoExiste: (ci: string) => void
}

export function BuscarPersonaStep({ onPersonaEncontrada, onPersonaNoExiste }: Props) {
  const [ci, setCi] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [personaEncontrada, setPersonaEncontrada] = useState<Persona | null>(null)

  const buscar = async () => {
    const ciTrimmed = ci.trim()
    if (!ciTrimmed) {
      setError('Ingresa una cédula para buscar.')
      return
    }
    setError('')
    setLoading(true)
    setPersonaEncontrada(null)
    try {
      const persona = await buscarPersonaPorCI(ciTrimmed)
      setPersonaEncontrada(persona)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 404) {
        onPersonaNoExiste(ciTrimmed)
      } else {
        setError('Error al buscar. Verifica tu conexión e intenta de nuevo.')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') buscar()
  }

  return (
    <div className="max-w-lg">
      {/* Input de búsqueda */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Buscar cliente por cédula</h2>
          <p className="text-sm text-slate-500 mt-0.5">
            Ingresa el número de cédula (CI) del cliente para encontrar su registro.
          </p>
        </div>

        <div className="flex gap-2">
          <input
            type="text"
            value={ci}
            onChange={e => { setCi(e.target.value); setError('') }}
            onKeyDown={handleKeyDown}
            placeholder="Ej: 0912345678"
            className="flex-1 border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
            disabled={loading}
          />
          <button
            onClick={buscar}
            disabled={loading || !ci.trim()}
            className="flex items-center gap-2 px-4 py-2.5 bg-orange-500 hover:bg-orange-600 disabled:opacity-60 text-white font-medium text-sm rounded-lg transition-colors"
          >
            {loading ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
            Buscar
          </button>
        </div>

        {error && (
          <div className="flex items-center gap-2 text-sm text-red-600">
            <AlertCircle size={15} />
            {error}
          </div>
        )}
      </div>

      {/* Resultado: persona encontrada */}
      {personaEncontrada && (
        <div className="mt-4 bg-white rounded-2xl border border-green-200 p-6 space-y-4">
          <div className="flex items-center gap-2 text-green-700 font-semibold text-sm">
            <CheckCircle2 size={18} />
            Cliente encontrado
          </div>

          <div className="grid grid-cols-2 gap-3 text-sm">
            {[
              { label: 'Nombre', value: personaEncontrada.nombre },
              { label: 'CI', value: personaEncontrada.ci },
              { label: 'Correo', value: personaEncontrada.correo ?? '—' },
              { label: 'Teléfono', value: personaEncontrada.telefono ?? '—' },
            ].map(item => (
              <div key={item.label}>
                <p className="text-xs text-slate-400">{item.label}</p>
                <p className="font-medium text-slate-900">{item.value}</p>
              </div>
            ))}
          </div>

          <button
            onClick={() => onPersonaEncontrada(personaEncontrada)}
            className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
          >
            <Smartphone size={16} />
            Crear cuenta app para este cliente
          </button>
        </div>
      )}
    </div>
  )
}
```

---

## 3. Paso 2 — CrearPersonaStep

**`src/features/auth/components/CrearPersonaStep.tsx`:**
```tsx
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { Loader2, ArrowLeft } from 'lucide-react'
import { isAxiosError } from 'axios'
import { crearPersona } from '@/api/auth.api'
import type { Persona } from '@/api/types/auth.types'
import { crearPersonaSchema, type CrearPersonaFormData } from '../schemas/persona.schema'

interface Props {
  ci: string
  onPersonaCreada: (persona: Persona) => void
  onVolver: () => void
}

const inputClass =
  'w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition'

export function CrearPersonaStep({ ci, onPersonaCreada, onVolver }: Props) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CrearPersonaFormData>({
    resolver: zodResolver(crearPersonaSchema),
    defaultValues: { ci },
  })

  const onSubmit = async (data: CrearPersonaFormData) => {
    try {
      const persona = await crearPersona({
        ci: data.ci,
        nombre: data.nombre,
        telefono: data.telefono || undefined,
        correo: data.correo || undefined,
        fecha_nacimiento: data.fecha_nacimiento || undefined,
      })
      toast.success('Cliente registrado correctamente.')
      onPersonaCreada(persona)
    } catch (err) {
      if (isAxiosError(err)) {
        if (err.response?.status === 409) {
          setError('ci', { message: 'Esta cédula ya está registrada.' })
        } else {
          toast.error('Error al registrar el cliente. Intenta de nuevo.')
        }
      }
    }
  }

  return (
    <div className="max-w-lg bg-white rounded-2xl border border-slate-200 p-6 space-y-5">
      <div>
        <button
          onClick={onVolver}
          className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 transition-colors mb-3"
        >
          <ArrowLeft size={15} />
          Buscar otro CI
        </button>
        <h2 className="text-lg font-semibold text-slate-900">Nuevo cliente</h2>
        <p className="text-sm text-slate-500 mt-0.5">
          La cédula <strong>{ci}</strong> no está registrada. Completa los datos del cliente.
        </p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {/* CI (solo lectura, precargado) */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">Cédula (CI)</label>
          <input
            type="text"
            {...register('ci')}
            readOnly
            className={`${inputClass} bg-slate-50 text-slate-500 cursor-not-allowed`}
          />
          {errors.ci && <p className="text-xs text-red-600">{errors.ci.message}</p>}
        </div>

        {/* Nombre */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            Nombre completo <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            autoFocus
            placeholder="Juan Pérez López"
            {...register('nombre')}
            className={inputClass}
          />
          {errors.nombre && <p className="text-xs text-red-600">{errors.nombre.message}</p>}
        </div>

        {/* Teléfono */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            Teléfono <span className="text-slate-400 font-normal">(opcional)</span>
          </label>
          <input
            type="tel"
            placeholder="099 123 4567"
            {...register('telefono')}
            className={inputClass}
          />
        </div>

        {/* Correo */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            Correo electrónico <span className="text-slate-400 font-normal">(opcional)</span>
          </label>
          <input
            type="email"
            placeholder="cliente@correo.com"
            {...register('correo')}
            className={inputClass}
          />
          {errors.correo && <p className="text-xs text-red-600">{errors.correo.message}</p>}
        </div>

        {/* Fecha de nacimiento */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            Fecha de nacimiento <span className="text-slate-400 font-normal">(opcional)</span>
          </label>
          <input
            type="date"
            {...register('fecha_nacimiento')}
            className={inputClass}
          />
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          {isSubmitting && <Loader2 size={16} className="animate-spin" />}
          {isSubmitting ? 'Registrando...' : 'Registrar cliente'}
        </button>
      </form>
    </div>
  )
}
```

---

## 4. Paso 3 — CrearAppUsuarioStep

**`src/features/auth/components/CrearAppUsuarioStep.tsx`:**
```tsx
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { Loader2, ArrowLeft, Eye, EyeOff, CheckCircle2 } from 'lucide-react'
import { isAxiosError } from 'axios'
import { crearUsuarioApp } from '@/api/auth.api'
import type { Persona } from '@/api/types/auth.types'
import { crearAppUsuarioSchema, type CrearAppUsuarioFormData } from '../schemas/persona.schema'

interface Props {
  persona: Persona
  onCreado: () => void
  onVolver: () => void
}

const inputClass =
  'w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition'

export function CrearAppUsuarioStep({ persona, onCreado, onVolver }: Props) {
  const [showPassword, setShowPassword] = useState(false)
  const [exito, setExito] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CrearAppUsuarioFormData>({
    resolver: zodResolver(crearAppUsuarioSchema),
    defaultValues: {
      login: persona.correo?.split('@')[0] ?? '',
    },
  })

  const onSubmit = async (data: CrearAppUsuarioFormData) => {
    try {
      await crearUsuarioApp({ id_persona: persona.id, ...data })
      toast.success('Cuenta app creada. El cliente ya puede ingresar.')
      setExito(true)
    } catch (err) {
      if (isAxiosError(err)) {
        if (err.response?.status === 409) {
          setError('login', { message: 'Este nombre de usuario ya está en uso.' })
        } else {
          toast.error('Error al crear la cuenta. Intenta de nuevo.')
        }
      }
    }
  }

  // Estado de éxito
  if (exito) {
    return (
      <div className="max-w-lg bg-white rounded-2xl border border-green-200 p-8 text-center space-y-4">
        <div className="flex items-center justify-center w-16 h-16 rounded-full bg-green-100 mx-auto">
          <CheckCircle2 size={32} className="text-green-600" />
        </div>
        <div>
          <h2 className="text-lg font-bold text-slate-900">¡Cuenta creada!</h2>
          <p className="text-slate-500 text-sm mt-1">
            <strong>{persona.nombre}</strong> ya puede ingresar a la app móvil.
          </p>
        </div>
        <button
          onClick={onCreado}
          className="w-full bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          Registrar otro cliente
        </button>
      </div>
    )
  }

  return (
    <div className="max-w-lg bg-white rounded-2xl border border-slate-200 p-6 space-y-5">
      <div>
        <button
          onClick={onVolver}
          className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 transition-colors mb-3"
        >
          <ArrowLeft size={15} />
          Cambiar cliente
        </button>
        <h2 className="text-lg font-semibold text-slate-900">Crear cuenta app</h2>
        <p className="text-sm text-slate-500 mt-0.5">
          Cliente: <strong className="text-slate-700">{persona.nombre}</strong>
        </p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {/* Login */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            Nombre de usuario (login)
          </label>
          <input
            type="text"
            autoFocus
            autoComplete="off"
            placeholder="juanperez"
            {...register('login')}
            className={inputClass}
          />
          {errors.login && (
            <p className="text-xs text-red-600">{errors.login.message}</p>
          )}
          <p className="text-xs text-slate-400">
            Solo letras, números, puntos, guiones y guion bajo. Sin espacios.
          </p>
        </div>

        {/* Contraseña */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">Contraseña inicial</label>
          <div className="relative">
            <input
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="••••••••"
              {...register('password')}
              className={`${inputClass} pr-10`}
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
          {errors.password && (
            <p className="text-xs text-red-600">{errors.password.message}</p>
          )}
          <p className="text-xs text-slate-400">
            Comparte esta contraseña con el cliente de forma segura.
          </p>
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          {isSubmitting && <Loader2 size={16} className="animate-spin" />}
          {isSubmitting ? 'Creando cuenta...' : 'Crear cuenta'}
        </button>
      </form>
    </div>
  )
}
```

---

## 5. ClientesAppPage — orquestador de los 3 pasos

**`src/features/auth/pages/ClientesAppPage.tsx`:**
```tsx
import { useState, useCallback } from 'react'
import type { Persona } from '@/api/types/auth.types'
import { PageHeader } from '@/components/PageHeader'
import { BuscarPersonaStep } from '../components/BuscarPersonaStep'
import { CrearPersonaStep } from '../components/CrearPersonaStep'
import { CrearAppUsuarioStep } from '../components/CrearAppUsuarioStep'

type Paso = 'buscar' | 'crear-persona' | 'crear-app-usuario'

export function ClientesAppPage() {
  const [paso, setPaso] = useState<Paso>('buscar')
  const [ciPendiente, setCiPendiente] = useState('')
  const [personaSeleccionada, setPersonaSeleccionada] = useState<Persona | null>(null)

  const reiniciar = useCallback(() => {
    setPaso('buscar')
    setCiPendiente('')
    setPersonaSeleccionada(null)
  }, [])

  return (
    <div className="flex flex-col h-full">
      <PageHeader
        title="Cuentas App"
        description="Registra o gestiona las cuentas de clientes en la app móvil"
      />

      {/* Indicador de pasos */}
      <div className="px-6 py-4 border-b bg-white">
        <div className="flex items-center gap-2 text-sm">
          {[
            { key: 'buscar',           label: '1. Buscar cliente' },
            { key: 'crear-persona',    label: '2. Registrar cliente' },
            { key: 'crear-app-usuario', label: '3. Crear cuenta app' },
          ].map((s, i) => {
            const pasos: Paso[] = ['buscar', 'crear-persona', 'crear-app-usuario']
            const idx = pasos.indexOf(paso)
            const sIdx = pasos.indexOf(s.key as Paso)
            const activo = s.key === paso
            const completado = sIdx < idx

            return (
              <div key={s.key} className="flex items-center gap-2">
                {i > 0 && <span className="text-slate-300">›</span>}
                <span className={`${
                  activo
                    ? 'text-orange-600 font-semibold'
                    : completado
                    ? 'text-green-600'
                    : 'text-slate-400'
                }`}>
                  {completado ? '✓ ' : ''}{s.label}
                </span>
              </div>
            )
          })}
        </div>
      </div>

      {/* Contenido del paso actual */}
      <div className="flex-1 overflow-auto p-6">
        {paso === 'buscar' && (
          <BuscarPersonaStep
            onPersonaEncontrada={(persona) => {
              setPersonaSeleccionada(persona)
              setPaso('crear-app-usuario')
            }}
            onPersonaNoExiste={(ci) => {
              setCiPendiente(ci)
              setPaso('crear-persona')
            }}
          />
        )}

        {paso === 'crear-persona' && (
          <CrearPersonaStep
            ci={ciPendiente}
            onPersonaCreada={(persona) => {
              setPersonaSeleccionada(persona)
              setPaso('crear-app-usuario')
            }}
            onVolver={reiniciar}
          />
        )}

        {paso === 'crear-app-usuario' && personaSeleccionada && (
          <CrearAppUsuarioStep
            persona={personaSeleccionada}
            onCreado={reiniciar}
            onVolver={reiniciar}
          />
        )}
      </div>
    </div>
  )
}
```

---

## 6. Actualizar el router

**`src/router/index.tsx`** — dentro del bloque `AdminLayout`:
```tsx
import { ClientesAppPage } from '@/features/auth/pages/ClientesAppPage'

// Dentro del children de AdminLayout:
{ path: '/admin/clientes/app', element: <ClientesAppPage /> },
```

---

## Cómo probar

**Flujo CI existente (paso 1 → paso 3):**
1. Ir a "Cuentas App" en el sidebar
2. Ingresar un CI que ya exista en el backend → aparece la tarjeta verde con los datos del cliente
3. Clic en "Crear cuenta app" → cambia al paso 3 directamente
4. En paso 3: login duplicado → error "usuario ya en uso"
5. Login válido → pantalla de éxito verde + "Registrar otro cliente" reinicia el flujo

**Flujo CI nuevo (paso 1 → paso 2 → paso 3):**
1. Ingresar un CI que NO exista → el formulario del paso 2 aparece con CI precargado y no editable
2. "← Buscar otro CI" → regresa al paso 1
3. Completar formulario de persona → toast "Cliente registrado" + pasa automáticamente al paso 3
4. Crear cuenta app → pantalla de éxito

**Siguiente paso:** [IMPL-10 — Roles y Permisos](./10-roles-permisos.md)
