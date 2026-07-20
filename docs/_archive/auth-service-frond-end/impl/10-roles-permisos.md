# IMPL-10 — RolesPage (Roles y editor de permisos)

> **ESTADO:** 📜 Histórico — paso de implementación ya completado. NO describe el estado actual del código; es el registro de cómo se construyó este módulo. Ver [../../STATUS.md](../../../STATUS.md).

> **Pantalla:** P-07 Roles  
> **Complejidad:** ★★★★★  
> **Prerequisito:** IMPL-08 completado  
> **Resultado:** Pantalla más compleja del módulo: tabla de roles + modal crear rol + editor de permisos por módulo con checkboxes y selección masiva

---

## Por qué es la más compleja

1. **Dos estados de carga simultáneos:** lista de roles + lista de todos los permisos
2. **Panel lateral con lógica compleja:** checkboxes por permiso + seleccionar/deseleccionar módulo completo
3. **Permisos agrupados dinámicamente** por módulo (viene del backend, no hardcoded)
4. **Confirmación en eliminar rol** con mensaje de consecuencias
5. **Tres acciones por fila** en la tabla: editar permisos, eliminar, (más en el futuro)

---

## Archivos que se crean en este paso

```
src/features/auth/
├── schemas/
│   └── rol.schema.ts
├── components/
│   ├── CrearRolModal.tsx
│   └── RolPermisosEditor.tsx     ← el más complejo
└── pages/
    └── RolesPage.tsx
```

También se actualiza `src/router/index.tsx`.

---

## Vista de la pantalla

```
┌── PageHeader ────────────────────────────────────────────────────┐
│ Roles y permisos                         [+ Nuevo rol]           │
│ Define qué puede hacer cada tipo de usuario                       │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│ Nombre       │ Descripción          │ Acciones                   │
│──────────────┼──────────────────────┼────────────────────────────│
│ Administrador│ Acceso total         │ [Permisos]  [Eliminar]     │
│ Recepción    │ Solo mostrador       │ [Permisos]  [Eliminar]     │
│ Instructor   │ Ver clientes         │ [Permisos]  [Eliminar]     │
└──────────────────────────────────────────────────────────────────┘

(panel lateral editor de permisos):
┌────────────────────────────────────────────┐
│ Permisos: Recepción                       ✕│
│──────────────────────────────────────────── │
│ USUARIOS         [✓ Seleccionar todo]       │
│  ☑ usuarios:leer                            │
│  ☐ usuarios:crear                           │
│  ☐ usuarios:editar                          │
│                                             │
│ ROLES            [✓ Seleccionar todo]       │
│  ☐ roles:leer                               │
│  ☐ roles:crear                              │
│                                             │
│──────────────────────────────────────────── │
│ [Cancelar]        [Guardar cambios]         │
└────────────────────────────────────────────┘
```

---

## 1. Schema

**`src/features/auth/schemas/rol.schema.ts`:**
```ts
import { z } from 'zod'

export const crearRolSchema = z.object({
  nombre: z
    .string()
    .min(2, 'El nombre debe tener al menos 2 caracteres')
    .max(80, 'Nombre demasiado largo'),
  descripcion: z
    .string()
    .max(250, 'Descripción demasiado larga')
    .optional()
    .or(z.literal('')),
})

export type CrearRolFormData = z.infer<typeof crearRolSchema>
```

---

## 2. CrearRolModal

