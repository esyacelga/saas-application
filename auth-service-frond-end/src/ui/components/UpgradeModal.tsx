import { useNavigate } from 'react-router-dom'
import { TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useLimitPlanModalStore } from '@/infrastructure/store/plan/useLimitPlanModalStore'

export function UpgradeModal() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { abierto, recurso, actual, maximo, planActual, cerrarModal } = useLimitPlanModalStore()

  // Determina el título según recurso × planActual
  function getTitulo(): string {
    if (planActual === 'TRIAL') return t('upgradeModal.title.trialLimite')
    if (recurso === 'clientes') return t('upgradeModal.title.clientes')
    if (recurso === 'staff') return t('upgradeModal.title.staff')
    if (recurso === 'sucursales') return t('upgradeModal.title.sucursales')
    return t('upgradeModal.title.clientes')
  }

  // Subtítulo
  function getSubtitulo(): string {
    const recursoLabel = recurso ?? ''
    const maxLabel = maximo ?? '?'
    const actLabel = actual ?? '?'
    if (planActual === 'TRIAL') {
      return t('upgradeModal.subtitle.trial', { maximo: maxLabel, actual: actLabel, recurso: recursoLabel })
    }
    return t('upgradeModal.subtitle.free', { maximo: maxLabel, actual: actLabel, recurso: recursoLabel })
  }

  // Cuerpo / beneficio
  function getCuerpo(): string {
    if (planActual === 'TRIAL') return t('upgradeModal.body.trial')
    if (recurso === 'clientes') return t('upgradeModal.body.clientes')
    if (recurso === 'staff') return t('upgradeModal.body.staff')
    if (recurso === 'sucursales') return t('upgradeModal.body.sucursales')
    return t('upgradeModal.body.clientes')
  }

  // Color del icono
  const iconColor = planActual === 'TRIAL' ? '#ef4444' : '#f59e0b'

  function handleVerPlan() {
    cerrarModal()
    navigate('/admin/mi-suscripcion')
  }

  return (
    <Dialog open={abierto} onOpenChange={(open) => { if (!open) cerrarModal() }} modal>
      <DialogContent
        className="max-w-sm"
        aria-label={t('upgradeModal.close')}
      >
        {/* Icono central */}
        <div className="flex flex-col items-center gap-3 pt-2 text-center">
          <TriangleAlert size={40} style={{ color: iconColor }} />

          <DialogTitle className="text-base font-semibold" style={{ color: 'var(--page-text)' }}>
            {getTitulo()}
          </DialogTitle>

          <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
            {getSubtitulo()}
          </p>

          {/* Bloque de beneficio */}
          <div
            className="w-full rounded-lg p-3 text-left"
            style={{
              background: 'var(--page-bg)',
              border: '1px solid var(--page-border)',
            }}
          >
            <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
              {getCuerpo()}
            </p>
          </div>
        </div>

        {/* Acciones */}
        <div className="flex flex-col gap-2 mt-2">
          <Button
            className="w-full bg-orange-500 hover:bg-orange-600 text-white border-0"
            onClick={handleVerPlan}
            autoFocus
          >
            {t('upgradeModal.cta.primary')}
          </Button>
          <Button
            variant="ghost"
            className="w-full"
            onClick={cerrarModal}
          >
            {t('upgradeModal.cta.secondary')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
