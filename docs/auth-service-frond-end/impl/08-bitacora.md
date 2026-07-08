# IMPL-08 — BitacoraPage (Registro de actividad)

> **Pantalla:** P-09 Bitácora  
> **Complejidad:** ★★★☆☆  
> **Prerequisito:** IMPL-07 completado  
> **Resultado:** Tabla paginada con filtros por módulo y rango de fechas; solo lectura, sin acciones destructivas

---

## Archivos que se crean en este paso

```
src/
├── components/
│   └── DataTable.tsx           ← wrapper genérico reutilizable
└── features/auth/pages/
    └── BitacoraPage.tsx
```

También se actualiza `src/router/index.tsx`.

---

## Vista de la pantalla

```
┌── PageHeader ─────────────────────────────────────────────────────┐
│ Bitácora de actividad                                              │
│ Registro de todas las acciones realizadas en el sistema            │
└────────────────────────────────────────────────────────────────────┘

┌── Filtros ─────────────────────────────────────────────────────────┐
│ [Módulo ▾]   [Desde: 2026-05-01]  [Hasta: 2026-05-23]  [Limpiar] │
└────────────────────────────────────────────────────────────────────┘

┌── Tabla ───────────────────────────────────────────────────────────┐
│ Usuario     │ Módulo  │ Acción │ Entidad │ IP              │ Fecha  │
│─────────────┼─────────┼────────┼─────────┼─────────────────┼────────│
│ Juan Mora   │ USUARIOS│ crear  │ 42      │ 192.168.1.10    │23 may  │
│ Ana García  │ AUTH    │ login  │ —       │ 190.15.44.3     │22 may  │
└────────────────────────────────────────────────────────────────────┘

← Anterior   Pág 1 / 12   Total: 118 registros   Siguiente →
```

---

## 1. Componente DataTable genérico

**`src/components/DataTable.tsx`:**
```tsx
import {
  useReactTable,
  getCoreRowModel,
  flexRender,
  type ColumnDef,
} from '@tanstack/react-table'
import { Loader2 } from 'lucide-react'

interface Props<T> {
  data: T[]
  columns: ColumnDef<T, unknown>[]
  loading?: boolean
  emptyMessage?: string
}

export function DataTable<T>({ data, columns, loading, emptyMessage = 'No hay registros' }: Props<T>) {
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          {table.getHeaderGroups().map(hg => (
            <tr key={hg.id} className="border-b bg-slate-50">
              {hg.headers.map(h => (
                <th
                  key={h.id}
                  className="px-6 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap"
                >
                  {h.isPlaceholder
                    ? null
                    : flexRender(h.column.columnDef.header, h.getContext())}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody className="divide-y divide-slate-100">
          {loading ? (
            <tr>
              <td colSpan={columns.length} className="px-6 py-16 text-center">
                <Loader2 size={24} className="text-slate-400 animate-spin mx-auto" />
              </td>
            </tr>
          ) : table.getRowModel().rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="px-6 py-16 text-center text-slate-400 text-sm">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            table.getRowModel().rows.map(row => (
              <tr key={row.id} className="hover:bg-slate-50 transition-colors">
                {row.getVisibleCells().map(cell => (
                  <td key={cell.id} className="px-6 py-4">
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
```

---

## 2. BitacoraPage

