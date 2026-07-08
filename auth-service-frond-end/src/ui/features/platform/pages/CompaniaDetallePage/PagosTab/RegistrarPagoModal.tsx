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
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { registrarPagoSchema, type RegistrarPagoForm } from '../../../schemas/registrar-pago.schema'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarPagoUseCase } from '@/application/platform/GestionarPago.usecase'
import type { Pago } from '@/domain/platform/entities/Plan.entity'

const usecase = new GestionarPagoUseCase(platformRepository)

interface Props {
  open: boolean
  idCompaniaPlan: number
  onClose: () => void
  onCreated: (pago: Pago) => void
}

const METODOS: { value: string; label: string }[] = [
  { value: 'efectivo', label: 'Efectivo' },
  { value: 'transferencia', label: 'Transferencia' },
  { value: 'tarjeta', label: 'Tarjeta' },
]
const TIPOS: { value: string; label: string }[] = [
  { value: 'pago_completo', label: 'Pago completo' },
  { value: 'diferencia_upgrade', label: 'Diferencia upgrade' },
  { value: 'credito_downgrade', label: 'Crédito downgrade' },
  { value: 'renovacion', label: 'Renovación' },
]

export function RegistrarPagoModal({ open, idCompaniaPlan, onClose, onCreated }: Props) {
  const { t } = useTranslation()
  const { register, handleSubmit, reset, setValue, formState: { errors, isSubmitting } } = useForm<RegistrarPagoForm, unknown, RegistrarPagoForm>({
    resolver: zodResolver(registrarPagoSchema) as never,
  })

  const onSubmit = async (values: RegistrarPagoForm) => {
    try {
      const pago = await usecase.registrarPago({
        idCompaniaPlan,
        monto: values.monto,
        metodoPago: values.metodoPago,
        tipoPago: values.tipoPago,
        referencia: values.referencia || undefined,
        periodoDesde: values.periodoDesde || undefined,
        periodoHasta: values.periodoHasta || undefined,
      })
      toast.success('Pago registrado')
      reset()
      onCreated(pago)
      onClose()
    } catch {
      toast.error('Error al registrar el pago')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Registrar pago</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label>Plan de compañía</Label>
            <p className="text-sm font-mono bg-slate-50 rounded p-2 mt-1 text-slate-600">#{idCompaniaPlan}</p>
          </div>
          <div>
            <Label>Monto (USD)</Label>
            <Input {...register('monto')} type="number" step="0.01" placeholder="0.00" className="mt-1" />
            {errors.monto && <p className="text-xs text-red-500 mt-1">{errors.monto.message}</p>}
          </div>
          <div>
            <Label>Método de pago</Label>
            <Select onValueChange={(v: string | null) => v && setValue('metodoPago', v)}>
              <SelectTrigger className="mt-1"><SelectValue placeholder="Seleccionar" /></SelectTrigger>
              <SelectContent>
                {METODOS.map(m => <SelectItem key={m.value} value={m.value} label={m.label}>{m.label}</SelectItem>)}
              </SelectContent>
            </Select>
            {errors.metodoPago && <p className="text-xs text-red-500 mt-1">{errors.metodoPago.message}</p>}
          </div>
          <div>
            <Label>Tipo de pago</Label>
            <Select onValueChange={(v: string | null) => v && setValue('tipoPago', v)}>
              <SelectTrigger className="mt-1"><SelectValue placeholder="Seleccionar" /></SelectTrigger>
              <SelectContent>
                {TIPOS.map(t => <SelectItem key={t.value} value={t.value} label={t.label}>{t.label}</SelectItem>)}
              </SelectContent>
            </Select>
            {errors.tipoPago && <p className="text-xs text-red-500 mt-1">{errors.tipoPago.message}</p>}
          </div>
          <div>
            <Label>Referencia <span className="text-xs text-slate-400">(opcional)</span></Label>
            <Input {...register('referencia')} placeholder={t('platform.pagos.transactionPlaceholder')} className="mt-1" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Período desde</Label>
              <Input {...register('periodoDesde')} type="date" className="mt-1" />
            </div>
            <div>
              <Label>Período hasta</Label>
              <Input {...register('periodoHasta')} type="date" className="mt-1" />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 size={14} className="animate-spin mr-2" />}
              Registrar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
