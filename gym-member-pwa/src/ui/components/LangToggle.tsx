import { useTranslation } from 'react-i18next'

const LANGS = ['es', 'en'] as const
type Lang = (typeof LANGS)[number]

export function LangToggle() {
  const { i18n } = useTranslation()
  const current = (LANGS.includes(i18n.language as Lang) ? i18n.language : 'es') as Lang

  const toggle = (lang: Lang) => {
    if (lang === current) return
    i18n.changeLanguage(lang)
    localStorage.setItem('gym-lang', lang)
  }

  return (
    <div
      role="group"
      aria-label="Language"
      className="flex items-center rounded-lg bg-slate-800/70 ring-1 ring-white/8 p-0.5 gap-0.5"
    >
      {LANGS.map((lang) => (
        <button
          key={lang}
          type="button"
          onClick={() => toggle(lang)}
          aria-pressed={lang === current}
          className={`rounded-md px-2.5 py-1 text-xs font-semibold uppercase tracking-wider transition-all ${
            lang === current
              ? 'bg-accent-600 text-white shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          {lang}
        </button>
      ))}
    </div>
  )
}
