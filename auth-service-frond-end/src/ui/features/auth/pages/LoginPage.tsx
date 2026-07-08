import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Navigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Eye, EyeOff, Loader2, AlertCircle, Building2, ChevronDown, MessageCircle } from 'lucide-react'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import { useAuthStore, useIsAuthenticated } from '@/infrastructure/store/auth/auth.store'
import { createLoginStaffSchema, type LoginStaffForm } from '../schemas/login.schema'
import type { CompaniaBasica } from '@/infrastructure/http/auth/auth.dto'

export function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { setSession } = useAuthStore()
  const isAuthenticated = useIsAuthenticated()
  const [showPassword, setShowPassword] = useState(false)

  const [companies, setCompanies] = useState<CompaniaBasica[]>([])
  const [loadingCompanies, setLoadingCompanies] = useState(false)
  const [companiesChecked, setCompaniesChecked] = useState(false)

  const schema = useMemo(() => createLoginStaffSchema(t), [t])

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<LoginStaffForm, unknown, LoginStaffForm>({ resolver: zodResolver(schema) as never })

  if (isAuthenticated) return <Navigate to="/admin/dashboard" replace />

  const correoRegistration = register('correo')

  const handleEmailBlur = async (email: string) => {
    const trimmed = email.trim()
    if (!trimmed || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) return

    setLoadingCompanies(true)
    setCompaniesChecked(false)
    setCompanies([])

    try {
      const result = await authRepository.getCompaniesByCorreo(trimmed)
      setCompanies(result)
      if (result.length === 1) {
        setValue('id_compania', result[0].id, { shouldValidate: false })
      }
    } catch {
      // network error — user will see it on submit
    } finally {
      setLoadingCompanies(false)
      setCompaniesChecked(true)
    }
  }

  const handleEmailChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    await correoRegistration.onChange(e)
    setCompanies([])
    setCompaniesChecked(false)
  }

  const onSubmit = async (data: LoginStaffForm) => {
    try {
      const response = await authRepository.loginStaff(data)
      setSession(response.access_token)

      if (response.requiere_cambio_pwd) {
        navigate('/change-password', { replace: true })
      } else {
        navigate('/admin/dashboard', { replace: true })
      }
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 401) {
          setError('root', { message: t('login.error401') })
        } else if (status === 403) {
          setError('root', { message: t('login.error403') })
        } else if (status === 429) {
          setError('root', { message: t('login.error429') })
        } else {
          toast.error(t('login.errorConnection'))
        }
      }
    }
  }

  const showCompanySelector = loadingCompanies || companiesChecked
  const singleCompany = companies.length === 1 ? companies[0] : null
  const multipleCompanies = companies.length > 1

  return (
    <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-8 space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">{t('login.title')}</h1>
        <p className="text-slate-500 text-sm mt-1">
          {t('login.subtitle')}
        </p>
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
            placeholder={t('login.emailPlaceholder')}
            {...correoRegistration}
            onChange={handleEmailChange}
            onBlur={async (e) => {
              correoRegistration.onBlur(e)
              await handleEmailBlur(e.target.value)
            }}
            className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
          />
          {errors.correo && (
            <p className="text-xs text-red-600">{errors.correo.message}</p>
          )}
        </div>

        {showCompanySelector && (
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              {t('login.selectCompanyLabel')}
            </label>

            {loadingCompanies && (
              <div className="flex items-center gap-2 text-sm text-slate-500 border border-slate-200 rounded-lg px-3 py-2.5">
                <Loader2 size={14} className="animate-spin text-orange-500" />
                <span>{t('login.loadingCompanies')}</span>
              </div>
            )}

            {!loadingCompanies && singleCompany && (
              <div className="flex items-center gap-2 bg-orange-50 border border-orange-200 text-orange-800 text-sm rounded-lg px-3 py-2.5">
                <Building2 size={14} className="flex-shrink-0" />
                <span className="font-medium">{singleCompany.nombre}</span>
              </div>
            )}

            {!loadingCompanies && multipleCompanies && (
              <div className="relative">
                <select
                  defaultValue=""
                  onChange={(e) => {
                    const id = Number(e.target.value)
                    if (id > 0) setValue('id_compania', id, { shouldValidate: false })
                  }}
                  className="w-full appearance-none border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition pr-8 bg-white"
                >
                  <option value="" disabled>{t('login.selectCompanyPlaceholder')}</option>
                  {companies.map(c => (
                    <option key={c.id} value={c.id}>{c.nombre}</option>
                  ))}
                </select>
                <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
              </div>
            )}

            {!loadingCompanies && companiesChecked && companies.length === 0 && (
              <p className="text-xs text-amber-600">{t('login.companyNotFound')}</p>
            )}

            {errors.id_compania && (
              <p className="text-xs text-red-600">{t('login.companyRequired')}</p>
            )}
          </div>
        )}

        <div className="space-y-1.5">
          <label htmlFor="password" className="block text-sm font-medium text-slate-700">
            {t('common.password')}
          </label>
          <div className="relative">
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="••••••••"
              {...register('password')}
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
          {errors.password && (
            <p className="text-xs text-red-600">{errors.password.message}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={isSubmitting || loadingCompanies}
          className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 active:bg-orange-700 disabled:opacity-60 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm mt-2"
        >
          {isSubmitting && <Loader2 size={16} className="animate-spin" />}
          {isSubmitting ? t('login.submitting') : t('login.submit')}
        </button>
      </form>

      <div className="text-center space-y-2 text-sm pt-2 border-t border-slate-100">
        <Link
          to="/reset-password"
          className="block text-orange-600 hover:text-orange-700 font-medium transition-colors"
        >
          {t('login.forgotPassword')}
        </Link>
        <a
          href="https://wa.me/593958832436"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1.5 text-green-600 hover:text-green-700 text-xs font-medium transition-colors"
        >
          <MessageCircle size={13} />
          {t('login.contactUs')}
        </a>
        <Link
          to="/registro"
          className="block text-orange-600 hover:text-orange-700 font-medium transition-colors text-sm"
        >
          ¿No tienes cuenta? Regístrate gratis
        </Link>
        <Link
          to="/platform/login"
          className="block text-slate-400 hover:text-slate-600 text-xs transition-colors"
        >
          {t('login.platformAccess')}
        </Link>
      </div>
    </div>
  )
}
