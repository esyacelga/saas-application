import { useState } from 'react'
import { Loader2, AlertTriangle } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarSuscripcionUseCase } from '@/application/platform/GestionarSuscripcion.usecase'
import { PlanSelector } from '../../../components/PlanSelector'
import { usePlatformStore } from '@/infrastructure/store/platform/platform.store'
import type { CompaniaPlan } from '@/domain/platform/entities/Plan.entity'
import type { UpgradeResponse } from '@/infrastructure/http/platform/platform.dto'

const usecase = new GestionarSuscripcionUseCase(platformRepository)

interface Props {
  open: boolean
  idCompania: number
  suscripcionActiva: CompaniaPlan | null
  onClose: () => void
  onUpgraded: () => void
}

export function UpgradePlanModal({ open, idCompania, suscripcionActiva, onClose, onUpgraded }: Props) {
  const { planes } = usePlatformStore()
  const [idPlanNuevo, setIdPlanNuevo] = useState<number>(0)
  const [submitting, setSubmitting] = useState(false)
  const [resultado, setResultado] = useState<UpgradeResponse | null>(null)

  const planActual = planes.find(p => p.id === suscripcionActiva?.idPlan)
  const creditoEstimado = suscripcionActiva
    ? ((suscripcionActiva.diasRestantes / 30) * (planActual?.precioMensual ?? 0))
    : 0

  const handleSubmit = async () => {
    if (!idPlanNuevo) return
    setSubmitting(true)
    try {
      const res = await usecase.upgradePlan(idCompania, { idPlanNuevo })
      setResultado(res)
      toast.success('Upgrade realizado')
      onUpgraded()
    } catch {
      toast.error('Error al realizar el upgrade')
    } finally {
      setSubmitting(false)
    }
  }

  const handleClose = () => {
    setResultado(null)
    setIdPlanNuevo(0)
    onClose()
  }

  if (resultado) {
    return (
      <Dialog open={open} onOpenChange={handleClose}>
        <DialogContent>
          <DialogHeader><DialogTitle>Upgrade completado</DialogTitle></DialogHeader>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between"><span className="text-slate-500">Crédito aplicado:</span><span className="font-medium">${resultado.creditoAplicado.toFixed(2)}</span></div>
            <div className="flex justify-between"><span className="text-slate-500">Monto a pagar:</span><span className="font-bold text-lg">${resultado.montoAPagar.toFixed(2)}</span></div>
          </div>
          <DialogFooter>
            <Button onClick={handleClose}>Cerrar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    )
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader><DialogTitle>Upgrade de plan</DialogTitle></DialogHeader>
        <div className="space-y-4">
          <div>
            <Label>Nuevo plan (precio mayor al actual)</Label>
            <div className="mt-1">
              <PlanSelector
                value={idPlanNuevo ? String(idPlanNuevo) : undefined}
                onValueChange={(v: string | null) => v && setIdPlanNuevo(Number(v))}
                filter={p => p.precioMensual > (planActual?.precioMensual ?? 0)}
              />
            </div>
          </div>
          {planActual && (
            <div className="bg-slate-50 rounded-lg p-3 text-sm">
              <p className="text-slate-500">Crédito proporcional estimado</p>
              <p className="font-medium">${creditoEstimado.toFixed(2)} ({suscripcionActiva?.diasRestantes}d × ${(planActual.precioMensual / 30).toFixed(4)}/d)</p>
            </div>
          )}
          <Alert>
            <AlertTriangle size={14} className="text-amber-500" />
            <AlertDescription className="text-xs">
              El plan actual será cancelado inmediatamente al confirmar.
            </AlertDescription>
          </Alert>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>Cancelar</Button>
          <Button onClick={handleSubmit} disabled={submitting || !idPlanNuevo}>
            {submitting && <Loader2 size={14} className="animate-spin mr-2" />}
            Confirmar upgrade
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
