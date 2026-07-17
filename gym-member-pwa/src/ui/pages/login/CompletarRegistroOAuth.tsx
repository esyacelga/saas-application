import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/AuthHttpRepository'
import { useAuthStore } from '@/infrastructure/store/auth.store'
import { getApiErrorCode, getApiErrorMessage } from '@/lib/api-error'

// ── Types ─────────────────────────────────────────────────────────────────────

export type CompletarRegistroOAuthProps = {
  provider: 'google' | 'facebook'
  token: string
  email: string
  nombre: string
  idCompania: number
  onCancelar: () => void
  onRegistrado: (accessToken: string, refreshToken: string) => void
}

// ── Input class helper (matches LoginPage) ────────────────────────────────────

const inputCls = (opts?: { readonly?: boolean }) =>
  `w-full rounded-xl border border-slate-700/50 pl-11 pr-11 py-3.5 text-sm placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-accent-500/50 focus:border-transparent transition-all ${
    opts?.readonly
      ? 'bg-slate-800/60 text-slate-400 cursor-default'
      : 'bg-slate-800/60 text-slate-50'
  }`

// ── Inline icons ──────────────────────────────────────────────────────────────

function IcoEnvelope() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" />
    </svg>
  )
}

function IcoUser() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
    </svg>
  )
}

function IcoIdCard() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M15 9h3.75M15 12h3.75M15 15h3.75M4.5 19.5h15a2.25 2.25 0 002.25-2.25V6.75A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25v10.5A2.25 2.25 0 004.5 19.5zm6-10.125a1.875 1.875 0 11-3.75 0 1.875 1.875 0 013.75 0zm1.294 6.336a6.721 6.721 0 01-3.17.789 6.721 6.721 0 01-3.168-.789 3.376 3.376 0 016.338 0z" />
    </svg>
  )
}

function IcoPhone() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M2.25 6.75c0 8.284 6.716 15 15 15h2.25a2.25 2.25 0 002.25-2.25v-1.372c0-.516-.351-.966-.852-1.091l-4.423-1.106c-.44-.11-.902.055-1.173.417l-.97 1.293c-.282.376-.769.542-1.21.38a12.035 12.035 0 01-7.143-7.143c-.162-.441.004-.928.38-1.21l1.293-.97c.363-.271.527-.734.417-1.173L6.963 3.102a1.125 1.125 0 00-1.091-.852H4.5A2.25 2.25 0 002.25 4.5v2.25z" />
    </svg>
  )
}

