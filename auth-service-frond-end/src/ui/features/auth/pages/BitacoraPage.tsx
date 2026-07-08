import { useState, useEffect, useCallback, useMemo } from 'react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'
import { toast } from 'sonner'
import { ChevronLeft, ChevronRight, X } from 'lucide-react'
import { type ColumnDef } from '@tanstack/react-table'
import { useTranslation } from 'react-i18next'
import { PageHeader } from '@/ui/components/PageHeader'
import { DataTable } from '@/ui/components/DataTable'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { BitacoraEntry, BitacoraParams } from '@/infrastructure/http/auth/auth.dto'

const MODULOS = ['auth', 'usuarios', 'roles', 'permisos', 'personas', 'bitacora']

const MODULO_COLORS: Record<string, string> = {
  usuarios: 'bg-blue-100 text-blue-700',
  roles: 'bg-purple-100 text-purple-700',
  auth: 'bg-orange-100 text-orange-700',
  personas: 'bg-teal-100 text-teal-700',
  permisos: 'bg-yellow-100 text-yellow-700',
  bitacora: 'bg-slate-100 text-slate-600',
}

const getModuloClass = (modulo: string) =>
  MODULO_COLORS[modulo.toLowerCase()] ?? 'bg-slate-100 text-slate-600'

const POR_PAGINA = 15

export function BitacoraPage() {
  const { t } = useTranslation()
  const [datos, setDatos] = useState<BitacoraEntry[]>([])
  const [total, setTotal] = useState(0)
  const [pagina, setPagina] = useState(1)
  const [loading, setLoading] = useState(true)

  const [modulo, setModulo] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')

  const totalPaginas = Math.ceil(total / POR_PAGINA)
  const hayFiltros = modulo || desde || hasta

  const columnas = useMemo<ColumnDef<BitacoraEntry, unknown>[]>(() => [
    {
      accessorKey: 'nombre_usuario',
      header: t('activityLog.colUser'),
      cell: ({ getValue }) => (
        <span className="font-medium text-slate-900 whitespace-nowrap">{getValue() as string}</span>
      ),
    },
    {
      accessorKey: 'modulo',
      header: t('activityLog.colModule'),
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
      header: t('activityLog.colAction'),
      cell: ({ getValue }) => (
        <code className="text-xs text-slate-600 font-mono">{getValue() as string}</code>
      ),
    },
    {
      accessorKey: 'entidad_id',
      header: t('activityLog.colEntity'),
      cell: ({ getValue }) => {
        const val = getValue() as number | null
        return <span className="text-slate-500">{val ?? '—'}</span>
      },
    },
    {
      accessorKey: 'ip',
      header: t('activityLog.colIp'),
      cell: ({ getValue }) => {
        const val = getValue() as string | null
        return <span className="text-slate-400 font-mono text-xs whitespace-nowrap">{val ?? '—'}</span>
      },
    },
    {
      accessorKey: 'fecha',
      header: t('activityLog.colDate'),
      cell: ({ getValue }) => {
        try {
          return (
            <span className="text-slate-500 whitespace-nowrap text-xs">
              {format(new Date(getValue() as string), 'd MMM, HH:mm', { locale: es })}
            </span>
          )
        } catch {
          return <span className="text-slate-400">—</span>
        }
      },
    },
  ], [t])

  const cargar = useCallback(async () => {
    setLoading(true)
    const params: BitacoraParams = { pagina }
    if (modulo) params.modulo = modulo
    if (desde) params.desde = desde
    if (hasta) params.hasta = hasta

    try {
      const resp = await authRepository.getBitacora(params)
      setDatos(resp.datos)
      setTotal(resp.total)
    } catch {
      toast.error(t('activityLog.loadError'))
    } finally {
      setLoading(false)
    }
  }, [pagina, modulo, desde, hasta, t])

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
        title={t('activityLog.title')}
        description={t('activityLog.description')}
      />

      <div className="px-6 py-4 border-b bg-white flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <label className="block text-xs font-medium text-slate-500">{t('activityLog.filterModule')}</label>
          <select
            value={modulo}
            onChange={e => { setModulo(e.target.value); setPagina(1) }}
            className={`${inputClass} pr-8 min-w-[140px]`}
          >
            <option value="">{t('activityLog.filterAllModules')}</option>
            {MODULOS.map(m => (
              <option key={m} value={m}>{m.toUpperCase()}</option>
            ))}
          </select>
        </div>

        <div className="space-y-1">
          <label className="block text-xs font-medium text-slate-500">{t('activityLog.filterFrom')}</label>
          <input
            type="date"
            value={desde}
            onChange={e => { setDesde(e.target.value); setPagina(1) }}
            className={inputClass}
          />
        </div>

        <div className="space-y-1">
          <label className="block text-xs font-medium text-slate-500">{t('activityLog.filterTo')}</label>
          <input
            type="date"
            value={hasta}
            onChange={e => { setHasta(e.target.value); setPagina(1) }}
            className={inputClass}
          />
        </div>

        {hayFiltros && (
          <button
            onClick={limpiarFiltros}
            className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 border border-slate-300 rounded-lg px-3 py-2 transition-colors"
          >
            <X size={14} />
            {t('activityLog.filterClear')}
          </button>
        )}
      </div>

      <div className="flex-1 overflow-auto">
        <DataTable
          data={datos}
          columns={columnas}
          loading={loading}
          emptyMessage={
            hayFiltros
              ? t('activityLog.emptyWithFilters')
              : t('activityLog.emptyNoFilters')
          }
        />
      </div>

      {!loading && total > 0 && (
        <div className="flex items-center justify-between px-6 py-4 border-t bg-white text-sm">
          <p className="text-slate-500">
            {t('activityLog.total', { count: total })}
          </p>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setPagina(p => Math.max(1, p - 1))}
              disabled={pagina === 1}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-300 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              <ChevronLeft size={16} />
              {t('activityLog.previous')}
            </button>
            <span className="text-slate-600">
              {t('activityLog.page', { page: pagina, total: totalPaginas })}
            </span>
            <button
              onClick={() => setPagina(p => Math.min(totalPaginas, p + 1))}
              disabled={pagina >= totalPaginas}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-300 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              {t('activityLog.next')}
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
