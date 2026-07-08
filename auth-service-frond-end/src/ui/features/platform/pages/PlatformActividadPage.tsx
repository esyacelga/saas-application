import { useState, useEffect, useCallback, useMemo } from 'react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'
import { toast } from 'sonner'
import { Activity, Building2, Package, RefreshCw, ChevronLeft, ChevronRight, X, ArrowUpDown } from 'lucide-react'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import type { ActividadPlataformaItem, ActividadParams } from '@/infrastructure/http/platform/platform.dto'

const MODULOS = ['companias', 'suscripciones', 'planes']

const EVENTOS: Record<string, string[]> = {
  companias:     ['COMPANIA_CREADA', 'COMPANIA_ACTUALIZADA', 'COMPANIA_SUSPENDIDA'],
  suscripciones: ['SUSCRIPCION_RENOVADA', 'SUSCRIPCION_UPGRADE', 'SUSCRIPCION_DOWNGRADE'],
  planes:        ['PLAN_CREADO', 'PLAN_ACTUALIZADO'],
}

const EVENTO_META: Record<string, { label: string; color: string; bg: string; icon: React.ElementType }> = {
  COMPANIA_CREADA:       { label: 'Gym creado',        color: '#16a34a', bg: 'rgba(22,163,74,0.12)',    icon: Building2 },
  COMPANIA_ACTUALIZADA:  { label: 'Gym actualizado',   color: '#3b82f6', bg: 'rgba(59,130,246,0.12)',   icon: Building2 },
  COMPANIA_SUSPENDIDA:   { label: 'Gym suspendido',    color: '#ef4444', bg: 'rgba(239,68,68,0.12)',    icon: Building2 },
  SUSCRIPCION_RENOVADA:  { label: 'Suscripción renovada',  color: '#10b981', bg: 'rgba(16,185,129,0.12)', icon: RefreshCw },
  SUSCRIPCION_UPGRADE:   { label: 'Upgrade de plan',   color: '#f97316', bg: 'rgba(249,115,22,0.12)',   icon: ArrowUpDown },
  SUSCRIPCION_DOWNGRADE: { label: 'Downgrade de plan', color: '#f59e0b', bg: 'rgba(245,158,11,0.12)',   icon: ArrowUpDown },
  PLAN_CREADO:           { label: 'Plan creado',       color: '#8b5cf6', bg: 'rgba(139,92,246,0.12)',   icon: Package },
  PLAN_ACTUALIZADO:      { label: 'Plan actualizado',  color: '#64748b', bg: 'rgba(100,116,139,0.12)',  icon: Package },
}

const FALLBACK_META = { label: 'Evento', color: '#64748b', bg: 'rgba(100,116,139,0.12)', icon: Activity }

const POR_PAGINA = 25

function EventoBadge({ tipo }: { tipo: string }) {
  const meta = EVENTO_META[tipo] ?? FALLBACK_META
  const Icon = meta.icon
  return (
    <span className="inline-flex items-center gap-1 font-semibold px-2 py-0.5 rounded-full whitespace-nowrap"
      style={{ fontSize: '0.6rem', background: meta.bg, color: meta.color }}>
      <Icon size={11} />
      {meta.label}
    </span>
  )
}

