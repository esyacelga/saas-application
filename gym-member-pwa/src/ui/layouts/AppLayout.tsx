import { NavLink, Outlet } from 'react-router-dom'
import { Home, QrCode, CreditCard, User } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { InstallBanner } from '@/ui/components/InstallBanner'

export function AppLayout() {
  const { t } = useTranslation()

  const NAV = [
    { to: '/home',      icon: Home,       label: t('nav.home') },
    { to: '/check-in',  icon: QrCode,     label: t('nav.checkin') },
    { to: '/membresia', icon: CreditCard, label: t('nav.membership') },
    { to: '/profile',   icon: User,       label: t('nav.profile') },
  ]

  return (
    <div className="flex flex-col min-h-svh">
      <main className="flex-1 overflow-y-auto pb-20">
        <InstallBanner />
        <Outlet />
      </main>

      <nav aria-label={t('nav.aria')} className="fixed bottom-0 inset-x-0 bg-slate-900 border-t border-slate-800 flex"
           style={{ paddingBottom: 'var(--safe-bottom)' }}>
        {NAV.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex-1 flex flex-col items-center gap-1 py-3 text-xs transition-colors border-t-2 ${
                isActive
                  ? 'text-accent-400 border-t-accent-500 bg-accent-900/20'
                  : 'text-slate-500 border-t-transparent'
              }`
            }
          >
            <Icon size={22} />
            {label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
