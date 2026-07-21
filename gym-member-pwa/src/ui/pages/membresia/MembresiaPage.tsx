import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarDays, Zap, Snowflake, RefreshCw, CheckCircle, Receipt, Clock } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import {
  coreRepository,
  type MiPerfilResponse,
  type MembresiaHistorialItem,
  type TipoMembresia,
} from '@/infrastructure/http/CoreHttpRepository'
import { usePerfilStore, isPerfilStale } from '@/infrastructure/store/perfil.store'
import { useAuthStore } from '@/infrastructure/store/auth.store'
import { getApiErrorMessage, getApiErrorCode } from '@/lib/api-error'
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
  const navigate = useNavigate()
  const { data: cachedData, fetchedAt, setData: setCached, invalidate } = usePerfilStore()

  const [data, setData] = useState<MiPerfilResponse | null>(cachedData)
  const [solicitudPendiente, setSolicitudPendiente] = useState<MembresiaHistorialItem | null>(null)
  const [tipos, setTipos] = useState<TipoMembresia[]>([])
  const [loading, setLoading] = useState(!cachedData)
  const [reactivando, setReactivando] = useState(false)
  const [reactivado, setReactivado] = useState(false)
  const [solicitando, setSolicitando] = useState(false)

  const fetchAll = async (showLoading = true, allowAutoRegister = true) => {
    if (showLoading) setLoading(true)
    try {
      const [perfil, mems] = await Promise.all([
        coreRepository.miPerfil(),
        coreRepository.misMembresias(),
      ])
      setData(perfil)
      setCached(perfil)
      const pending = mems.find((m) => m.estado_pago === 'PENDIENTE' && !m.eliminado) ?? null
      setSolicitudPendiente(pending)

      // Only fetch catalog when there's no active membership and no pending request
      if (!perfil.membresia_activa && !pending) {
        fetchTipos()
      }
    } catch (err) {
      // La persona tiene cuenta (Persona/UsuarioApp) pero aún no está registrada
      // como cliente del gym → `mi-perfil`/`me/membresias` responden 404. Se
      // auto-registra una sola vez y se reintenta; tras registrar no hay membresía,
      // así que la propia UI mostrará el catálogo.
      const code = getApiErrorCode(err)
      if (allowAutoRegister && code === 'recurso_no_encontrado') {
        try {
          const idSucursal = useAuthStore.getState().gymInfo?.id_sucursal ?? undefined
          await coreRepository.asegurarClienteRegistrado(idSucursal)
          await fetchAll(false, false) // reintento único, sin volver a auto-registrar
          return
        } catch {
          // El auto-registro falló — cae al manejo genérico de abajo
        }
      }
      const hasResponse = !!(err as { response?: unknown })?.response
      if (!hasResponse) {
        toast.error(getApiErrorMessage(err, t('membresia.errors.load')))
      }
    } finally {
      if (showLoading) setLoading(false)
    }
  }

  const fetchTipos = async () => {
    try {
      const lista = await coreRepository.listarTiposMembresia()
      setTipos(lista)
    } catch {
      // Silently fail — catalog will show empty state
    }
  }

  useEffect(() => {
    if (isPerfilStale(fetchedAt)) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      fetchAll(!cachedData)
    } else {
      // Cache is fresh — still fetch historial to detect pending request
      coreRepository.misMembresias()
        .then((mems) => {
          const pending = mems.find((m) => m.estado_pago === 'PENDIENTE' && !m.eliminado) ?? null
          setSolicitudPendiente(pending)
          if (!cachedData?.membresia_activa && !pending) {
            fetchTipos()
          }
        })
        .catch((err) => {
          // Caché de perfil fresca pero el cliente no existe en el gym (404):
          // delega a fetchAll para auto-registrar y recargar todo.
          if (getApiErrorCode(err) === 'recurso_no_encontrado') {
            fetchAll(false)
          }
        })
    }
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
      fetchAll()
    } catch (err) {
      toast.error(getApiErrorMessage(err, t('membresia.errors.reactivate')))
    } finally {
      setReactivando(false)
    }
  }

  const handleSolicitar = async (idTipoMembresia: number) => {
    setSolicitando(true)
    try {
      await coreRepository.solicitarMembresia(idTipoMembresia)
      invalidate()
      toast.success(t('membresia.solicitud.exito'))
      fetchAll(false)
    } catch (err) {
      const code = getApiErrorCode(err)
      if (code === 'solicitud_ya_existe') {
        toast.error(t('membresia.solicitud.errores.yaExiste'))
        fetchAll(false)
      } else if (code === 'membresia_activa_vigente') {
        toast.error(t('membresia.solicitud.errores.activaVigente'))
        fetchAll(false)
      } else if (code === 'tipo_membresia_no_disponible') {
        toast.error(t('membresia.solicitud.errores.tipoNoDisponible'))
        fetchTipos()
      } else {
        toast.error(getApiErrorMessage(err, t('membresia.solicitud.errores.generico')))
      }
    } finally {
      setSolicitando(false)
    }
  }

  const membresiaActiva = data?.membresia_activa ?? null

  return (
    <div className="pb-6 space-y-5">
      <PulseBackground />
      <div className="px-4 pt-8 pb-4 bg-accent-900/20 border-b border-accent-800/40">
        <h1 className="text-xl font-bold text-slate-50">{t('membresia.title')}</h1>
      </div>
      <div className="px-4 space-y-5">

      {loading && <SkeletonCard />}

      {!loading && membresiaActiva && (
        <>
          <StatusBanner estado={data!.estado_cliente} />

          <MembresiaCard mem={membresiaActiva} />

          {data?.congelamiento_activo && !reactivado && (
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
            onClick={() => fetchAll()}
            disabled={loading}
            className="w-full flex items-center justify-center gap-2 rounded-lg border border-slate-700 py-2.5 text-xs text-slate-400 hover:border-slate-500 transition-colors"
          >
            <RefreshCw size={13} />
            {t('membresia.update')}
          </button>

          <button
            onClick={() => navigate('/membresia/historial')}
            className="w-full flex items-center justify-center gap-2 rounded-lg border border-slate-700 py-2.5 text-xs text-slate-400 hover:border-slate-500 transition-colors"
          >
            <Receipt size={13} />
            {t('membresia.verHistorialPagos')}
          </button>
        </>
      )}

      {!loading && !membresiaActiva && solicitudPendiente && (
        <SolicitudPendienteCard mem={solicitudPendiente} />
      )}

      {!loading && !membresiaActiva && !solicitudPendiente && (
        <CatalogoMembresias
          tipos={tipos}
          onSolicitar={handleSolicitar}
          loading={solicitando}
        />
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

function SolicitudPendienteCard({ mem }: { mem: MembresiaHistorialItem }) {
  const { t } = useTranslation()
  return (
    <div className="rounded-2xl bg-amber-900/20 border border-amber-700 p-4 space-y-3">
      <div className="flex items-center gap-2">
        <Clock size={18} className="text-amber-400" />
        <p className="text-sm font-semibold text-amber-300">{t('membresia.solicitud.pendienteTitulo')}</p>
      </div>
      <p className="text-xs text-amber-400">
        {t('membresia.solicitud.pendienteDescripcion', { plan: mem.tipo_nombre })}
      </p>
      {mem.fecha_inicio && (
        <p className="text-xs text-amber-500">
          {formatDate(mem.fecha_inicio)}
        </p>
      )}
    </div>
  )
}

function CatalogoMembresias({
  tipos,
  onSolicitar,
  loading,
}: {
  tipos: TipoMembresia[]
  onSolicitar: (idTipo: number) => void
  loading: boolean
}) {
  const { t } = useTranslation()

  return (
    <div className="space-y-4">
      <div>
        <p className="text-base font-semibold text-slate-50">{t('membresia.solicitud.catalogoTitulo')}</p>
        <p className="text-xs text-slate-400 mt-0.5">{t('membresia.solicitud.catalogoSubtitulo')}</p>
      </div>

      {tipos.length === 0 && (
        <div className="rounded-2xl bg-slate-800 border border-slate-700 p-6 text-center">
          <p className="text-sm text-slate-400">{t('membresia.solicitud.catalogoVacio')}</p>
        </div>
      )}

      <div className="space-y-3">
        {tipos.map((tipo) => {
          const esAccesos = tipo.modo_control === 'accesos'
          const duracionLabel = esAccesos
            ? t('membresia.solicitud.duracion.accesos', { count: tipo.dias_acceso ?? 0 })
            : t(`membresia.solicitud.duracion.${tipo.duracion_tipo}`, { count: tipo.duracion_valor })

          return (
            <div
              key={tipo.id}
              className="rounded-2xl bg-slate-800 border border-slate-700 overflow-hidden"
            >
              <div className="px-4 pt-4 pb-3 border-b border-slate-700 flex items-center justify-between">
                <div>
                  <p className="text-sm font-semibold text-slate-50">{tipo.nombre}</p>
                  <p className="text-xs text-slate-400 mt-0.5">{duracionLabel}</p>
                </div>
                {esAccesos
                  ? <Zap size={18} className="text-amber-400" />
                  : <CalendarDays size={18} className="text-accent-400" />}
              </div>
              <div className="px-4 py-3 flex items-center justify-between">
                <p className="text-base font-bold text-slate-50">
                  ${tipo.precio.toFixed(2)}
                </p>
                <button
                  onClick={() => onSolicitar(tipo.id)}
                  disabled={loading}
                  className="rounded-lg bg-accent-600 px-4 py-2 text-sm font-semibold text-white hover:bg-accent-500 disabled:opacity-50 transition-colors"
                >
                  {loading ? t('membresia.solicitud.solicitando') : t('membresia.solicitud.solicitar')}
                </button>
              </div>
            </div>
          )
        })}
      </div>
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
