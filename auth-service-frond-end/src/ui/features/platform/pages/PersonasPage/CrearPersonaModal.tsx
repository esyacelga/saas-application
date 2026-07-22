import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { X } from 'lucide-react'
import { toast } from 'sonner'
import { Dialog } from 'primereact/dialog'
import { Button } from 'primereact/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Persona } from '@/infrastructure/http/auth/auth.dto'
import { getApiErrorMessage } from '@/lib/api-error'
import { useTranslation } from 'react-i18next'
import { PhoneInputE164Controller } from '@/ui/components/PhoneInputE164'

const AVATAR_HOMBRE = import.meta.env.VITE_AVATAR_HOMBRE_URL as string
const AVATAR_MUJER  = import.meta.env.VITE_AVATAR_MUJER_URL as string

const SEXO_OPTIONS = [
  { value: 'M' as const, avatar: AVATAR_HOMBRE, label: 'Masculino', fotoUrl: AVATAR_HOMBRE },
  { value: 'F' as const, avatar: AVATAR_MUJER,  label: 'Femenina',  fotoUrl: AVATAR_MUJER  },
]

const schema = z.object({
  ci:               z.string().min(5, 'CI requerida'),
  nombre:           z.string().min(2, 'Nombre requerido'),
  sexo:             z.enum(['M', 'F']).optional(),
  telefono:         z.string().optional().refine((v) => !v || /^\+[1-9]\d{6,14}$/.test(v), 'Número de teléfono inválido'),
  correo:           z.string().email('Correo inválido').optional().or(z.literal('')),
  fecha_nacimiento: z.string().optional(),
})

type FormData = z.infer<typeof schema>

const inputClass =
  'w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-orange-400 transition bg-[var(--input-bg)] border-[var(--page-border)] text-[var(--page-text)] placeholder:text-[var(--page-muted)]'

interface Props {
  open: boolean
  onClose: () => void
  onCreada: (p: Persona) => void
}

export function CrearPersonaModal({ open, onClose, onCreada }: Props) {
  const { t } = useTranslation()
  const [saving, setSaving] = useState(false)

  const { register, handleSubmit, control, setValue, watch, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const selectedSexo = watch('sexo')

  const onSubmit = async (data: FormData) => {
    setSaving(true)
    try {
      const opt = SEXO_OPTIONS.find(o => o.value === data.sexo)
      const persona = await authRepository.crearPersona({
        ci: data.ci,
        nombre: data.nombre,
        telefono: data.telefono || undefined,
        correo: data.correo || undefined,
        fecha_nacimiento: data.fecha_nacimiento || undefined,
        sexo: data.sexo,
        foto_url: opt?.fotoUrl ?? undefined,
      })
      toast.success('Persona creada correctamente')
      onCreada(persona)
    } catch (err) {
      toast.error(getApiErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog
      visible={open}
      onHide={onClose}
      header={
        <div className="flex items-center justify-between w-full">
          <span className="text-base font-semibold" style={{ color: 'var(--page-text)' }}>Nueva Persona</span>
          <button onClick={onClose} className="text-[var(--page-muted)] hover:text-[var(--page-text)] transition-colors">
            <X size={18} />
          </button>
        </div>
      }
      style={{ width: '480px' }}
      modal
      closable={false}
      pt={{ root: { style: { background: 'var(--page-surface)', border: '1px solid var(--page-border)' } } }}
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 pt-1" noValidate>
        {/* CI */}
        <div className="space-y-1">
          <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
            Cédula <span className="text-red-500">*</span>
          </label>
          <input type="text" placeholder="Ej. 1234567890" {...register('ci')} className={inputClass} />
          {errors.ci && <p className="text-xs text-red-500">{errors.ci.message}</p>}
        </div>

        {/* Nombre */}
        <div className="space-y-1">
          <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
            Nombre completo <span className="text-red-500">*</span>
          </label>
          <input type="text" placeholder="Ej. Juan Pérez" {...register('nombre')} className={inputClass} autoFocus />
          {errors.nombre && <p className="text-xs text-red-500">{errors.nombre.message}</p>}
        </div>

        {/* Sexo */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
            Sexo <span className="text-xs font-normal" style={{ color: 'var(--page-muted)' }}>(opcional)</span>
          </label>
          <div className="flex gap-2">
            {SEXO_OPTIONS.map(opt => {
              const active = selectedSexo === opt.value
              return (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => setValue('sexo', opt.value, { shouldValidate: true })}
                  className="flex-1 flex flex-col items-center gap-1.5 py-2.5 px-1 rounded-xl border-2 transition-all"
                  style={{
                    borderColor: active ? '#f97316' : 'var(--page-border)',
                    background: active ? 'rgba(249,115,22,0.07)' : 'var(--input-bg)',
                  }}
                >
                  {opt.avatar ? (
                    <img src={opt.avatar} alt={opt.label} className="w-11 h-11 rounded-full object-cover" />
                  ) : (
                    <div className="w-11 h-11 rounded-full flex items-center justify-center text-lg font-semibold bg-slate-200 text-slate-500">?</div>
                  )}
                  <span className="text-xs font-medium" style={{ color: active ? '#f97316' : 'var(--page-muted)' }}>
                    {opt.label}
                  </span>
                </button>
              )
            })}
          </div>
        </div>

        {/* Teléfono */}
        <div className="space-y-1">
          <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
            Teléfono <span className="text-xs font-normal" style={{ color: 'var(--page-muted)' }}>(opcional)</span>
          </label>
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

        {/* Correo */}
        <div className="space-y-1">
          <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
            Correo <span className="text-xs font-normal" style={{ color: 'var(--page-muted)' }}>(opcional)</span>
          </label>
          <input type="email" placeholder="Ej. juan@mail.com" {...register('correo')} className={inputClass} />
          {errors.correo && <p className="text-xs text-red-500">{errors.correo.message}</p>}
        </div>

        {/* Fecha nacimiento */}
        <div className="space-y-1">
          <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
            Fecha de nacimiento <span className="text-xs font-normal" style={{ color: 'var(--page-muted)' }}>(opcional)</span>
          </label>
          <input type="date" {...register('fecha_nacimiento')} className={inputClass} />
        </div>

        {/* Acciones */}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" label="Cancelar" severity="secondary" size="small" onClick={onClose} disabled={saving} />
          <Button type="submit" label="Crear persona" size="small" loading={saving} />
        </div>
      </form>
    </Dialog>
  )
}
