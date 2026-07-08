import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Eye, EyeOff, Loader2, Lock, AlertCircle } from 'lucide-react'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import { createResetConfirmSchema, type ResetConfirmForm } from '../schemas/reset-confirm.schema'
import { PasswordStrength } from '../components/PasswordStrength'

export function ResetConfirmPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const schema = useMemo(() => createResetConfirmSchema(t), [t])

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ResetConfirmForm>({ resolver: zodResolver(schema) })

  const passwordValue = watch('nueva_password', '')

  if (!token) {
    return (
      <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6 text-center">
        <div className="flex items-center justify-center w-16 h-16 rounded-full bg-red-100 mx-auto">
          <AlertCircle size={32} className="text-red-500" />
        </div>
        <div>
          <h2 className="text-xl font-bold text-slate-900">{t('resetConfirm.invalidTitle')}</h2>
          <p className="text-slate-500 text-sm mt-2">
            {t('resetConfirm.invalidMessage')}
          </p>
        </div>
        <Link
          to="/reset-password"
          className="block w-full text-center bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          {t('resetConfirm.invalidButton')}
        </Link>
      </div>
    )
  }

  const onSubmit = async (data: ResetConfirmForm) => {
    try {
      await authRepository.confirmPasswordReset({ token, nueva_password: data.nueva_password })
      toast.success(t('resetConfirm.success'))
      navigate('/login', { replace: true })
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 400 || status === 404) {
          setError('root', { message: t('resetConfirm.expiredLinkError') })
        } else {
          toast.error(t('resetConfirm.errorConnection'))
        }
      }
    }
  }

  return (
    <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6">
      <div className="flex items-start gap-3">
        <div className="flex items-center justify-center w-11 h-11 rounded-xl bg-orange-100 flex-shrink-0 mt-0.5">
          <Lock size={22} className="text-orange-600" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-slate-900">{t('resetConfirm.title')}</h1>
          <p className="text-slate-500 text-sm mt-0.5">
            {t('resetConfirm.subtitle')}
          </p>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {errors.root && (
          <div className="flex items-start gap-3 bg-red-50 border border-red-200 text-red-700 text-sm p-3 rounded-lg">
            <AlertCircle size={16} className="text-red-500 mt-0.5 flex-shrink-0" />
            <div className="space-y-1">
              <span>{errors.root.message}</span>
              <Link
                to="/reset-password"
                className="block text-red-600 underline text-xs"
              >
                {t('resetConfirm.requestNewLink')}
              </Link>
            </div>
          </div>
        )}

        <div className="space-y-1.5">
          <label htmlFor="nueva_password" className="block text-sm font-medium text-slate-700">
            {t('resetConfirm.newPassword')}
          </label>
          <div className="relative">
            <input
              id="nueva_password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="••••••••"
              {...register('nueva_password')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition pr-10"
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
          {errors.nueva_password && (
            <p className="text-xs text-red-600">{errors.nueva_password.message}</p>
          )}
          <PasswordStrength password={passwordValue} />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="confirmar_password" className="block text-sm font-medium text-slate-700">
            {t('resetConfirm.confirmPassword')}
          </label>
          <div className="relative">
            <input
              id="confirmar_password"
              type={showConfirm ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="••••••••"
              {...register('confirmar_password')}
              className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition pr-10"
            />
            <button
              type="button"
              onClick={() => setShowConfirm(v => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
              tabIndex={-1}
              aria-label={showConfirm ? t('common.hidePassword') : t('common.showPassword')}
            >
              {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {errors.confirmar_password && (
            <p className="text-xs text-red-600">{errors.confirmar_password.message}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 active:bg-orange-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm mt-2"
        >
          {isSubmitting && <Loader2 size={16} className="animate-spin" />}
          {isSubmitting ? t('resetConfirm.submitting') : t('resetConfirm.submit')}
        </button>
      </form>
    </div>
  )
}
