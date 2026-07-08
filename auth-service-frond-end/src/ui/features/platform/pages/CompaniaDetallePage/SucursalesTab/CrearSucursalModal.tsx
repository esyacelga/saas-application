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
import { Checkbox } from '@/components/ui/checkbox'
import { crearSucursalSchema, type CrearSucursalForm } from '../../../schemas/crear-sucursal.schema'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarSucursalUseCase } from '@/application/platform/GestionarSucursal.usecase'
import type { Sucursal } from '@/domain/platform/entities/Plan.entity'

const usecase = new GestionarSucursalUseCase(platformRepository)

interface Props {
  open: boolean
  idCompania: number
  onClose: () => void
  onCreated: (s: Sucursal) => void
}

export function CrearSucursalModal({ open, idCompania, onClose, onCreated }: Props) {
  const { register, handleSubmit, reset, setValue, watch, formState: { errors, isSubmitting } } = useForm<CrearSucursalForm, unknown, CrearSucursalForm>({
    resolver: zodResolver(crearSucursalSchema) as never,
    defaultValues: { esPrincipal: false },
  })
  const esPrincipal = watch('esPrincipal')

  const onSubmit = async (values: CrearSucursalForm) => {
    try {
      const s = await usecase.crearSucursal(idCompania, values)
      toast.success('Sucursal creada')
      reset()
      onCreated(s)
      onClose()
    } catch {
      toast.error('Error al crear la sucursal')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader><DialogTitle>Nueva sucursal</DialogTitle></DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label>Nombre *</Label>
            <Input {...register('nombre')} placeholder="Sucursal Norte" className="mt-1" />
            {errors.nombre && <p className="text-xs text-red-500 mt-1">{errors.nombre.message}</p>}
          </div>
          <div>
            <Label>Dirección</Label>
            <Input {...register('direccion')} placeholder="Av. 123" className="mt-1" />
          </div>
          <div className="flex items-center gap-2">
            <Checkbox
              id="esPrincipal"
              checked={esPrincipal}
              onCheckedChange={v => setValue('esPrincipal', Boolean(v))}
            />
            <Label htmlFor="esPrincipal" className="cursor-pointer">Es sede principal</Label>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 size={14} className="animate-spin mr-2" />}
              Crear sucursal
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
