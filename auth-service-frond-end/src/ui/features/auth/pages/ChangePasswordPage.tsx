import { useMemo, useState } from 'react'
import { useForm, type UseFormRegisterReturn } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Navigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Eye, EyeOff, Loader2, ShieldCheck, AlertCircle } from 'lucide-react'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import { useIsAuthenticated, useAuthStore } from '@/infrastructure/store/auth/auth.store'
import { createChangePasswordSchema, type ChangePasswordForm } from '../schemas/change-password.schema'
import { PasswordStrength } from '../components/PasswordStrength'

function PasswordInput({
  id,
  label,
  autoComplete,
  registration,
  error,
}: {
  id: string
  label: string
  autoComplete: string
  registration: UseFormRegisterReturn
  error?: string
}) {
  const { t } = useTranslation()
  const [show, setShow] = useState(false)
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          type={show ? 'text' : 'password'}
          autoComplete={autoComplete}
          placeholder="••••••••"
          {...registration}
          className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition pr-10"
        />
        <button
          type="button"
          onClick={() => setShow(v => !v)}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
          tabIndex={-1}
          aria-label={show ? t('common.hidePassword') : t('common.showPassword')}
        >
          {show ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}

export function ChangePasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const isAuthenticated = useIsAuthenticated()
  const { logout } = useAuthStore()

  if (!isAuthenticated) return <Navigate to="/login" replace />

  const schema = useMemo(() => createChangePasswordSchema(t), [t])

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ChangePasswordForm>({ resolver: zodResolver(schema) })

  const nuevaPassword = watch('nueva_password', '')

  const onSubmit = async (data: ChangePasswordForm) => {
    try {
      await authRepository.changePassword({
        password_actual: data.password_actual,
        nueva_password: data.nueva_password,
      })
      toast.success(t('changePassword.success'))
      navigate('/admin/dashboard', { replace: true })
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 401) {
          setError('password_actual', { message: t('changePassword.error401') })
        } else if (status === 422) {
          setError('nueva_password', { message: t('changePassword.error422') })
        } else {
          toast.error(t('changePassword.errorConnection'))
        }
      }
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6">
        <div className="flex items-start gap-3">
          <div className="flex items-center justify-center w-11 h-11 rounded-xl bg-orange-100 flex-shrink-0 mt-0.5">
            <ShieldCheck size={22} className="text-orange-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-900">{t('changePassword.title')}</h1>
            <p className="text-slate-500 text-sm mt-0.5 leading-snug">
              {t('changePassword.subtitle')}
            </p>
          </div>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {errors.root && (
            <div className="flex items-start gap-3 bg-red-50 border border-red-200 text-red-700 text-sm p-3 rounded-lg">
              <AlertCircle size={16} className="text-red-500 mt-0.5 flex-shrink-0" />
              <span>{errors.root.message}</span>
            </div>
          )}

          <PasswordInput
            id="password_actual"
            label={t('changePassword.currentPassword')}
            autoComplete="current-password"
            registration={register('password_actual')}
            error={errors.password_actual?.message}
          />

          <div className="border-t border-slate-100 pt-2" />

          <div className="space-y-1.5">
            <label htmlFor="nueva_password" className="block text-sm font-medium text-slate-700">
              {t('changePassword.newPassword')}
            </label>
            <div className="relative">
              <input
                id="nueva_password"
                type="password"
                autoComplete="new-password"
                placeholder="••••••••"
                {...register('nueva_password')}
                className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
              />
            </div>
            {errors.nueva_password && (
              <p className="text-xs text-red-600">{errors.nueva_password.message}</p>
            )}
            <PasswordStrength password={nuevaPassword} />
          </div>

          <PasswordInput
            id="confirmar_password"
            label={t('changePassword.confirmPassword')}
            autoComplete="new-password"
            registration={register('confirmar_password')}
            error={errors.confirmar_password?.message}
          />

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 active:bg-orange-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm mt-2"
          >
            {isSubmitting && <Loader2 size={16} className="animate-spin" />}
            {isSubmitting ? t('changePassword.submitting') : t('changePassword.submit')}
          </button>
        </form>

        <div className="text-center">
          <button
            onClick={() => {
              logout()
              navigate('/login', { replace: true })
            }}
            className="text-xs text-slate-400 hover:text-slate-600 transition-colors"
          >
            {t('changePassword.cancelLogout')}
          </button>
        </div>
      </div>
    </div>
  )
}
