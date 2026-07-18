import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, CheckCircle, Clock, ChevronDown, ChevronUp, Receipt, AlertTriangle } from 'lucide-react'
import { PulseBackground } from '@/ui/components/PulseBackground'
import { coreRepository, type MembresiaHistorialItem } from '@/infrastructure/http/CoreHttpRepository'
import { getApiErrorMessage } from '@/lib/api-error'

export function HistorialPagosMembresiaPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const [membresias, setMembresias] = useState<MembresiaHistorialItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchMembresias = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await coreRepository.misMembresias()
      const sorted = [...data].sort(
        (a, b) => b.fecha_inicio.localeCompare(a.fecha_inicio),
      )
      setMembresias(sorted)
    } catch (err) {
      setError(getApiErrorMessage(err, t('pagoHistorial.error.title')))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchMembresias()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="pb-24">
      <PulseBackground />
      <div className="flex items-center gap-3 px-4 pt-8 pb-4 bg-accent-900/20 border-b border-accent-800/40">
        <button
          onClick={() => navigate(-1)}
          aria-label={t('pagoHistorial.back')}
          className="flex items-center justify-center w-10 h-10 rounded-full bg-slate-800 text-slate-400 hover:text-slate-50 transition-colors"
        >
          <ArrowLeft size={18} />
        </button>
        <h1 className="text-xl font-bold text-slate-50">{t('pagoHistorial.title')}</h1>
      </div>

      {loading && <LoadingSkeleton />}

      {!loading && error && (
        <ErrorState message={error} onRetry={fetchMembresias} />
      )}

      {!loading && !error && membresias.length === 0 && <EmptyState />}

      {!loading && !error && membresias.length > 0 && (
        <div className="space-y-3 px-4 pb-24 pt-4">
          {membresias.map((m) => (
            <VentaCard key={m.id} membresia={m} />
          ))}
        </div>
      )}
    </div>
  )
}

// ── Sub-components ──────────────────────────────────────────────────────────

function PagoBadge({ estado_pago }: { estado_pago: MembresiaHistorialItem['estado_pago'] }) {
  const { t } = useTranslation()
  const isPagado = estado_pago === 'PAGADO'
  const cls = isPagado
    ? 'bg-emerald-900/40 border-emerald-700 text-emerald-300'
    : 'bg-amber-900/40 border-amber-700 text-amber-300'
  const label = t(`pagoHistorial.estadoPago.${estado_pago}`)
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold border ${cls}`}
      aria-label={t('pagoHistorial.badge.ariaLabel', { estado: label })}
    >
      {isPagado
        ? <CheckCircle size={11} />
        : <Clock size={11} />}
      {label}
    </span>
  )
}

function EstadoBadgeMini({
  estado,
  eliminado,
}: {
  estado: MembresiaHistorialItem['estado']
  eliminado: boolean
}) {
  const { t } = useTranslation()
  const clsMap: Record<string, string> = {
    activa: 'text-emerald-400',
    vencida: 'text-red-400',
    congelada: 'text-sky-400',
    anulada: 'text-slate-500',
  }
  const cls = clsMap[estado] ?? 'text-slate-500'
  return (
    <span
      className={`text-[10px] font-semibold uppercase tracking-wide ${cls} ${eliminado ? 'opacity-60' : ''}`}
    >
      {t(`pagoHistorial.estadoMembresia.${estado}`, { defaultValue: estado })}
    </span>
  )
}

function VentaCard({ membresia: m }: { membresia: MembresiaHistorialItem }) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(false)

  const nombrePlan = m.tipo_nombre || `Plan #${m.id_tipo_membresia}`
  const fechaInicio = formatDate(m.fecha_inicio)
  const fechaFin = formatDate(m.fecha_fin)

  return (
    <div className="rounded-2xl bg-slate-800 border border-slate-700 p-4">
      {/* Fila superior */}
      <div className="flex justify-between items-start gap-2">
        <div>
          <p className="text-sm font-semibold text-slate-50">{nombrePlan}</p>
          <p className="text-xs text-slate-400 mt-0.5">{fechaInicio}</p>
        </div>
        <PagoBadge estado_pago={m.estado_pago} />
      </div>

      {/* Fila inferior */}
      <div className="flex gap-2 items-center mt-2">
        <EstadoBadgeMini estado={m.estado} eliminado={m.eliminado} />
        <span className="text-slate-600 text-xs">·</span>
        <span className="text-xs text-slate-500">
          {fechaInicio} → {fechaFin}
        </span>
      </div>

      {/* Toggle accordion */}
      <button
        onClick={() => setExpanded((v) => !v)}
        className="flex items-center gap-1 mt-3 text-xs text-slate-500 hover:text-slate-300 transition-colors min-h-[44px] -ml-1 px-1"
        aria-expanded={expanded}
      >
        {expanded
          ? <><ChevronUp size={14} />{t('pagoHistorial.card.collapse')}</>
          : <><ChevronDown size={14} />{t('pagoHistorial.card.expand')}</>}
      </button>

      {/* Detalle expandido */}
      {expanded && (
        <div className="border-t border-slate-700 pt-3 mt-1 space-y-2">
          <DetailRow label={t('pagoHistorial.card.labels.precioPagado')} value={formatCurrency(m.precio_pagado)} />

          {m.monto_pagado > 0 && (
            <DetailRow label={t('pagoHistorial.card.labels.montoPagado')} value={formatCurrency(m.monto_pagado)} />
          )}

          {m.saldo_pendiente > 0 && (
            <DetailRow
              label={t('pagoHistorial.card.labels.saldoPendiente')}
              value={formatCurrency(m.saldo_pendiente)}
              highlight
            />
          )}

          {m.descuento_aplicado > 0 && (
            <DetailRow
              label={t('pagoHistorial.card.labels.descuento')}
              value={formatCurrency(m.descuento_aplicado)}
            />
          )}

          {m.modo_control === 'accesos' && m.dias_acceso_total != null && (
            <DetailRow
              label={t('pagoHistorial.card.labels.diasAcceso')}
              value={`${m.dias_acceso_usados ?? 0} / ${m.dias_acceso_total}`}
            />
          )}

          {m.motivo_eliminacion != null && (
            <p className="text-xs text-slate-400 italic mt-1">
              {t('pagoHistorial.card.labels.motivo')}:{' '}
              {t(`pagoHistorial.motivoEliminacion.${m.motivo_eliminacion}`, {
                defaultValue: m.motivo_eliminacion,
              })}
            </p>
          )}
        </div>
      )}
    </div>
  )
}

