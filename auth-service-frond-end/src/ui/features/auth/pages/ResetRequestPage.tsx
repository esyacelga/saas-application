import { useState, useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Loader2, KeyRound, CheckCircle2, HelpCircle, AlertCircle, ArrowLeft } from 'lucide-react'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import { createResetRequestSchema, type ResetRequestForm } from '../schemas/reset-request.schema'

export function ResetRequestPage() {
  const { t } = useTranslation()
  const [enviado, setEnviado] = useState(false)
  const [correoEnviado, setCorreoEnviado] = useState('')

  const schema = useMemo(() => createResetRequestSchema(t), [t])

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ResetRequestForm, unknown, ResetRequestForm>({ resolver: zodResolver(schema) as never })

  const onSubmit = async (data: ResetRequestForm) => {
    try {
      const id_compania = data.id_compania === '' ? undefined : data.id_compania as number | undefined
      await authRepository.requestPasswordReset({
        correo: data.correo,
        id_compania,
        tipo: 'staff',
      })
      setCorreoEnviado(data.correo)
      setEnviado(true)
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 404) {
          setCorreoEnviado(data.correo)
          setEnviado(true)
        } else if (status === 429) {
          setError('root', { message: t('resetRequest.error429') })
        } else {
          toast.error(t('resetRequest.errorConnection'))
        }
      }
    }
  }

  if (enviado) {
    return (
      <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6 text-center">
        <div className="flex items-center justify-center w-16 h-16 rounded-full bg-green-100 mx-auto">
          <CheckCircle2 size={32} className="text-green-600" />
        </div>
        <div>
          <h2 className="text-xl font-bold text-slate-900">{t('resetRequest.successTitle')}</h2>
          <p className="text-slate-500 text-sm mt-2 leading-relaxed">
            {t('resetRequest.successCheckInbox')}{' '}
            <span className="font-medium text-slate-700">{correoEnviado}</span>.
            <br />
            {t('resetRequest.successExpiry', { minutes: 15 })}
          </p>
        </div>
        <p className="text-xs text-slate-400 bg-slate-50 rounded-lg p-3">
          {t('resetRequest.successSpamHint')}
        </p>
        <Link
          to="/login"
          className="block w-full text-center bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          {t('resetRequest.successBackToLogin')}
        </Link>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6">
      <Link
        to="/login"
        className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 transition-colors -mb-2"
      >
        <ArrowLeft size={15} />
        {t('resetRequest.backToLogin')}
      </Link>

      <div className="flex items-start gap-3">
        <div className="flex items-center justify-center w-11 h-11 rounded-xl bg-orange-100 flex-shrink-0 mt-0.5">
          <KeyRound size={22} className="text-orange-600" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-slate-900">{t('resetRequest.title')}</h1>
          <p className="text-slate-500 text-sm mt-0.5 leading-snug">
            {t('resetRequest.subtitle')}
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

        <div className="space-y-1.5">
          <label htmlFor="correo" className="block text-sm font-medium text-slate-700">
            {t('common.email')}
          </label>
          <input
            id="correo"
            type="email"
            autoComplete="email"
            placeholder={t('resetRequest.emailPlaceholder')}
            {...register('correo')}
            className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
          />
          {errors.correo && (
            <p className="text-xs text-red-600">{errors.correo.message}</p>
          )}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="id_compania" className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
            {t('resetRequest.gymId')}
            <span className="text-slate-400 font-normal">{t('common.optional')}</span>
            <span
              title={t('resetRequest.gymIdTooltip')}
              className="text-slate-400 cursor-help"
            >
              <HelpCircle size={14} />
            </span>
          </label>
          <input
            id="id_compania"
            type="number"
            placeholder={t('resetRequest.gymIdPlaceholder')}
            min={1}
            {...register('id_compania')}
            className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
          />
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 active:bg-orange-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm mt-2"
        >
          {isSubmitting && <Loader2 size={16} className="animate-spin" />}
          {isSubmitting ? t('resetRequest.submitting') : t('resetRequest.submit')}
        </button>
      </form>

      <p className="text-xs text-slate-400 text-center">
        {t('resetRequest.spamHint')}
      </p>
    </div>
  )
}
