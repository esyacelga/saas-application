# IMPL-07 — UsuariosPage (Gestión de usuarios staff)

> **Pantalla:** P-06 Usuarios  
> **Complejidad:** ★★★☆☆  
> **Prerequisito:** IMPL-06 completado (AdminLayout)  
> **Resultado:** Tabla de usuarios con crear, activar/desactivar y ver permisos; primer módulo CRUD completo

---

## Archivos que se crean en este paso

```
src/features/auth/
├── schemas/
│   └── usuario.schema.ts
├── components/
│   ├── CrearUsuarioModal.tsx     ← formulario en modal
│   └── PermisosPanel.tsx         ← panel lateral de permisos (solo lectura)
└── pages/
    └── UsuariosPage.tsx
```

También se actualiza `src/router/index.tsx`.

---

## Vista de la pantalla

```
┌── PageHeader ────────────────────────────────────────────────────┐
│ Usuarios                         [+ Nuevo usuario]               │
│ Gestiona los miembros del equipo                                 │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│ Nombre      │ Correo           │ Rol        │ Último acceso │ Estado  │ Acciones │
│─────────────┼──────────────────┼────────────┼───────────────┼─────────┼──────────│
│ Juan Mora   │ juan@gym.com     │ Admin      │ 23 may, 14:30 │ ● Activo│ Ver permisos  Desactivar │
│ Ana García  │ ana@gym.com      │ Recepción  │ —             │ ● Inact.│ Ver permisos  Activar    │
└──────────────────────────────────────────────────────────────────┘

(modal crear usuario):
┌─────────────────────────────────────┐
│  Nuevo usuario                    ✕ │
│                                     │
│  Nombre completo [______________]   │
│  Correo         [______________]    │
│  Rol            [Seleccionar ▾]     │
│  Sucursal ID    [__]                │
│  Contraseña     [______________]    │
│                                     │
│  [Cancelar]    [Crear usuario]      │
└─────────────────────────────────────┘

(panel lateral permisos):
┌─────────────────────────────┐
│ Permisos de Juan Mora      ✕│
│ Rol: Administrador          │
│─────────────────────────────│
│ USUARIOS                    │
│ ✓ usuarios:leer             │
│ ✓ usuarios:crear            │
│                             │
│ ROLES                       │
│ ✓ roles:leer                │
└─────────────────────────────┘
```

---

## 1. Schema

**`src/features/auth/schemas/usuario.schema.ts`:**
```ts
import { z } from 'zod'

export const crearUsuarioSchema = z.object({
  nombre: z
    .string()
    .min(2, 'El nombre debe tener al menos 2 caracteres')
    .max(100, 'Nombre demasiado largo'),
  correo: z
    .string()
    .min(1, 'El correo es requerido')
    .email('Ingresa un correo válido'),
  id_rol: z.coerce
    .number({ invalid_type_error: 'Selecciona un rol' })
    .int()
    .positive('Selecciona un rol'),
  id_sucursal: z.coerce
    .number({ invalid_type_error: 'Ingresa el ID de la sucursal' })
    .int()
    .positive('El ID de sucursal debe ser mayor a cero'),
  password_temporal: z
    .string()
    .min(6, 'La contraseña temporal debe tener al menos 6 caracteres'),
})

export type CrearUsuarioFormData = z.infer<typeof crearUsuarioSchema>
```

---

## 2. Componente: CrearUsuarioModal