**`src/features/auth/pages/BitacoraPage.tsx`:**
```tsx
import { useState, useEffect, useCallback } from 'react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'
import { toast } from 'sonner'
import { ScrollText, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { type ColumnDef } from '@tanstack/react-table'
import { PageHeader } from '@/components/PageHeader'
import { DataTable } from '@/components/DataTable'
import { getBitacora, type BitacoraParams } from '@/api/auth.api'
import type { BitacoraEntry } from '@/api/types/auth.types'

const MODULOS = [
  'auth', 'usuarios', 'roles', 'permisos', 'personas', 'bitacora',
]

const MODULO_COLORS: Record<string, string> = {
  usuarios:  'bg-blue-100 text-blue-700',
  roles:     'bg-purple-100 text-purple-700',
  auth:      'bg-orange-100 text-orange-700',
  personas:  'bg-teal-100 text-teal-700',
  permisos:  'bg-yellow-100 text-yellow-700',
  bitacora:  'bg-slate-100 text-slate-600',
}

const getModuloClass = (modulo: string) =>
  MODULO_COLORS[modulo.toLowerCase()] ?? 'bg-slate-100 text-slate-600'

const COLUMNAS: ColumnDef<BitacoraEntry, unknown>[] = [
  {
    accessorKey: 'nombre_usuario',
    header: 'Usuario',
    cell: ({ getValue }) => (
      <span className="font-medium text-slate-900 whitespace-nowrap">
        {getValue() as string}
      </span>
    ),
  },
  {
    accessorKey: 'modulo',
    header: 'Módulo',
    cell: ({ getValue }) => {
      const mod = (getValue() as string).toLowerCase()
      return (
        <span className={`text-xs font-medium px-2.5 py-1 rounded-full whitespace-nowrap ${getModuloClass(mod)}`}>
          {mod.toUpperCase()}
        </span>
      )
    },
  },
  {
    accessorKey: 'accion',
    header: 'Acción',
    cell: ({ getValue }) => (
      <code className="text-xs text-slate-600 font-mono">{getValue() as string}</code>
    ),
  },
  {
    accessorKey: 'entidad_id',
    header: 'Entidad',
    cell: ({ getValue }) => {
      const val = getValue() as number | null
      return <span className="text-slate-500">{val ?? '—'}</span>
    },
  },
  {
    accessorKey: 'ip',
    header: 'IP',
    cell: ({ getValue }) => {
      const val = getValue() as string | null
      return (
        <span className="text-slate-400 font-mono text-xs whitespace-nowrap">
          {val ?? '—'}
        </span>
      )
    },
  },
  {
    accessorKey: 'fecha',
    header: 'Fecha',
    cell: ({ getValue }) => {
      try {
        return (
          <span className="text-slate-500 whitespace-nowrap text-xs">
            {format(new Date(getValue() as string), "d MMM, HH:mm", { locale: es })}
          </span>
        )
      } catch {
        return <span className="text-slate-400">—</span>
      }
    },
  },
]

const POR_PAGINA = 15

export function BitacoraPage() {
  const [datos, setDatos] = useState<BitacoraEntry[]>([])
  const [total, setTotal] = useState(0)
  const [pagina, setPagina] = useState(1)
  const [loading, setLoading] = useState(true)

  // Filtros
  const [modulo, setModulo] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')

  const totalPaginas = Math.ceil(total / POR_PAGINA)
  const hayFiltros = modulo || desde || hasta

  const cargar = useCallback(async () => {
    setLoading(true)
    const params: BitacoraParams = { pagina }
    if (modulo) params.modulo = modulo
    if (desde) params.desde = desde
    if (hasta) params.hasta = hasta

    try {
      const resp = await getBitacora(params)
      setDatos(resp.datos)
      setTotal(resp.total)
    } catch {
      toast.error('No se pudo cargar la bitácora.')
    } finally {
      setLoading(false)
    }
  }, [pagina, modulo, desde, hasta])

  useEffect(() => { cargar() }, [cargar])

  const limpiarFiltros = () => {
    setModulo('')
    setDesde('')
    setHasta('')
    setPagina(1)
  }

  const inputClass =
    'border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition bg-white'

  return (
    <div className="flex flex-col h-full">
      <PageHeader
        title="Bitácora de actividad"
        description="Registro de todas las acciones realizadas en el sistema"
      />

      {/* Barra de filtros */}
      <div className="px-6 py-4 border-b bg-white flex flex-wrap items-end gap-3">
        {/* Módulo */}
        <div className="space-y-1">
          <label className="block text-xs font-medium text-slate-500">Módulo</label>
          <select
            value={modulo}
            onChange={e => { setModulo(e.target.value); setPagina(1) }}
            className={`${inputClass} pr-8 min-w-[140px]`}
          >
            <option value="">Todos</option>
            {MODULOS.map(m => (
              <option key={m} value={m}>{m.toUpperCase()}</option>
            ))}
          </select>
        </div>

        {/* Desde */}
        <div className="space-y-1">
          <label className="block text-xs font-medium text-slate-500">Desde</label>
          <input
            type="date"
            value={desde}
            onChange={e => { setDesde(e.target.value); setPagina(1) }}
            className={inputClass}
          />
        </div>

        {/* Hasta */}
        <div className="space-y-1">
          <label className="block text-xs font-medium text-slate-500">Hasta</label>
          <input
            type="date"
            value={hasta}
            onChange={e => { setHasta(e.target.value); setPagina(1) }}
            className={inputClass}
          />
        </div>

        {/* Limpiar filtros */}
        {hayFiltros && (
          <button
            onClick={limpiarFiltros}
            className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 border border-slate-300 rounded-lg px-3 py-2 transition-colors"
          >
            <X size={14} />
            Limpiar
          </button>
        )}
      </div>

      {/* Tabla */}
      <div className="flex-1 overflow-auto">
        <DataTable
          data={datos}
          columns={COLUMNAS}
          loading={loading}
          emptyMessage={
            hayFiltros
              ? 'No hay registros para los filtros aplicados.'
              : 'No hay registros en la bitácora.'
          }
        />
      </div>

      {/* Paginación */}
      {!loading && total > 0 && (
        <div className="flex items-center justify-between px-6 py-4 border-t bg-white text-sm">
          <p className="text-slate-500">
            Total: <span className="font-medium text-slate-700">{total}</span> registros
          </p>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setPagina(p => Math.max(1, p - 1))}
              disabled={pagina === 1}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-300 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              <ChevronLeft size={16} />
              Anterior
            </button>
            <span className="text-slate-600">
              Pág <span className="font-medium">{pagina}</span> / {totalPaginas}
            </span>
            <button
              onClick={() => setPagina(p => Math.min(totalPaginas, p + 1))}
              disabled={pagina >= totalPaginas}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-300 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Siguiente
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
```

---

## 3. Actualizar el router

**`src/router/index.tsx`** — dentro del bloque `AdminLayout`:
```tsx
import { BitacoraPage } from '@/features/auth/pages/BitacoraPage'

// Dentro del children de AdminLayout:
{ path: '/admin/bitacora', element: <BitacoraPage /> },
```

---

## Cómo probar

1. Navegar a `/admin/bitacora` desde el sidebar
2. La tabla carga con spinner central mientras trae datos
3. Los registros muestran badges de módulo con color propio (AUTH=naranja, USUARIOS=azul, etc.)
4. Filtrar por módulo → tabla recarga automáticamente
5. Filtrar por rango de fechas → tabla recarga
6. Aplicar filtros y no hay resultados → mensaje "No hay registros para los filtros aplicados"
7. Botón "Limpiar" → aparece solo cuando hay filtros activos, resetea todo
8. Paginación: botones Anterior/Siguiente navegan entre páginas, se deshabilitan en los extremos
9. El contador "Total: X registros" actualiza con los filtros

**Siguiente paso:** [IMPL-09 — Clientes App](./09-clientes-app.md)
