import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { MailCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/AuthHttpRepository'

const schema = z.object({
  id_compania: z.coerce.number().min(1, 'Requerido'),
  email: z.string().email('Correo inválido'),
})
type FormData = z.infer<typeof schema>

export function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [sent, setSent] = useState(false)

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const onSubmit = async (data: FormData) => {
    setLoading(true)
    try {
      await authRepository.forgotPassword(data)
      setSent(true)
    } catch {
      toast.error(t('forgotPassword.errors.send'))
    } finally {
      setLoading(false)
    }
  }

  if (sent) {
    return (
      <div className="flex min-h-svh flex-col items-center justify-center px-6 py-12 bg-slate-950">
        <div className="w-full max-w-sm space-y-4 text-center">
          <div className="flex justify-center">
            <MailCheck size={56} className="text-accent-400" />
          </div>
          <h2 className="text-xl font-bold text-slate-50">{t('forgotPassword.success.title')}</h2>
          <p className="text-sm text-slate-400">{t('forgotPassword.success.message')}</p>
          <Link
            to="/login"
            className="inline-flex items-center justify-center min-h-[44px] px-2 text-sm text-accent-400 hover:text-accent-300"
          >
            {t('forgotPassword.buttons.backToLogin')}
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-svh flex-col items-center justify-center px-6 py-12 bg-slate-950">
      <div className="w-full max-w-sm space-y-8">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-slate-50">{t('forgotPassword.title')}</h1>
          <p className="mt-1 text-sm text-slate-400">{t('forgotPassword.subtitle')}</p>
        </div>

        <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <input
              {...register('id_compania')}
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              placeholder={t('forgotPassword.fields.gymId')}
              aria-describedby={errors.id_compania ? 'fp-gymid-error' : undefined}
              className="w-full rounded-lg bg-slate-800 border border-slate-700 px-4 py-3 text-sm text-slate-50 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-accent-500"
            />
            {errors.id_compania && (
              <p id="fp-gymid-error" className="mt-1 text-xs text-red-400">
                {errors.id_compania.message}
              </p>
            )}
          </div>

          <div>
            <input
              {...register('email')}
              type="email"
              autoComplete="email"
              placeholder={t('forgotPassword.fields.email')}
              aria-describedby={errors.email ? 'fp-email-error' : undefined}
              className="w-full rounded-lg bg-slate-800 border border-slate-700 px-4 py-3 text-sm text-slate-50 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-accent-500"
            />
            {errors.email && (
              <p id="fp-email-error" className="mt-1 text-xs text-red-400">
                {errors.email.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={loading}
            aria-busy={loading}
            className="w-full rounded-lg bg-accent-600 py-3 text-sm font-semibold text-white transition hover:bg-accent-500 disabled:opacity-50"
          >
            {loading ? t('forgotPassword.buttons.submitting') : t('forgotPassword.buttons.submit')}
          </button>
        </form>

        <div className="text-center">
          <Link
            to="/login"
            className="inline-flex items-center justify-center min-h-[44px] px-2 text-sm text-accent-400 hover:text-accent-300"
          >
            {t('forgotPassword.buttons.backToLogin')}
          </Link>
        </div>
      </div>
    </div>
  )
}
