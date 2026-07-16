import { useState, useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { Camera } from 'lucide-react'
import { useAuthStore, useCurrentUser } from '@/infrastructure/store/auth.store'
import { useThemeStore } from '@/infrastructure/store/theme.store'
import { PulseBackground } from '@/ui/components/PulseBackground'
import { authRepository } from '@/infrastructure/http/AuthHttpRepository'
import { ThemeSelector } from './ThemeSelector'
import { LangToggle } from '@/ui/components/LangToggle'
import type { PersonaResponse, ConsentimientoWaPersonaResponse } from '@/application/usecase/auth.types'

// ── Schema ────────────────────────────────────────────────────────────────────

const profileSchema = z.object({
  nombre: z.string().min(2),
  ci: z.string().optional(),
  telefono: z.string().optional(),
  sexo: z.enum(['M', 'F']).optional(),
  fecha_nacimiento: z.string().optional(),
})
type ProfileFormData = z.infer<typeof profileSchema>

// ── Helpers ───────────────────────────────────────────────────────────────────

function IcoSpinner() {
  return (
    <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  )
}

const fieldCls =
  'mt-1 w-full rounded-lg bg-slate-700/40 border border-slate-700/50 px-3 py-2.5 text-sm text-slate-50 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-accent-500/50 transition-all'
const readOnlyCls =
  'mt-1 px-3 py-2.5 rounded-lg bg-slate-700/20 text-sm text-slate-500 border border-slate-700/30'

// ── Component ─────────────────────────────────────────────────────────────────

export function ProfilePage() {
  const { t } = useTranslation()
  const user = useCurrentUser()
  const clear = useAuthStore((s) => s.clear)
  const refreshToken = useAuthStore((s) => s.refreshToken)
  const setTokens = useAuthStore((s) => s.setTokens)
  const initTheme = useThemeStore((s) => s.initTheme)
  const navigate = useNavigate()

  const [persona, setPersona] = useState<PersonaResponse | null>(null)
  const [loadingPersona, setLoadingPersona] = useState(true)
  const [photoPreview, setPhotoPreview] = useState<string | null>(user?.foto_url ?? null)
  const [uploadingPhoto, setUploadingPhoto] = useState(false)
  const [saving, setSaving] = useState(false)
  const photoRef = useRef<HTMLInputElement>(null)

  const [waConsent, setWaConsent] = useState<ConsentimientoWaPersonaResponse | null>(null)
  const [savingWa, setSavingWa] = useState(false)

  const form = useForm<ProfileFormData>({ resolver: zodResolver(profileSchema) })
  const currentSexo = form.watch('sexo')

  useEffect(() => {
    if (!user) return
    authRepository
      .getPersona(user.id_persona)
      .then((p) => {
        setPersona(p)
        setPhotoPreview(p.foto_url)
        form.reset({
          nombre: p.nombre,
          ci: p.ci ?? '',
          telefono: p.telefono ?? '',
          sexo: (p.sexo as 'M' | 'F') ?? undefined,
          fecha_nacimiento: p.fecha_nacimiento ?? '',
        })
      })
      .catch(() => toast.error(t('profile.edit.errors.load')))
      .finally(() => setLoadingPersona(false))
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const refreshSession = async () => {
    if (!refreshToken) return
    const res = await authRepository.refresh(refreshToken)
    setTokens(res.access_token, refreshToken)
    initTheme(useAuthStore.getState().user?.sexo ?? null)
  }

  const handlePhotoChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      toast.error(t('profile.edit.errors.photoSize'))
      return
    }
    setPhotoPreview(URL.createObjectURL(file))
    setUploadingPhoto(true)
    try {
      await authRepository.subirFotoMiembro(user!.id_persona, file)
      await refreshSession()
      toast.success(t('profile.edit.success'))
    } catch {
      toast.error(t('profile.edit.errors.photoUpload'))
      setPhotoPreview(persona?.foto_url ?? null)
    } finally {
      setUploadingPhoto(false)
      e.target.value = ''
    }
  }

  const onSubmit = async (data: ProfileFormData) => {
    if (!user) return
    setSaving(true)
    try {
      const updated = await authRepository.actualizarPersona(user.id_persona, {
        nombre: data.nombre,
        ci: data.ci || undefined,
        telefono: data.telefono || undefined,
        sexo: data.sexo,
        fecha_nacimiento: data.fecha_nacimiento || undefined,
      })
      setPersona(updated)
      await refreshSession()
      form.reset(data)
      toast.success(t('profile.edit.success'))
    } catch {
      toast.error(t('profile.edit.errors.save'))
    } finally {
      setSaving(false)
    }
  }

  const handleLogout = async () => {
    try { await authRepository.logout() } catch { /* ignore */ }
    clear()
    navigate('/login', { replace: true })
    toast.success(t('profile.success.logout'))
  }

  const initials = user?.nombre
    ? user.nombre.split(' ').slice(0, 2).map((w) => w[0]).join('').toUpperCase()
    : '?'

  const ciIsSet = !!(persona?.ci)

  return (
    <div className="space-y-6 pb-6">
      <PulseBackground />

      {/* Header */}
      <div className="px-4 pt-8 pb-4 bg-accent-900/20 border-b border-accent-800/40">
        <h1 className="text-xl font-bold text-slate-50">{t('profile.title')}</h1>
      </div>

      <div className="px-4 space-y-6">

        {/* Avatar */}
        <div className="flex flex-col items-center gap-2 py-2">
          <button
            type="button"
            onClick={() => photoRef.current?.click()}
            disabled={uploadingPhoto}
            className="relative focus:outline-none"
            aria-label={t('profile.edit.buttons.changePhoto')}
          >
            {photoPreview ? (
              <img
                src={photoPreview}
                alt={user?.nombre ?? ''}
                className="w-24 h-24 rounded-full object-cover border-2 border-slate-700"
              />
            ) : (
              <div className="w-24 h-24 rounded-full bg-accent-700 flex items-center justify-center text-2xl font-bold text-white border-2 border-slate-700">
                {initials}
              </div>
            )}

            {uploadingPhoto ? (
              <div className="absolute inset-0 rounded-full bg-slate-900/70 flex items-center justify-center">
                <IcoSpinner />
              </div>
            ) : (
              <div className="absolute bottom-0 right-0 w-7 h-7 rounded-full bg-accent-600 border-2 border-slate-950 flex items-center justify-center">
                <Camera size={13} className="text-white" />
              </div>
            )}
          </button>

          <input
            ref={photoRef}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            className="hidden"
            onChange={handlePhotoChange}
          />

          <p className="text-base font-semibold text-slate-50">{user?.nombre ?? '—'}</p>
          {user?.nombre_compania && (
            <p className="text-xs text-slate-400">{user.nombre_compania}</p>
          )}
        </div>

        {/* Edit form */}
        <div className="rounded-xl bg-slate-800 border border-slate-700 p-4 space-y-4">
          <h2 className="text-sm font-semibold text-slate-300">{t('profile.edit.title')}</h2>

          {loadingPersona ? (
            <div className="flex justify-center py-6 text-slate-400">
              <IcoSpinner />
            </div>
          ) : (
            <form noValidate onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">

              {/* Correo — read only */}
              <div>
                <p className="text-xs text-slate-400">{t('profile.edit.fields.correo')}</p>
                <div className={readOnlyCls}>{persona?.correo ?? '—'}</div>
              </div>

              {/* Nombre */}
              <div>
                <p className="text-xs text-slate-400">{t('profile.edit.fields.nombre')}</p>
                <input {...form.register('nombre')} className={fieldCls} />
                {form.formState.errors.nombre && (
                  <p className="mt-1 text-xs text-red-400">{form.formState.errors.nombre.message}</p>
                )}
              </div>

              {/* Cédula */}
              <div>
                <p className="text-xs text-slate-400">
                  {t('profile.edit.fields.ci')}
                  {ciIsSet && (
                    <span className="ml-1 text-slate-600">· {t('profile.edit.ciLocked')}</span>
                  )}
                </p>
                {ciIsSet ? (
                  <div className={readOnlyCls}>{persona!.ci}</div>
                ) : (
                  <input
                    {...form.register('ci')}
                    inputMode="numeric"
                    placeholder="—"
                    className={fieldCls}
                  />
                )}
              </div>

              {/* Teléfono */}
              <div>
                <p className="text-xs text-slate-400">{t('profile.edit.fields.telefono')}</p>
                <input {...form.register('telefono')} type="tel" className={fieldCls} />
              </div>

              {/* Sexo */}
              <div>
                <p className="text-xs text-slate-400 mb-1">{t('profile.edit.fields.sexo')}</p>
                <div className="flex gap-2">
                  {(['M', 'F'] as const).map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => form.setValue('sexo', s, { shouldDirty: true, shouldValidate: true })}
                      className={`flex-1 rounded-lg py-2.5 text-sm font-semibold border transition-all ${
                        currentSexo === s
                          ? 'bg-accent-600 border-accent-600 text-white'
                          : 'border-slate-600 text-slate-400 hover:border-slate-500'
                      }`}
                    >
                      {t(`profile.edit.sexo.${s}`)}
                    </button>
                  ))}
                </div>
              </div>

              {/* Fecha de nacimiento */}
              <div>
                <p className="text-xs text-slate-400">{t('profile.edit.fields.fechaNacimiento')}</p>
                <input
                  {...form.register('fecha_nacimiento')}
                  type="date"
                  className={`${fieldCls} [color-scheme:dark]`}
                />
              </div>

              <button
                type="submit"
                disabled={saving || !form.formState.isDirty}
                className="w-full rounded-xl bg-accent-600 py-3 text-sm font-semibold text-white transition hover:bg-accent-500 active:scale-[0.98] disabled:opacity-40"
              >
                {saving ? (
                  <span className="flex items-center justify-center gap-2">
                    <IcoSpinner /> {t('profile.edit.buttons.saving')}
                  </span>
                ) : (
                  t('profile.edit.buttons.save')
                )}
              </button>
            </form>
          )}
        </div>

        {/* WhatsApp opt-in */}
        <WhatsAppConsentBlock
          idPersona={user?.id_persona ?? null}
          consent={waConsent}
          saving={savingWa}
          onToggle={async (acepta) => {
            if (!user?.id_persona) return
            setSavingWa(true)
            try {
              const res = await authRepository.patchConsentimientoWaPersona(user.id_persona, acepta)
              setWaConsent(res)
              toast.success(t('profile.whatsapp.saveSuccess'))
            } catch {
              toast.error(t('profile.whatsapp.saveError'))
            } finally {
              setSavingWa(false)
            }
          }}
        />

        <ThemeSelector />

        <div className="rounded-xl bg-slate-800 border border-slate-700 p-4 space-y-3">
          <h2 className="text-sm font-semibold text-slate-300">{t('profile.language.title')}</h2>
          <LangToggle />
        </div>

        <button
          onClick={handleLogout}
          className="w-full rounded-lg border border-red-800 py-3 text-sm font-semibold text-red-400 transition hover:bg-red-950"
        >
          {t('profile.logout')}
        </button>

      </div>
    </div>
  )
}

