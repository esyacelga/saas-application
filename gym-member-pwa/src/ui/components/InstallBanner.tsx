import { Download, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useInstallPrompt } from '@/ui/hooks/useInstallPrompt'

export function InstallBanner() {
  const { t } = useTranslation()
  const { canInstall, install, dismiss } = useInstallPrompt()

  if (!canInstall) return null

  return (
    <div className="mx-4 mt-4 flex items-center gap-3 rounded-xl bg-accent-900/40 border border-accent-700/50 px-4 py-3">
      <img src="/pwa-64x64.png" alt="" className="size-10 rounded-lg shrink-0" />

      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-slate-50 leading-tight">
          {t('install.title')}
        </p>
        <p className="text-xs text-slate-400 leading-tight mt-0.5">
          {t('install.description')}
        </p>
      </div>

      <button
        onClick={install}
        className="flex items-center gap-1.5 bg-accent-600 hover:bg-accent-500 active:bg-accent-700 text-white text-xs font-semibold px-3 py-2 rounded-lg transition-colors shrink-0"
      >
        <Download size={14} />
        {t('install.button')}
      </button>

      <button
        onClick={dismiss}
        aria-label={t('install.dismiss')}
        className="text-slate-500 hover:text-slate-300 transition-colors shrink-0 -mr-1"
      >
        <X size={18} />
      </button>
    </div>
  )
}