import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/AuthHttpRepository'

const schema = z
  .object({
    id_compania: z.coerce.number().min(1, 'Requerido'),
    new_password: z.string().min(6, 'Mínimo 6 caracteres'),
    confirm_password: z.string(),
  })
  .refine((d) => d.new_password === d.confirm_password, {
    message: 'Las contraseñas no coinciden',
    path: ['confirm_password'],
  })
type FormData = z.infer<typeof schema>

export function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const idCompaniaFromUrl = Number(searchParams.get('id_compania')) || 0
  const [loading, setLoading] = useState(false)

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { id_compania: idCompaniaFromUrl || undefined },
  })

  if (!token) {
    return (
      <div className="flex min-h-svh items-center justify-center px-6 bg-slate-950">
        <p className="text-sm text-red-400">{t('resetPassword.errors.invalidToken')}</p>
      </div>
    )
  }

  const onSubmit = async (data: FormData) => {
    setLoading(true)
    try {
      await authRepository.resetPassword({
        token,
        new_password: data.new_password,
        id_compania: data.id_compania,
      })
      toast.success(t('resetPassword.success'))
      navigate('/login', { replace: true })
    } catch {
      toast.error(t('resetPassword.errors.reset'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-svh flex-col items-center justify-center px-6 py-12 bg-slate-950">
      <div className="w-full max-w-sm space-y-8">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-slate-50">{t('resetPassword.title')}</h1>
          <p className="mt-1 text-sm text-slate-400">{t('resetPassword.subtitle')}</p>
        </div>

        <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {!idCompaniaFromUrl && (
            <div>
              <input
                {...register('id_compania')}
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                placeholder={t('resetPassword.fields.gymId')}
                aria-describedby={errors.id_compania ? 'rp-gymid-error' : undefined}
                className="w-full rounded-lg bg-slate-800 border border-slate-700 px-4 py-3 text-sm text-slate-50 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-accent-500"
              />
              {errors.id_compania && (
                <p id="rp-gymid-error" className="mt-1 text-xs text-red-400">
                  {errors.id_compania.message}
                </p>
              )}
            </div>
          )}

          <div>
            <input
              {...register('new_password')}
              type="password"
              autoComplete="new-password"
              placeholder={t('resetPassword.fields.newPassword')}
              aria-describedby={errors.new_password ? 'rp-newpw-error' : undefined}
              className="w-full rounded-lg bg-slate-800 border border-slate-700 px-4 py-3 text-sm text-slate-50 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-accent-500"
            />
            {errors.new_password && (
              <p id="rp-newpw-error" className="mt-1 text-xs text-red-400">
                {errors.new_password.message}
              </p>
            )}
          </div>

          <div>
            <input
              {...register('confirm_password')}
              type="password"
              autoComplete="new-password"
              placeholder={t('resetPassword.fields.confirmPassword')}
              aria-describedby={errors.confirm_password ? 'rp-confirmpw-error' : undefined}
              className="w-full rounded-lg bg-slate-800 border border-slate-700 px-4 py-3 text-sm text-slate-50 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-accent-500"
            />
            {errors.confirm_password && (
              <p id="rp-confirmpw-error" className="mt-1 text-xs text-red-400">
                {errors.confirm_password.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={loading}
            aria-busy={loading}
            className="w-full rounded-lg bg-accent-600 py-3 text-sm font-semibold text-white transition hover:bg-accent-500 disabled:opacity-50"
          >
            {loading ? t('resetPassword.buttons.submitting') : t('resetPassword.buttons.submit')}
          </button>
        </form>
      </div>
    </div>
  )
}
