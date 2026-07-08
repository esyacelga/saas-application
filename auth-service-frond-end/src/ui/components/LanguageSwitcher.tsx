import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'

interface Props {
  variant?: 'dark' | 'light'
}

const LANGS = [
  { code: 'es', label: 'ES', flag: '🇪🇸' },
  { code: 'en', label: 'EN', flag: '🇬🇧' },
] as const

export function LanguageSwitcher({ variant = 'dark' }: Props) {
  const { i18n, t } = useTranslation()
  const current = i18n.language.startsWith('es') ? 'es' : 'en'

  const trackClass = variant === 'dark'
    ? 'bg-gym-800 border border-gym-700'
    : 'bg-slate-100 border border-slate-200'

  const inactiveClass = variant === 'dark'
    ? 'text-slate-400 hover:text-slate-200'
    : 'text-slate-400 hover:text-slate-600'

  const ariaLabel = current === 'es'
    ? t('common.switchToEnglish')
    : t('common.switchToSpanish')

  return (
    <div
      className={cn('inline-flex items-center rounded-lg p-0.5 gap-0.5', trackClass)}
      role="group"
      aria-label={ariaLabel}
    >
      {LANGS.map(lang => {
        const isActive = current === lang.code
        return (
          <button
            key={lang.code}
            onClick={() => i18n.changeLanguage(lang.code)}
            disabled={isActive}
            aria-pressed={isActive}
            className={cn(
              'flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold transition-all duration-200',
              isActive
                ? 'bg-orange-500 text-white shadow-sm shadow-orange-500/30 cursor-default'
                : cn('cursor-pointer', inactiveClass)
            )}
          >
            <span className="text-sm leading-none">{lang.flag}</span>
            <span className="tracking-wide">{lang.label}</span>
          </button>
        )
      })}
    </div>
  )
}
