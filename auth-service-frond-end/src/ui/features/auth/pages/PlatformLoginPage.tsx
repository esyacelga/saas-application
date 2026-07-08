import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Navigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Eye, EyeOff, Loader2, AlertCircle, Dumbbell } from 'lucide-react'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import { useAuthStore, useIsPlatformUser } from '@/infrastructure/store/auth/auth.store'
import { createPlatformLoginSchema, type PlatformLoginForm } from '../schemas/platform-login.schema'

export function PlatformLoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { setSession } = useAuthStore()
  const isPlatformUser = useIsPlatformUser()
  const [showPassword, setShowPassword] = useState(false)

  if (isPlatformUser) return <Navigate to="/platform/dashboard" replace />

  const schema = useMemo(() => createPlatformLoginSchema(t), [t])

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<PlatformLoginForm>({ resolver: zodResolver(schema) })

  const onSubmit = async (data: PlatformLoginForm) => {
    try {
      const response = await authRepository.loginPlatform(data)
      setSession(response.access_token)
      navigate('/platform/dashboard', { replace: true })
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 401) {
          setError('root', { message: t('platformLogin.error401') })
        } else if (status === 403) {
          setError('root', { message: t('platformLogin.error403') })
        } else if (status === 429) {
          setError('root', { message: t('platformLogin.error429') })
        } else {
          toast.error(t('platformLogin.errorConnection'))
        }
      }
    }
  }

  return (
    <div className="min-h-screen bg-gym-950 flex flex-col items-center justify-center p-6">
      <div className="flex items-center gap-3 mb-8">
        <div className="w-11 h-11 rounded-xl bg-orange-500 flex items-center justify-center shadow-lg shadow-orange-500/30">
          <Dumbbell size={24} className="text-white" />
        </div>
        <div>
          <p className="text-white font-bold text-base leading-tight">{t('common.gymAdmin')}</p>
          <p className="text-slate-500 text-xs leading-tight">{t('platformLogin.platformPanel')}</p>
        </div>
      </div>

      <div className="w-full max-w-md bg-gym-900 border border-gym-800 rounded-2xl p-8 space-y-6 shadow-2xl">
        <div>
          <h1 className="text-xl font-bold text-white">{t('platformLogin.title')}</h1>
          <p className="text-slate-500 text-sm mt-1">
            {t('platformLogin.subtitle')}
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {errors.root && (
            <div className="flex items-start gap-3 bg-red-500/10 border border-red-500/30 text-red-400 text-sm p-3 rounded-lg">
              <AlertCircle size={16} className="mt-0.5 flex-shrink-0" />
              <span>{errors.root.message}</span>
            </div>
          )}

          <div className="space-y-1.5">
            <label htmlFor="correo" className="block text-sm font-medium text-slate-300">
              {t('common.email')}
            </label>
            <input
              id="correo"
              type="email"
              autoComplete="email"
              placeholder={t('platformLogin.emailPlaceholder')}
              {...register('correo')}
              className="w-full bg-gym-800 border border-gym-700 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
            />
            {errors.correo && (
              <p className="text-xs text-red-400">{errors.correo.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <label htmlFor="password" className="block text-sm font-medium text-slate-300">
              {t('common.password')}
            </label>
            <div className="relative">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="••••••••"
                {...register('password')}
                className="w-full bg-gym-800 border border-gym-700 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition pr-10"
              />
              <button
                type="button"
                onClick={() => setShowPassword(v => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors"
                tabIndex={-1}
                aria-label={showPassword ? t('common.hidePassword') : t('common.showPassword')}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {errors.password && (
              <p className="text-xs text-red-400">{errors.password.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 active:bg-orange-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm mt-2"
          >
            {isSubmitting && <Loader2 size={16} className="animate-spin" />}
            {isSubmitting ? t('platformLogin.submitting') : t('platformLogin.submit')}
          </button>
        </form>
      </div>

      <Link
        to="/login"
        className="mt-6 text-sm text-slate-500 hover:text-slate-300 transition-colors"
      >
        {t('platformLogin.backToGym')}
      </Link>
    </div>
  )
}
