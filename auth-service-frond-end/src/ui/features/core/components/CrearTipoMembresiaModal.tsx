import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { Button } from 'primereact/button'
import { ArrowLeft } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import { PLANTILLAS } from '@/ui/features/core/constants/tiposMembresia.plantillas'
import type { PlantillaOption } from '@/ui/features/core/constants/tiposMembresia.plantillas'

const schema = z.object({
  nombre: z.string().min(2).max(100),
  modo_control: z.enum(['calendario', 'accesos']),
  duracion_tipo: z.enum(['dias', 'semanas', 'meses', 'años']),
  duracion_valor: z.coerce.number().int().min(1),
  dias_acceso: z.coerce.number().int().min(1).optional().nullable(),
  precio: z.coerce.number().min(0),
})

type FormValues = z.infer<typeof schema>
type Step = 'plantilla' | 'form'

const DURACION_TIPOS: { value: FormValues['duracion_tipo']; label: string }[] = [
  { value: 'dias',    label: 'días'    },
  { value: 'semanas', label: 'semanas' },
  { value: 'meses',   label: 'meses'   },
  { value: 'años',    label: 'años'    },
]

interface Props {
  open: boolean
  onClose: () => void
  onCreado: () => void
  initialStep?: 'plantilla' | 'form'
  initialDefaults?: Partial<FormValues>
}

