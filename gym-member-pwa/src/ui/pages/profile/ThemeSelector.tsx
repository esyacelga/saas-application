import { Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { THEMES, useThemeStore } from '@/infrastructure/store/theme.store'
import { useCurrentUser } from '@/infrastructure/store/auth.store'

export function ThemeSelector() {
  const { t } = useTranslation()
  const { theme, setTheme } = useThemeStore()
  const user = useCurrentUser()
  const userGender = user?.sexo ?? null

  return (
    <div className="rounded-xl bg-slate-800 border border-slate-700 p-4 space-y-3">
      <h2 className="text-sm font-semibold text-slate-300">{t('profile.theme.title')}</h2>
      <div className="grid grid-cols-3 gap-2">
        {THEMES.map((meta) => {
          const isSelected = theme === meta.id
          const isSuggested = userGender !== null && meta.gender === userGender
          return (
            <button
              key={meta.id}
              onClick={() => setTheme(meta.id)}
              aria-pressed={isSelected}
              aria-label={`${meta.name}${isSuggested ? ' — sugerido' : ''}`}
              className={`relative flex flex-col items-center gap-1.5 rounded-xl p-3 border-2 transition-all ${
                isSelected
                  ? 'border-white/30 bg-white/5'
                  : 'border-slate-700 hover:border-slate-500'
              }`}
            >
              {isSuggested && (
                <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-accent-400" />
              )}
              <div
                className="w-9 h-9 rounded-full flex items-center justify-center shadow-md"
                style={{ backgroundColor: meta.hex }}
              >
                {isSelected && <Check size={15} className="text-white" strokeWidth={3} />}
              </div>
              <span className="text-[11px] font-medium text-slate-300 leading-none">
                {meta.name}
              </span>
            </button>
          )
        })}
      </div>
      {userGender !== null && (
        <p className="text-[10px] text-slate-500">{t('profile.theme.hint')}</p>
      )}
    </div>
  )
}
