import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarSucursalUseCase } from '@/application/platform/GestionarSucursal.usecase'
import type { Sucursal } from '@/domain/platform/entities/Plan.entity'

const usecase = new GestionarSucursalUseCase(platformRepository)

const schema = z.object({
  nombre: z.string().min(2, 'Nombre requerido'),
  direccion: z.string().optional(),
})
type Form = z.infer<typeof schema>

interface Props {
  open: boolean
  sucursal: Sucursal | null
  onClose: () => void
  onUpdated: (s: Sucursal) => void
}

export function EditarSucursalModal({ open, sucursal, onClose, onUpdated }: Props) {
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<Form>({
    resolver: zodResolver(schema),
  })

  useEffect(() => {
    if (sucursal) reset({ nombre: sucursal.nombre, direccion: sucursal.direccion })
  }, [sucursal, reset])

  const onSubmit = async (values: Form) => {
    if (!sucursal) return
    try {
      const updated = await usecase.actualizarSucursal(sucursal.id, values)
      toast.success('Sucursal actualizada')
      onUpdated(updated)
      onClose()
    } catch {
      toast.error('Error al actualizar la sucursal')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader><DialogTitle>Editar sucursal</DialogTitle></DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label>Nombre</Label>
            <Input {...register('nombre')} className="mt-1" />
            {errors.nombre && <p className="text-xs text-red-500 mt-1">{errors.nombre.message}</p>}
          </div>
          <div>
            <Label>Dirección</Label>
            <Input {...register('direccion')} className="mt-1" />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 size={14} className="animate-spin mr-2" />}
              Guardar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
