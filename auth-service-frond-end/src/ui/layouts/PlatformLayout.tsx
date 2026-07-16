import { useState, useEffect } from 'react'
import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import {
  Activity, ChevronLeft, ChevronRight, Dumbbell,
  LayoutDashboard, LogOut, Shield, Users, Package, Settings2, Building2, Palette, UserRound, ClipboardCheck, BellRing,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
import { LanguageSwitcher } from '@/ui/components/LanguageSwitcher'
import { useCurrentUser, useAuthStore } from '@/infrastructure/store/auth/auth.store'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { useThemeStore } from '@/infrastructure/store/theme/theme.store'

const ROLE_BADGE_CLASS: Record<string, string> = {
  super_admin: 'bg-orange-500/20 text-orange-400 border border-orange-500/30',
  soporte:     'bg-blue-500/20 text-blue-400 border border-blue-500/30',
  viewer:      'bg-slate-600/40 text-slate-400 border border-slate-700',
}

const ROLE_LABEL: Record<string, string> = {
  super_admin: 'Super Admin',
  soporte:     'Soporte',
  viewer:      'Viewer',
}

interface NavItem {
  to: string
  labelKey: string
  icon: React.ReactNode
  roles?: string[]
}

const NAV_ITEMS: NavItem[] = [
  { to: '/platform/dashboard',       labelKey: 'nav.dashboard',        icon: <LayoutDashboard size={15} /> },
  { to: '/platform/companias',       labelKey: 'nav.gyms',             icon: <Building2 size={15} /> },
  { to: '/platform/planes',          labelKey: 'nav.plans',            icon: <Package size={15} />,   roles: ['super_admin'] },
  { to: '/platform/caracteristicas', labelKey: 'nav.features',         icon: <Settings2 size={15} />, roles: ['super_admin'] },
  { to: '/platform/usuarios',        labelKey: 'nav.operators',        icon: <Users size={15} /> },
  { to: '/platform/roles',           labelKey: 'nav.platformRoles',    icon: <Shield size={15} /> },
  { to: '/platform/actividad',       labelKey: 'nav.activity',         icon: <Activity size={15} /> },
  { to: '/platform/personas',        labelKey: 'nav.personas',         icon: <UserRound size={15} /> },
  { to: '/platform/pagos-pendientes', labelKey: 'nav.pagosPendientes', icon: <ClipboardCheck size={15} /> },
  { to: '/platform/notif-buckets',    labelKey: 'nav.notifBuckets',    icon: <BellRing size={15} />,    roles: ['super_admin'] },
]

export function PlatformLayout() {
  const { t } = useTranslation()
  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem('gym-platform-sidebar-collapsed') === 'true'
  )
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const { logout } = useAuthStore()
  const navigate = useNavigate()
  const { theme, setTheme } = useThemeStore()
  const [themeMenuOpen, setThemeMenuOpen] = useState(false)

  const THEMES: { value: import('@/infrastructure/store/theme/theme.store').AppTheme; label: string; color: string }[] = [
    { value: 'light',        label: 'Light',        color: '#f8fafc' },
    { value: 'dark',         label: 'Dark',         color: '#020817' },
    { value: 'dark-blue',    label: 'Dark Blue',    color: '#0d1b2a' },
    { value: 'ocean-blue',   label: 'Ocean Blue',   color: '#bfdbfe' },
    { value: 'slate-carbon', label: 'Slate Carbon', color: '#18181b' },
    { value: 'mint-pastel',  label: 'Mint Pastel',  color: '#f0fdf4' },
  ]

  const handleLogout = async () => {
    await authRepository.logout().catch(() => {})
    logout()
    navigate('/platform/login', { replace: true })
  }

  const toggleCollapse = () => {
    setCollapsed(prev => {
      const next = !prev
      localStorage.setItem('gym-platform-sidebar-collapsed', String(next))
      return next
    })
  }

  useEffect(() => {
    document.body.dataset.layout = theme
    return () => { delete document.body.dataset.layout }
  }, [theme])

  const inicialNombre = user?.nombre?.[0]?.toUpperCase() ?? 'O'
  const rolLabel    = user ? (ROLE_LABEL[user.rol_plataforma]    ?? user.rol_plataforma) : ''
  const rolBadgeClass = user ? (ROLE_BADGE_CLASS[user.rol_plataforma] ?? '') : ''

  return (
    <div className="flex h-screen overflow-hidden" data-layout={theme}
      style={{ background: 'var(--page-bg)', color: 'var(--page-text)' }}
    >

      {/* Sidebar */}
      <aside
        className={cn(
          'flex-shrink-0 flex flex-col overflow-hidden',
          'transition-[width] duration-300 ease-in-out',
          collapsed ? 'w-14' : 'w-52',
        )}
        style={{ background: 'var(--sidebar-bg)', borderRight: '1px solid var(--sidebar-border)' }}
      >

        {/* Header */}
        <div
          className={cn('flex items-center px-3 py-4 gap-2', collapsed ? 'flex-col' : 'flex-row')}
          style={{ borderBottom: '1px solid var(--sidebar-border)' }}
        >
          <div className="flex items-center gap-1.5 flex-shrink-0">
            <Dumbbell size={15} className="text-orange-500" />
            {!collapsed && (
              <span className="text-white font-bold text-sm">Gym Admin</span>
            )}
          </div>

          <button
            onClick={toggleCollapse}
            className="flex items-center justify-center p-1 rounded-md transition-colors flex-shrink-0"
            style={{ color: 'var(--sidebar-text)' }}
            aria-label={collapsed ? t('layout.expandSidebar') : t('layout.collapseSidebar')}
          >
            {collapsed ? <ChevronRight size={13} /> : <ChevronLeft size={13} />}
          </button>
        </div>

        {/* Role badge */}
        {user && !collapsed && (
          <div className="px-4 pt-3 pb-1">
            <span className={`inline-block text-xs px-2 py-0.5 rounded-full font-medium ${rolBadgeClass}`}>
              {rolLabel}
            </span>
          </div>
        )}
        {user && collapsed && (
          <div className="flex justify-center pt-3 pb-1">
            <span
              title={rolLabel}
              className={`inline-block text-xs px-1.5 py-0.5 rounded-full font-medium ${rolBadgeClass}`}
            >
              {inicialNombre}
            </span>
          </div>
        )}

        {/* Nav */}
        <nav className="flex-1 px-2 py-3 space-y-0.5 overflow-y-auto">
          {NAV_ITEMS.filter(item => !item.roles || (user && item.roles.includes(user.rol_plataforma))).map(item => (
            <NavLink
              key={item.to}
              to={item.to}
              title={collapsed ? t(item.labelKey) : undefined}
              className={cn(
                'flex items-center px-2 py-2 rounded-md text-xs font-medium transition-colors',
                collapsed ? 'justify-center' : 'gap-2.5',
              )}
              style={({ isActive }: { isActive: boolean }) => isActive
                ? { background: 'var(--sidebar-active-bg)', color: 'var(--sidebar-active-text)' }
                : { color: 'var(--sidebar-text)' }
              }
            >
              <span className="flex-shrink-0">{item.icon}</span>
              {!collapsed && <span className="flex-1">{t(item.labelKey)}</span>}
              {!collapsed && (
                <ChevronRight
                  size={12}
                  className="opacity-0 group-hover:opacity-40 transition-opacity"
                />
              )}
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        <div
          className={cn('py-4 space-y-2', collapsed ? 'px-2 flex flex-col items-center' : 'px-4')}
          style={{ borderTop: '1px solid var(--sidebar-border)' }}
        >
          <div className={cn(
            'flex items-center gap-2',
            collapsed && 'justify-center',
          )}>
            <div
              className="flex items-center justify-center w-7 h-7 rounded-full text-xs font-bold flex-shrink-0"
              style={{ background: 'var(--sidebar-border)', color: 'var(--sidebar-text-hover)' }}
            >
              {inicialNombre}
            </div>
            {!collapsed && (
              <div className="min-w-0">
                <p className="text-xs font-medium truncate" style={{ color: 'var(--sidebar-text-hover)' }}>{user?.nombre}</p>
              </div>
            )}
          </div>

          {!collapsed && <LanguageSwitcher variant="dark" />}

          {/* Theme picker */}
          {!collapsed ? (
            <div className="relative">
              <button
                onClick={() => setThemeMenuOpen(p => !p)}
                className="flex items-center gap-1.5 text-xs transition-colors py-1"
                style={{ color: 'var(--sidebar-text)' }}
              >
                <Palette size={12} />
                <span>Tema: <span style={{ color: 'var(--sidebar-text-hover)' }}>{THEMES.find(t => t.value === theme)?.label}</span></span>
              </button>
              {themeMenuOpen && (
                <div
                  className="absolute bottom-7 left-0 rounded-lg shadow-xl py-1 z-50 min-w-[130px]"
                  style={{ background: 'var(--sidebar-border)', border: '1px solid var(--sidebar-item-hover)' }}
                >
                  {THEMES.map(t => (
                    <button
                      key={t.value}
                      onClick={() => { setTheme(t.value); setThemeMenuOpen(false) }}
                      className="flex items-center gap-2 w-full px-3 py-1.5 text-xs transition-colors"
                      style={{ color: theme === t.value ? '#fb923c' : 'var(--sidebar-text)' }}
                    >
                      <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: t.color, border: '1px solid var(--sidebar-text)' }} />
                      {t.label}
                      {theme === t.value && <span className="ml-auto" style={{ color: '#fb923c' }}>✓</span>}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <button
              title="Cambiar tema"
              onClick={() => {
                const idx = THEMES.findIndex(t => t.value === theme)
                setTheme(THEMES[(idx + 1) % THEMES.length].value)
              }}
              className="p-1.5 rounded-md transition-colors"
              style={{ color: 'var(--sidebar-text)' }}
            >
              <Palette size={13} />
            </button>
          )}

          <button
            onClick={handleLogout}
            title={collapsed ? t('layout.logout') : undefined}
            className={cn(
              'flex items-center text-xs transition-colors hover:text-red-400',
              collapsed ? 'p-1.5 justify-center w-full rounded-md hover:bg-red-500/10' : 'gap-2 py-1',
            )}
            style={{ color: 'var(--sidebar-text)' }}
          >
            <LogOut size={13} />
            {!collapsed && t('layout.logout')}
          </button>
        </div>
      </aside>

      <main className="flex-1 overflow-y-auto" style={{ background: 'var(--page-bg)' }}>
        <Outlet />
      </main>
    </div>
  )
}