export function PlatformActividadPage() {
  const [datos, setDatos] = useState<ActividadPlataformaItem[]>([])
  const [total, setTotal] = useState(0)
  const [pagina, setPagina] = useState(1)
  const [loading, setLoading] = useState(true)

  const [modulo, setModulo] = useState('')
  const [tipoEvento, setTipoEvento] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')

  const totalPaginas = Math.ceil(total / POR_PAGINA)
  const hayFiltros = modulo || tipoEvento || desde || hasta

  const eventosDisponibles = useMemo(() =>
    modulo ? (EVENTOS[modulo] ?? []) : Object.values(EVENTOS).flat(),
    [modulo],
  )

  const cargar = useCallback(async () => {
    setLoading(true)
    const params: ActividadParams = { pagina }
    if (modulo) params.modulo = modulo
    if (tipoEvento) params.tipoEvento = tipoEvento
    if (desde) params.desde = desde
    if (hasta) params.hasta = hasta

    try {
      const resp = await platformRepository.getActividad(params)
      setDatos(resp.datos)
      setTotal(resp.total)
    } catch {
      toast.error('No se pudo cargar la actividad de la plataforma.')
    } finally {
      setLoading(false)
    }
  }, [pagina, modulo, tipoEvento, desde, hasta])

  useEffect(() => { cargar() }, [cargar])

  const limpiarFiltros = () => {
    setModulo('')
    setTipoEvento('')
    setDesde('')
    setHasta('')
    setPagina(1)
  }

  const inputClass = [
    'border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition',
    'bg-[var(--input-bg)] border-[var(--page-border)] text-[var(--page-text)]',
  ].join(' ')

  return (
    <div className="flex flex-col h-full">

      {/* Header */}
      <div className="px-6 pt-6 pb-4 flex-shrink-0">
        <h1 className="text-lg font-bold" style={{ color: 'var(--page-text)' }}>Actividad de plataforma</h1>
        <p className="text-sm mt-0.5" style={{ color: 'var(--page-muted)' }}>
          Registro de eventos realizados por operadores de plataforma
        </p>
      </div>

      {/* Filtros */}
      <div className="px-6 pb-4 flex-shrink-0 flex flex-wrap items-end gap-3"
        style={{ borderBottom: '1px solid var(--page-border)' }}>

        <div className="space-y-1">
          <label className="block text-xs font-medium" style={{ color: 'var(--page-muted)' }}>Módulo</label>
          <select
            value={modulo}
            onChange={e => { setModulo(e.target.value); setTipoEvento(''); setPagina(1) }}
            className={`${inputClass} pr-8 min-w-[140px]`}
            style={{ background: 'var(--input-bg)' }}
          >
            <option value="">Todos los módulos</option>
            {MODULOS.map(m => (
              <option key={m} value={m}>{m.charAt(0).toUpperCase() + m.slice(1)}</option>
            ))}
          </select>
        </div>

        <div className="space-y-1">
          <label className="block text-xs font-medium" style={{ color: 'var(--page-muted)' }}>Evento</label>
          <select
            value={tipoEvento}
            onChange={e => { setTipoEvento(e.target.value); setPagina(1) }}
            className={`${inputClass} pr-8 min-w-[180px]`}
            style={{ background: 'var(--input-bg)' }}
          >
            <option value="">Todos los eventos</option>
            {eventosDisponibles.map(e => (
              <option key={e} value={e}>{EVENTO_META[e]?.label ?? e}</option>
            ))}
          </select>
        </div>

        <div className="space-y-1">
          <label className="block text-xs font-medium" style={{ color: 'var(--page-muted)' }}>Desde</label>
          <input type="date" value={desde}
            onChange={e => { setDesde(e.target.value); setPagina(1) }}
            className={inputClass} style={{ background: 'var(--input-bg)', colorScheme: 'dark' }}
          />
        </div>

        <div className="space-y-1">
          <label className="block text-xs font-medium" style={{ color: 'var(--page-muted)' }}>Hasta</label>
          <input type="date" value={hasta}
            onChange={e => { setHasta(e.target.value); setPagina(1) }}
            className={inputClass} style={{ background: 'var(--input-bg)', colorScheme: 'dark' }}
          />
        </div>

        {hayFiltros && (
          <button
            onClick={limpiarFiltros}
            className="flex items-center gap-1.5 text-sm px-3 py-2 rounded-lg border transition-colors"
            style={{ color: 'var(--page-muted)', borderColor: 'var(--page-border)' }}
            onMouseEnter={e => (e.currentTarget.style.color = 'var(--page-text)')}
            onMouseLeave={e => (e.currentTarget.style.color = 'var(--page-muted)')}
          >
            <X size={14} />
            Limpiar
          </button>
        )}
      </div>

      {/* Tabla */}
      <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="p-6 space-y-3">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="h-12 rounded-lg animate-pulse" style={{ background: 'var(--page-border)' }} />
            ))}
          </div>
        ) : datos.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-3 py-20">
            <Activity size={36} style={{ color: 'var(--page-muted)' }} />
            <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
              {hayFiltros ? 'No hay eventos para los filtros aplicados.' : 'No hay actividad registrada aún.'}
            </p>
          </div>
        ) : (
          <table className="w-full table-dense">
            <thead>
              <tr style={{ borderBottom: '1px solid var(--page-border)', background: 'var(--page-surface)' }}>
                {['Evento', 'Entidad', 'Módulo', 'Operador', 'Fecha'].map(col => (
                  <th key={col} className="text-left uppercase" style={{ color: 'var(--page-muted)' }}>
                    {col}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {datos.map(item => (
                <tr key={item.id}
                  style={{ borderBottom: '1px solid var(--page-border)' }}
                  onMouseEnter={e => (e.currentTarget.style.background = 'var(--page-surface)')}
                  onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                >
                  <td><EventoBadge tipo={item.tipoEvento} /></td>
                  <td>
                    {item.entidadNombre ? (
                      <span className="font-medium" style={{ color: 'var(--page-text)' }}>{item.entidadNombre}</span>
                    ) : item.entidadId ? (
                      <span style={{ color: 'var(--page-muted)' }}>ID {item.entidadId}</span>
                    ) : (
                      <span style={{ color: 'var(--page-muted)' }}>—</span>
                    )}
                  </td>
                  <td>
                    <span className="font-medium px-2 py-0.5 rounded"
                      style={{ fontSize: '0.58rem', background: 'var(--page-border)', color: 'var(--page-muted)' }}>
                      {item.modulo}
                    </span>
                  </td>
                  <td>
                    <span className="font-mono" style={{ color: 'var(--page-muted)' }}>{item.usuario}</span>
                  </td>
                  <td className="whitespace-nowrap" style={{ color: 'var(--page-muted)' }}>
                    {(() => {
                      try { return format(new Date(item.fecha), 'd MMM yyyy, HH:mm', { locale: es }) }
                      catch { return '—' }
                    })()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Paginación */}
      {!loading && total > 0 && (
        <div className="flex items-center justify-between px-6 py-4 flex-shrink-0"
          style={{ borderTop: '1px solid var(--page-border)', background: 'var(--page-surface)' }}>
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
            {total} evento{total !== 1 ? 's' : ''} en total
          </p>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setPagina(p => Math.max(1, p - 1))}
              disabled={pagina === 1}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              style={{ borderColor: 'var(--page-border)', color: 'var(--page-muted)' }}
            >
              <ChevronLeft size={14} />
              Anterior
            </button>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>
              Pág {pagina} / {totalPaginas}
            </span>
            <button
              onClick={() => setPagina(p => Math.min(totalPaginas, p + 1))}
              disabled={pagina >= totalPaginas}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              style={{ borderColor: 'var(--page-border)', color: 'var(--page-muted)' }}
            >
              Siguiente
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
