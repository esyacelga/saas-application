import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarSuscripcionUseCase } from '@/application/platform/GestionarSuscripcion.usecase'
import { PlanSelector } from '../../../components/PlanSelector'
import { usePlatformStore } from '@/infrastructure/store/platform/platform.store'
import type { CompaniaPlan } from '@/domain/platform/entities/Plan.entity'

const usecase = new GestionarSuscripcionUseCase(platformRepository)

const schema = z.object({
  idPlan: z.coerce.number().positive('Selecciona un plan'),
  meses: z.coerce.number().int().min(1, 'Mínimo 1 mes'),
})
type Form = z.infer<typeof schema>

interface Props {
  open: boolean
  idCompania: number
  suscripcionActiva: CompaniaPlan | null
  onClose: () => void
  onRenewed: () => void
}

export function RenovarSuscripcionModal({ open, idCompania, suscripcionActiva, onClose, onRenewed }: Props) {
  const { t } = useTranslation()
  const { planes } = usePlatformStore()
  const { register, handleSubmit, setValue, watch, formState: { errors, isSubmitting } } = useForm<Form, unknown, Form>({
    resolver: zodResolver(schema) as never,
    defaultValues: { idPlan: suscripcionActiva?.idPlan ?? 0, meses: 1 },
  })
  const [idPlanValue, mesesValue] = watch(['idPlan', 'meses'])
  const planSeleccionado = planes.find(p => p.id === idPlanValue)
  const totalEstimado = planSeleccionado ? planSeleccionado.precioMensual * (mesesValue || 0) : 0

  const onSubmit = async (values: Form) => {
    try {
      await usecase.renovarSuscripcion(idCompania, { idPlan: values.idPlan, meses: values.meses })
      toast.success(t('platform.suscripcion.renewSuccess'))
      onRenewed()
      onClose()
    } catch {
      toast.error(t('platform.suscripcion.renewError'))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Renovar suscripción</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label>Plan</Label>
            <div className="mt-1">
              <PlanSelector
                value={idPlanValue ? String(idPlanValue) : undefined}
                onValueChange={(v: string | null) => v && setValue('idPlan', Number(v))}
              />
            </div>
            {errors.idPlan && <p className="text-xs text-red-500 mt-1">{errors.idPlan.message}</p>}
          </div>
          <div>
            <Label>Meses</Label>
            <Input {...register('meses')} type="number" min={1} className="mt-1" />
            {errors.meses && <p className="text-xs text-red-500 mt-1">{errors.meses.message}</p>}
          </div>
          {planSeleccionado && (
            <div className="bg-slate-50 rounded-lg p-3 text-sm">
              <p className="text-slate-500">Total estimado</p>
              <p className="text-lg font-bold text-slate-900">${totalEstimado.toFixed(2)}</p>
            </div>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 size={14} className="animate-spin mr-2" />}
              Renovar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