**`src/features/auth/components/CrearUsuarioModal.tsx`:**
```tsx
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { Loader2, AlertCircle } from 'lucide-react'
import { isAxiosError } from 'axios'
import {
  Dialog, DialogContent, DialogHeader,
  DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { crearUsuario, getRoles } from '@/api/auth.api'
import type { Rol } from '@/api/types/auth.types'
import { crearUsuarioSchema, type CrearUsuarioFormData } from '../schemas/usuario.schema'

interface Props {
  open: boolean
  onClose: () => void
  onCreado: () => void
}

export function CrearUsuarioModal({ open, onClose, onCreado }: Props) {
  const [roles, setRoles] = useState<Rol[]>([])

  useEffect(() => {
    if (open) {
      getRoles().then(setRoles).catch(() => {})
    }
  }, [open])

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CrearUsuarioFormData>({ resolver: zodResolver(crearUsuarioSchema) })

  const handleClose = () => {
    reset()
    onClose()
  }

  const onSubmit = async (data: CrearUsuarioFormData) => {
    try {
      await crearUsuario(data)
      toast.success('Usuario creado correctamente.')
      reset()
      onCreado()
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 409) {
          setError('correo', { message: 'Este correo ya está registrado.' })
        } else if (status === 404) {
          setError('id_rol', { message: 'El rol seleccionado no existe.' })
        } else {
          toast.error('Error al crear el usuario. Intenta de nuevo.')
        }
      }
    }
  }

  const inputClass =
    'w-full border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition'

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Nuevo usuario</DialogTitle>
        </DialogHeader>

        <form
          id="crear-usuario-form"
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4 py-2"
          noValidate
        >
          {/* Nombre */}
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              Nombre completo
            </label>
            <input
              type="text"
              autoComplete="off"
              placeholder="Juan Pérez"
              {...register('nombre')}
              className={inputClass}
            />
            {errors.nombre && (
              <p className="text-xs text-red-600">{errors.nombre.message}</p>
            )}
          </div>

          {/* Correo */}
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              Correo electrónico
            </label>
            <input
              type="email"
              autoComplete="off"
              placeholder="usuario@correo.com"
              {...register('correo')}
              className={inputClass}
            />
            {errors.correo && (
              <p className="text-xs text-red-600">{errors.correo.message}</p>
            )}
          </div>

          {/* Rol */}
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">Rol</label>
            <select {...register('id_rol')} className={inputClass}>
              <option value="">Seleccionar rol...</option>
              {roles.map(r => (
                <option key={r.id} value={r.id}>
                  {r.nombre}
                </option>
              ))}
            </select>
            {errors.id_rol && (
              <p className="text-xs text-red-600">{errors.id_rol.message}</p>
            )}
          </div>

          {/* ID Sucursal */}
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              ID de sucursal
            </label>
            <input
              type="number"
              min={1}
              placeholder="Ej: 1"
              {...register('id_sucursal')}
              className={inputClass}
            />
            {errors.id_sucursal && (
              <p className="text-xs text-red-600">{errors.id_sucursal.message}</p>
            )}
          </div>

          {/* Contraseña temporal */}
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              Contraseña temporal
            </label>
            <input
              type="text"
              autoComplete="off"
              placeholder="El usuario la cambiará al ingresar"
              {...register('password_temporal')}
              className={inputClass}
            />
            {errors.password_temporal && (
              <p className="text-xs text-red-600">{errors.password_temporal.message}</p>
            )}
            <p className="text-xs text-slate-400">
              El usuario deberá cambiarla en su primer inicio de sesión.
            </p>
          </div>
        </form>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            Cancelar
          </Button>
          <Button
            type="submit"
            form="crear-usuario-form"
            disabled={isSubmitting}
            className="bg-orange-500 hover:bg-orange-600 text-white"
          >
            {isSubmitting && <Loader2 size={14} className="mr-2 animate-spin" />}
            {isSubmitting ? 'Creando...' : 'Crear usuario'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

---

## 3. Componente: PermisosPanel (solo lectura)

Panel lateral deslizante que muestra los permisos de un usuario agrupados por módulo.

**`src/features/auth/components/PermisosPanel.tsx`:**
```tsx
import { useEffect, useState } from 'react'
import { X, Loader2 } from 'lucide-react'
import { getPermisosUsuario } from '@/api/auth.api'
import type { PermisosUsuario } from '@/api/types/auth.types'

interface Props {
  usuarioId: number
  usuarioNombre: string
  onClose: () => void
}