// ── Sub-components ────────────────────────────────────────────────────────────

interface WhatsAppConsentBlockProps {
  idPersona: number | null
  consent: ConsentimientoWaPersonaResponse | null
  saving: boolean
  onToggle: (acepta: boolean) => Promise<void>
}

function WhatsAppConsentBlock({ consent, saving, onToggle }: WhatsAppConsentBlockProps) {
  const { t } = useTranslation()

  const checked = consent?.aceptaWhatsapp ?? false

  const fechaStr = consent?.fechaConsentimientoWa
    ? new Date(consent.fechaConsentimientoWa).toLocaleDateString('es', {
        day: '2-digit', month: 'short', year: 'numeric',
      })
    : null

  return (
    <div className="rounded-xl bg-slate-800 border border-slate-700 p-4 space-y-3">
      <h2 className="text-sm font-semibold text-slate-300">{t('profile.whatsapp.title')}</h2>

      <div className="flex items-center justify-between gap-4">
        <div className="space-y-0.5">
          <p className="text-sm text-slate-200">{t('profile.whatsapp.label')}</p>
          {fechaStr && (
            <p className="text-xs text-slate-500">
              {t('profile.whatsapp.fechaConsentimiento', { fecha: fechaStr })}
            </p>
          )}
        </div>

        {/* Custom toggle using accent-* classes */}
        <button
          type="button"
          role="switch"
          aria-checked={checked}
          disabled={saving}
          onClick={() => onToggle(!checked)}
          className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 focus:outline-none disabled:opacity-50 ${
            checked ? 'bg-accent-600' : 'bg-slate-600'
          }`}
        >
          <span
            className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
              checked ? 'translate-x-5' : 'translate-x-0'
            }`}
          />
        </button>
      </div>

      {saving && (
        <p className="text-xs text-slate-400 animate-pulse">{t('profile.whatsapp.saving')}</p>
      )}
    </div>
  )
}
