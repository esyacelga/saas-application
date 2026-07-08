import { Dumbbell } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { PulsingDots } from './PulsingDots'

export function FullScreenLoader() {
  const { t } = useTranslation()

  return (
    <div className="min-h-screen bg-gym-950 flex items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        <div className="w-14 h-14 rounded-2xl bg-orange-500 flex items-center justify-center">
          <Dumbbell size={28} className="text-white" />
        </div>
        <PulsingDots size="lg" />
        <p className="text-slate-500 text-sm">{t('common.loading')}</p>
      </div>
    </div>
  )
}
