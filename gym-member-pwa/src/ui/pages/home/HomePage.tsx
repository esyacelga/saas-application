import { Link } from 'react-router-dom'
import { CreditCard, QrCode, CalendarDays } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useCurrentUser } from '@/infrastructure/store/auth.store'
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
        <Card
          to="/membresia"
          icon={<CreditCard size={20} className="text-accent-400" />}
          title={t('home.cards.membership.title')}
          description={t('home.cards.membership.description')}
        />
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
