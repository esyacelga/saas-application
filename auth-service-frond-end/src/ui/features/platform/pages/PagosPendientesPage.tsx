import { useState, useEffect, useCallback } from 'react'
import { ClipboardCheck, ChevronLeft, ChevronRight, Eye, Check, X, RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Loader2 } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/ui/components/PageHeader'
import { ConfirmDialog } from '@/ui/components/ConfirmDialog'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { usePlatformStore } from '@/infrastructure/store/platform/platform.store'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { getApiErrorMessage } from '@/lib/api-error'
import type { PagoPendienteResponse } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'

// ── Constantes ────────────────────────────────────────────────────────────────

const LIMIT = 20
type EstadoFiltro = '' | 'PENDIENTE' | 'APROBADO' | 'RECHAZADO'

// ── Badge de estado ───────────────────────────────────────────────────────────

function EstadoPagoPendienteBadge({ estado }: { estado: PagoPendienteResponse['estado'] }) {
  const map: Record<string, { bg: string; color: string; label: string }> = {
    PENDIENTE:  { bg: 'rgba(234,179,8,0.15)',  color: '#b45309', label: 'PENDIENTE' },
    APROBADO:   { bg: 'rgba(34,197,94,0.15)',  color: '#15803d', label: 'APROBADO' },
    RECHAZADO:  { bg: 'rgba(239,68,68,0.15)',  color: '#b91c1c', label: 'RECHAZADO' },
    PAGADO:     { bg: 'rgba(59,130,246,0.15)', color: '#1d4ed8', label: 'PAGADO' },
    FALLIDO:    { bg: 'rgba(107,114,128,0.15)',color: '#374151', label: 'FALLIDO' },
  }
  const s = map[estado] ?? map.PENDIENTE
  return (
    <span
      className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold"
      style={{ background: s.bg, color: s.color }}
    >
      {s.label}
    </span>
  )
}

// ── Skeleton rows ─────────────────────────────────────────────────────────────

function SkeletonRows() {
  return (
    <>
      {[1, 2, 3].map(i => (
        <tr key={i}>
          {Array.from({ length: 7 }).map((_, j) => (
            <td key={j} className="px-3 py-3">
              <div className="h-4 rounded animate-pulse" style={{ background: 'var(--page-border)', width: j === 6 ? '80px' : '100%' }} />
            </td>
          ))}
        </tr>
      ))}
    </>
  )
}

// ── Modal: ver detalle ─────────────────────────────────────────────────────────

