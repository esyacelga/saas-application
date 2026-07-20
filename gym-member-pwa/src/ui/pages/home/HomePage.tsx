import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CreditCard, QrCode, CalendarDays } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useCurrentUser } from '@/infrastructure/store/auth.store'
import { usePerfilStore, isPerfilStale } from '@/infrastructure/store/perfil.store'
import { coreRepository, type MembresiaHistorialItem } from '@/infrastructure/http/CoreHttpRepository'
import { PulseBackground } from '@/ui/components/PulseBackground'

export function HomePage() {
  const { t } = useTranslation()
  const user = useCurrentUser()

  return (
    <div className="pb-4 space-y-6">
      <PulseBackground />
      <div className="bg-accent-900/20 border-b border-accent-800/40 px-4 pt-8 pb-6">
        <p className="text-sm text-slate-400">{t('home.greeting')}</p>
        <h1 className="text-2xl font-bold text-slate-50">{user?.nombre ?? '—'}</h1>
        {user?.nombre_compania && (
          <p className="text-sm text-accent-400 mt-1">{user.nombre_compania}</p>
        )}
      </div>

      <div className="grid grid-cols-2 gap-3 px-4">
        <MembresiaStatusWidget />
        <Card
          to="/check-in"
          icon={<QrCode size={20} className="text-emerald-400" />}
          title={t('home.cards.checkin.title')}
          description={t('home.cards.checkin.description')}
        />
        <Card
          to="/historial"
          icon={<CalendarDays size={20} className="text-amber-400" />}
          title={t('home.cards.history.title')}
          description={t('home.cards.history.description')}
          className="col-span-2"
        />
      </div>
    </div>
  )
}

// ── Sub-components ──────────────────────────────────────────────────────────

function MembresiaStatusWidget() {
  const { t } = useTranslation()
  const { data: cachedData, fetchedAt, setData: setCached } = usePerfilStore()
  const [loading, setLoading] = useState(!cachedData)
  const [solicitudPendiente, setSolicitudPendiente] = useState<MembresiaHistorialItem | null>(null)
  const [memFetched, setMemFetched] = useState(false)

  useEffect(() => {
    if (!isPerfilStale(fetchedAt)) {
      // Perfil cache is fresh — still need historial to detect pending request
      coreRepository.misMembresias()
        .then((mems) => {
          const pending = mems.find((m) => m.estado_pago === 'PENDIENTE' && !m.eliminado) ?? null
          setSolicitudPendiente(pending)
          setMemFetched(true)
        })
        .catch(() => setMemFetched(true))
      return
    }

    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true)
    Promise.all([
      coreRepository.miPerfil(),
      coreRepository.misMembresias(),
    ])
      .then(([perfil, mems]) => {
        setCached(perfil)
        const pending = mems.find((m) => m.estado_pago === 'PENDIENTE' && !m.eliminado) ?? null
        setSolicitudPendiente(pending)
        setMemFetched(true)
      })
      .catch(() => setMemFetched(true))
      .finally(() => setLoading(false))
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const perfil = cachedData
  const isReady = !loading && (memFetched || !!cachedData)

  if (!isReady) {
    return (
      <div
        role="status"
        aria-label="Cargando membresía"
        className="flex items-center gap-3 rounded-xl bg-slate-800 border border-slate-700 border-l-4 border-l-accent-600 px-4 py-4 motion-safe:animate-pulse"
      >
        <div className="shrink-0 w-5 h-5 rounded bg-slate-700" />
        <div className="space-y-2 flex-1">
          <div className="h-3 rounded bg-slate-700 w-3/4" />
          <div className="h-2.5 rounded bg-slate-700 w-1/2" />
        </div>
      </div>
    )
  }

  const memActiva = perfil?.membresia_activa

  if (memActiva) {
    const esAccesos = memActiva.modo_control === 'accesos'
    const diasRestantes = Math.max(0, Math.ceil(
      (new Date(memActiva.fecha_fin).getTime() - Date.now()) / 86_400_000,
    ))
    const description = esAccesos
      ? t('home.membershipWidget.activeAccesos', {
          plan: memActiva.tipo_nombre,
          count: memActiva.dias_acceso_restantes ?? 0,
        })
      : t('home.membershipWidget.active', {
          plan: memActiva.tipo_nombre,
          count: diasRestantes,
        })

    return (
      <Link
        to="/membresia"
        className="flex items-center gap-3 rounded-xl bg-slate-800 border border-slate-700 border-l-4 border-l-emerald-500 px-4 py-4 hover:border-emerald-400 active:scale-[0.98] transition-all"
      >
        <div className="shrink-0">
          <CreditCard size={20} className="text-emerald-400" />
        </div>
        <div>
          <p className="text-sm font-semibold text-slate-50">{t('home.cards.membership.title')}</p>
          <p className="text-xs text-emerald-400">{description}</p>
        </div>
      </Link>
    )
  }

  if (solicitudPendiente) {
    return (
      <Link
        to="/membresia"
        className="flex items-center gap-3 rounded-xl bg-slate-800 border border-slate-700 border-l-4 border-l-amber-500 px-4 py-4 hover:border-amber-400 active:scale-[0.98] transition-all"
      >
        <div className="shrink-0">
          <CreditCard size={20} className="text-amber-400" />
        </div>
        <div>
          <p className="text-sm font-semibold text-slate-50">{t('home.cards.membership.title')}</p>
          <p className="text-xs text-amber-400">{t('home.membershipWidget.pending')}</p>
        </div>
      </Link>
    )
  }

  return (
    <Link
      to="/membresia"
      className="flex items-center gap-3 rounded-xl bg-slate-800 border border-slate-700 border-l-4 border-l-accent-600 px-4 py-4 hover:border-accent-500 active:scale-[0.98] transition-all"
    >
      <div className="shrink-0">
        <CreditCard size={20} className="text-accent-400" />
      </div>
      <div>
        <p className="text-sm font-semibold text-slate-50">{t('home.cards.membership.title')}</p>
        <p className="text-xs text-slate-400">{t('home.membershipWidget.empty')}</p>
      </div>
    </Link>
  )
}

function Card({
  to, icon, title, description, className = '',
}: {
  to: string
  icon: React.ReactNode
  title: string
  description: string
  className?: string
}) {
  return (
    <Link
      to={to}
      className={`flex items-center gap-3 rounded-xl bg-slate-800 border border-slate-700 border-l-4 border-l-accent-600 px-4 py-4
        hover:border-accent-500 active:scale-[0.98] transition-all ${className}`}
    >
      <div className="shrink-0">{icon}</div>
      <div>
        <p className="text-sm font-semibold text-slate-50">{title}</p>
        <p className="text-xs text-slate-400">{description}</p>
      </div>
    </Link>
  )
}
