import { useState, useEffect, useCallback } from 'react'
import { AlertTriangle, XCircle, RefreshCw, Check, AlertOctagon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { PageHeader } from '@/ui/components/PageHeader'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { EstadoPlanBadge } from '@/ui/features/platform/components/EstadoPlanBadge'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { ReportarPagoModal } from '@/ui/features/admin/components/ReportarPagoModal'
import type { JwtPayloadStaff } from '@/domain/auth/entities/User.entity'
import type { CompaniaPlan, Plan, UsoLimitesResponse, PagoPendienteResponse } from '@/domain/platform/entities/Plan.entity'
import { cn } from '@/lib/utils'

// ── Sub-componentes de layout ─────────────────────────────────────────────────

function SectionCard({
  title,
  children,
  className,
}: {
  title: string
  children: React.ReactNode
  className?: string
}) {
  return (
    <div
      className={cn('rounded-xl p-5', className)}
      style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}
    >
      <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--page-text)' }}>
        {title}
      </h3>
      {children}
    </div>
  )
}

// ── Banners ───────────────────────────────────────────────────────────────────

function BannerPagoEnRevision({ mensaje }: { mensaje: string }) {
  return (
    <div
      className="flex items-start gap-3 rounded-xl px-4 py-3"
      style={{
        background: 'rgba(234,179,8,0.1)',
        border: '1px solid rgba(234,179,8,0.4)',
      }}
    >
      <AlertTriangle size={16} className="mt-0.5 flex-shrink-0" style={{ color: '#eab308' }} />
      <p className="text-sm" style={{ color: 'var(--page-text)' }}>
        {mensaje}
      </p>
    </div>
  )
}

function BannerTrialVencido({ mensaje, detalle }: { mensaje: string; detalle: string }) {
  return (
    <div
      className="flex items-start gap-3 rounded-xl px-4 py-3"
      style={{
        background: 'rgba(239,68,68,0.1)',
        border: '1px solid rgba(239,68,68,0.45)',
      }}
    >
      <AlertOctagon size={16} className="mt-0.5 flex-shrink-0" style={{ color: '#ef4444' }} />
      <div>
        <p className="text-sm font-semibold" style={{ color: '#ef4444' }}>{mensaje}</p>
        <p className="text-xs mt-1" style={{ color: 'var(--page-text)' }}>{detalle}</p>
      </div>
    </div>
  )
}

function BannerPagoRechazado({ mensaje, motivo }: { mensaje: string; motivo: string | null }) {
  const { t } = useTranslation()
  return (
    <div
      className="flex items-start gap-3 rounded-xl px-4 py-3"
      style={{
        background: 'rgba(239,68,68,0.08)',
        border: '1px solid rgba(239,68,68,0.35)',
      }}
    >
      <XCircle size={16} className="mt-0.5 flex-shrink-0" style={{ color: '#ef4444' }} />
      <div>
        <p className="text-sm" style={{ color: 'var(--page-text)' }}>{mensaje}</p>
        {motivo && (
          <p className="text-xs mt-1" style={{ color: '#ef4444' }}>
            {t('miSuscripcion.motivoRechazo', { motivo })}
          </p>
        )}
      </div>
    </div>
  )
}

// ── Barras de uso ─────────────────────────────────────────────────────────────

function UsageBar({
  label,
  actual,
  maximo,
  ilimitadoLabel,
}: {
  label: string
  actual: number
  maximo: number | null
  ilimitadoLabel: string
}) {
  if (maximo === null) {
    return (
      <div className="space-y-1">
        <div className="flex justify-between text-xs" style={{ color: 'var(--page-muted)' }}>
          <span>{label}</span>
          <span style={{ color: '#10b981' }}>{ilimitadoLabel}</span>
        </div>
        <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
          {actual}
        </p>
      </div>
    )
  }

  const porcentaje = maximo > 0 ? Math.min(100, Math.round((actual / maximo) * 100)) : 0
  const barColor =
    porcentaje >= 100 ? '#ef4444' : porcentaje >= 80 ? '#f59e0b' : '#22c55e'

  return (
    <div className="space-y-1.5">
      <div className="flex justify-between text-xs" style={{ color: 'var(--page-muted)' }}>
        <span>{label}</span>
        <span style={{ color: 'var(--page-text)' }}>
          {actual} / {maximo}
          <span className="ml-1">({porcentaje}%)</span>
        </span>
      </div>
      <div
        className="w-full rounded-full overflow-hidden"
        style={{ height: '8px', background: 'var(--page-border)' }}
      >
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{ width: `${porcentaje}%`, background: barColor }}
        />
      </div>
    </div>
  )
}

