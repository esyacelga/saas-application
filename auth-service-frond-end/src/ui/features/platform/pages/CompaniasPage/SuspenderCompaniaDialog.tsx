import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, AlertTriangle } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { suspenderSchema, type SuspenderForm } from '../../schemas/suspender.schema'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarCompaniaUseCase } from '@/application/platform/GestionarCompania.usecase'
import type { Compania } from '@/domain/platform/entities/Plan.entity'

const usecase = new GestionarCompaniaUseCase(platformRepository)

interface Props {
  open: boolean
  compania: Compania | null
  onClose: () => void
  onSuspended: () => void
}

export function SuspenderCompaniaDialog({ open, compania, onClose, onSuspended }: Props) {
  const { t } = useTranslation()
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<SuspenderForm>({
    resolver: zodResolver(suspenderSchema),
  })

  const onSubmit = async (values: SuspenderForm) => {
    if (!compania) return
    try {
      await usecase.suspenderCompania(compania.id, { motivo: values.motivo })
      toast.success(t('platform.companias.suspendSuccess', { name: compania.nombre }))
      reset()
      onSuspended()
      onClose()
    } catch {
      toast.error(t('platform.companias.suspendError'))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-red-600">
            <AlertTriangle size={18} />
            Suspender compañía
          </DialogTitle>
        </DialogHeader>
        <p className="text-sm text-slate-600">
          Vas a suspender <strong>{compania?.nombre}</strong>. Esto impedirá el acceso de su personal y clientes. Esta acción es reversible.
        </p>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label>Motivo de suspensión *</Label>
            <Input {...register('motivo')} placeholder={t('platform.companias.suspendMotivePlaceholder')} className="mt-1" />
            {errors.motivo && <p className="text-xs text-red-500 mt-1">{errors.motivo.message}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" variant="destructive" disabled={isSubmitting}>
              {isSubmitting && <Loader2 size={14} className="animate-spin mr-2" />}
              Suspender
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
