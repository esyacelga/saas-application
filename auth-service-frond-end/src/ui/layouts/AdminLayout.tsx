import { useState, useEffect } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { CreditCard, Dumbbell, LayoutDashboard, LogOut, Menu, Palette, Printer, ScrollText, Settings, Shield, Smartphone, Tag, Users, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
import { LanguageSwitcher } from '@/ui/components/LanguageSwitcher'
import { PrintQrModal } from '@/ui/components/PrintQrModal'
import { PlanUsageWidget } from '@/ui/components/PlanUsageWidget'
import { useAuthStore, useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { JwtPayloadStaff } from '@/domain/auth/entities/User.entity'
import { useThemeStore } from '@/infrastructure/store/theme/theme.store'
import type { AppTheme } from '@/infrastructure/store/theme/theme.store'

interface NavItem {
  to: string
  labelKey: string
  icon: React.ReactNode
  permiso?: string
  end?: boolean
}

const ALL_NAV_ITEMS: NavItem[] = [
  { to: '/admin/dashboard',        labelKey: 'nav.dashboard',        icon: <LayoutDashboard size={20} /> },
  { to: '/admin/clientes',         labelKey: 'nav.clientes',         icon: <Users size={20} />,        permiso: 'clientes:leer', end: true },
  { to: '/admin/tipos-membresia',  labelKey: 'nav.tiposMembresia',   icon: <Tag size={20} />,          permiso: 'membresias:leer' },
  { to: '/admin/usuarios',         labelKey: 'nav.users',            icon: <CreditCard size={20} />,   permiso: 'usuarios:leer' },
  { to: '/admin/roles',            labelKey: 'nav.rolesPermissions', icon: <Shield size={20} />,       permiso: 'roles:leer' },
  { to: '/admin/clientes/app',     labelKey: 'nav.appAccounts',      icon: <Smartphone size={20} /> },
  { to: '/admin/bitacora',         labelKey: 'nav.activityLog',      icon: <ScrollText size={20} />,   permiso: 'usuarios:leer' },
  { to: '/admin/configuracion',   labelKey: 'nav.configuracion',    icon: <Settings size={20} /> },
]

const THEMES: { value: AppTheme; label: string; color: string }[] = [
  { value: 'light',        label: 'Light',        color: '#f8fafc' },
  { value: 'dark',         label: 'Dark',         color: '#020817' },
  { value: 'dark-blue',    label: 'Dark Blue',    color: '#0d1b2a' },
  { value: 'ocean-blue',   label: 'Ocean Blue',   color: '#bfdbfe' },
  { value: 'slate-carbon', label: 'Slate Carbon', color: '#18181b' },
  { value: 'mint-pastel',  label: 'Mint Pastel',  color: '#f0fdf4' },
]

interface SidebarProps {
  navItems: NavItem[]
  user: JwtPayloadStaff | null
  inicialNombre: string
  onClose: () => void
  onLogout: () => void
  onPrintQr: () => void
}

function SidebarContent({ navItems, user, inicialNombre, onClose, onLogout, onPrintQr }: SidebarProps) {
  const { t } = useTranslation()
  const { theme, setTheme } = useThemeStore()
  const [themeMenuOpen, setThemeMenuOpen] = useState(false)

  return (
    <>
      <div
        className="flex items-center gap-3 px-5 py-5"
        style={{ borderBottom: '1px solid var(--sidebar-border)' }}
      >
        <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-orange-500 flex-shrink-0">
          <Dumbbell size={20} className="text-white" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="font-bold text-sm leading-tight" style={{ color: 'var(--sidebar-text-hover)' }}>
            Gym Admin
          </p>
          {user && (
            <p className="text-xs leading-tight truncate" style={{ color: 'var(--sidebar-text)' }}>
              {t('layout.branch', { id: user.id_sucursal })}
            </p>
          )}
        </div>
        <button
          onClick={onClose}
          className="transition-colors lg:hidden"
          style={{ color: 'var(--sidebar-text)' }}
          aria-label={t('layout.closeMenu')}
        >
          <X size={20} />
        </button>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
        {navItems.map(item => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            onClick={onClose}
            className={cn('flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors')}
            style={({ isActive }) => isActive
              ? { background: 'var(--sidebar-active-bg)', color: 'var(--sidebar-active-text)' }
              : { color: 'var(--sidebar-text)' }
            }
          >
            <span className="flex-shrink-0">{item.icon}</span>
            {t(item.labelKey)}
          </NavLink>
        ))}
      </nav>

      <div className="px-3 py-4 space-y-2" style={{ borderTop: '1px solid var(--sidebar-border)' }}>
        {/* Zona (0): widget de uso del plan — visible solo para id_rol === 1 (Dueño/Admin) */}
        <div style={{ borderBottom: '1px solid var(--sidebar-border)', paddingBottom: '0.5rem', marginBottom: '0.25rem' }}>
          <PlanUsageWidget />
        </div>

        <div className="flex items-center gap-3 px-3 py-2 rounded-lg">
          <div className="flex items-center justify-center w-8 h-8 rounded-full bg-orange-500 text-white text-sm font-bold flex-shrink-0">
            {inicialNombre}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium truncate" style={{ color: 'var(--sidebar-text-hover)' }}>
              {user?.nombre ?? 'Usuario'}
            </p>
            <p className="text-xs truncate" style={{ color: 'var(--sidebar-text)' }}>{t('layout.staff')}</p>
          </div>
        </div>

        <div className="px-3">
          <LanguageSwitcher variant="dark" />
        </div>

        {/* Theme picker */}
        <div className="relative px-3">
          <button
            onClick={() => setThemeMenuOpen(p => !p)}
            className="flex items-center gap-1.5 text-xs transition-colors py-1 w-full"
            style={{ color: 'var(--sidebar-text)' }}
          >
            <Palette size={12} />
            <span>
              Tema:{' '}
              <span style={{ color: 'var(--sidebar-text-hover)' }}>
                {THEMES.find(t => t.value === theme)?.label}
              </span>
            </span>
          </button>
          {themeMenuOpen && (
            <div
              className="absolute bottom-8 left-3 rounded-lg shadow-xl py-1 z-50 min-w-[140px]"
              style={{ background: 'var(--sidebar-border)', border: '1px solid var(--sidebar-item-hover)' }}
            >
              {THEMES.map(t => (
                <button
                  key={t.value}
                  onClick={() => { setTheme(t.value); setThemeMenuOpen(false) }}
                  className="flex items-center gap-2 w-full px-3 py-1.5 text-xs transition-colors"
                  style={{ color: theme === t.value ? '#fb923c' : 'var(--sidebar-text)' }}
                >
                  <span
                    className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                    style={{ background: t.color, border: '1px solid var(--sidebar-text)' }}
                  />
                  {t.label}
                  {theme === t.value && <span className="ml-auto" style={{ color: '#fb923c' }}>✓</span>}
                </button>
              ))}
            </div>
          )}
        </div>

        <button
          onClick={onPrintQr}
          className="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sm font-medium transition-colors"
          style={{ color: 'var(--sidebar-text)' }}
          onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.color = 'var(--sidebar-text-hover)'; (e.currentTarget as HTMLButtonElement).style.background = 'var(--sidebar-item-hover)' }}
          onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.color = 'var(--sidebar-text)'; (e.currentTarget as HTMLButtonElement).style.background = '' }}
        >
          <Printer size={18} />
          {t('layout.printQr')}
        </button>

        <button
          onClick={onLogout}
          className="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sm font-medium transition-colors hover:text-red-400 hover:bg-red-500/10"
          style={{ color: 'var(--sidebar-text)' }}
        >
          <LogOut size={18} />
          {t('layout.logout')}
        </button>
      </div>
    </>
  )
}

