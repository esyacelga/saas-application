import { useState, useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { Button } from 'primereact/button'
import { Camera } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import type { ClienteDetalle } from '@/infrastructure/http/core/core.dto'
import { getAvatarUrl } from '@/lib/avatar'
import { getApiErrorMessage } from '@/lib/api-error'

// ── Schema ────────────────────────────────────────────────────────────────────

const schema = z.object({
  // Persona
  nombre: z.string().min(2, 'Mínimo 2 caracteres').max(120),
  telefono: z.string().max(20).optional(),
  correo: z.string().email('Correo no válido').optional().or(z.literal('')),
  sexo: z.enum(['M', 'F']).optional(),
  fecha_nacimiento: z.string().optional(),
  // Cliente
  peso_kg: z.string().optional(),
  altura_cm: z.string().optional(),
  objetivos: z.string().max(500).optional(),
  lesiones: z.string().max(500).optional(),
})

type FormValues = z.infer<typeof schema>

// ── Constants ─────────────────────────────────────────────────────────────────

const AVATAR_HOMBRE = import.meta.env.VITE_AVATAR_HOMBRE_URL as string
const AVATAR_MUJER  = import.meta.env.VITE_AVATAR_MUJER_URL as string

// Solo M/F: la BD (identidad.personas.sexo) tiene CHECK (sexo IN ('M','F')) — 'O' violaría el constraint
const SEXO_OPTIONS = [
  { value: 'M', avatar: AVATAR_HOMBRE, labelKey: 'clientes.sexoM' },
  { value: 'F', avatar: AVATAR_MUJER,  labelKey: 'clientes.sexoF' },
] as const

const MAX_FILE_SIZE = 5 * 1024 * 1024 // 5 MB

// ── Component ─────────────────────────────────────────────────────────────────

interface Props {
  open: boolean
  detalle: ClienteDetalle
  onClose: () => void
  onGuardado: () => void
}

export function EditarClienteModal({ open, detalle, onClose, onGuardado }: Props) {
  const { t } = useTranslation()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [fotoFile, setFotoFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [fotoError, setFotoError] = useState('')

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: buildDefaults(detalle),
  })

  const selectedSexo = watch('sexo')

  // Reset form when modal opens with fresh detalle
  useEffect(() => {
    if (open) {
      reset(buildDefaults(detalle))
      setFotoFile(null)
      setPreviewUrl(null)
      setFotoError('')
    }
  }, [open, detalle, reset])

  // Revoke object URL when component unmounts or preview changes
  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl)
    }
  }, [previewUrl])

  function buildDefaults(d: ClienteDetalle): FormValues {
    return {
      nombre: d.persona.nombre,
      telefono: d.persona.telefono ?? '',
      correo: d.persona.correo ?? '',
      sexo: (() => { const s = d.sexo ?? d.persona.sexo; return s === 'M' || s === 'F' ? s : undefined })(),
      fecha_nacimiento: d.persona.fecha_nacimiento ?? '',
      peso_kg: d.peso_kg != null ? String(d.peso_kg) : '',
      altura_cm: d.altura_cm != null ? String(d.altura_cm) : '',
      objetivos: d.objetivos ?? '',
      lesiones: d.lesiones ?? '',
    }
  }

  const handleFotoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    if (!file.type.startsWith('image/')) {
      setFotoError(t('clientes.editarFotoTipoError'))
      return
    }
    if (file.size > MAX_FILE_SIZE) {
      setFotoError(t('clientes.editarFotoTamanoError'))
      return
    }

    setFotoError('')
    setFotoFile(file)
    if (previewUrl) URL.revokeObjectURL(previewUrl)
    setPreviewUrl(URL.createObjectURL(file))
  }

  const onSubmit = async (values: FormValues) => {
    const orig = buildDefaults(detalle)

    const personaCambiada =
      values.nombre !== orig.nombre ||
      (values.telefono || '') !== (orig.telefono || '') ||
      (values.correo || '') !== (orig.correo || '') ||
      values.sexo !== orig.sexo ||
      (values.fecha_nacimiento || '') !== (orig.fecha_nacimiento || '')

    const clienteCambiado =
      String(values.peso_kg ?? '') !== String(orig.peso_kg ?? '') ||
      String(values.altura_cm ?? '') !== String(orig.altura_cm ?? '') ||
      (values.objetivos || '') !== (orig.objetivos || '') ||
      (values.lesiones || '') !== (orig.lesiones || '')

    try {
      // 1. Actualizar datos de persona si cambiaron
      if (personaCambiada) {
        await authRepository.actualizarPersona(detalle.id_persona, {
          nombre: values.nombre,
          telefono: values.telefono || undefined,
          correo: values.correo || undefined,
          sexo: values.sexo,
          fecha_nacimiento: values.fecha_nacimiento || undefined,
        })
      }

      // 2. Subir foto si se seleccionó una nueva
      if (fotoFile) {
        await authRepository.subirFotoPersona(detalle.id_persona, fotoFile)
      }

      // 3. Actualizar datos de cliente si cambiaron
      if (clienteCambiado) {
        await coreRepository.actualizarCliente(detalle.id, {
          peso_kg: values.peso_kg ? Number(values.peso_kg) : undefined,
          altura_cm: values.altura_cm ? Number(values.altura_cm) : undefined,
          objetivos: values.objetivos || undefined,
          lesiones: values.lesiones || undefined,
          telefono: values.telefono || undefined,
        })
      }

      toast.success(t('clientes.editarGuardadoOk'))
      onGuardado()
    } catch (err) {
      toast.error(getApiErrorMessage(err))
    }
  }

  const currentAvatarUrl = previewUrl ?? getAvatarUrl(detalle.persona.foto_url, selectedSexo ?? detalle.sexo ?? detalle.persona.sexo)

  const inputCls = 'w-full px-3 py-2 text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500'
  const inputStyle = { background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }
  const disabledInputStyle = { background: 'var(--page-surface)', border: '1px solid var(--page-border)', color: 'var(--page-muted)', cursor: 'not-allowed' }
  const labelCls = 'block text-xs font-medium mb-1'
  const labelStyle = { color: 'var(--page-muted)' }

  return (
    <Dialog open={open} onOpenChange={v => !v && onClose()}>
      <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle style={{ color: 'var(--page-text)', fontSize: '0.875rem' }}>
            {t('clientes.editarTitle')} — {detalle.persona.nombre}
          </DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-2">

          {/* Foto */}
          <div className="flex flex-col items-center gap-3">
            <div className="relative">
              <img
                src={currentAvatarUrl}
                alt={detalle.persona.nombre}
                className="w-20 h-20 rounded-full object-cover"
                style={{ border: '2px solid var(--page-border)' }}
                onError={e => {
                  const img = e.currentTarget
                  img.onerror = null
                  img.src = getAvatarUrl(null, selectedSexo ?? detalle.sexo ?? detalle.persona.sexo)
                }}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="absolute bottom-0 right-0 w-6 h-6 rounded-full flex items-center justify-center transition-colors"
                style={{ background: '#f97316', color: '#fff' }}
                title={t('clientes.editarFotoBtn')}
              >
                <Camera size={12} />
              </button>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleFotoChange}
            />
            {fotoFile && (
              <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
                {fotoFile.name}
              </p>
            )}
            {fotoError && <p className="text-xs text-red-400">{fotoError}</p>}
          </div>

          {/* Sección persona */}
          <div className="rounded-lg p-3 space-y-3"
            style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
            <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--page-muted)' }}>
              {t('clientes.sectionPersona')}
            </p>

            {/* CI — solo lectura */}
            <div>
              <label className={labelCls} style={labelStyle}>{t('clientes.fieldCi')}</label>
              <input
                type="text"
                value={detalle.persona.ci}
                readOnly
                disabled
                className={inputCls}
                style={disabledInputStyle}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="col-span-2">
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldNombre')}</label>
                <input {...register('nombre')} className={inputCls} style={inputStyle} />
                {errors.nombre && <p className="text-xs text-red-400 mt-1">{errors.nombre.message}</p>}
              </div>
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldTelefono')}</label>
                <input {...register('telefono')} className={inputCls} style={inputStyle} placeholder="099..." />
              </div>
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldCorreo')}</label>
                <input {...register('correo')} type="email" className={inputCls} style={inputStyle} placeholder="cliente@mail.com" />
                {errors.correo && <p className="text-xs text-red-400 mt-1">{errors.correo.message}</p>}
              </div>
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldNacimiento')}</label>
                <input {...register('fecha_nacimiento')} type="date" className={inputCls} style={inputStyle} />
              </div>
            </div>

            {/* Sexo */}
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
                        <img src={opt.avatar} alt={t(opt.labelKey)} className="w-8 h-8 rounded-full object-cover" />
                      ) : (
                        <div
                          className="w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold"
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
          </div>

          {/* Sección datos físicos / cliente */}
          <div className="rounded-lg p-3 space-y-3"
            style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
            <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--page-muted)' }}>
              {t('clientes.sectionFisico')}
            </p>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldPeso')}</label>
                <input {...register('peso_kg')} type="number" step="0.1" className={inputCls} style={inputStyle} placeholder="65.0" />
                {errors.peso_kg && <p className="text-xs text-red-400 mt-1">{errors.peso_kg.message}</p>}
              </div>
              <div>
                <label className={labelCls} style={labelStyle}>{t('clientes.fieldAltura')}</label>
                <input {...register('altura_cm')} type="number" step="0.1" className={inputCls} style={inputStyle} placeholder="170.0" />
                {errors.altura_cm && <p className="text-xs text-red-400 mt-1">{errors.altura_cm.message}</p>}
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
          </div>
        </form>

        <DialogFooter>
          <Button
            label={t('common.cancel')}
            text
            size="small"
            onClick={onClose}
            style={{ color: 'var(--page-muted)' }}
          />
          <Button
            label={isSubmitting ? t('common.saving') : t('clientes.editarSubmit')}
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
