import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, Info } from 'lucide-react'
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
import { reportarPagoSchema, type ReportarPagoForm } from '@/ui/features/platform/schemas/reportar-pago.schema'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { useLimitPlanModalStore } from '@/infrastructure/store/plan/useLimitPlanModalStore'
import { getApiErrorStatus, getApiErrorMessage } from '@/lib/api-error'
import type { Plan } from '@/domain/platform/entities/Plan.entity'

interface Props {
  open: boolean
  onClose: () => void
  idCompania: number
  planes: Plan[]
  idPlanDestinoInicial?: number | null
  onSuccess?: () => void
}

export function ReportarPagoModal({
  open,
  onClose,
  idCompania,
  planes,
  idPlanDestinoInicial = null,
  onSuccess,
}: Props) {
  const { t } = useTranslation()
  const [errorBanner, setErrorBanner] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<ReportarPagoForm>({
    resolver: zodResolver(reportarPagoSchema) as never,
    defaultValues: {
      idPlanDestino: idPlanDestinoInicial ?? undefined,
    },
  })

  const today = new Date().toISOString().slice(0, 10)

  const handleClose = () => {
    reset()
    setErrorBanner(null)
    onClose()
  }

  const onSubmit = async (values: ReportarPagoForm) => {
    setErrorBanner(null)
    const fd = new FormData()
    fd.append('id_plan_destino', String(values.idPlanDestino))
    fd.append('monto', String(values.monto))
    fd.append('fecha_transferencia', values.fechaTransferencia)
    fd.append('referencia', values.referencia)
    if (values.bancoOrigen) fd.append('banco_origen', values.bancoOrigen)
    // TODO(REQ-SAAS-001): subida de comprobante — agregar <input type="file"> cuando esté S3 storage

    try {
      await platformRepository.reportarPagoOwner(idCompania, fd)
      toast.success(t('reportarPago.exito'))
      useLimitPlanModalStore.getState().triggerRefetch()
      onSuccess?.()
      handleClose()
    } catch (err) {
      const status = getApiErrorStatus(err)
      if (status === 429) {
        setErrorBanner(t('reportarPago.error.rateLimitExcedido'))
      } else {
        const msg = getApiErrorMessage(err)
        setErrorBanner(msg !== 'Error desconocido' ? msg : t('reportarPago.error.generico'))
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('reportarPago.title')}</DialogTitle>
        </DialogHeader>

        {errorBanner && (
          <div
            className="flex items-start gap-2 rounded-lg px-3 py-2 text-sm"
            style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.35)', color: '#ef4444' }}
          >
            {errorBanner}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">

          {/* Plan destino */}
          <div>
            <Label>{t('reportarPago.labelPlan')}</Label>
            {idPlanDestinoInicial != null ? (
              <div
                className="mt-1 inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold"
                style={{ background: 'rgba(249,115,22,0.12)', color: 'var(--color-warning, #f97316)' }}
              >
                {planes.find(p => p.id === idPlanDestinoInicial)?.nombre ?? `Plan #${idPlanDestinoInicial}`}
              </div>
            ) : (
              <Select
                onValueChange={(v: string | null) => v && setValue('idPlanDestino', Number(v))}
              >
                <SelectTrigger className="mt-1">
                  <SelectValue placeholder={t('reportarPago.placeholderPlan')} />
                </SelectTrigger>
                <SelectContent>
                  {planes
                    .filter(p => p.activo && p.precioMensual > 0)
                    .map(p => (
                      <SelectItem key={p.id} value={String(p.id)} label={p.nombre}>
                        {p.nombre} — ${p.precioMensual.toFixed(2)}/mes
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
            )}
            {errors.idPlanDestino && (
              <p className="text-xs mt-1" style={{ color: '#ef4444' }}>
                {errors.idPlanDestino.message}
              </p>
            )}
          </div>

          {/* Monto */}
          <div>
            <Label>{t('reportarPago.labelMonto')}</Label>
            <Input
              {...register('monto')}
              type="number"
              step="0.01"
              min="0.01"
              placeholder="0.00"
              className="mt-1"
            />
            {errors.monto && (
              <p className="text-xs mt-1" style={{ color: '#ef4444' }}>
                {errors.monto.message}
              </p>
            )}
          </div>

          {/* Fecha de transferencia */}
          <div>
            <Label>{t('reportarPago.labelFechaTransferencia')}</Label>
            <Input
              {...register('fechaTransferencia')}
              type="date"
              max={today}
              className="mt-1"
            />
            {errors.fechaTransferencia && (
              <p className="text-xs mt-1" style={{ color: '#ef4444' }}>
                {errors.fechaTransferencia.message}
              </p>
            )}
          </div>

          {/* Referencia bancaria (obligatorio por UX) */}
          <div>
            <Label>{t('reportarPago.labelReferencia')}</Label>
            <Input
              {...register('referencia')}
              placeholder={t('reportarPago.placeholderReferencia')}
              className="mt-1"
            />
            {errors.referencia && (
              <p className="text-xs mt-1" style={{ color: '#ef4444' }}>
                {errors.referencia.message}
              </p>
            )}
          </div>

          {/* Banco origen (opcional) */}
          <div>
            <Label>
              {t('reportarPago.labelBancoOrigen')}
              <span className="ml-1 text-xs" style={{ color: 'var(--page-muted)' }}>
                {t('common.optional')}
              </span>
            </Label>
            <Input
              {...register('bancoOrigen')}
              placeholder={t('reportarPago.placeholderBancoOrigen')}
              className="mt-1"
            />
          </div>

          {/* Nota de rate limit */}
          <div
            className="flex items-start gap-2 rounded-lg px-3 py-2 text-xs"
            style={{ background: 'rgba(234,179,8,0.08)', border: '1px solid rgba(234,179,8,0.3)', color: 'var(--page-muted)' }}
          >
            <Info size={13} className="flex-shrink-0 mt-0.5" style={{ color: '#eab308' }} />
            <span>{t('reportarPago.rateLimitNota')}</span>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={handleClose}>
              {t('reportarPago.cancelar')}
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <Loader2 size={14} className="animate-spin mr-2" />
                  {t('reportarPago.enviando')}
                </>
              ) : (
                t('reportarPago.enviar')
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