export function AdminLayout() {
  const { t } = useTranslation()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [printQrOpen, setPrintQrOpen] = useState(false)
  const navigate = useNavigate()
  const rawUser = useCurrentUser()
  const { logout } = useAuthStore()
  const { theme } = useThemeStore()

  const user = rawUser?.tipo === 'staff' ? (rawUser as JwtPayloadStaff) : null
  const permisos = user?.permisos ?? []

  const navItems = ALL_NAV_ITEMS.filter(
    item => !item.permiso || permisos.includes(item.permiso)
  )

  useEffect(() => {
    document.body.dataset.layout = theme
    return () => { delete document.body.dataset.layout }
  }, [theme])

  const inicialNombre = user?.nombre?.[0]?.toUpperCase() ?? 'U'

  const handleLogout = async () => {
    await authRepository.logout().catch(() => {})
    logout()
    navigate('/login', { replace: true })
  }

  const closeSidebar = () => setSidebarOpen(false)

  const sidebarProps: SidebarProps = {
    navItems, user, inicialNombre,
    onClose: closeSidebar,
    onLogout: handleLogout,
    onPrintQr: () => { closeSidebar(); setPrintQrOpen(true) },
  }

  return (
    <div
      className="flex h-screen overflow-hidden"
      data-layout={theme}
      style={{ background: 'var(--page-bg)' }}
    >
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-20 bg-black/60 lg:hidden"
          onClick={closeSidebar}
          aria-hidden="true"
        />
      )}

      {/* Sidebar móvil (drawer) */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-30 flex flex-col w-72 shadow-sidebar',
          'transition-transform duration-300 ease-in-out lg:hidden',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
        style={{ background: 'var(--sidebar-bg)', borderRight: '1px solid var(--sidebar-border)' }}
      >
        <SidebarContent {...sidebarProps} />
      </aside>

      {/* Sidebar desktop (siempre visible) */}
      <aside
        className="hidden lg:flex lg:flex-col lg:w-64 flex-shrink-0"
        style={{ background: 'var(--sidebar-bg)', borderRight: '1px solid var(--sidebar-border)' }}
      >
        <SidebarContent {...sidebarProps} />
      </aside>

      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Header solo en móvil/tablet */}
        <header
          className="flex items-center gap-4 px-4 py-3 border-b shadow-sm lg:hidden flex-shrink-0"
          style={{ background: 'var(--page-surface)', borderColor: 'var(--page-border)' }}
        >
          <button
            onClick={() => setSidebarOpen(true)}
            className="p-2 rounded-lg transition-colors"
            style={{ color: 'var(--page-muted)' }}
            aria-label={t('layout.openMenu')}
          >
            <Menu size={22} />
          </button>
          <div className="flex items-center gap-2">
            <Dumbbell size={18} className="text-orange-500" />
            <span className="font-semibold text-sm" style={{ color: 'var(--page-text)' }}>Gym Admin</span>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>

      <PrintQrModal open={printQrOpen} onClose={() => setPrintQrOpen(false)} />
    </div>
  )
}
