import { useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { CrearPersonaRequest } from '@/infrastructure/http/auth/auth.dto'
import { createPersonaSchema, type CrearPersonaFormData } from '../schemas/persona.schema'

const AVATAR_HOMBRE = import.meta.env.VITE_AVATAR_HOMBRE_URL as string
const AVATAR_MUJER  = import.meta.env.VITE_AVATAR_MUJER_URL as string

const SEXO_OPTIONS = [
  { value: 'M' as const, avatar: AVATAR_HOMBRE, labelKey: 'clientes.sexoM', fotoUrl: AVATAR_HOMBRE },
  { value: 'F' as const, avatar: AVATAR_MUJER,  labelKey: 'clientes.sexoF', fotoUrl: AVATAR_MUJER  },
  { value: 'O' as const, avatar: null,           labelKey: 'clientes.sexoO', fotoUrl: null          },
]

interface Props {
  ci: string
  onDatosPersona: (payload: CrearPersonaRequest) => void
  onVolver: () => void
}

const inputClass =
  'w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition'

export function CrearPersonaStep({ ci, onDatosPersona, onVolver }: Props) {
  const { t } = useTranslation()
  const schema = useMemo(() => createPersonaSchema(t), [t])

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<CrearPersonaFormData>({
    resolver: zodResolver(schema),
    defaultValues: { ci },
  })

  const selectedSexo = watch('sexo')

  const onSubmit = (data: CrearPersonaFormData) => {
    const opt = SEXO_OPTIONS.find(o => o.value === data.sexo)
    onDatosPersona({
      ci: data.ci,
      nombre: data.nombre,
      telefono: data.telefono || undefined,
      correo: data.correo || undefined,
      fecha_nacimiento: data.fecha_nacimiento || undefined,
      sexo: data.sexo,
      foto_url: opt?.fotoUrl ?? undefined,
    })
  }

  return (
    <div className="max-w-lg bg-white rounded-2xl border border-slate-200 p-6 space-y-5">
      <div>
        <button
          onClick={onVolver}
          className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 transition-colors mb-3"
        >
          <ArrowLeft size={15} />
          {t('appAccounts.registerBack')}
        </button>
        <h2 className="text-lg font-semibold text-slate-900">{t('appAccounts.registerTitle')}</h2>
        <p className="text-sm text-slate-500 mt-0.5">
          {t('appAccounts.registerSubtitlePre')}{' '}
          <strong>{ci}</strong>{' '}
          {t('appAccounts.registerSubtitlePost')}
        </p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">{t('appAccounts.ciLabel')}</label>
          <input
            type="text"
            {...register('ci')}
            readOnly
            className={`${inputClass} bg-slate-50 text-slate-500 cursor-not-allowed`}
          />
          {errors.ci && <p className="text-xs text-red-600">{errors.ci.message}</p>}
        </div>

        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            {t('appAccounts.nameLabel')} <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            autoFocus
            placeholder={t('appAccounts.registerNamePlaceholder')}
            {...register('nombre')}
            className={inputClass}
          />
          {errors.nombre && <p className="text-xs text-red-600">{errors.nombre.message}</p>}
        </div>

        {/* Sexo picker */}
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            {t('clientes.fieldSexo')} <span className="text-slate-400 font-normal">{t('common.optional')}</span>
          </label>
          <div className="flex gap-2">
            {SEXO_OPTIONS.map(opt => {
              const isSelected = selectedSexo === opt.value
              return (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => setValue('sexo', opt.value, { shouldValidate: true })}
                  className="flex-1 flex flex-col items-center gap-1.5 py-2.5 px-1 rounded-xl border-2 transition-all"
                  style={{
                    borderColor: isSelected ? '#f97316' : '#e2e8f0',
                    background: isSelected ? '#fff7ed' : '#f8fafc',
                  }}
                >
                  {opt.avatar ? (
                    <img src={opt.avatar} alt={t(opt.labelKey)} className="w-11 h-11 rounded-full object-cover" />
                  ) : (
                    <div className="w-11 h-11 rounded-full flex items-center justify-center text-lg font-semibold bg-slate-200 text-slate-500">
                      ?
                    </div>
                  )}
                  <span
                    className="text-xs font-medium"
                    style={{ color: isSelected ? '#f97316' : '#64748b' }}
                  >
                    {t(opt.labelKey)}
                  </span>
                </button>
              )
            })}
          </div>
        </div>

        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            {t('appAccounts.phoneLabel')} <span className="text-slate-400 font-normal">{t('common.optional')}</span>
          </label>
          <input
            type="tel"
            placeholder={t('appAccounts.registerPhonePlaceholder')}
            {...register('telefono')}
            className={inputClass}
          />
        </div>

        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            {t('appAccounts.emailLabel')} <span className="text-slate-400 font-normal">{t('common.optional')}</span>
          </label>
          <input
            type="email"
            placeholder={t('appAccounts.registerEmailPlaceholder')}
            {...register('correo')}
            className={inputClass}
          />
          {errors.correo && <p className="text-xs text-red-600">{errors.correo.message}</p>}
        </div>

        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            {t('appAccounts.birthDateLabel')} <span className="text-slate-400 font-normal">{t('common.optional')}</span>
          </label>
          <input
            type="date"
            {...register('fecha_nacimiento')}
            className={inputClass}
          />
        </div>

        <button
          type="submit"
          className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          {t('common.next')}
        </button>
      </form>
    </div>
  )
}
