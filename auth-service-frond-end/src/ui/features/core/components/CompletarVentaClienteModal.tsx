// TODO(backend): reemplazar por GET /metodos-pago cuando esté disponible. IDs actuales son placeholders.
// Los IDs 1, 2, 3 NO existen en la BD todavía — el endpoint de métodos de pago no está implementado.
// Cuando el backend exponga el endpoint, reemplazar METODOS_PAGO_PLACEHOLDER por una llamada dinámica.

import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { Button } from 'primereact/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import { getApiErrorCode, getApiErrorStatus } from '@/lib/api-error'
import type { CompletarVentaClienteDto } from '@/infrastructure/http/core/core.dto'
import { isAxiosError } from 'axios'

// TODO(backend): reemplazar por GET /metodos-pago cuando esté disponible. IDs actuales son placeholders.
const METODOS_PAGO_PLACEHOLDER = [
  { id: 1, nombre: 'Efectivo' },
  { id: 2, nombre: 'Tarjeta' },
  { id: 3, nombre: 'Transferencia' },
]

const schema = z.object({
  precio_pagado: z.coerce.number().min(0, 'El precio no puede ser negativo'),
  id_metodo_pago: z.coerce.number().int().min(1, 'Selecciona un método de pago'),
  fecha_inicio: z.string().min(1, 'La fecha es requerida'),
  descuento_aplicado: z.coerce.number().min(0).default(0),
})

type FormInput = z.input<typeof schema>
type FormValues = z.infer<typeof schema>

interface Props {
  idMembresia: number
  nombreCliente: string | null
  tipoNombre: string
  open: boolean
  onClose: () => void
  onCompletada: () => void
}

export function CompletarVentaClienteModal({ idMembresia, nombreCliente, tipoNombre, open, onClose, onCompletada }: Props) {
  const { t } = useTranslation()

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      precio_pagado: 0,
      id_metodo_pago: 0,
      fecha_inicio: new Date().toISOString().split('T')[0],
      descuento_aplicado: 0,
    },
  })

  useEffect(() => {
    if (!open) {
      reset({
        precio_pagado: 0,
        id_metodo_pago: 0,
        fecha_inicio: new Date().toISOString().split('T')[0],
        descuento_aplicado: 0,
      })
    }
  }, [open, reset])

  const onSubmit = async (values: FormValues) => {
    const body: CompletarVentaClienteDto = {
      id_metodo_pago: values.id_metodo_pago,
      precio_pagado: values.precio_pagado,
      descuento_aplicado: values.descuento_aplicado ?? 0,
      fecha_inicio: values.fecha_inicio,
    }

    try {
      await coreRepository.confirmarPago(idMembresia, body)
      toast.success(t('ventasPendientes.completar.success'))
      onCompletada()
    } catch (err) {
      const codigo = getApiErrorCode(err)
      const status = getApiErrorStatus(err)

      if (codigo === 'datos_venta_incompletos') {
        toast.error(t('ventasPendientes.completar.errorDatosIncompletos'))
        // Attempt to paint per-field errors from ProblemDetail extensions
        if (isAxiosError(err)) {
          const errores = (err.response?.data as Record<string, unknown> | undefined)?.errores
          if (errores && typeof errores === 'object') {
            const campoMap: Record<string, keyof FormValues> = {
              id_metodo_pago: 'id_metodo_pago',
              precio_pagado: 'precio_pagado',
              fecha_inicio: 'fecha_inicio',
              descuento_aplicado: 'descuento_aplicado',
            }
            for (const [campo, msg] of Object.entries(errores as Record<string, string>)) {
              const key = campoMap[campo]
              if (key) setError(key, { message: String(msg) })
            }
          }
        }
      } else if (status === 404 || codigo === 'recurso_no_encontrado') {
        toast.error(t('ventasPendientes.completar.errorGenerico'))
        onClose()
        onCompletada()
      } else if (status === 409 || codigo === 'conflicto') {
        toast.info(t('ventasPendientes.completar.conflictoIdempotente'))
        onClose()
        onCompletada()
      } else {
        toast.error(t('ventasPendientes.completar.errorGenerico'))
      }
    }
  }

  const inputCls = 'w-full px-3 py-2 text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500'
  const inputStyle = { background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }
  const labelCls = 'block text-xs font-medium mb-1'
  const labelStyle = { color: 'var(--page-muted)' }

  return (
    <Dialog open={open} onOpenChange={v => !v && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle style={{ color: 'var(--page-text)', fontSize: '0.875rem' }}>
            {t('ventasPendientes.completar.titulo', { cliente: nombreCliente ?? '' })}
          </DialogTitle>
        </DialogHeader>

        <p className="text-xs pb-1" style={{ color: 'var(--page-muted)' }}>
          {t('ventasPendientes.completar.subtitulo', { tipo: tipoNombre })}
        </p>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-2">
          <div>
            <label className={labelCls} style={labelStyle}>
              {t('ventasPendientes.completar.fieldPrecio')}
            </label>
            <input
              {...register('precio_pagado')}
              type="number"
              min={0}
              step="0.01"
              className={inputCls}
              style={inputStyle}
              placeholder="0.00"
            />
            {errors.precio_pagado && (
              <p className="text-xs text-red-400 mt-1">{errors.precio_pagado.message}</p>
            )}
          </div>

          <div>
            <label className={labelCls} style={labelStyle}>
              {t('ventasPendientes.completar.fieldMetodoPago')}
            </label>
            <select {...register('id_metodo_pago')} className={inputCls} style={inputStyle}>
              <option value={0}>Selecciona un método...</option>
              {METODOS_PAGO_PLACEHOLDER.map(m => (
                <option key={m.id} value={m.id}>{m.nombre}</option>
              ))}
            </select>
            {errors.id_metodo_pago && (
              <p className="text-xs text-red-400 mt-1">{errors.id_metodo_pago.message}</p>
            )}
          </div>

          <div>
            <label className={labelCls} style={labelStyle}>
              {t('ventasPendientes.completar.fieldFecha')}
            </label>
            <input
              {...register('fecha_inicio')}
              type="date"
              className={inputCls}
              style={inputStyle}
            />
            {errors.fecha_inicio && (
              <p className="text-xs text-red-400 mt-1">{errors.fecha_inicio.message}</p>
            )}
          </div>

          <div>
            <label className={labelCls} style={labelStyle}>
              {t('ventasPendientes.completar.fieldDescuento')}{' '}
              <span style={{ color: 'var(--page-border)', fontWeight: 400 }}>({t('common.optional')})</span>
            </label>
            <input
              {...register('descuento_aplicado')}
              type="number"
              min={0}
              step="0.01"
              className={inputCls}
              style={inputStyle}
              placeholder="0.00"
            />
            {errors.descuento_aplicado && (
              <p className="text-xs text-red-400 mt-1">{errors.descuento_aplicado.message}</p>
            )}
          </div>
        </form>

        <DialogFooter>
          <Button
            label={t('ventasPendientes.completar.cancel')}
            text
            size="small"
            onClick={onClose}
            disabled={isSubmitting}
            style={{ color: 'var(--page-muted)' }}
          />
          <Button
            label={isSubmitting ? t('common.saving') : t('ventasPendientes.completar.submit')}
            severity="warning"
            size="small"
            disabled={isSubmitting}
            onClick={handleSubmit(onSubmit)}
          />
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