function DetailRow({
  label,
  value,
  highlight,
}: {
  label: string
  value: string
  highlight?: boolean
}) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-sm text-slate-400">{label}</span>
      <span className={`text-sm font-medium ${highlight ? 'text-amber-300' : 'text-slate-300'}`}>
        {value}
      </span>
    </div>
  )
}

function EmptyState() {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center gap-4 rounded-2xl bg-slate-800 border border-slate-700 p-8 text-center mx-4 mt-4">
      <Receipt size={40} className="text-slate-500" />
      <div>
        <p className="text-sm font-semibold text-slate-300">{t('pagoHistorial.empty.title')}</p>
        <p className="text-xs text-slate-500 mt-1">{t('pagoHistorial.empty.description')}</p>
      </div>
    </div>
  )
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center gap-4 rounded-2xl bg-slate-800 border border-slate-700 p-8 text-center mx-4 mt-4">
      <AlertTriangle size={40} className="text-red-400" />
      <div>
        <p className="text-sm font-semibold text-slate-300">{t('pagoHistorial.error.title')}</p>
        {message && <p className="text-xs text-slate-500 mt-1">{message}</p>}
      </div>
      <button
        onClick={onRetry}
        className="rounded-lg bg-accent-600 px-4 py-2 text-xs font-semibold text-white hover:bg-accent-500 transition-colors"
      >
        {t('pagoHistorial.error.retry')}
      </button>
    </div>
  )
}

function LoadingSkeleton() {
  const { t } = useTranslation()
  return (
    <div
      role="status"
      aria-label={t('pagoHistorial.loading.aria')}
      className="space-y-3 px-4 pt-4 motion-safe:animate-pulse"
    >
      {[1, 2, 3].map((i) => (
        <div key={i} className="rounded-2xl bg-slate-800 h-28 border border-slate-700" />
      ))}
    </div>
  )
}

// ── Utilities ────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  return new Date(iso + 'T00:00:00').toLocaleDateString('es', {
    day: '2-digit', month: 'short', year: 'numeric',
  })
}

function formatCurrency(value: number) {
  return `$${value.toLocaleString('es-EC', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
