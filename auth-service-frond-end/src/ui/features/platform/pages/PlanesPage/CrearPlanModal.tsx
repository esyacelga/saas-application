import { useForm } from 'react-hook-form'
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
import { crearPlanSchema, type CrearPlanForm } from '../../schemas/crear-plan.schema'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarPlanUseCase } from '@/application/platform/GestionarPlan.usecase'
import type { Plan } from '@/domain/platform/entities/Plan.entity'

const usecase = new GestionarPlanUseCase(platformRepository)

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (plan: Plan) => void
}

export function CrearPlanModal({ open, onClose, onCreated }: Props) {
  const { t } = useTranslation()
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<CrearPlanForm, unknown, CrearPlanForm>({
    resolver: zodResolver(crearPlanSchema) as never,
    defaultValues: { nombre: '', descripcion: '', precioMensual: 0 },
  })

  const onSubmit = async (values: CrearPlanForm) => {
    try {
      const plan = await usecase.crearPlan({
        nombre: values.nombre,
        descripcion: values.descripcion ?? '',
        precioMensual: values.precioMensual,
      })
      toast.success(t('platform.planes.createSuccess'))
      reset()
      onCreated(plan)
      onClose()
    } catch {
      toast.error(t('platform.planes.createError'))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nuevo plan</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label style={{ color: 'var(--page-text)' }}>Nombre</Label>
            <Input {...register('nombre')} placeholder="Plan Premium" className="mt-1" />
            {errors.nombre && <p className="text-xs text-red-500 mt-1">{errors.nombre.message}</p>}
          </div>
          <div>
            <Label style={{ color: 'var(--page-text)' }}>Descripción <span className="text-xs" style={{ color: 'var(--page-muted)' }}>(opcional)</span></Label>
            <Input {...register('descripcion')} placeholder={t('platform.planes.descPlaceholder')} className="mt-1" />
          </div>
          <div>
            <Label style={{ color: 'var(--page-text)' }}>Precio mensual (USD)</Label>
            <Input {...register('precioMensual')} type="number" step="0.01" placeholder="49.99" className="mt-1" />
            {errors.precioMensual && <p className="text-xs text-red-500 mt-1">{errors.precioMensual.message}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 size={14} className="animate-spin mr-2" />}
              Crear plan
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
