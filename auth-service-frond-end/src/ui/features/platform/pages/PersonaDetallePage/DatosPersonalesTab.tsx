import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Button } from 'primereact/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Persona } from '@/infrastructure/http/auth/auth.dto'
import { getApiErrorMessage } from '@/lib/api-error'

const AVATAR_HOMBRE = import.meta.env.VITE_AVATAR_HOMBRE_URL as string
const AVATAR_MUJER  = import.meta.env.VITE_AVATAR_MUJER_URL as string

const SEXO_OPTIONS = [
  { value: 'M' as const, avatar: AVATAR_HOMBRE, label: 'Masculino', fotoUrl: AVATAR_HOMBRE },
  { value: 'F' as const, avatar: AVATAR_MUJER,  label: 'Femenina',  fotoUrl: AVATAR_MUJER  },
  { value: 'O' as const, avatar: null,           label: 'Otro',      fotoUrl: null          },
]

const schema = z.object({
  ci:               z.string().min(5, 'CI requerida'),
  nombre:           z.string().min(2, 'Nombre requerido'),
  sexo:             z.enum(['M', 'F', 'O']).optional(),
  telefono:         z.string().optional(),
  correo:           z.string().email('Correo inválido').optional().or(z.literal('')),
  fecha_nacimiento: z.string().optional(),
})

type FormData = z.infer<typeof schema>

const inputClass =
  'w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-orange-400 transition bg-[var(--input-bg)] border-[var(--page-border)] text-[var(--page-text)] placeholder:text-[var(--page-muted)]'

const readonlyClass =
  'w-full rounded-lg px-3 py-2 text-sm border border-[var(--page-border)] bg-[var(--page-surface)] text-[var(--page-muted)]'

interface Props {
  persona: Persona
  readonly: boolean
  onActualizada: (p: Persona) => void
}

export function DatosPersonalesTab({ persona, readonly, onActualizada }: Props) {
  const [saving, setSaving] = useState(false)

  const { register, handleSubmit, setValue, watch, formState: { errors, isDirty } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      ci:               persona.ci,
      nombre:           persona.nombre,
      sexo:             persona.sexo,
      telefono:         persona.telefono ?? '',
      correo:           persona.correo ?? '',
      fecha_nacimiento: persona.fecha_nacimiento ?? '',
    },
  })

  const selectedSexo = watch('sexo')

  const onSubmit = async (data: FormData) => {
    setSaving(true)
    try {
      const opt = SEXO_OPTIONS.find(o => o.value === data.sexo)
      const updated = await authRepository.actualizarPersona(persona.id, {
        ci:               data.ci,
        nombre:           data.nombre,
        telefono:         data.telefono || undefined,
        correo:           data.correo || undefined,
        fecha_nacimiento: data.fecha_nacimiento || undefined,
        sexo:             data.sexo,
        foto_url:         data.sexo !== persona.sexo ? (opt?.fotoUrl ?? undefined) : undefined,
      })
      toast.success('Datos actualizados correctamente')
      onActualizada(updated)
    } catch (err) {
      toast.error(getApiErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  if (readonly) {
    return (
      <div className="max-w-lg space-y-4">
        <div className="grid grid-cols-2 gap-4">
          {[
            { label: 'Cédula', value: persona.ci },
            { label: 'Nombre', value: persona.nombre },
            { label: 'Teléfono', value: persona.telefono ?? '—' },
            { label: 'Correo', value: persona.correo ?? '—' },
            { label: 'Fecha nacimiento', value: persona.fecha_nacimiento ?? '—' },
            { label: 'Sexo', value: persona.sexo === 'M' ? 'Masculino' : persona.sexo === 'F' ? 'Femenina' : persona.sexo === 'O' ? 'Otro' : '—' },
          ].map(f => (
            <div key={f.label}>
              <p className="text-xs font-medium mb-1" style={{ color: 'var(--page-muted)' }}>{f.label}</p>
              <p className="text-sm" style={{ color: 'var(--page-text)' }}>{f.value}</p>
            </div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="max-w-lg space-y-4" noValidate>
      {/* CI — editable en modo plataforma */}
      <div className="space-y-1">
        <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
          Cédula <span className="text-red-500">*</span>
        </label>
        <input type="text" {...register('ci')} className={inputClass} />
        {errors.ci && <p className="text-xs text-red-500">{errors.ci.message}</p>}
      </div>

      {/* Nombre */}
      <div className="space-y-1">
        <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
          Nombre completo <span className="text-red-500">*</span>
        </label>
        <input type="text" {...register('nombre')} className={inputClass} />
        {errors.nombre && <p className="text-xs text-red-500">{errors.nombre.message}</p>}
      </div>

      {/* Sexo picker */}
      <div className="space-y-1.5">
        <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
          Sexo
        </label>
        <div className="flex gap-2">
          {SEXO_OPTIONS.map(opt => {
            const active = selectedSexo === opt.value
            return (
              <button
                key={opt.value}
                type="button"
                onClick={() => setValue('sexo', opt.value, { shouldValidate: true, shouldDirty: true })}
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
          Teléfono
        </label>
        <input type="tel" {...register('telefono')} className={inputClass} />
      </div>

      {/* Correo */}
      <div className="space-y-1">
        <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
          Correo
        </label>
        <input type="email" {...register('correo')} className={inputClass} />
        {errors.correo && <p className="text-xs text-red-500">{errors.correo.message}</p>}
      </div>

      {/* Fecha nacimiento */}
      <div className="space-y-1">
        <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
          Fecha de nacimiento
        </label>
        <input type="date" {...register('fecha_nacimiento')} className={inputClass} />
      </div>

      <div className="flex justify-end pt-2">
        <Button
          type="submit"
          label="Guardar cambios"
          size="small"
          loading={saving}
          disabled={!isDirty}
        />
      </div>
    </form>
  )
}
