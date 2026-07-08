import { useEffect, useState } from 'react'
import { CalendarDays, Zap, Snowflake, AlertTriangle, RefreshCw, CheckCircle } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { coreRepository, type MiPerfilResponse } from '@/infrastructure/http/CoreHttpRepository'
import { usePerfilStore, isPerfilStale } from '@/infrastructure/store/perfil.store'
import { getApiErrorMessage } from '@/lib/api-error'
import { PulseBackground } from '@/ui/components/PulseBackground'

const STATUS_CLS: Record<string, string> = {
  activo:          'bg-emerald-900/30 border-emerald-700 text-emerald-300',
  proximo_vencer:  'bg-amber-900/30 border-amber-700 text-amber-300',
  vencido:         'bg-red-900/30 border-red-700 text-red-300',
  congelado:       'bg-sky-900/30 border-sky-700 text-sky-300',
  riesgo_abandono: 'bg-orange-900/30 border-orange-700 text-orange-300',
}

export function MembresiaPage() {
  const { t } = useTranslation()
  const { data: cachedData, fetchedAt, setData: setCached, invalidate } = usePerfilStore()
  const [data, setData] = useState<MiPerfilResponse | null>(cachedData)
  const [loading, setLoading] = useState(!cachedData)
  const [reactivando, setReactivando] = useState(false)
  const [reactivado, setReactivado] = useState(false)

  const fetchPerfil = async (showLoading = true) => {
    if (showLoading) setLoading(true)
    try {
      const res = await coreRepository.miPerfil()
      setData(res)
      setCached(res)
    } catch (err) {
      const hasResponse = !!(err as { response?: unknown })?.response
      if (!hasResponse) {
        toast.error(getApiErrorMessage(err, t('membresia.errors.load')))
      }
    } finally {
      if (showLoading) setLoading(false)
    }
  }

  useEffect(() => {
    if (isPerfilStale(fetchedAt)) fetchPerfil(!cachedData)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleReactivar = async () => {
    if (!data?.congelamiento_activo) return
    setReactivando(true)
    try {
      await coreRepository.reactivarCongelamiento(data.congelamiento_activo.id)
      invalidate()
      setReactivado(true)
      toast.success(t('membresia.success.reactivated'))
      fetchPerfil()
    } catch (err) {
      toast.error(getApiErrorMessage(err, t('membresia.errors.reactivate')))
    } finally {
      setReactivando(false)
    }
  }

  return (
    <div className="pb-6 space-y-5">
      <PulseBackground />
      <div className="px-4 pt-8 pb-4 bg-accent-900/20 border-b border-accent-800/40">
        <h1 className="text-xl font-bold text-slate-50">{t('membresia.title')}</h1>
      </div>
      <div className="px-4 space-y-5">

      {loading && <SkeletonCard />}

      {!loading && !data?.membresia_activa && (
        <EmptyState onRetry={fetchPerfil} />
      )}

      {!loading && data?.membresia_activa && (
        <>
          <StatusBanner estado={data.estado_cliente} />

          <MembresiaCard mem={data.membresia_activa} />

          {data.congelamiento_activo && !reactivado && (
            <FreezeCard
              congelamiento={data.congelamiento_activo}
              onReactivar={handleReactivar}
              loading={reactivando}
            />
          )}

          {reactivado && (
            <div className="flex items-center gap-3 rounded-xl bg-emerald-900/30 border border-emerald-700 px-4 py-3">
              <CheckCircle size={18} className="text-emerald-400 shrink-0" />
              <p className="text-sm text-emerald-300">{t('membresia.reactivated')}</p>
            </div>
          )}

          <button
            onClick={() => fetchPerfil()}
            disabled={loading}
            className="w-full flex items-center justify-center gap-2 rounded-lg border border-slate-700 py-2.5 text-xs text-slate-400 hover:border-slate-500 transition-colors"
          >
            <RefreshCw size={13} />
            {t('membresia.update')}
          </button>
        </>
      )}
      </div>
    </div>
  )
}

// ── Sub-components ──────────────────────────────────────────────────────────

function StatusBanner({ estado }: { estado: MiPerfilResponse['estado_cliente'] }) {
  const { t } = useTranslation()
  const cls = STATUS_CLS[estado] ?? 'bg-slate-800 border-slate-600 text-slate-300'
  const label = t(`membresia.status.${estado}`, { defaultValue: estado })
  return (
    <div className={`rounded-xl border px-4 py-2.5 text-sm font-medium ${cls}`}>
      {label}
    </div>
  )
}

function MembresiaCard({ mem }: { mem: NonNullable<MiPerfilResponse['membresia_activa']> }) {
  const { t } = useTranslation()
  const esAccesos = mem.modo_control === 'accesos'
  const diasRestantes = Math.max(0, Math.ceil(
    (new Date(mem.fecha_fin).getTime() - Date.now()) / 86_400_000
  ))

  return (
    <div className="rounded-2xl bg-slate-800 border border-slate-700 overflow-hidden">
      <div className="px-4 pt-4 pb-3 border-b border-slate-700 flex items-center justify-between">
        <div>
          <p className="text-xs text-slate-400">{t('membresia.card.plan')}</p>
          <p className="text-base font-semibold text-slate-50">{mem.tipo_nombre}</p>
        </div>
        {esAccesos
          ? <Zap size={20} className="text-amber-400" />
          : <CalendarDays size={20} className="text-accent-400" />}
      </div>

      <div className="divide-y divide-slate-700">
        <Row label={t('membresia.card.labels.start')} value={formatDate(mem.fecha_inicio)} />
        <Row label={t('membresia.card.labels.expiry')} value={formatDate(mem.fecha_fin)} />

        {!esAccesos && (
          <Row
            label={t('membresia.card.labels.daysRemaining')}
            value={t('membresia.card.labels.days', { count: diasRestantes })}
            highlight={diasRestantes <= 7}
          />
        )}

        {esAccesos && mem.dias_acceso_restantes != null && (
          <>
            <Row label={t('membresia.card.labels.accessUsed')} value={String(mem.dias_acceso_usados ?? 0)} />
            <Row
              label={t('membresia.card.labels.accessRemaining')}
              value={String(mem.dias_acceso_restantes)}
              highlight={mem.dias_acceso_restantes === 0 ? 'zero' : mem.dias_acceso_restantes <= 3}
            />
          </>
        )}
      </div>
    </div>
  )
}

function FreezeCard({
  congelamiento,
  onReactivar,
  loading,
}: {
  congelamiento: NonNullable<MiPerfilResponse['congelamiento_activo']>
  onReactivar: () => void
  loading: boolean
}) {
  const { t } = useTranslation()
  return (
    <div className="rounded-2xl bg-sky-900/20 border border-sky-700 p-4 space-y-3">
      <div className="flex items-center gap-2">
        <Snowflake size={18} className="text-sky-400" />
        <p className="text-sm font-semibold text-sky-300">{t('membresia.freeze.title')}</p>
      </div>
      <p className="text-xs text-sky-400">
        {t('membresia.freeze.description', { date: formatDate(congelamiento.fecha_inicio) })}
      </p>
      <button
        onClick={onReactivar}
        disabled={loading}
        className="w-full rounded-lg bg-sky-600 py-2.5 text-sm font-semibold text-white hover:bg-sky-500 disabled:opacity-50 transition-colors"
      >
        {loading ? t('membresia.freeze.reactivating') : t('membresia.freeze.reactivate')}
      </button>
    </div>
  )
}

function EmptyState({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center gap-4 rounded-2xl bg-slate-800 border border-slate-700 p-8 text-center">
      <AlertTriangle size={40} className="text-slate-500" />
      <div>
        <p className="text-sm font-semibold text-slate-300">{t('membresia.empty.title')}</p>
        <p className="text-xs text-slate-500 mt-1">{t('membresia.empty.description')}</p>
      </div>
      <button
        onClick={onRetry}
        className="text-xs text-accent-400 underline underline-offset-2"
      >
        {t('membresia.empty.retry')}
      </button>
    </div>
  )
}

function SkeletonCard() {
  return (
    <div role="status" aria-label="Cargando membresía" className="rounded-2xl bg-slate-800 border border-slate-700 p-4 space-y-3 motion-safe:animate-pulse">
      {[1, 2, 3].map((i) => (
        <div key={i} className="h-4 rounded bg-slate-700 w-3/4" />
      ))}
    </div>
  )
}

function Row({ label, value, highlight }: { label: string; value: string; highlight?: boolean | 'zero' }) {
  const valueCls = highlight === 'zero' ? 'text-emerald-400' : highlight ? 'text-amber-400' : 'text-slate-50'
  return (
    <div className="flex items-center justify-between px-4 py-3">
      <span className="text-sm text-slate-400">{label}</span>
      <span className={`text-sm font-medium ${valueCls}`}>{value}</span>
    </div>
  )
}

function formatDate(iso: string) {
  return new Date(iso + 'T00:00:00').toLocaleDateString('es', {
    day: '2-digit', month: 'short', year: 'numeric',
  })
}