function IcoCheckCircle() {
  return (
    <svg className="h-4 w-4 text-accent-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  )
}

function IcoSpinner() {
  return (
    <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  )
}

// ── Component ─────────────────────────────────────────────────────────────────

export function CompletarRegistroOAuth({
  provider,
  token,
  email,
  nombre: nombreInicial,
  idCompania,
  onCancelar,
  onRegistrado,
}: CompletarRegistroOAuthProps) {
  const { t } = useTranslation()
  const gymInfo = useAuthStore((s) => s.gymInfo)
  const [ciError, setCiError] = useState<string | null>(null)

  const schema = z.object({
    nombre: z.string().min(3, t('completarRegistroOauth.errors.nombreMin')),
    ci: z
      .string()
      .min(3, t('completarRegistroOauth.errors.ciFormato'))
      .regex(/^\d+$/, t('completarRegistroOauth.errors.ciFormato')),
    telefono: z.string().optional(),
  })
  type FormData = z.infer<typeof schema>

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { nombre: nombreInicial, ci: '', telefono: '' },
  })

  const onSubmit = async (data: FormData) => {
    setCiError(null)
    try {
      const res = await authRepository.completarRegistroOauth({
        provider,
        token,
        id_compania: idCompania,
        ci: data.ci,
        nombre: data.nombre,
        telefono: data.telefono || undefined,
      })
      onRegistrado(res.access_token, res.refresh_token)
    } catch (err) {
      const code = getApiErrorCode(err)
      const status = (err as { response?: { status?: number } })?.response?.status

      if (status === 401) {
        toast.error(t('completarRegistroOauth.errors.generic'))
        onCancelar()
        return
      }

      if (code === 'CI_INVALIDO' || status === 400) {
        setCiError(t('completarRegistroOauth.errors.ciInvalido'))
        setError('ci', { message: t('completarRegistroOauth.errors.ciInvalido') })
        return
      }

      if (status === 409) {
        const msg = getApiErrorMessage(err)
        const isDuplicate =
          msg.toLowerCase().includes('cédula') ||
          msg.toLowerCase().includes('ci') ||
          code === 'CI_DUPLICADO'
        if (isDuplicate) {
          setCiError(t('completarRegistroOauth.errors.ciDuplicado'))
          setError('ci', { message: t('completarRegistroOauth.errors.ciDuplicado') })
        } else {
          toast.error(t('completarRegistroOauth.errors.generic'))
        }
        return
      }

      toast.error(t('completarRegistroOauth.errors.generic'))
    }
  }

  const logoUrl = gymInfo?.logo_url ?? null

  return (
    <div className="rounded-2xl bg-slate-900/75 backdrop-blur-xl ring-1 ring-white/8 shadow-2xl shadow-black/60 px-7 py-8 space-y-6">

      {/* ── Header ──────────────────────────────────────────────────────────── */}
      <div className="text-center space-y-3">
        {logoUrl ? (
          <img
            src={logoUrl}
            alt={gymInfo?.nombre_compania ?? ''}
            className="mx-auto h-20 w-20 rounded-2xl object-cover shadow-xl shadow-black/50 ring-1 ring-white/10"
          />
        ) : (
          <div className="mx-auto h-20 w-20 rounded-2xl bg-slate-800 ring-1 ring-white/8 flex items-center justify-center shadow-lg">
            <svg className="h-10 w-10 text-accent-500" fill="none" viewBox="0 0 24 24"
              stroke="currentColor" strokeWidth={1.5} aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round"
                d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
            </svg>
          </div>
        )}
        <div>
          <h2 className="text-xl font-black text-slate-50 tracking-tight">
            {t('completarRegistroOauth.greeting', { nombre: nombreInicial })}
          </h2>
          <span className="mt-2 inline-block bg-slate-800 text-slate-300 ring-1 ring-white/10 px-3 py-1 rounded-full text-xs">
            {provider === 'google'
              ? t('completarRegistroOauth.badgeGoogle')
              : t('completarRegistroOauth.badgeFacebook')}
          </span>
        </div>
      </div>

      {/* ── Form ────────────────────────────────────────────────────────────── */}
      <form noValidate onSubmit={handleSubmit(onSubmit)} className="space-y-4">

        {/* Correo (readonly) */}
        <div>
          <label className="block text-xs font-medium text-slate-400 mb-1.5">
            {t('completarRegistroOauth.fields.correo')}
          </label>
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
              <IcoEnvelope />
            </span>
            <input
              type="email"
              value={email}
              readOnly
              className={inputCls({ readonly: true })}
            />
            <span className="absolute right-4 top-1/2 -translate-y-1/2 pointer-events-none">
              <IcoCheckCircle />
            </span>
          </div>
        </div>

        {/* Nombre (editable) */}
        <div>
          <label className="block text-xs font-medium text-slate-400 mb-1.5">
            {t('completarRegistroOauth.fields.nombre')}
          </label>
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
              <IcoUser />
            </span>
            <input
              {...register('nombre')}
              disabled={isSubmitting}
              autoComplete="name"
              placeholder={t('completarRegistroOauth.fields.nombrePlaceholder')}
              className={inputCls()}
            />
          </div>
          {errors.nombre && (
            <p className="mt-1 text-xs text-red-400 pl-1">{errors.nombre.message}</p>
          )}
        </div>

        {/* Cédula / RUC (requerido) */}
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <label className="text-xs font-medium text-slate-400">
              {t('completarRegistroOauth.fields.ci')}
            </label>
            <span className="text-xs text-accent-400">
              {t('completarRegistroOauth.fields.ciRequired')}
            </span>
          </div>
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
              <IcoIdCard />
            </span>
            <input
              {...register('ci')}
              disabled={isSubmitting}
              inputMode="numeric"
              placeholder={t('completarRegistroOauth.fields.ciPlaceholder')}
              className={inputCls()}
            />
          </div>
          {(errors.ci || ciError) && (
            <p className="mt-1 text-xs text-red-400 pl-1">
              {ciError ?? errors.ci?.message}
            </p>
          )}
          {!errors.ci && !ciError && (
            <p className="mt-1 text-xs text-slate-500 pl-1">
              {t('completarRegistroOauth.fields.ciHint')}
            </p>
          )}
        </div>

        {/* Teléfono (opcional) */}
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <label className="text-xs font-medium text-slate-400">
              {t('completarRegistroOauth.fields.telefono')}
            </label>
            <span className="text-xs text-slate-500">
              {t('completarRegistroOauth.fields.telefonoOptional')}
            </span>
          </div>
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
              <IcoPhone />
            </span>
            <input
              {...register('telefono')}
              disabled={isSubmitting}
              type="tel"
              placeholder={t('completarRegistroOauth.fields.telefonoPlaceholder')}
              className={inputCls()}
            />
          </div>
          <p className="mt-1 text-xs text-slate-500 pl-1">
            {t('completarRegistroOauth.fields.telefonoHint')}
          </p>
        </div>

        {/* Submit */}
        <button
          type="submit"
          disabled={isSubmitting}
          aria-busy={isSubmitting}
          className="w-full rounded-xl bg-accent-600 py-3.5 text-sm font-semibold text-white hover:bg-accent-500 active:scale-[0.98] disabled:opacity-50 transition-all"
        >
          {isSubmitting ? (
            <span className="flex items-center justify-center gap-2">
              <IcoSpinner />
              {t('completarRegistroOauth.buttons.submitting')}
            </span>
          ) : (
            t('completarRegistroOauth.buttons.submit')
          )}
        </button>

        {/* Cancel link */}
        <p className="text-center text-xs text-slate-500 hover:text-slate-300 transition-colors cursor-pointer"
          onClick={onCancelar}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === 'Enter' && onCancelar()}
        >
          {t('completarRegistroOauth.buttons.cancel')}
        </p>

      </form>
    </div>
  )
}
