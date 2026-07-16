import { useState, useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { Button } from 'primereact/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import { useHasPermission } from '@/infrastructure/store/auth/auth.store'
import { SinTiposMembresiaBanner } from './SinTiposMembresiaBanner'
import type { TipoMembresia } from '@/infrastructure/http/core/core.dto'

const schema = z.object({
  id_tipo_membresia: z.coerce.number().int().min(1, 'Selecciona un tipo'),
  fecha_inicio: z.string().min(1),
  descuento_aplicado: z.coerce.number().min(0).default(0),
})

type FormValues = z.infer<typeof schema>

interface Props {
  idCliente: number
  nombreCliente: string
  open: boolean
  onClose: () => void
  onVendida: () => void
}

export function VenderMembresiaModal({ idCliente, nombreCliente, open, onClose, onVendida }: Props) {
  const { t } = useTranslation()
  const puedeCrear = useHasPermission('membresias:leer')
  const [tipos, setTipos] = useState<TipoMembresia[]>([])
  const [todosLosTipos, setTodosLosTipos] = useState<TipoMembresia[]>([])
  const [tipoSeleccionado, setTipoSeleccionado] = useState<TipoMembresia | null>(null)

  const { register, handleSubmit, watch, reset, formState: { errors, isSubmitting } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fecha_inicio: new Date().toISOString().split('T')[0], descuento_aplicado: 0 },
  })

  const idTipo = watch('id_tipo_membresia')
  const descuento = watch('descuento_aplicado') || 0

  useEffect(() => {
    if (!open) { reset(); setTipoSeleccionado(null); setTodosLosTipos([]); setTipos([]); return }
    coreRepository.getTiposMembresia()
      .then(data => {
        setTodosLosTipos(data)
        setTipos(data.filter(tm => tm.activo))
      })
      .catch(() => toast.error(t('membresias.tiposLoadError')))
  }, [open, reset, t])

  useEffect(() => {
    const found = tipos.find(tm => tm.id === Number(idTipo))
    setTipoSeleccionado(found ?? null)
  }, [idTipo, tipos])

  const precioFinal = tipoSeleccionado ? Math.max(0, tipoSeleccionado.precio - Number(descuento)) : 0

  const todosDesactivados = todosLosTipos.length > 0 && tipos.length === 0

  const onSubmit = async (values: FormValues) => {
    try {
      await coreRepository.venderMembresia(idCliente, {
        id_tipo_membresia: values.id_tipo_membresia,
        fecha_inicio: values.fecha_inicio,
        descuento_aplicado: values.descuento_aplicado,
      })
      toast.success(t('membresias.vendidaSuccess'))
      onVendida()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error(t('membresias.vendidaError409'))
      } else {
        toast.error(t('membresias.vendidaErrorConn'))
      }
    }
  }

  const inputCls = 'w-full px-3 py-2 text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500'
  const inputStyle = { background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }
  const labelCls = 'block text-xs font-medium mb-1'
  const labelStyle = { color: 'var(--page-muted)' }

  const showBanner = todosLosTipos.length === 0 || todosDesactivados
  const bannerCaso = todosLosTipos.length === 0 ? 'sin_tipos' : 'todos_desactivados'

  return (
    <Dialog open={open} onOpenChange={v => !v && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle style={{ color: 'var(--page-text)', fontSize: '0.875rem' }}>
            {t('membresias.venderTitle')} — {nombreCliente}
          </DialogTitle>
        </DialogHeader>

        {showBanner ? (
          <SinTiposMembresiaBanner
            caso={bannerCaso}
            canCreate={puedeCrear}
            onNavegar={onClose}
          />
        ) : (
          <>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-2">
              <div>
                <label className={labelCls} style={labelStyle}>{t('membresias.fieldTipo')}</label>
                <select {...register('id_tipo_membresia')} className={inputCls} style={inputStyle}>
                  <option value="">{t('membresias.selectTipo')}</option>
                  {tipos.map(tm => (
                    <option key={tm.id} value={tm.id}>
                      {tm.nombre} — ${tm.precio} ({tm.modo_control})
                    </option>
                  ))}
                </select>
                {errors.id_tipo_membresia && <p className="text-xs text-red-400 mt-1">{errors.id_tipo_membresia.message}</p>}
              </div>

              {tipoSeleccionado && (
                <div className="rounded-lg p-3 text-xs space-y-1"
                  style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
                  <div className="flex justify-between">
                    <span style={{ color: 'var(--page-muted)' }}>{t('membresias.infoModo')}</span>
                    <span style={{ color: 'var(--page-text)' }}>{tipoSeleccionado.modo_control}</span>
                  </div>
                  <div className="flex justify-between">
                    <span style={{ color: 'var(--page-muted)' }}>{t('membresias.infoDuracion')}</span>
                    <span style={{ color: 'var(--page-text)' }}>{tipoSeleccionado.duracion_valor} {tipoSeleccionado.duracion_tipo}</span>
                  </div>
                  {tipoSeleccionado.dias_acceso && (
                    <div className="flex justify-between">
                      <span style={{ color: 'var(--page-muted)' }}>{t('membresias.infoAccesos')}</span>
                      <span style={{ color: 'var(--page-text)' }}>{tipoSeleccionado.dias_acceso} entradas</span>
                    </div>
                  )}
                </div>
              )}

              <div>
                <label className={labelCls} style={labelStyle}>{t('membresias.fieldFechaInicio')}</label>
                <input {...register('fecha_inicio')} type="date" className={inputCls} style={inputStyle} />
              </div>

              <div>
                <label className={labelCls} style={labelStyle}>{t('membresias.fieldDescuento')}</label>
                <input {...register('descuento_aplicado')} type="number" min={0} step="0.01"
                  className={inputCls} style={inputStyle} placeholder="0.00" />
              </div>

              {tipoSeleccionado && (
                <div className="flex items-center justify-between px-3 py-2 rounded-lg"
                  style={{ background: 'rgba(249,115,22,0.1)', border: '1px solid rgba(249,115,22,0.3)' }}>
                  <span className="text-xs font-semibold" style={{ color: '#f97316' }}>
                    {t('membresias.precioFinal')}
                  </span>
                  <span className="text-sm font-bold" style={{ color: '#f97316' }}>
                    ${precioFinal.toFixed(2)}
                  </span>
                </div>
              )}
            </form>

            <DialogFooter>
              <Button label={t('common.cancel')} text size="small" onClick={onClose}
                style={{ color: 'var(--page-muted)' }} />
              <Button label={isSubmitting ? t('common.creating') : t('membresias.venderSubmit')}
                severity="warning" size="small" disabled={isSubmitting} onClick={handleSubmit(onSubmit)} />
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