export function CrearTipoMembresiaModal({ open, onClose, onCreado, initialStep, initialDefaults }: Props) {
  const { t } = useTranslation()
  const [step, setStep] = useState<Step>('plantilla')

  const {
    register, handleSubmit, watch, reset, setValue,
    formState: { errors, isSubmitting },
  } = useForm<z.input<typeof schema>, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { modo_control: 'calendario', duracion_tipo: 'meses', duracion_valor: 1, precio: 0 },
  })

  const nombre       = watch('nombre')
  const modoControl  = watch('modo_control')
  const duracionTipo = watch('duracion_tipo')
  const duracionValor = watch('duracion_valor')
  const diasAcceso   = watch('dias_acceso')
  const precio       = watch('precio')

  useEffect(() => {
    if (!open) { reset(); setStep('plantilla'); return }
    if (initialStep === 'form' && initialDefaults) {
      reset({ modo_control: 'calendario', duracion_tipo: 'meses', duracion_valor: 1, precio: 0, ...initialDefaults })
      setStep('form')
    }
  }, [open, initialStep, initialDefaults, reset])

  const handleSelectPlantilla = (p: PlantillaOption) => {
    reset({ modo_control: 'calendario', duracion_tipo: 'meses', duracion_valor: 1, precio: 0, ...p.defaults })
    setStep('form')
  }

  const buildPreview = () => {
    const parts: string[] = []
    if (nombre) parts.push(nombre)
    if (modoControl === 'calendario') {
      if (duracionValor) parts.push(`${duracionValor} ${duracionTipo}`)
    } else {
      if (diasAcceso) parts.push(`${diasAcceso} accesos`)
      if (duracionValor) parts.push(`vence en ${duracionValor} ${duracionTipo}`)
    }
    if (Number(precio) > 0) parts.push(`$${Number(precio).toFixed(2)}`)
    return parts.join(' · ')
  }

  const onSubmit = async (values: FormValues) => {
    try {
      await coreRepository.crearTipoMembresia({
        nombre: values.nombre,
        modo_control: values.modo_control,
        duracion_tipo: values.duracion_tipo,
        duracion_valor: values.duracion_valor,
        dias_acceso: values.modo_control === 'accesos' ? (values.dias_acceso ?? null) : null,
        precio: values.precio,
      })
      toast.success(t('tiposMembresia.createSuccess'))
      onCreado()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error(t('tiposMembresia.createError409'))
      } else {
        toast.error(t('tiposMembresia.createErrorConn'))
      }
    }
  }

  const inputCls = 'w-full px-3 py-2 text-xs rounded-lg font-sans focus:outline-none focus:ring-2 focus:ring-orange-500 transition-colors'
  const inputStyle = { background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }
  const labelCls = 'block text-xs font-medium mb-1.5'
  const labelStyle = { color: 'var(--page-muted)' }

  const preview = buildPreview()

  return (
    <Dialog open={open} onOpenChange={v => !v && onClose()}>
      <DialogContent className="max-w-2xl" style={{ maxWidth: '680px' }}>
        <DialogHeader>
          <DialogTitle style={{ color: 'var(--page-text)', fontSize: '0.875rem' }} className="flex items-center gap-2">
            {step === 'form' && (
              <button
                type="button"
                onClick={() => setStep('plantilla')}
                className="p-1 rounded-md hover:opacity-70 transition-opacity flex-shrink-0"
              >
                <ArrowLeft size={14} style={{ color: 'var(--page-muted)' }} />
              </button>
            )}
            {step === 'plantilla' ? 'Nueva Membresía' : t('tiposMembresia.createTitle')}
          </DialogTitle>
        </DialogHeader>

        {/* ── Step 1: Plantillas ── */}
        {step === 'plantilla' && (
          <div className="py-2">
            <p className="text-xs mb-4" style={{ color: 'var(--page-muted)' }}>
              Elige un punto de partida — podrás ajustar los detalles en el siguiente paso
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
              {PLANTILLAS.map(p => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => handleSelectPlantilla(p)}
                  className={`flex items-start gap-2.5 p-3 rounded-xl text-left transition-all hover:scale-[1.02] active:scale-[0.98]${p.colSpan ? ' sm:col-span-2' : ''}`}
                  style={{
                    background: 'var(--page-surface)',
                    border: '1px solid var(--page-border)',
                  }}
                  onMouseEnter={e => { e.currentTarget.style.borderColor = '#f97316' }}
                  onMouseLeave={e => { e.currentTarget.style.borderColor = 'var(--page-border)' }}
                >
                  <span className="text-xl flex-shrink-0">{p.emoji}</span>
                  <div className="min-w-0">
                    <p className="text-xs font-semibold" style={{ color: 'var(--page-text)' }}>
                      {p.label}
                    </p>
                    <p className="text-[0.65rem] mt-0.5 leading-snug" style={{ color: 'var(--page-muted)' }}>
                      {p.desc}
                    </p>
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* ── Step 2: Formulario ── */}
        {step === 'form' && (
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-2">

            {/* Nombre */}
            <div>
              <label className={labelCls} style={labelStyle}>Nombre</label>
              <input
                {...register('nombre')}
                className={inputCls}
                style={inputStyle}
                placeholder="Ej: Mensual, Pack 10 clases..."
                autoFocus
              />
              {errors.nombre && <p className="text-xs text-red-400 mt-1">{errors.nombre.message}</p>}
            </div>

            {/* Modo de control */}
            <div>
              <label className={labelCls} style={labelStyle}>¿Cómo se controla?</label>
              <div className="grid grid-cols-2 gap-3">
                {[
                  { value: 'calendario' as const, emoji: '📅', label: 'Por calendario', desc: 'Vence en una fecha fija' },
                  { value: 'accesos'    as const, emoji: '🎯', label: 'Por accesos',    desc: 'Número limitado de entradas' },
                ].map(opt => {
                  const active = modoControl === opt.value
                  return (
                    <button
                      key={opt.value}
                      type="button"
                      onClick={() => setValue('modo_control', opt.value, { shouldValidate: true })}
                      className="flex items-start gap-2.5 p-3 rounded-xl text-left transition-all"
                      style={{
                        background: active ? 'rgba(249,115,22,0.08)' : 'var(--page-surface)',
                        border: `1.5px solid ${active ? '#f97316' : 'var(--page-border)'}`,
                      }}
                    >
                      <span className="text-base mt-0.5 flex-shrink-0">{opt.emoji}</span>
                      <div>
                        <p className="text-xs font-semibold" style={{ color: active ? '#f97316' : 'var(--page-text)' }}>
                          {opt.label}
                        </p>
                        <p className="text-[0.6rem] mt-0.5" style={{ color: 'var(--page-muted)' }}>
                          {opt.desc}
                        </p>
                      </div>
                    </button>
                  )
                })}
              </div>
            </div>

            {/* Número de accesos (solo modo accesos) */}
            {modoControl === 'accesos' && (
              <div>
                <label className={labelCls} style={labelStyle}>Número de accesos</label>
                <input
                  {...register('dias_acceso')}
                  type="number"
                  min={1}
                  className={inputCls}
                  style={inputStyle}
                  placeholder="10"
                />
                <p className="text-[0.6rem] mt-1" style={{ color: 'var(--page-muted)' }}>
                  💡 El cliente puede usarlos cuando quiera dentro del período de vigencia
                </p>
                {errors.dias_acceso && <p className="text-xs text-red-400 mt-1">{errors.dias_acceso.message}</p>}
              </div>
            )}

            {/* Duración */}
            <div>
              <label className={labelCls} style={labelStyle}>
                {modoControl === 'calendario' ? 'Duración' : 'Vigencia'}
              </label>
              <div className="flex items-center gap-2 flex-wrap">
                <input
                  {...register('duracion_valor')}
                  type="number"
                  min={1}
                  className="w-20 px-3 py-2 text-xs rounded-lg font-sans focus:outline-none focus:ring-2 focus:ring-orange-500 transition-colors flex-shrink-0"
                  style={inputStyle}
                />
                <div className="flex gap-1.5 flex-wrap">
                  {DURACION_TIPOS.map(dt => {
                    const active = duracionTipo === dt.value
                    return (
                      <button
                        key={dt.value}
                        type="button"
                        onClick={() => setValue('duracion_tipo', dt.value, { shouldValidate: true })}
                        className="px-2.5 py-1.5 rounded-lg text-[0.65rem] font-medium transition-all"
                        style={{
                          background: active ? '#f97316' : 'var(--page-surface)',
                          color: active ? '#ffffff' : 'var(--page-muted)',
                          border: `1px solid ${active ? '#f97316' : 'var(--page-border)'}`,
                        }}
                      >
                        {dt.label}
                      </button>
                    )
                  })}
                </div>
              </div>
              {errors.duracion_valor && <p className="text-xs text-red-400 mt-1">{errors.duracion_valor.message}</p>}
            </div>

            {/* Precio */}
            <div>
              <label className={labelCls} style={labelStyle}>Precio (USD)</label>
              <div className="relative">
                <span
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-xs select-none"
                  style={{ color: 'var(--page-muted)' }}
                >
                  $
                </span>
                <input
                  {...register('precio')}
                  type="number"
                  min={0}
                  step="0.01"
                  className={`${inputCls} pl-6`}
                  style={inputStyle}
                  placeholder="0.00"
                />
              </div>
              {errors.precio && <p className="text-xs text-red-400 mt-1">{errors.precio.message}</p>}
            </div>

            {/* Vista previa */}
            {preview && (
              <div className="pt-1">
                <div className="w-full border-t" style={{ borderColor: 'var(--page-border)' }} />
                <div className="mt-3 flex items-center gap-2 flex-wrap">
                  <span
                    className="text-[0.6rem] font-medium uppercase tracking-wide flex-shrink-0"
                    style={{ color: 'var(--page-muted)' }}
                  >
                    Vista previa
                  </span>
                  <span
                    className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium"
                    style={{
                      background: 'rgba(249,115,22,0.10)',
                      color: '#f97316',
                      border: '1px solid rgba(249,115,22,0.25)',
                    }}
                  >
                    {modoControl === 'calendario' ? '📅' : '🎯'} {preview}
                  </span>
                </div>
              </div>
            )}
          </form>
        )}

        {step === 'form' && (
          <DialogFooter>
            <Button
              label={t('common.cancel')}
              text
              size="small"
              onClick={onClose}
              style={{ color: 'var(--page-muted)' }}
            />
            <Button
              label={isSubmitting ? t('common.creating') : t('tiposMembresia.createSubmit')}
              severity="warning"
              size="small"
              disabled={isSubmitting}
              onClick={handleSubmit(onSubmit)}
            />
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  )
}
