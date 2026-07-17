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
import type { TipoMembresia } from '@/infrastructure/http/core/core.dto'

const schema = z.object({
  nombre: z.string().min(2).max(100),
  precio: z.coerce.number().min(0),
  duracion_valor: z.coerce.number().int().min(1),
})

type FormValues = z.infer<typeof schema>

interface Props {
  tipo: TipoMembresia
  onClose: () => void
  onActualizado: () => void
}

export function EditarTipoMembresiaModal({ tipo, onClose, onActualizado }: Props) {
  const { t } = useTranslation()
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<z.input<typeof schema>, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { nombre: tipo.nombre, precio: tipo.precio, duracion_valor: tipo.duracion_valor },
  })

  useEffect(() => {
    reset({ nombre: tipo.nombre, precio: tipo.precio, duracion_valor: tipo.duracion_valor })
  }, [tipo, reset])

  const onSubmit = async (values: FormValues) => {
    try {
      await coreRepository.actualizarTipoMembresia(tipo.id, values)
      toast.success(t('tiposMembresia.editSuccess'))
      onActualizado()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error(t('tiposMembresia.createError409'))
      } else {
        toast.error(t('tiposMembresia.editErrorConn'))
      }
    }
  }

  const inputCls = 'w-full px-3 py-2 text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500'
  const inputStyle = { background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }
  const labelCls = 'block text-xs font-medium mb-1'
  const labelStyle = { color: 'var(--page-muted)' }

  return (
    <Dialog open onOpenChange={v => !v && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle style={{ color: 'var(--page-text)', fontSize: '0.875rem' }}>
            {t('tiposMembresia.editTitle')}
          </DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-2">
          <div>
            <label className={labelCls} style={labelStyle}>{t('tiposMembresia.fieldNombre')}</label>
            <input {...register('nombre')} className={inputCls} style={inputStyle} />
            {errors.nombre && <p className="text-xs text-red-400 mt-1">{errors.nombre.message}</p>}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelCls} style={labelStyle}>{t('tiposMembresia.fieldValor')}</label>
              <input {...register('duracion_valor')} type="number" min={1} className={inputCls} style={inputStyle} />
            </div>
            <div>
              <label className={labelCls} style={labelStyle}>{t('tiposMembresia.fieldPrecio')}</label>
              <input {...register('precio')} type="number" min={0} step="0.01" className={inputCls} style={inputStyle} />
              {errors.precio && <p className="text-xs text-red-400 mt-1">{errors.precio.message}</p>}
            </div>
          </div>
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
            {t('tiposMembresia.editPriceNote')}
          </p>
        </form>

        <DialogFooter>
          <Button label={t('common.cancel')} text size="small" onClick={onClose}
            style={{ color: 'var(--page-muted)' }} />
          <Button label={isSubmitting ? t('common.saving') : t('tiposMembresia.editSubmit')}
            severity="warning" size="small" disabled={isSubmitting} onClick={handleSubmit(onSubmit)} />
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
