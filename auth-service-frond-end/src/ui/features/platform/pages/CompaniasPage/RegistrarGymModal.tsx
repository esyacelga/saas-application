import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, Copy, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { registrarGymSchema, type RegistrarGymForm } from '../../schemas/registrar-gym.schema'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarCompaniaUseCase } from '@/application/platform/GestionarCompania.usecase'
import { PlanSelector } from '../../components/PlanSelector'
import type { RegistrarGymResponse } from '@/infrastructure/http/platform/platform.dto'

const usecase = new GestionarCompaniaUseCase(platformRepository)

interface Props { open: boolean; onClose: () => void; onCreated: () => void }

export function RegistrarGymModal({ open, onClose, onCreated }: Props) {
  const [result, setResult] = useState<RegistrarGymResponse | null>(null)
  const [copied, setCopied] = useState(false)

  const { register, handleSubmit, reset, setValue, watch, formState: { errors, isSubmitting } } = useForm<RegistrarGymForm, unknown, RegistrarGymForm>({
    resolver: zodResolver(registrarGymSchema) as never,
  })
  const idPlanValue = watch('idPlan')

  const onSubmit = async (values: RegistrarGymForm) => {
    try {
      const res = await usecase.registrarGym({
        nombre: values.nombre,
        ruc: values.ruc,
        correo: values.correo || undefined,
        telefono: values.telefono || undefined,
        whatsapp: values.whatsapp || undefined,
        idPlan: values.idPlan,
        nombreSucursal: values.nombreSucursal,
        direccionSucursal: values.direccionSucursal || undefined,
      })
      setResult(res)
      reset()
      onCreated()
    } catch {
      toast.error('Error al registrar el gimnasio')
    }
  }

  const handleCopyToken = async () => {
    if (!result) return
    await navigator.clipboard.writeText(result.qrToken)
    setCopied(true)
    toast.success('Token copiado')
    setTimeout(() => setCopied(false), 2000)
  }

  const handleClose = () => {
    setResult(null)
    setCopied(false)
    onClose()
  }

  if (result) {
    return (
      <Dialog open={open} onOpenChange={handleClose}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-green-600">
              <CheckCircle2 size={20} /> Gimnasio registrado
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <p className="text-sm text-slate-600">El gimnasio fue registrado exitosamente. Guarda el QR token de la sede principal:</p>
            <div className="bg-slate-900 rounded-lg p-3">
              <p className="text-xs text-slate-400 mb-1">QR Token</p>
              <code className="font-mono text-xs text-green-400 break-all">{result.qrToken}</code>
            </div>
            <Button variant="outline" className="w-full" onClick={handleCopyToken}>
              {copied ? <CheckCircle2 size={14} className="mr-2 text-green-500" /> : <Copy size={14} className="mr-2" />}
              {copied ? 'Token copiado' : 'Copiar token'}
            </Button>
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
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Registrar nuevo gimnasio</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3">Datos del gimnasio</p>
            <div className="space-y-3">
              <div>
                <Label>Nombre *</Label>
                <Input {...register('nombre')} placeholder="Gym Elite" className="mt-1" />
                {errors.nombre && <p className="text-xs text-red-500 mt-1">{errors.nombre.message}</p>}
              </div>
              <div>
                <Label>RUC *</Label>
                <Input {...register('ruc')} placeholder="1234567890001" className="mt-1" />
                {errors.ruc && <p className="text-xs text-red-500 mt-1">{errors.ruc.message}</p>}
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label>Correo</Label>
                  <Input {...register('correo')} type="email" placeholder="gym@email.com" className="mt-1" />
                  {errors.correo && <p className="text-xs text-red-500 mt-1">{errors.correo.message}</p>}
                </div>
                <div>
                  <Label>Teléfono</Label>
                  <Input {...register('telefono')} placeholder="+593..." className="mt-1" />
                </div>
              </div>
              <div>
                <Label>WhatsApp</Label>
                <Input {...register('whatsapp')} placeholder="+593..." className="mt-1" />
              </div>
            </div>
          </div>

          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3">Plan inicial</p>
            <PlanSelector
              value={idPlanValue ? String(idPlanValue) : undefined}
              onValueChange={(v: string) => setValue('idPlan', Number(v))}
            />
            {errors.idPlan && <p className="text-xs text-red-500 mt-1">{errors.idPlan.message}</p>}
          </div>

          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3">Sede principal</p>
            <div className="space-y-3">
              <div>
                <Label>Nombre de la sede *</Label>
                <Input {...register('nombreSucursal')} placeholder="Sede Central" className="mt-1" />
                {errors.nombreSucursal && <p className="text-xs text-red-500 mt-1">{errors.nombreSucursal.message}</p>}
              </div>
              <div>
                <Label>Dirección</Label>
                <Input {...register('direccionSucursal')} placeholder="Av. Principal 123" className="mt-1" />
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={handleClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 size={14} className="animate-spin mr-2" />}
              Registrar gym
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
