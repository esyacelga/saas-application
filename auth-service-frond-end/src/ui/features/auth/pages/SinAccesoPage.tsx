import { Link } from 'react-router-dom'
import { Lock } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export function SinAccesoPage() {
  const { t } = useTranslation()

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] py-16 px-4 text-center">
      <div className="flex items-center justify-center w-16 h-16 rounded-full bg-slate-100 mb-4">
        <Lock size={28} className="text-slate-400" />
      </div>
      <h2 className="text-xl font-bold text-slate-800">{t('noAccess.title')}</h2>
      <p className="text-slate-500 text-sm mt-2 max-w-xs leading-relaxed">
        {t('noAccess.description')}
      </p>
      <Link
        to="/admin/dashboard"
        className="mt-6 inline-flex items-center gap-2 bg-orange-500 hover:bg-orange-600 text-white font-semibold text-sm px-5 py-2.5 rounded-lg transition-colors"
      >
        {t('noAccess.backToHome')}
      </Link>
    </div>
  )
}