function DetalleModal({ pago, onClose, planNombre }: { pago: PagoPendienteResponse; onClose: () => void; planNombre: string }) {
  const { t } = useTranslation()
  const rows: [string, string | null | number | boolean][] = [
    [t('pagosPendientes.detalle.id'), pago.id],
    [t('pagosPendientes.detalle.compania'), pago.nombreCompania ?? `#${pago.idCompania}`],
    [t('pagosPendientes.detalle.plan'), planNombre],
    [t('pagosPendientes.detalle.monto'), `${pago.monto.toFixed(2)} ${pago.moneda}`],
    [t('pagosPendientes.detalle.fechaReporte'), pago.fechaReporte?.slice(0, 10) ?? '—'],
    [t('pagosPendientes.detalle.fechaTransferencia'), pago.fechaTransferencia ?? '—'],
    [t('pagosPendientes.detalle.referencia'), pago.referencia ?? '—'],
    [t('pagosPendientes.detalle.bancoOrigen'), pago.bancoOrigen ?? '—'],
    [t('pagosPendientes.detalle.estado'), pago.estado],
    [t('pagosPendientes.detalle.motivoRechazo'), pago.motivoRechazo ?? '—'],
    [t('pagosPendientes.detalle.fechaAprobacion'), pago.fechaAprobacion?.slice(0, 10) ?? '—'],
  ]
  return (
    <Dialog open onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('pagosPendientes.detalle.title', { id: pago.id })}</DialogTitle>
        </DialogHeader>
        <div className="space-y-2 text-sm">
          {rows.map(([label, val]) => (
            <div key={label} className="flex justify-between gap-4">
              <span style={{ color: 'var(--page-muted)' }}>{label}</span>
              <span className="text-right font-medium" style={{ color: 'var(--page-text)' }}>{String(val)}</span>
            </div>
          ))}
          {pago.comprobanteUrl && (
            <div className="pt-2">
              <a
                href={pago.comprobanteUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-xs underline"
                style={{ color: 'var(--color-warning, #f97316)' }}
              >
                {t('pagosPendientes.detalle.verComprobante')}
              </a>
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ── Modal: rechazar pago ───────────────────────────────────────────────────────

const rechazarSchema = z.object({
  motivo: z.string().min(10, 'Mínimo 10 caracteres').max(500, 'Máximo 500 caracteres'),
})
type RechazarForm = z.infer<typeof rechazarSchema>

function RechazarPagoModal({
  pago,
  onClose,
  onRechazado,
}: {
  pago: PagoPendienteResponse
  onClose: () => void
  onRechazado: () => void
}) {
  const { t } = useTranslation()
  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm<RechazarForm>({
    resolver: zodResolver(rechazarSchema) as never,
  })

  const motivo = watch('motivo', '')

  const onSubmit = async (values: RechazarForm) => {
    try {
      await platformRepository.rechazarPagoPendiente(pago.id, values.motivo)
      toast.success(t('pagosPendientes.rechazar.exito', { id: pago.id }))
      onRechazado()
      onClose()
    } catch (err) {
      toast.error(getApiErrorMessage(err))
    }
  }

  return (
    <Dialog open onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('pagosPendientes.rechazar.title', { id: pago.id })}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
          <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
            {t('pagosPendientes.rechazar.descripcion', {
              compania: pago.nombreCompania ?? `#${pago.idCompania}`,
            })}
          </p>
          <div>
            <textarea
              {...register('motivo')}
              rows={4}
              placeholder={t('pagosPendientes.rechazar.placeholder')}
              className="w-full rounded-lg border px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-orange-500"
              style={{
                background: 'var(--input-bg)',
                borderColor: 'var(--page-border)',
                color: 'var(--page-text)',
              }}
            />
            <div className="flex justify-between mt-1">
              {errors.motivo ? (
                <p className="text-xs" style={{ color: '#ef4444' }}>{errors.motivo.message}</p>
              ) : (
                <span />
              )}
              <span className="text-xs" style={{ color: 'var(--page-muted)' }}>
                {(motivo ?? '').length}/500
              </span>
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t('pagosPendientes.rechazar.cancelar')}
            </Button>
            <Button type="submit" variant="destructive" disabled={isSubmitting}>
              {isSubmitting ? (
                <><Loader2 size={14} className="animate-spin mr-2" />{t('pagosPendientes.rechazar.enviando')}</>
              ) : (
                t('pagosPendientes.rechazar.confirmar')
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// ── Página principal ──────────────────────────────────────────────────────────

export function PagosPendientesPage() {
  const { t } = useTranslation()
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const canWrite = user?.rol_plataforma === 'super_admin' || user?.rol_plataforma === 'soporte'

  const { planes } = usePlatformStore()

  const [datos, setDatos] = useState<PagoPendienteResponse[]>([])
  const [total, setTotal] = useState(0)
  const [pagina, setPagina] = useState(1)
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoFiltro>('')
  const [loading, setLoading] = useState(true)
  const [errorCarga, setErrorCarga] = useState(false)

  // Modales
  const [detalle, setDetalle] = useState<PagoPendienteResponse | null>(null)
  const [aprobarPago, setAprobarPago] = useState<PagoPendienteResponse | null>(null)
  const [rechazarPago, setRechazarPago] = useState<PagoPendienteResponse | null>(null)

  const totalPaginas = Math.max(1, Math.ceil(total / LIMIT))

  const cargar = useCallback(async () => {
    setLoading(true)
    setErrorCarga(false)
    try {
      const resp = await platformRepository.getPagosPendientesRoot({
        estado: estadoFiltro || undefined,
        pagina,
        limit: LIMIT,
      })
      setDatos(resp.datos)
      setTotal(resp.total)
    } catch {
      setErrorCarga(true)
    } finally {
      setLoading(false)
    }
  }, [estadoFiltro, pagina])

  useEffect(() => { cargar() }, [cargar])

  const handleAprobar = async () => {
    if (!aprobarPago) return
    try {
      await platformRepository.aprobarPagoPendiente(aprobarPago.id)
      toast.success(t('pagosPendientes.aprobar.exito', { id: aprobarPago.id }))
      setAprobarPago(null)
      cargar()
    } catch (err) {
      toast.error(getApiErrorMessage(err))
    }
  }

  const getPlanNombre = (idPlan: number) =>
    planes.find(p => p.id === idPlan)?.nombre ?? t('pagosPendientes.planDesconocido', { id: idPlan })

  const hayFiltros = Boolean(estadoFiltro)

  const inputClass = [
    'border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-500 transition',
    'bg-[var(--input-bg)] border-[var(--page-border)] text-[var(--page-text)]',
  ].join(' ')

  return (
    <div className="flex flex-col h-full">
      <PageHeader
        title={t('pagosPendientes.title')}
        description={t('pagosPendientes.description')}
      />

      {/* ── Filtros ── */}
      <div
        className="px-6 py-3 flex flex-wrap items-end gap-3 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--page-border)' }}
      >
        <div className="space-y-1">
          <label className="block text-xs font-medium" style={{ color: 'var(--page-muted)' }}>
            {t('pagosPendientes.filtroEstado')}
          </label>
          <select
            value={estadoFiltro}
            onChange={e => { setEstadoFiltro(e.target.value as EstadoFiltro); setPagina(1) }}
            className={`${inputClass} pr-8 min-w-[160px]`}
            style={{ background: 'var(--input-bg)' }}
          >
            <option value="">{t('pagosPendientes.filtroTodos')}</option>
            <option value="PENDIENTE">PENDIENTE</option>
            <option value="APROBADO">APROBADO</option>
            <option value="RECHAZADO">RECHAZADO</option>
          </select>
        </div>

        <Button
          size="sm"
          variant="outline"
          onClick={() => { setPagina(1); cargar() }}
          className="flex items-center gap-1.5"
        >
          <RefreshCw size={13} />
          {t('pagosPendientes.aplicarFiltros')}
        </Button>
      </div>

      {/* ── Tabla ── */}
      <div className="flex-1 overflow-auto">
        {errorCarga ? (
          <div className="flex flex-col items-center justify-center h-64 gap-4">
            <p className="text-sm" style={{ color: 'var(--page-muted)' }}>{t('pagosPendientes.errorCarga')}</p>
            <Button size="sm" variant="outline" onClick={cargar}>
              <RefreshCw size={13} className="mr-1.5" />
              {t('pagosPendientes.reintentar')}
            </Button>
          </div>
        ) : (
          <table className="w-full text-xs min-w-[800px]">
            <thead>
              <tr style={{ borderBottom: '1px solid var(--page-border)', background: 'var(--page-surface)' }}>
                {[
                  t('pagosPendientes.colId'),
                  t('pagosPendientes.colCompania'),
                  t('pagosPendientes.colPlan'),
                  t('pagosPendientes.colMonto'),
                  t('pagosPendientes.colFechaReporte'),
                  t('pagosPendientes.colEstado'),
                  t('pagosPendientes.colAcciones'),
                ].map(col => (
                  <th
                    key={col}
                    className="text-left px-3 py-2.5 uppercase tracking-wide font-medium"
                    style={{ color: 'var(--page-muted)', fontSize: '0.65rem' }}
                  >
                    {col}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <SkeletonRows />
              ) : datos.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-3 py-16 text-center text-sm" style={{ color: 'var(--page-muted)' }}>
                    <ClipboardCheck size={32} className="mx-auto mb-3" style={{ color: 'var(--page-border)' }} />
                    {hayFiltros ? t('pagosPendientes.emptyFiltered') : t('pagosPendientes.empty')}
                  </td>
                </tr>
              ) : (
                datos.map(pago => (
                  <tr
                    key={pago.id}
                    style={{ borderBottom: '1px solid var(--page-border)' }}
                    onMouseEnter={e => (e.currentTarget.style.background = 'var(--page-surface)')}
                    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                  >
                    <td className="px-3 py-2.5 font-mono" style={{ color: 'var(--page-muted)' }}>
                      #{pago.id}
                    </td>
                    <td className="px-3 py-2.5" style={{ color: 'var(--page-text)' }}>
                      {pago.nombreCompania ?? `#${pago.idCompania}`}
                    </td>
                    <td className="px-3 py-2.5" style={{ color: 'var(--page-text)' }}>
                      {getPlanNombre(pago.idPlanDestino)}
                    </td>
                    <td className="px-3 py-2.5 font-medium" style={{ color: 'var(--page-text)' }}>
                      {pago.monto.toFixed(2)} {pago.moneda}
                    </td>
                    <td className="px-3 py-2.5" style={{ color: 'var(--page-muted)' }}>
                      {pago.fechaReporte?.slice(0, 10)}
                    </td>
                    <td className="px-3 py-2.5">
                      <EstadoPagoPendienteBadge estado={pago.estado} />
                    </td>
                    <td className="px-3 py-2.5">
                      <div className="flex items-center gap-1.5">
                        {/* Ver detalle siempre disponible */}
                        <button
                          title={t('pagosPendientes.btnVer')}
                          onClick={() => setDetalle(pago)}
                          className="flex items-center justify-center w-7 h-7 rounded transition-colors"
                          style={{ color: 'var(--page-muted)' }}
                          onMouseEnter={e => (e.currentTarget.style.color = 'var(--page-text)')}
                          onMouseLeave={e => (e.currentTarget.style.color = 'var(--page-muted)')}
                        >
                          <Eye size={13} />
                        </button>

                        {/* Aprobar / Rechazar — solo PENDIENTE y canWrite */}
                        {pago.estado === 'PENDIENTE' && canWrite && (
                          <>
                            <button
                              title={t('pagosPendientes.btnAprobar')}
                              onClick={() => setAprobarPago(pago)}
                              className="flex items-center justify-center w-7 h-7 rounded transition-colors"
                              style={{ color: '#16a34a' }}
                              onMouseEnter={e => (e.currentTarget.style.background = 'rgba(22,163,74,0.1)')}
                              onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                            >
                              <Check size={13} />
                            </button>
                            <button
                              title={t('pagosPendientes.btnRechazar')}
                              onClick={() => setRechazarPago(pago)}
                              className="flex items-center justify-center w-7 h-7 rounded transition-colors"
                              style={{ color: '#dc2626' }}
                              onMouseEnter={e => (e.currentTarget.style.background = 'rgba(220,38,38,0.1)')}
                              onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                            >
                              <X size={13} />
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        )}
      </div>

      {/* ── Paginación ── */}
      {!loading && !errorCarga && total > 0 && (
        <div
          className="flex items-center justify-between px-6 py-3 flex-shrink-0"
          style={{ borderTop: '1px solid var(--page-border)', background: 'var(--page-surface)' }}
        >
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
            {total} registros
          </p>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setPagina(p => Math.max(1, p - 1))}
              disabled={pagina === 1}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              style={{ borderColor: 'var(--page-border)', color: 'var(--page-muted)' }}
            >
              <ChevronLeft size={14} />
              {t('pagosPendientes.anterior')}
            </button>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>
              {t('pagosPendientes.paginaX', { pagina, total: totalPaginas })}
            </span>
            <button
              onClick={() => setPagina(p => Math.min(totalPaginas, p + 1))}
              disabled={pagina >= totalPaginas}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              style={{ borderColor: 'var(--page-border)', color: 'var(--page-muted)' }}
            >
              {t('pagosPendientes.siguiente')}
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      )}

      {/* ── Modales ── */}

      {/* Detalle */}
      {detalle && (
        <DetalleModal
          pago={detalle}
          planNombre={getPlanNombre(detalle.idPlanDestino)}
          onClose={() => setDetalle(null)}
        />
      )}

      {/* Aprobar */}
      <ConfirmDialog
        open={aprobarPago !== null}
        title={t('pagosPendientes.aprobar.title')}
        description={t('pagosPendientes.aprobar.descripcion', {
          id: aprobarPago?.id ?? '',
          compania: aprobarPago?.nombreCompania ?? `#${aprobarPago?.idCompania ?? ''}`,
        })}
        destructive={false}
        onConfirm={handleAprobar}
        onCancel={() => setAprobarPago(null)}
      />

      {/* Rechazar */}
      {rechazarPago && (
        <RechazarPagoModal
          pago={rechazarPago}
          onClose={() => setRechazarPago(null)}
          onRechazado={cargar}
        />
      )}
    </div>
  )
}
