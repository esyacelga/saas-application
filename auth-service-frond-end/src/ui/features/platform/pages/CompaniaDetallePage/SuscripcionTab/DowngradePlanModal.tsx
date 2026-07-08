import { useState } from 'react'
import { Loader2 } from 'lucide-react'
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
import type { DowngradeResponse } from '@/infrastructure/http/platform/platform.dto'

const usecase = new GestionarSuscripcionUseCase(platformRepository)

interface Props {
  open: boolean
  idCompania: number
  suscripcionActiva: CompaniaPlan | null
  onClose: () => void
  onDowngraded: () => void
}

export function DowngradePlanModal({ open, idCompania, suscripcionActiva, onClose, onDowngraded }: Props) {
  const { planes } = usePlatformStore()
  const [idPlanNuevo, setIdPlanNuevo] = useState<number>(0)
  const [submitting, setSubmitting] = useState(false)
  const [resultado, setResultado] = useState<DowngradeResponse | null>(null)

  const planActual = planes.find(p => p.id === suscripcionActiva?.idPlan)

  const handleSubmit = async () => {
    if (!idPlanNuevo) return
    setSubmitting(true)
    try {
      const res = await usecase.downgradePlan(idCompania, { idPlanNuevo })
      setResultado(res)
      toast.success('Downgrade programado')
      onDowngraded()
    } catch {
      toast.error('Error al realizar el downgrade')
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
          <DialogHeader><DialogTitle>Downgrade programado</DialogTitle></DialogHeader>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between"><span className="text-slate-500">Estado:</span><span className="font-medium">{resultado.estado}</span></div>
            <div className="flex justify-between"><span className="text-slate-500">Efectivo desde:</span><span className="font-medium">{resultado.efectivoDe}</span></div>
            <div className="flex justify-between"><span className="text-slate-500">Crédito generado:</span><span className="font-medium">${resultado.creditoGenerado.toFixed(2)}</span></div>
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
        <DialogHeader><DialogTitle>Downgrade de plan</DialogTitle></DialogHeader>
        <div className="space-y-4">
          <div>
            <Label>Nuevo plan (precio menor al actual)</Label>
            <div className="mt-1">
              <PlanSelector
                value={idPlanNuevo ? String(idPlanNuevo) : undefined}
                onValueChange={(v: string | null) => v && setIdPlanNuevo(Number(v))}
                filter={p => p.precioMensual < (planActual?.precioMensual ?? Infinity)}
              />
            </div>
          </div>
          <Alert>
            <AlertDescription className="text-xs">
              El plan actual sigue activo hasta su fecha de vencimiento. El nuevo plan entrará en vigor en la fecha programada por el backend.
            </AlertDescription>
          </Alert>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>Cancelar</Button>
          <Button onClick={handleSubmit} disabled={submitting || !idPlanNuevo}>
            {submitting && <Loader2 size={14} className="animate-spin mr-2" />}
            Confirmar downgrade
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
