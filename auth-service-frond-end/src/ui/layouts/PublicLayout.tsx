import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { Dumbbell, Palette } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { LanguageSwitcher } from '@/ui/components/LanguageSwitcher'
import { useThemeStore } from '@/infrastructure/store/theme/theme.store'
import type { AppTheme } from '@/infrastructure/store/theme/theme.store'
import { cn } from '@/lib/utils'

const THEMES: { value: AppTheme; label: string; color: string }[] = [
  { value: 'light',        label: 'Light',        color: '#f8fafc' },
  { value: 'dark',         label: 'Dark',         color: '#020817' },
  { value: 'dark-blue',    label: 'Dark Blue',    color: '#0d1b2a' },
  { value: 'ocean-blue',   label: 'Ocean Blue',   color: '#bfdbfe' },
  { value: 'slate-carbon', label: 'Slate Carbon', color: '#18181b' },
  { value: 'mint-pastel',  label: 'Mint Pastel',  color: '#f0fdf4' },
]

export function PublicLayout() {
  const { t } = useTranslation()
  const { theme, setTheme } = useThemeStore()
  const [themeMenuOpen, setThemeMenuOpen] = useState(false)

  useEffect(() => {
    document.body.dataset.layout = theme
    return () => { delete document.body.dataset.layout }
  }, [theme])

  const stats = [
    { value: t('publicLayout.stat1Value'), label: t('publicLayout.stat1Label') },
    { value: t('publicLayout.stat2Value'), label: t('publicLayout.stat2Label') },
    { value: t('publicLayout.stat3Value'), label: t('publicLayout.stat3Label') },
  ]

  return (
    <div className="min-h-screen flex" data-layout={theme}>
      {/* Panel izquierdo — branding (solo desktop) */}
      <div className="hidden lg:flex lg:w-1/2 relative bg-gym-950 flex-col items-center justify-center p-12 overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-orange-600/20 via-transparent to-transparent pointer-events-none" />
        <div className="absolute top-0 right-0 w-96 h-96 rounded-full bg-orange-500/10 blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 left-0 w-64 h-64 rounded-full bg-orange-500/5 blur-3xl pointer-events-none" />

        <div className="relative z-10 text-center space-y-8 max-w-sm">
          <div className="flex items-center justify-center w-20 h-20 rounded-2xl bg-orange-500 mx-auto shadow-lg shadow-orange-500/30">
            <Dumbbell size={40} className="text-white" />
          </div>
          <div>
            <h1 className="text-4xl font-extrabold text-white tracking-tight">Gym Admin</h1>
            <p className="text-slate-400 mt-3 text-lg leading-relaxed">{t('publicLayout.tagline')}</p>
          </div>
          <div className="flex gap-8 justify-center pt-2">
            {stats.map(stat => (
              <div key={stat.label} className="text-center">
                <p className="text-orange-400 font-bold text-lg">{stat.value}</p>
                <p className="text-slate-500 text-xs mt-0.5">{stat.label}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Panel derecho — formulario */}
      <div
        className="flex-1 flex items-center justify-center p-6 relative"
        style={{ background: 'var(--page-bg)' }}
      >
        {/* Top-right: idioma + tema */}
        <div className="absolute top-4 right-4 flex items-center gap-2">
          <LanguageSwitcher variant={theme === 'light' ? 'light' : 'dark'} />

          <div className="relative">
            <button
              onClick={() => setThemeMenuOpen(p => !p)}
              className="flex items-center gap-1.5 px-2 py-1.5 rounded-md transition-colors"
              style={{ color: 'var(--page-muted)', border: '1px solid var(--page-border)', background: 'var(--page-surface)' }}
              title="Cambiar tema"
            >
              <Palette size={13} />
              <span
                className="w-2.5 h-2.5 rounded-full"
                style={{ background: THEMES.find(t => t.value === theme)?.color, border: '1px solid var(--page-border)' }}
              />
            </button>
            {themeMenuOpen && (
              <div
                className="absolute top-9 right-0 rounded-lg shadow-xl py-1 z-50 min-w-[140px]"
                style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}
              >
                {THEMES.map(t => (
                  <button
                    key={t.value}
                    onClick={() => { setTheme(t.value); setThemeMenuOpen(false) }}
                    className={cn('flex items-center gap-2 w-full px-3 py-1.5 text-xs transition-colors')}
                    style={{ color: theme === t.value ? '#f97316' : 'var(--page-text)' }}
                  >
                    <span
                      className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                      style={{ background: t.color, border: '1px solid var(--page-border)' }}
                    />
                    {t.label}
                    {theme === t.value && <span className="ml-auto" style={{ color: '#f97316' }}>✓</span>}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="w-full max-w-md space-y-6">
          {/* Logo visible solo en móvil */}
          <div className="flex items-center gap-3 lg:hidden">
            <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-orange-500">
              <Dumbbell size={22} className="text-white" />
            </div>
            <span className="text-xl font-bold" style={{ color: 'var(--page-text)' }}>Gym Admin</span>
          </div>

          <Outlet />
        </div>
      </div>
    </div>
  )
}
