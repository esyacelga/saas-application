import { useState, useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { Button } from 'primereact/button'
import { CheckCircle, Search } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import type { BuscarPorCiResponse } from '@/infrastructure/http/core/core.dto'
import { PhoneInputE164Controller } from '@/ui/components/PhoneInputE164'

const AVATAR_HOMBRE = import.meta.env.VITE_AVATAR_HOMBRE_URL as string
const AVATAR_MUJER  = import.meta.env.VITE_AVATAR_MUJER_URL as string

const SEXO_OPTIONS = [
  { value: 'M', avatar: AVATAR_HOMBRE, labelKey: 'clientes.sexoM' },
  { value: 'F', avatar: AVATAR_MUJER,  labelKey: 'clientes.sexoF' },
  { value: 'O', avatar: null,          labelKey: 'clientes.sexoO' },
] as const

const schema = z.object({
  ci: z.string().min(4, 'Mínimo 4 caracteres'),
  nombre: z.string().min(2),
  telefono: z.string().optional().refine((v) => !v || /^\+[1-9]\d{6,14}$/.test(v), 'Número de teléfono inválido'),
  correo: z.string().email().optional().or(z.literal('')),
  peso_kg: z.coerce.number().min(1).optional().or(z.literal('')),
  altura_cm: z.coerce.number().min(1).optional().or(z.literal('')),
  objetivos: z.string().optional(),
  lesiones: z.string().optional(),
  id_sucursal: z.coerce.number().int().min(1),
  sexo: z.enum(['M', 'F', 'O']).optional(),
  // Sin `.default()`: haría diferir los tipos input/output de Zod y rompería el
  // zodResolver. El valor inicial va en `defaultValues` del useForm.
  acepta_whatsapp: z.boolean(),
})

type FormValues = z.infer<typeof schema>

interface Props {
  open: boolean
  idSucursal: number
  onClose: () => void
  onRegistrado: (idCliente: number) => void
}

export function RegistrarClienteModal({ open, idSucursal, onClose, onRegistrado }: Props) {
  const { t } = useTranslation()
  const [ciSearch, setCiSearch] = useState('')
  const [searching, setSearching] = useState(false)
  const [ciResult, setCiResult] = useState<BuscarPorCiResponse | null>(null)
  const [step, setStep] = useState<'search' | 'form'>('search')

  const { register, handleSubmit, control, setValue, watch, reset, formState: { errors, isSubmitting } } = useForm<z.input<typeof schema>, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { id_sucursal: idSucursal },
  })

  const selectedSexo = watch('sexo')

  useEffect(() => {
    if (!open) { setCiSearch(''); setCiResult(null); setStep('search'); reset() }
  }, [open, reset])

  const handleBuscarCi = async () => {
    if (!ciSearch.trim()) return
    setSearching(true)
    try {
      const res = await coreRepository.buscarPorCi(ciSearch.trim())
      setCiResult(res)
      if (res.es_cliente_en_este_gym) {
        toast.info(t('clientes.ciYaEsCliente'))
      } else {
        setValue('ci', res.persona.ci)
        setValue('nombre', res.persona.nombre)
        setStep('form')
      }
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 404) {
        setValue('ci', ciSearch.trim())
        setCiResult(null)
        setStep('form')
      } else {
        toast.error(t('clientes.searchError'))
      }
    } finally {
      setSearching(false)
    }
  }

  const onSubmit = async (values: FormValues) => {
    try {
      const res = await coreRepository.registrarCliente({
        ci: values.ci,
        nombre: values.nombre,
        telefono: values.telefono || undefined,
        correo: values.correo || undefined,
        peso_kg: values.peso_kg ? Number(values.peso_kg) : undefined,
        altura_cm: values.altura_cm ? Number(values.altura_cm) : undefined,
        objetivos: values.objetivos || undefined,
        lesiones: values.lesiones || undefined,
        id_sucursal: values.id_sucursal,
        sexo: values.sexo || undefined,
      })
      toast.success(t('clientes.registerSuccess'))
      onRegistrado(res.id_cliente)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error(t('clientes.registerError409'))
      } else {
        toast.error(t('clientes.registerErrorConn'))
      }
    }
  }

  const inputCls = 'w-full px-3 py-2 text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500'
  const inputStyle = { background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }
  const labelCls = 'block text-xs font-medium mb-1'
  const labelStyle = { color: 'var(--page-muted)' }

  return (
    <Dialog open={open} onOpenChange={v => !v && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle style={{ color: 'var(--page-text)', fontSize: '0.875rem' }}>
            {t('clientes.registerTitle')}
          </DialogTitle>
        </DialogHeader>

        {step === 'search' ? (
          <div className="py-4 space-y-4">
            <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('clientes.searchHint')}</p>
            <div className="flex gap-2">
              <input
                value={ciSearch}
                onChange={e => setCiSearch(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleBuscarCi()}
                placeholder={t('clientes.ciPlaceholder')}
                className={inputCls + ' flex-1'}
                style={inputStyle}
              />
              <Button icon={<Search size={14} />} label={t('clientes.searchBtn')}
                severity="warning" size="small" loading={searching} onClick={handleBuscarCi} />
            </div>

            {ciResult?.es_cliente_en_este_gym && (
              <div className="flex items-center gap-2 p-3 rounded-lg"
                style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
                <CheckCircle size={16} className="text-green-500 flex-shrink-0" />
                <div>
                  <p className="text-xs font-semibold" style={{ color: 'var(--page-text)' }}>
                    {ciResult.persona.nombre}
                  </p>
                  <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
                    {t('clientes.ciYaEsCliente')}
                  </p>
                </div>
              </div>
            )}
          </div>
        ) : (
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-3 py-2">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldCi')}</label>
                <input {...register('ci')} className={inputCls} style={inputStyle}
                  readOnly={!!ciResult} />
                {errors.ci && <p className="text-xs text-red-400 mt-1">{errors.ci.message}</p>}
              </div>
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldNombre')}</label>
                <input {...register('nombre')} className={inputCls} style={inputStyle} />
                {errors.nombre && <p className="text-xs text-red-400 mt-1">{errors.nombre.message}</p>}
              </div>
            </div>

            {/* Sexo selector */}
            <div>
              <label className={labelCls} style={labelStyle}>{t('clientes.fieldSexo')}</label>
              <div className="flex gap-2 mt-1">
                {SEXO_OPTIONS.map(opt => {
                  const isSelected = selectedSexo === opt.value
                  return (
                    <button
                      key={opt.value}
                      type="button"
                      onClick={() => setValue('sexo', opt.value, { shouldValidate: true })}
                      className="flex-1 flex flex-col items-center gap-1.5 py-2 px-1 rounded-lg border-2 transition-all"
                      style={{
                        background: isSelected ? 'var(--page-surface)' : 'var(--input-bg)',
                        borderColor: isSelected ? '#f97316' : 'var(--page-border)',
                      }}
                    >
                      {opt.avatar ? (
                        <img src={opt.avatar} alt={t(opt.labelKey)} className="w-10 h-10 rounded-full object-cover" />
                      ) : (
                        <div
                          className="w-10 h-10 rounded-full flex items-center justify-center text-base font-semibold"
                          style={{ background: 'var(--page-border)', color: 'var(--page-muted)' }}
                        >
                          ?
                        </div>
                      )}
                      <span className="text-xs font-medium" style={{ color: isSelected ? '#f97316' : 'var(--page-muted)' }}>
                        {t(opt.labelKey)}
                      </span>
                    </button>
                  )
                })}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldTelefono')}</label>
                <PhoneInputE164Controller
                  name="telefono"
                  control={control}
                  defaultCountry="EC"
                  placeholder={t('phoneInput.placeholder')}
                />
                {errors.telefono && (
                  <p className="text-xs text-red-500 mt-1">{errors.telefono.message ?? t('phoneInput.invalid')}</p>
                )}
              </div>
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldCorreo')}</label>
                <input {...register('correo')} type="email" className={inputCls} style={inputStyle} placeholder="cliente@mail.com" />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldPeso')}</label>
                <input {...register('peso_kg')} type="number" step="0.1" className={inputCls} style={inputStyle} placeholder="65.0" />
              </div>
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldAltura')}</label>
                <input {...register('altura_cm')} type="number" step="0.1" className={inputCls} style={inputStyle} placeholder="170.0" />
              </div>
            </div>
            <div>
              <label className={labelCls} style={labelStyle}>{t('clientes.fieldObjetivos')}</label>
              <textarea {...register('objetivos')} rows={2} className={inputCls} style={inputStyle}
                placeholder={t('clientes.fieldObjetivosPlaceholder')} />
            </div>
            <div>
              <label className={labelCls} style={labelStyle}>{t('clientes.fieldLesiones')}</label>
              <textarea {...register('lesiones')} rows={2} className={inputCls} style={inputStyle}
                placeholder={t('clientes.fieldLesionesPlaceholder')} />
            </div>
            <input type="hidden" {...register('id_sucursal')} />
          </form>
        )}

        <DialogFooter>
          <Button label={step === 'form' ? t('clientes.backToSearch') : t('common.cancel')}
            text size="small" onClick={step === 'form' ? () => setStep('search') : onClose}
            style={{ color: 'var(--page-muted)' }} />
          {step === 'form' && (
            <Button label={isSubmitting ? t('common.creating') : t('clientes.registerSubmit')}
              severity="warning" size="small" disabled={isSubmitting} onClick={handleSubmit(onSubmit)} />
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
