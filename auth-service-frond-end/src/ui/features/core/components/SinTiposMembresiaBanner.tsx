import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Tag } from 'lucide-react'
import { Button } from 'primereact/button'

interface Props {
  caso: 'sin_tipos' | 'todos_desactivados'
  canCreate: boolean
  onNavegar?: () => void
}

export function SinTiposMembresiaBanner({ caso, canCreate, onNavegar }: Props) {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const handleCta = () => {
    onNavegar?.()
    navigate('/admin/tipos-membresia')
  }

  const titleKey = caso === 'sin_tipos' ? 'sinTipos.titleNoHay' : 'sinTipos.titleDesactivados'

  let subtitleKey: string
  let ctaKey: string | null = null

  if (caso === 'sin_tipos') {
    if (canCreate) {
      subtitleKey = 'sinTipos.subtitleCrear'
      ctaKey = 'sinTipos.ctaCrear'
    } else {
      subtitleKey = 'sinTipos.subtitleSinPermiso'
    }
  } else {
    if (canCreate) {
      subtitleKey = 'sinTipos.subtitleReactivar'
      ctaKey = 'sinTipos.ctaReactivar'
    } else {
      subtitleKey = 'sinTipos.subtitleDesactivadosSinPermiso'
    }
  }

  return (
    <div className="flex flex-col items-center justify-center py-10 text-center px-4">
      <Tag size={32} style={{ color: 'var(--page-border)' }} className="mb-3" />
      <p style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--page-text)' }}>
        {t(titleKey)}
      </p>
      <p style={{ fontSize: '0.75rem', color: 'var(--page-muted)', marginTop: '4px' }}>
        {t(subtitleKey)}
      </p>
      {ctaKey && (
        <div className="mt-4">
          <Button
            label={t(ctaKey)}
            severity="warning"
            size="small"
            onClick={handleCta}
          />
        </div>
      )}
    </div>
  )
}
