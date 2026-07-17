import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { Button } from 'primereact/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'

const schema = z.object({
  fecha_inicio: z.string().min(1),
  motivo: z.enum(['viaje', 'lesion', 'enfermedad', 'voluntario', 'otro']),
  detalle: z.string().optional(),
  retroactivo: z.boolean().default(false),
})

type FormValues = z.infer<typeof schema>

interface Props {
  idMembresia: number
  open: boolean
  onClose: () => void
  onCongelada: () => void
}

export function CongelarMembresiaModal({ idMembresia, open, onClose, onCongelada }: Props) {
  const { t } = useTranslation()
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<z.input<typeof schema>, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      fecha_inicio: new Date().toISOString().split('T')[0],
      motivo: 'voluntario',
      retroactivo: false,
    },
  })

  useEffect(() => { if (!open) reset() }, [open, reset])

  const onSubmit = async (values: FormValues) => {
    try {
      await coreRepository.congelarMembresia(idMembresia, values)
      toast.success(t('congelamientos.congeladaSuccess'))
      onCongelada()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error(t('congelamientos.congeladaError409'))
      } else {
        toast.error(t('congelamientos.congeladaErrorConn'))
      }
    }
  }

  const inputCls = 'w-full px-3 py-2 text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500'
  const inputStyle = { background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }
  const labelCls = 'block text-xs font-medium mb-1'
  const labelStyle = { color: 'var(--page-muted)' }

  return (
    <Dialog open={open} onOpenChange={v => !v && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle style={{ color: 'var(--page-text)', fontSize: '0.875rem' }}>
            {t('congelamientos.congelarTitle')}
          </DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-3 py-2">
          <div>
            <label className={labelCls} style={labelStyle}>{t('congelamientos.fieldFechaInicio')}</label>
            <input {...register('fecha_inicio')} type="date" className={inputCls} style={inputStyle} />
            {errors.fecha_inicio && <p className="text-xs text-red-400 mt-1">{errors.fecha_inicio.message}</p>}
          </div>
          <div>
            <label className={labelCls} style={labelStyle}>{t('congelamientos.fieldMotivo')}</label>
            <select {...register('motivo')} className={inputCls} style={inputStyle}>
              <option value="viaje">{t('congelamientos.motivoViaje')}</option>
              <option value="lesion">{t('congelamientos.motivoLesion')}</option>
              <option value="enfermedad">{t('congelamientos.motivoEnfermedad')}</option>
              <option value="voluntario">{t('congelamientos.motivoVoluntario')}</option>
              <option value="otro">{t('congelamientos.motivoOtro')}</option>
            </select>
          </div>
          <div>
            <label className={labelCls} style={labelStyle}>{t('congelamientos.fieldDetalle')}</label>
            <textarea {...register('detalle')} rows={2} className={inputCls} style={inputStyle}
              placeholder={t('congelamientos.fieldDetallePlaceholder')} />
          </div>
          <div className="flex items-center gap-2">
            <input {...register('retroactivo')} type="checkbox" id="retroactivo"
              className="w-3.5 h-3.5 accent-orange-500" />
            <label htmlFor="retroactivo" className="text-xs cursor-pointer" style={{ color: 'var(--page-muted)' }}>
              {t('congelamientos.fieldRetroactivo')}
            </label>
          </div>
        </form>

        <DialogFooter>
          <Button label={t('common.cancel')} text size="small" onClick={onClose}
            style={{ color: 'var(--page-muted)' }} />
          <Button label={isSubmitting ? t('common.saving') : t('congelamientos.congelarSubmit')}
            severity="warning" size="small" disabled={isSubmitting} onClick={handleSubmit(onSubmit)} />
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
