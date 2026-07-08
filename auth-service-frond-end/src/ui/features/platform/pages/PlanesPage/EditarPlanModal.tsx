import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { crearPlanSchema, type CrearPlanForm } from '../../schemas/crear-plan.schema'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarPlanUseCase } from '@/application/platform/GestionarPlan.usecase'
import type { Plan } from '@/domain/platform/entities/Plan.entity'

const usecase = new GestionarPlanUseCase(platformRepository)

interface Props {
  open: boolean
  plan: Plan | null
  onClose: () => void
  onUpdated: (plan: Plan) => void
}

export function EditarPlanModal({ open, plan, onClose, onUpdated }: Props) {
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<CrearPlanForm, unknown, CrearPlanForm>({
    resolver: zodResolver(crearPlanSchema) as never,
  })

  useEffect(() => {
    if (plan) reset({ nombre: plan.nombre, descripcion: plan.descripcion, precioMensual: plan.precioMensual })
  }, [plan, reset])

  const onSubmit = async (values: CrearPlanForm) => {
    if (!plan) return
    try {
      const updated = await usecase.actualizarPlan(plan.id, {
        nombre: values.nombre,
        descripcion: values.descripcion,
        precioMensual: values.precioMensual,
      })
      toast.success('Plan actualizado')
      onUpdated(updated)
      onClose()
    } catch {
      toast.error('Error al actualizar el plan')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Editar plan</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label style={{ color: 'var(--page-text)' }}>Nombre</Label>
            <Input {...register('nombre')} className="mt-1" />
            {errors.nombre && <p className="text-xs text-red-500 mt-1">{errors.nombre.message}</p>}
          </div>
          <div>
            <Label style={{ color: 'var(--page-text)' }}>Descripción <span className="text-xs" style={{ color: 'var(--page-muted)' }}>(opcional)</span></Label>
            <Input {...register('descripcion')} className="mt-1" />
          </div>
          <div>
            <Label style={{ color: 'var(--page-text)' }}>Precio mensual (USD)</Label>
            <Input {...register('precioMensual')} type="number" step="0.01" className="mt-1" />
            {errors.precioMensual && <p className="text-xs text-red-500 mt-1">{errors.precioMensual.message}</p>}
            <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>
              Cambiar el precio no afecta contratos vigentes, solo nuevas suscripciones.
            </p>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 size={14} className="animate-spin mr-2" />}
              Guardar cambios
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