**`src/features/auth/components/CrearRolModal.tsx`:**
```tsx
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { Loader2 } from 'lucide-react'
import { isAxiosError } from 'axios'
import {
  Dialog, DialogContent, DialogHeader,
  DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { crearRol } from '@/api/auth.api'
import { crearRolSchema, type CrearRolFormData } from '../schemas/rol.schema'

interface Props {
  open: boolean
  onClose: () => void
  onCreado: () => void
}

const inputClass =
  'w-full border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition'

export function CrearRolModal({ open, onClose, onCreado }: Props) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CrearRolFormData>({ resolver: zodResolver(crearRolSchema) })

  const handleClose = () => { reset(); onClose() }

  const onSubmit = async (data: CrearRolFormData) => {
    try {
      await crearRol({
        nombre: data.nombre,
        descripcion: data.descripcion || undefined,
      })
      toast.success('Rol creado correctamente.')
      reset()
      onCreado()
    } catch (err) {
      if (isAxiosError(err)) {
        if (err.response?.status === 409) {
          setError('nombre', { message: 'Ya existe un rol con ese nombre.' })
        } else {
          toast.error('Error al crear el rol. Intenta de nuevo.')
        }
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Nuevo rol</DialogTitle>
        </DialogHeader>

        <form
          id="crear-rol-form"
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4 py-2"
          noValidate
        >
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              Nombre del rol <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              autoFocus
              placeholder="Ej: Recepcionista"
              {...register('nombre')}
              className={inputClass}
            />
            {errors.nombre && (
              <p className="text-xs text-red-600">{errors.nombre.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              Descripción <span className="text-slate-400 font-normal">(opcional)</span>
            </label>
            <textarea
              rows={2}
              placeholder="¿Qué responsabilidades tiene este rol?"
              {...register('descripcion')}
              className={`${inputClass} resize-none`}
            />
            {errors.descripcion && (
              <p className="text-xs text-red-600">{errors.descripcion.message}</p>
            )}
          </div>

          <p className="text-xs text-slate-400">
            Después de crear el rol podrás asignarle permisos específicos.
          </p>
        </form>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            Cancelar
          </Button>
          <Button
            type="submit"
            form="crear-rol-form"
            disabled={isSubmitting}
            className="bg-orange-500 hover:bg-orange-600 text-white"
          >
            {isSubmitting && <Loader2 size={14} className="mr-2 animate-spin" />}
            {isSubmitting ? 'Creando...' : 'Crear rol'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

---

## 3. RolPermisosEditor (el componente más complejo)

**`src/features/auth/components/RolPermisosEditor.tsx`:**
```tsx
import { useState, useEffect, useMemo } from 'react'
import { toast } from 'sonner'
import { X, Loader2, ChevronDown, ChevronUp } from 'lucide-react'
import { getRolPermisos, getPermisos, actualizarRolPermisos } from '@/api/auth.api'
import type { Permiso, Rol } from '@/api/types/auth.types'

interface Props {
  rol: Rol
  onClose: () => void
}