export function PermisosPanel({ usuarioId, usuarioNombre, onClose }: Props) {
  const [datos, setDatos] = useState<PermisosUsuario | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    getPermisosUsuario(usuarioId)
      .then(setDatos)
      .catch(() => setDatos(null))
      .finally(() => setLoading(false))
  }, [usuarioId])

  // Agrupar permisos por módulo
  const porModulo: Record<string, string[]> = {}
  datos?.permisos.forEach(p => {
    const modulo = p.split(':')[0].toUpperCase()
    if (!porModulo[modulo]) porModulo[modulo] = []
    porModulo[modulo].push(p)
  })

  return (
    <>
      {/* Overlay */}
      <div
        className="fixed inset-0 z-20 bg-black/30"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Panel */}
      <aside className="fixed inset-y-0 right-0 z-30 w-80 bg-white shadow-2xl flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b">
          <div>
            <h2 className="font-semibold text-slate-900 text-sm">
              Permisos de {usuarioNombre}
            </h2>
            {datos && (
              <p className="text-xs text-slate-500 mt-0.5">
                Rol: {datos.rol.nombre}
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors"
            aria-label="Cerrar panel"
          >
            <X size={18} />
          </button>
        </div>

        {/* Contenido */}
        <div className="flex-1 overflow-y-auto px-5 py-4">
          {loading ? (
            <div className="flex items-center justify-center h-32">
              <Loader2 size={24} className="text-slate-400 animate-spin" />
            </div>
          ) : !datos ? (
            <p className="text-sm text-slate-400 text-center mt-8">
              No se pudieron cargar los permisos.
            </p>
          ) : Object.keys(porModulo).length === 0 ? (
            <p className="text-sm text-slate-400 text-center mt-8">
              Este usuario no tiene permisos asignados.
            </p>
          ) : (
            <div className="space-y-5">
              {Object.entries(porModulo).map(([modulo, permisos]) => (
                <div key={modulo}>
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                    {modulo}
                  </p>
                  <ul className="space-y-1">
                    {permisos.map(p => (
                      <li
                        key={p}
                        className="flex items-center gap-2 text-sm text-slate-700"
                      >
                        <span className="w-1.5 h-1.5 rounded-full bg-orange-500 flex-shrink-0" />
                        <code className="text-xs text-slate-600 font-mono">{p}</code>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>
      </aside>
    </>
  )
}
```

---

## 4. UsuariosPage

**`src/features/auth/pages/UsuariosPage.tsx`:**
```tsx
import { useState, useEffect, useCallback } from 'react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { UserPlus, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/PageHeader'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { IfPermission } from '@/router/guards/PermissionGuard'
import { getUsuarios, desactivarUsuario, activarUsuario } from '@/api/auth.api'
import type { UsuarioStaff } from '@/api/types/auth.types'
import { CrearUsuarioModal } from '../components/CrearUsuarioModal'
import { PermisosPanel } from '../components/PermisosPanel'

// Skeleton de tabla durante la carga
function TableSkeleton() {
  return (
    <div className="space-y-0">
      {Array.from({ length: 5 }).map((_, i) => (
        <div
          key={i}
          className="flex gap-4 px-6 py-4 border-b animate-pulse"
        >
          <div className="h-4 bg-slate-100 rounded w-1/5" />
          <div className="h-4 bg-slate-100 rounded w-1/4" />
          <div className="h-4 bg-slate-100 rounded w-1/6" />
          <div className="h-4 bg-slate-100 rounded w-1/6" />
          <div className="h-5 bg-slate-100 rounded-full w-16" />
          <div className="h-4 bg-slate-100 rounded w-24" />
        </div>
      ))}
    </div>
  )
}

// Estado vacío
function EmptyState({ onAdd }: { onAdd: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <Users size={48} className="text-slate-200 mb-4" />
      <h3 className="text-slate-700 font-medium">No hay usuarios registrados</h3>
      <p className="text-slate-400 text-sm mt-1 mb-5">
        Agrega el primer miembro del equipo
      </p>
      <Button
        onClick={onAdd}
        className="bg-orange-500 hover:bg-orange-600 text-white"
      >
        <UserPlus size={16} className="mr-2" />
        Nuevo usuario
      </Button>
    </div>
  )
}

// Badge de estado
function EstadoBadge({ activo }: { activo: boolean }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${
        activo
          ? 'bg-green-100 text-green-700'
          : 'bg-red-100 text-red-600'
      }`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${activo ? 'bg-green-600' : 'bg-red-500'}`} />
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

export function UsuariosPage() {
  const [usuarios, setUsuarios] = useState<UsuarioStaff[]>([])
  const [loading, setLoading] = useState(true)
  const [crearOpen, setCrearOpen] = useState(false)
  const [permisosUsuario, setPermisosUsuario] = useState<UsuarioStaff | null>(null)
  const [confirmToggle, setConfirmToggle] = useState<UsuarioStaff | null>(null)

  const cargar = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getUsuarios()
      setUsuarios(data)
    } catch {
      toast.error('No se pudieron cargar los usuarios.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { cargar() }, [cargar])

  const handleToggleActivo = async (usuario: UsuarioStaff) => {
    try {
      if (usuario.activo) {
        await desactivarUsuario(usuario.id)
        toast.success(`${usuario.nombre} fue desactivado.`)
      } else {
        await activarUsuario(usuario.id)
        toast.success(`${usuario.nombre} fue activado.`)
      }
      cargar()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error('No puedes desactivar al único administrador activo.')
      } else {
        toast.error('Ocurrió un error. Intenta de nuevo.')
      }
    } finally {
      setConfirmToggle(null)
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
      <PageHeader
        title="Usuarios"
        description="Gestiona los miembros del equipo y sus accesos"
        action={
          <IfPermission permiso="usuarios:crear">
            <Button
              onClick={() => setCrearOpen(true)}
              className="bg-orange-500 hover:bg-orange-600 text-white"
            >
              <UserPlus size={16} className="mr-2" />
              Nuevo usuario
            </Button>
          </IfPermission>
        }
      />

      <div className="flex-1 overflow-auto">
        {loading ? (
          <TableSkeleton />
        ) : usuarios.length === 0 ? (
          <EmptyState onAdd={() => setCrearOpen(true)} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-slate-50 text-left">
                  {['Nombre', 'Correo', 'Rol', 'Último acceso', 'Estado', 'Acciones'].map(h => (
                    <th
                      key={h}
                      className="px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {usuarios.map(u => (
                  <tr key={u.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-6 py-4 font-medium text-slate-900 whitespace-nowrap">
                      {u.nombre}
                    </td>
                    <td className="px-6 py-4 text-slate-600 whitespace-nowrap">
                      {u.correo}
                    </td>
                    <td className="px-6 py-4 text-slate-600 whitespace-nowrap">
                      {u.nombre_rol}
                    </td>
                    <td className="px-6 py-4 text-slate-500 whitespace-nowrap">
                      {formatFecha(u.ultimo_acceso)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <EstadoBadge activo={u.activo} />
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center gap-3">
                        <button
                          onClick={() => setPermisosUsuario(u)}
                          className="text-sm text-orange-600 hover:text-orange-700 font-medium transition-colors"
                        >
                          Ver permisos
                        </button>
                        <IfPermission permiso="usuarios:editar">
                          <button
                            onClick={() => setConfirmToggle(u)}
                            className="text-sm text-slate-500 hover:text-slate-700 transition-colors"
                          >
                            {u.activo ? 'Desactivar' : 'Activar'}
                          </button>
                        </IfPermission>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal crear usuario */}
      <CrearUsuarioModal
        open={crearOpen}
        onClose={() => setCrearOpen(false)}
        onCreado={() => { setCrearOpen(false); cargar() }}
      />

      {/* Panel de permisos */}
      {permisosUsuario && (
        <PermisosPanel
          usuarioId={permisosUsuario.id}
          usuarioNombre={permisosUsuario.nombre}
          onClose={() => setPermisosUsuario(null)}
        />
      )}

      {/* Confirmación de activar/desactivar */}
      <ConfirmDialog
        open={confirmToggle !== null}
        title={confirmToggle?.activo ? 'Desactivar usuario' : 'Activar usuario'}
        description={
          confirmToggle?.activo
            ? `Al desactivar a ${confirmToggle.nombre}, no podrá ingresar al sistema. Puedes reactivarlo en cualquier momento.`
            : `¿Confirmas reactivar a ${confirmToggle?.nombre}? Podrá volver a ingresar al sistema.`
        }
        onConfirm={() => confirmToggle && handleToggleActivo(confirmToggle)}
        onCancel={() => setConfirmToggle(null)}
        destructive={confirmToggle?.activo}
      />
    </div>
  )
}
```

---

## 5. Actualizar el router

**`src/router/index.tsx`** — dentro del bloque `AdminLayout`:
```tsx
import { UsuariosPage } from '@/features/auth/pages/UsuariosPage'

// Dentro del children de AdminLayout:
{ path: '/admin/usuarios', element: <UsuariosPage /> },
```

---

## Cómo probar

1. Login como admin y navegar a `/admin/usuarios`
2. La tabla carga con skeleton animado, luego muestra la lista
3. Sin usuarios → pantalla vacía con CTA "Nuevo usuario"
4. Clic en "Nuevo usuario" → modal se abre con formulario
5. Enviar formulario vacío → errores de validación en cada campo
6. Seleccionar rol (debe cargar la lista del backend)
7. Correo duplicado → error en el campo correo
8. Crear usuario exitoso → modal se cierra + toast verde + tabla recarga
9. Clic en "Ver permisos" → panel lateral desliza desde la derecha con los permisos agrupados por módulo
10. Clic en "Desactivar" → diálogo de confirmación con texto descriptivo
11. Confirmar → toast + badge cambia a rojo "Inactivo"

**Siguiente paso:** [IMPL-08 — Bitácora](./08-bitacora.md)