// ── PlanCard (inline sub-componente) ─────────────────────────────────────────

function PlanCard({
  plan,
  esPlanActual,
  hayPagoPendiente,
  onReportarPago,
}: {
  plan: Plan
  esPlanActual: boolean
  hayPagoPendiente: boolean
  onReportarPago: (idPlan: number) => void
}) {
  const { t } = useTranslation()
  const esGratis = plan.precioMensual === 0

  return (
    <div
      className="relative rounded-xl p-4 flex flex-col gap-3"
      style={{
        background: 'var(--page-surface)',
        border: esPlanActual
          ? '2px solid var(--color-warning, #f97316)'
          : '1px solid var(--page-border)',
      }}
    >
      {/* Tag "Plan activo" sobrepuesto */}
      {esPlanActual && (
        <div
          className="absolute -top-2.5 left-4 flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold"
          style={{ background: 'var(--color-warning, #f97316)', color: '#fff' }}
        >
          <Check size={10} />
          {t('miSuscripcion.planActivo')}
        </div>
      )}

      {/* Cabecera */}
      <div className="flex items-start justify-between gap-2 pt-1">
        <div>
          <span className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
            {plan.nombre}
          </span>
          <span
            className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-xs font-bold uppercase"
            style={{
              background: 'rgba(249,115,22,0.12)',
              color: 'var(--color-warning, #f97316)',
            }}
          >
            {plan.codigo}
          </span>
        </div>
        <div className="text-right flex-shrink-0">
          {esGratis ? (
            <span className="text-lg font-bold" style={{ color: '#22c55e' }}>Gratis</span>
          ) : (
            <>
              <span className="text-lg font-bold" style={{ color: 'var(--page-text)' }}>
                ${plan.precioMensual.toFixed(2)}
              </span>
              <span className="text-xs" style={{ color: 'var(--page-muted)' }}>/mes</span>
            </>
          )}
        </div>
      </div>

      {/* Descripción */}
      {plan.descripcion && (
        <p className="text-xs leading-relaxed" style={{ color: 'var(--page-muted)' }}>
          {plan.descripcion}
        </p>
      )}

      {/* Características (top 5) */}
      {plan.caracteristicas.length > 0 && (
        <ul className="space-y-1">
          {plan.caracteristicas.slice(0, 5).map(c => (
            <li key={c.id} className="flex items-center gap-1.5 text-xs" style={{ color: 'var(--page-muted)' }}>
              <Check size={11} style={{ color: '#22c55e', flexShrink: 0 }} />
              {c.nombre}
            </li>
          ))}
        </ul>
      )}

      {/* CTA */}
      <div className="mt-auto pt-1">
        {esPlanActual ? (
          <button
            disabled
            className="w-full py-2 rounded-lg text-xs font-semibold opacity-50 cursor-not-allowed"
            style={{
              background: 'var(--page-border)',
              color: 'var(--page-muted)',
            }}
          >
            {t('miSuscripcion.planActual')}
          </button>
        ) : (
          <button
            disabled={hayPagoPendiente || esGratis}
            onClick={() => onReportarPago(plan.id)}
            className="w-full py-2 rounded-lg text-xs font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            style={{
              background: (hayPagoPendiente || esGratis) ? 'var(--page-border)' : 'var(--color-warning, #f97316)',
              color: (hayPagoPendiente || esGratis) ? 'var(--page-muted)' : '#fff',
            }}
          >
            {hayPagoPendiente ? t('miSuscripcion.pagoEnRevision') : t('miSuscripcion.upgrade')}
          </button>
        )}
      </div>
    </div>
  )
}

// ── Skeleton ──────────────────────────────────────────────────────────────────