export function RolPermisosEditor({ rol, onClose }: Props) {
  const [todosPermisos, setTodosPermisos] = useState<Permiso[]>([])
  const [seleccionados, setSeleccionados] = useState<Set<number>>(new Set())
  const [loading, setLoading] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [modulosExpandidos, setModulosExpandidos] = useState<Set<string>>(new Set())

  useEffect(() => {
    setLoading(true)
    Promise.all([getPermisos(), getRolPermisos(rol.id)])
      .then(([todos, rolData]) => {
        setTodosPermisos(todos)
        setSeleccionados(new Set(rolData.permisos.map(p => p.id)))
        // Expandir todos los módulos por defecto
        const mods = new Set(todos.map(p => p.modulo))
        setModulosExpandidos(mods)
      })
      .catch(() => toast.error('No se pudieron cargar los permisos.'))
      .finally(() => setLoading(false))
  }, [rol.id])

  // Agrupar permisos por módulo
  const porModulo = useMemo(() => {
    return todosPermisos.reduce<Record<string, Permiso[]>>((acc, p) => {
      if (!acc[p.modulo]) acc[p.modulo] = []
      acc[p.modulo].push(p)
      return acc
    }, {})
  }, [todosPermisos])

  const togglePermiso = (id: number) => {
    setSeleccionados(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const toggleModulo = (modulo: string) => {
    const permisosDelModulo = porModulo[modulo] ?? []
    const todosSeleccionados = permisosDelModulo.every(p => seleccionados.has(p.id))

    setSeleccionados(prev => {
      const next = new Set(prev)
      if (todosSeleccionados) {
        permisosDelModulo.forEach(p => next.delete(p.id))
      } else {
        permisosDelModulo.forEach(p => next.add(p.id))
      }
      return next
    })
  }

  const toggleExpandirModulo = (modulo: string) => {
    setModulosExpandidos(prev => {
      const next = new Set(prev)
      if (next.has(modulo)) next.delete(modulo)
      else next.add(modulo)
      return next
    })
  }

  const guardar = async () => {
    setGuardando(true)
    try {
      await actualizarRolPermisos(rol.id, {
        id_permisos: Array.from(seleccionados),
      })
      toast.success(`Permisos de "${rol.nombre}" actualizados.`)
      onClose()
    } catch {
      toast.error('Error al guardar los permisos. Intenta de nuevo.')
    } finally {
      setGuardando(false)
    }
  }

  const totalSeleccionados = seleccionados.size
  const totalPermisos = todosPermisos.length

  return (
    <>
      {/* Overlay */}
      <div
        className="fixed inset-0 z-20 bg-black/30"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Panel */}
      <aside className="fixed inset-y-0 right-0 z-30 w-96 bg-white shadow-2xl flex flex-col">
        {/* Header */}
        <div className="flex items-start justify-between px-5 py-4 border-b">
          <div>
            <h2 className="font-semibold text-slate-900">
              Permisos: {rol.nombre}
            </h2>
            {!loading && (
              <p className="text-xs text-slate-400 mt-0.5">
                {totalSeleccionados} de {totalPermisos} permisos seleccionados
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Barra de progreso */}
        {!loading && totalPermisos > 0 && (
          <div className="px-5 py-2 border-b bg-slate-50">
            <div className="w-full bg-slate-200 rounded-full h-1.5">
              <div
                className="bg-orange-500 h-1.5 rounded-full transition-all duration-300"
                style={{ width: `${(totalSeleccionados / totalPermisos) * 100}%` }}
              />
            </div>
          </div>
        )}

        {/* Lista de permisos */}
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <Loader2 size={24} className="text-slate-400 animate-spin" />
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {Object.entries(porModulo).map(([modulo, permisos]) => {
                const todosChecked = permisos.every(p => seleccionados.has(p.id))
                const algunoChecked = permisos.some(p => seleccionados.has(p.id))
                const expandido = modulosExpandidos.has(modulo)

                return (
                  <div key={modulo}>
                    {/* Header del módulo */}
                    <div className="flex items-center gap-2 px-5 py-3 bg-slate-50">
                      {/* Checkbox de módulo (seleccionar todos) */}
                      <input
                        type="checkbox"
                        checked={todosChecked}
                        ref={el => {
                          if (el) el.indeterminate = algunoChecked && !todosChecked
                        }}
                        onChange={() => toggleModulo(modulo)}
                        className="w-4 h-4 rounded accent-orange-500 cursor-pointer"
                        id={`modulo-${modulo}`}
                      />
                      <label
                        htmlFor={`modulo-${modulo}`}
                        className="flex-1 text-xs font-bold uppercase tracking-wider text-slate-600 cursor-pointer select-none"
                      >
                        {modulo}
                      </label>
                      <span className="text-xs text-slate-400">
                        {permisos.filter(p => seleccionados.has(p.id)).length}/{permisos.length}
                      </span>
                      <button
                        onClick={() => toggleExpandirModulo(modulo)}
                        className="text-slate-400 hover:text-slate-600 transition-colors"
                      >
                        {expandido ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                      </button>
                    </div>

                    {/* Permisos individuales */}
                    {expandido && (
                      <div className="px-5 py-2 space-y-2">
                        {permisos.map(p => (
                          <label
                            key={p.id}
                            className="flex items-start gap-3 cursor-pointer group py-1"
                          >
                            <input
                              type="checkbox"
                              checked={seleccionados.has(p.id)}
                              onChange={() => togglePermiso(p.id)}
                              className="w-4 h-4 mt-0.5 rounded accent-orange-500 cursor-pointer flex-shrink-0"
                            />
                            <div>
                              <code className="text-xs font-mono text-orange-700 group-hover:text-orange-800">
                                {p.nombre}
                              </code>
                              {p.descripcion && (
                                <p className="text-xs text-slate-500 mt-0.5">
                                  {p.descripcion}
                                </p>
                              )}
                            </div>
                          </label>
                        ))}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Footer con acciones */}
        <div className="px-5 py-4 border-t flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 border border-slate-300 rounded-lg py-2.5 text-sm text-slate-700 hover:bg-slate-50 transition-colors font-medium"
          >
            Cancelar
          </button>
          <button
            onClick={guardar}
            disabled={guardando || loading}
            className="flex-1 flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 disabled:opacity-60 text-white rounded-lg py-2.5 text-sm font-semibold transition-colors"
          >
            {guardando && <Loader2 size={14} className="animate-spin" />}
            {guardando ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>
      </aside>
    </>
  )
}
```

---

## 4. RolesPage

**`src/features/auth/pages/RolesPage.tsx`:**
```tsx
import { useState, useEffect, useCallback } from 'react'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { Shield, PlusCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/PageHeader'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { IfPermission } from '@/router/guards/PermissionGuard'
import { getRoles, eliminarRol } from '@/api/auth.api'
import type { Rol } from '@/api/types/auth.types'
import { CrearRolModal } from '../components/CrearRolModal'
import { RolPermisosEditor } from '../components/RolPermisosEditor'

function TableSkeleton() {
  return (
    <div className="space-y-0">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="flex gap-4 px-6 py-4 border-b animate-pulse">
          <div className="h-4 bg-slate-100 rounded w-1/4" />
          <div className="h-4 bg-slate-100 rounded w-2/5" />
          <div className="h-4 bg-slate-100 rounded w-32" />
        </div>
      ))}
    </div>
  )
}

function EmptyState({ onAdd }: { onAdd: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <Shield size={48} className="text-slate-200 mb-4" />
      <h3 className="text-slate-700 font-medium">No hay roles creados</h3>
      <p className="text-slate-400 text-sm mt-1 mb-5">
        Crea el primer rol para asignarle permisos
      </p>
      <Button
        onClick={onAdd}
        className="bg-orange-500 hover:bg-orange-600 text-white"
      >
        <PlusCircle size={16} className="mr-2" />
        Nuevo rol
      </Button>
    </div>
  )
}

export function RolesPage() {
  const [roles, setRoles] = useState<Rol[]>([])
  const [loading, setLoading] = useState(true)
  const [crearOpen, setCrearOpen] = useState(false)
  const [rolEditor, setRolEditor] = useState<Rol | null>(null)
  const [rolEliminar, setRolEliminar] = useState<Rol | null>(null)

  const cargar = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getRoles()
      setRoles(data)
    } catch {
      toast.error('No se pudieron cargar los roles.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { cargar() }, [cargar])

  const handleEliminar = async (rol: Rol) => {
    try {
      await eliminarRol(rol.id)
      toast.success(`Rol "${rol.nombre}" eliminado.`)
      cargar()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error('No puedes eliminar un rol que tiene usuarios asignados.')
      } else {
        toast.error('Error al eliminar el rol. Intenta de nuevo.')
      }
    } finally {
      setRolEliminar(null)
    }
  }

  return (
    <div className="flex flex-col h-full">
      <PageHeader
        title="Roles y permisos"
        description="Define qué puede hacer cada tipo de usuario del sistema"
        action={
          <IfPermission permiso="roles:crear">
            <Button
              onClick={() => setCrearOpen(true)}
              className="bg-orange-500 hover:bg-orange-600 text-white"
            >
              <PlusCircle size={16} className="mr-2" />
              Nuevo rol
            </Button>
          </IfPermission>
        }
      />

      <div className="flex-1 overflow-auto">
        {loading ? (
          <TableSkeleton />
        ) : roles.length === 0 ? (
          <EmptyState onAdd={() => setCrearOpen(true)} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-slate-50">
                  {['Nombre', 'Descripción', 'Acciones'].map(h => (
                    <th
                      key={h}
                      className="px-6 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {roles.map(rol => (
                  <tr key={rol.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-6 py-4 font-semibold text-slate-900 whitespace-nowrap">
                      {rol.nombre}
                    </td>
                    <td className="px-6 py-4 text-slate-500 max-w-xs">
                      {rol.descripcion ?? (
                        <span className="text-slate-300 italic">Sin descripción</span>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <IfPermission permiso="roles:editar">
                          <button
                            onClick={() => setRolEditor(rol)}
                            className="text-sm text-orange-600 hover:text-orange-700 font-medium transition-colors"
                          >
                            Editar permisos
                          </button>
                        </IfPermission>
                        <IfPermission permiso="roles:eliminar">
                          <button
                            onClick={() => setRolEliminar(rol)}
                            className="text-sm text-red-500 hover:text-red-700 transition-colors"
                          >
                            Eliminar
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

      {/* Modal crear rol */}
      <CrearRolModal
        open={crearOpen}
        onClose={() => setCrearOpen(false)}
        onCreado={() => { setCrearOpen(false); cargar() }}
      />

      {/* Editor de permisos */}
      {rolEditor && (
        <RolPermisosEditor
          rol={rolEditor}
          onClose={() => { setRolEditor(null); cargar() }}
        />
      )}

      {/* Confirmación eliminar */}
      <ConfirmDialog
        open={rolEliminar !== null}
        title="Eliminar rol"
        description={`¿Eliminar el rol "${rolEliminar?.nombre}"? Los usuarios con este rol perderán sus permisos. Esta acción no se puede deshacer.`}
        onConfirm={() => rolEliminar && handleEliminar(rolEliminar)}
        onCancel={() => setRolEliminar(null)}
        destructive
      />
    </div>
  )
}
```

---

## 5. Actualizar el router

**`src/router/index.tsx`** — dentro del bloque `AdminLayout`:
```tsx
import { RolesPage } from '@/features/auth/pages/RolesPage'

// Dentro del children de AdminLayout:
{ path: '/admin/roles', element: <RolesPage /> },
```

---

## Cómo probar

1. Navegar a `/admin/roles`
2. Sin roles → pantalla vacía con CTA "Nuevo rol"
3. Crear rol → modal, nombre duplicado → error en campo, nombre nuevo → toast + tabla recarga
4. Clic en "Editar permisos" → panel lateral se desliza desde la derecha
5. En el panel: barra de progreso muestra `X de Y permisos seleccionados`
6. Hacer clic en el checkbox del nombre del módulo → selecciona/deselecciona todos sus permisos
7. El estado "intermedio" (indeterminate) del checkbox de módulo aparece si solo algunos están marcados
8. Usar la flecha ▼/▲ para colapsar/expandir secciones de módulo
9. "Guardar cambios" → toast de éxito + panel se cierra
10. "Eliminar" rol con usuarios asignados → el backend devuelve 409 → toast de error descriptivo
11. Eliminar rol sin usuarios → diálogo de confirmación con texto de consecuencias + toast de éxito

**Siguiente paso:** [IMPL-11 — Plataforma](./11-plataforma.md)
