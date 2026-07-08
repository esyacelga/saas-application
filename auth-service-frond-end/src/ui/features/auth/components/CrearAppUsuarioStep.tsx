import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowLeft, Eye, EyeOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { createAppUsuarioSchema, type CrearAppUsuarioFormData } from '../schemas/persona.schema'

interface Props {
  nombre: string
  correo: string | null
  onDatosUsuario: (data: CrearAppUsuarioFormData) => void
  onVolver: () => void
}

const inputClass =
  'w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition'

export function CrearAppUsuarioStep({ nombre, correo, onDatosUsuario, onVolver }: Props) {
  const { t } = useTranslation()
  const [showPassword, setShowPassword] = useState(false)
  const schema = useMemo(() => createAppUsuarioSchema(t), [t])

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CrearAppUsuarioFormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      login: correo?.split('@')[0] ?? '',
    },
  })

  const onSubmit = (data: CrearAppUsuarioFormData) => {
    onDatosUsuario(data)
  }

  return (
    <div className="max-w-lg bg-white rounded-2xl border border-slate-200 p-6 space-y-5">
      <div>
        <button
          onClick={onVolver}
          className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 transition-colors mb-3"
        >
          <ArrowLeft size={15} />
          {t('appAccounts.createBack')}
        </button>
        <h2 className="text-lg font-semibold text-slate-900">{t('appAccounts.createTitle')}</h2>
        <p className="text-sm text-slate-500 mt-0.5">
          {t('appAccounts.createSubtitlePre')}{' '}
          <strong className="text-slate-700">{nombre}</strong>
        </p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            {t('appAccounts.usernameLabel')}
          </label>
          <input
            type="text"
            autoFocus
            autoComplete="off"
            placeholder={t('appAccounts.usernamePlaceholder')}
            {...register('login')}
            className={inputClass}
          />
          {errors.login && <p className="text-xs text-red-600">{errors.login.message}</p>}
          <p className="text-xs text-slate-400">{t('appAccounts.usernameHint')}</p>
        </div>

        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            {t('appAccounts.passwordLabel')}
          </label>
          <div className="relative">
            <input
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="••••••••"
              {...register('password')}
              className={`${inputClass} pr-10`}
            />
            <button
              type="button"
              onClick={() => setShowPassword(v => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
              tabIndex={-1}
              aria-label={showPassword ? t('common.hidePassword') : t('common.showPassword')}
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {errors.password && <p className="text-xs text-red-600">{errors.password.message}</p>}
          <p className="text-xs text-slate-400">{t('appAccounts.passwordHint')}</p>
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