function PageSkeleton() {
  const heights = ['h-28', 'h-40', 'h-24', 'h-48']
  return (
    <div className="space-y-4 p-4">
      {heights.map((h, i) => (
        <div
          key={i}
          className={cn('rounded-xl animate-pulse', h)}
          style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}
        />
      ))}
    </div>
  )
}

// ── Error global ──────────────────────────────────────────────────────────────

function ErrorGlobal({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center h-64 gap-4">
      <XCircle size={32} style={{ color: '#ef4444' }} />
      <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
        {t('miSuscripcion.errorCarga')}
      </p>
      <button
        onClick={onRetry}
        className="flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold"
        style={{ background: 'var(--color-warning, #f97316)', color: '#fff' }}
      >
        <RefreshCw size={13} />
        {t('miSuscripcion.reintentar')}
      </button>
    </div>
  )
}

// ── Estado de datos ───────────────────────────────────────────────────────────

interface PageData {
  uso: UsoLimitesResponse | null
  suscripcion: CompaniaPlan | null
  historial: CompaniaPlan[]
  planes: Plan[]
  pagosPendientes: PagoPendienteResponse[]
}

// ── Página principal ──────────────────────────────────────────────────────────

export function MiSuscripcionPage() {
  const { t } = useTranslation()
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'staff' ? (rawUser as JwtPayloadStaff) : null

  if (!user) return null

  const idCompania = user.id_compania

  const [loading, setLoading] = useState(true)
  const [errorGlobal, setErrorGlobal] = useState(false)
  const [data, setData] = useState<PageData>({
    uso: null,
    suscripcion: null,
    historial: [],
    planes: [],
    pagosPendientes: [],
  })

  const cargar = useCallback(async () => {
    setLoading(true)
    setErrorGlobal(false)

    const [rUso, rSus, rHist, rPlanes, rPagos] = await Promise.allSettled([
      platformRepository.getUsoLimites(idCompania),
      platformRepository.getSuscripcionActiva(idCompania),
      platformRepository.getHistorialSuscripcion(idCompania),
      platformRepository.getPlanes(),
      platformRepository.getPagosPendientesOwner(idCompania, 10),
    ])

    const todoFallo =
      rUso.status === 'rejected' &&
      rSus.status === 'rejected' &&
      rHist.status === 'rejected' &&
      rPlanes.status === 'rejected' &&
      rPagos.status === 'rejected'

    if (todoFallo) {
      setErrorGlobal(true)
      setLoading(false)
      return
    }

    setData({
      uso: rUso.status === 'fulfilled' ? rUso.value : null,
      suscripcion: rSus.status === 'fulfilled' ? rSus.value : null,
      historial: rHist.status === 'fulfilled' ? rHist.value : [],
      planes: rPlanes.status === 'fulfilled' ? rPlanes.value : [],
      pagosPendientes: rPagos.status === 'fulfilled' ? rPagos.value : [],
    })
    setLoading(false)
  }, [idCompania])

  useEffect(() => { cargar() }, [cargar])

  // ── Estado modal reportar pago ───────────────────────────────────────────────
  const [modalReportar, setModalReportar] = useState<{ open: boolean; idPlanDestinoInicial: number | null }>({
    open: false,
    idPlanDestinoInicial: null,
  })

  const abrirModalReportar = (idPlan: number | null) =>
    setModalReportar({ open: true, idPlanDestinoInicial: idPlan })

  const cerrarModalReportar = () =>
    setModalReportar(prev => ({ ...prev, open: false }))

  // ── Derivaciones ────────────────────────────────────────────────────────────

  const { uso, suscripcion, historial, planes, pagosPendientes } = data

  const hayPagoPendiente = pagosPendientes.some(p => p.estado === 'PENDIENTE')

  // Último pago rechazado: el más reciente por fecha_reporte que tenga estado RECHAZADO y no haya PENDIENTE
  const ultimoPagoRechazado =
    !hayPagoPendiente
      ? [...pagosPendientes]
          .sort((a, b) => new Date(b.fechaReporte).getTime() - new Date(a.fechaReporte).getTime())
          .find(p => p.estado === 'RECHAZADO') ?? null
      : null

  // Plan activo en la lista de planes disponibles
  const planActualId = suscripcion?.idPlan ?? null
  const planActualObj = planActualId !== null ? planes.find(p => p.id === planActualId) : null

  // Suscripcion activa info
  const esTrial = planActualObj?.codigo === 'TRIAL' || uso?.planCodigo === 'TRIAL'
  const esFree = planActualObj?.codigo === 'FREE' || uso?.planCodigo === 'FREE'
  const diasRestantes = uso?.diasRestantes ?? suscripcion?.diasRestantes ?? 0
  const trialVencido = esTrial && diasRestantes <= 0
  const trialUrgente = esTrial && diasRestantes > 0 && diasRestantes <= 7

  // Filtra Trial del grid de planes si ya es el plan actual (usuario ya lo usa)
  const planesMostrar = planes.filter(p => {
    // Ocultar TRIAL si el usuario ya está en TRIAL (ya lo usó o lo está usando)
    if (p.codigo === 'TRIAL' && esTrial) return false
    return true
  })

  if (loading) return <PageSkeleton />
  if (errorGlobal) return <ErrorGlobal onRetry={cargar} />

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <PageHeader
        title={t('miSuscripcion.title')}
        description={t('miSuscripcion.description')}
      />

      <div className="flex-1 overflow-auto p-4 space-y-4">

        {/* ── Banners condicionales ── */}
        {trialVencido && (
          <BannerTrialVencido
            mensaje={t('miSuscripcion.trialVencido')}
            detalle={t('miSuscripcion.trialVencidoDetalle')}
          />
        )}
        {hayPagoPendiente && (
          <BannerPagoEnRevision mensaje={t('miSuscripcion.bannerRevision')} />
        )}
        {!hayPagoPendiente && ultimoPagoRechazado && (
          <BannerPagoRechazado
            mensaje={t('miSuscripcion.bannerRechazado')}
            motivo={ultimoPagoRechazado.motivoRechazo}
          />
        )}

        {/* ── Plan actual ── */}
        <SectionCard title={t('miSuscripcion.sectionPlan')}>
          {!suscripcion ? (
            <p className="text-sm" style={{ color: 'var(--page-muted)' }}>—</p>
          ) : (
            <div className="space-y-3">
              {/* Nombre + estado */}
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-base font-semibold" style={{ color: 'var(--page-text)' }}>
                  {planActualObj?.nombre ?? `Plan #${suscripcion.idPlan}`}
                </span>
                <EstadoPlanBadge estado={suscripcion.estado} />
                {planActualObj && (
                  <span
                    className="inline-flex items-center px-2 py-0.5 rounded text-xs font-bold uppercase"
                    style={{
                      background: 'rgba(249,115,22,0.12)',
                      color: 'var(--color-warning, #f97316)',
                    }}
                  >
                    {planActualObj.codigo}
                  </span>
                )}
              </div>

              {/* Precio */}
              <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
                {planActualObj
                  ? planActualObj.precioMensual === 0
                    ? 'Gratis'
                    : `$${planActualObj.precioMensual.toFixed(2)}/mes`
                  : '—'}
              </p>

              {/* Fecha fin + días restantes */}
              <div className="flex flex-wrap gap-4 text-sm">
                {suscripcion.fechaFin && !esFree && (
                  <div>
                    <span style={{ color: 'var(--page-muted)' }}>{t('miSuscripcion.fechaFin')}: </span>
                    <span style={{ color: 'var(--page-text)' }}>
                      {new Date(suscripcion.fechaFin).toLocaleDateString()}
                    </span>
                  </div>
                )}
                {esFree && (
                  <div>
                    <span style={{ color: 'var(--page-muted)' }}>Vigencia: </span>
                    <span style={{ color: 'var(--page-text)' }}>Permanente</span>
                  </div>
                )}
                {esTrial && (
                  <div>
                    <span style={{ color: 'var(--page-muted)' }}>{t('miSuscripcion.diasRestantes')}: </span>
                    <span
                      style={{
                        color: (trialUrgente || trialVencido) ? '#ef4444' : 'var(--page-text)',
                        fontWeight: (trialUrgente || trialVencido) ? 700 : 400,
                      }}
                    >
                      {trialVencido ? 0 : diasRestantes}
                    </span>
                  </div>
                )}
              </div>

              {/* Alerta Trial urgente (solo si aún no venció) */}
              {trialUrgente && (
                <p className="text-xs font-semibold" style={{ color: '#ef4444' }}>
                  {t('miSuscripcion.trialUrgente', { dias: diasRestantes })}
                </p>
              )}

              {/* Botón Reportar pago → abre modal con idPlanDestinoInicial = null */}
              {suscripcion.estado !== 'ACTIVO' && (
                <button
                  disabled={hayPagoPendiente}
                  onClick={() => abrirModalReportar(null)}
                  className="mt-1 px-4 py-2 rounded-lg text-xs font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  style={{ background: hayPagoPendiente ? 'var(--page-border)' : 'var(--color-warning, #f97316)', color: hayPagoPendiente ? 'var(--page-muted)' : '#fff' }}
                >
                  {hayPagoPendiente ? t('miSuscripcion.pagoEnRevision') : t('miSuscripcion.reportarPago')}
                </button>
              )}
            </div>
          )}
        </SectionCard>

        {/* ── Uso de recursos ── */}
        {uso && (
          <SectionCard title={t('miSuscripcion.sectionUso')}>
            <div className="space-y-4">
              <UsageBar
                label={t('miSuscripcion.recurso.sucursales')}
                actual={uso.sucursales.actual}
                maximo={uso.sucursales.maximo}
                ilimitadoLabel={t('miSuscripcion.ilimitado')}
              />
              <UsageBar
                label={t('miSuscripcion.recurso.clientes')}
                actual={uso.clientesActivos.actual}
                maximo={uso.clientesActivos.maximo}
                ilimitadoLabel={t('miSuscripcion.ilimitado')}
              />
              <UsageBar
                label={t('miSuscripcion.recurso.staff')}
                actual={uso.staff.actual}
                maximo={uso.staff.maximo}
                ilimitadoLabel={t('miSuscripcion.ilimitado')}
              />
            </div>
          </SectionCard>
        )}

        {/* ── Historial reciente ── */}
        <SectionCard title={t('miSuscripcion.sectionHistorial')}>
          {historial.length === 0 ? (
            <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
              {t('miSuscripcion.historialEmpty')}
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs" style={{ borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--page-border)' }}>
                    {['Plan', 'Estado', 'Inicio', 'Fin', 'Tipo'].map(col => (
                      <th
                        key={col}
                        className="text-left pb-2 pr-3 font-medium"
                        style={{ color: 'var(--page-muted)' }}
                      >
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {historial.slice(0, 3).map(h => {
                    const planNombre = planes.find(p => p.id === h.idPlan)?.nombre ?? `#${h.idPlan}`
                    return (
                      <tr key={h.id} style={{ borderBottom: '1px solid var(--page-border)' }}>
                        <td className="py-2 pr-3" style={{ color: 'var(--page-text)' }}>{planNombre}</td>
                        <td className="py-2 pr-3">
                          <EstadoPlanBadge estado={h.estado} />
                        </td>
                        <td className="py-2 pr-3" style={{ color: 'var(--page-muted)' }}>
                          {h.fechaInicio?.slice(0, 10)}
                        </td>
                        <td className="py-2 pr-3" style={{ color: 'var(--page-muted)' }}>
                          {h.fechaFin?.slice(0, 10)}
                        </td>
                        <td className="py-2" style={{ color: 'var(--page-muted)' }}>{h.tipoCambio}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </SectionCard>

        {/* ── Cambia tu plan ── */}
        {planesMostrar.length > 0 && (
          <SectionCard title={t('miSuscripcion.sectionPlanes')}>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {planesMostrar.map(plan => (
                <PlanCard
                  key={plan.id}
                  plan={plan}
                  esPlanActual={plan.id === planActualId}
                  hayPagoPendiente={hayPagoPendiente}
                  onReportarPago={abrirModalReportar}
                />
              ))}
            </div>
          </SectionCard>
        )}

      </div>

      {/* ── Modal Reportar Pago ── */}
      <ReportarPagoModal
        open={modalReportar.open}
        onClose={cerrarModalReportar}
        idCompania={idCompania}
        planes={planes.filter(p => p.activo && p.precioMensual > 0)}
        idPlanDestinoInicial={modalReportar.idPlanDestinoInicial}
        onSuccess={cargar}
      />
    </div>
  )
}
