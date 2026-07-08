import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import {
  Loader2, Eye, EyeOff, Search, X,
  CheckCircle2, AlertCircle,
} from 'lucide-react'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader,
  DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Persona } from '@/infrastructure/http/auth/auth.dto'
import { createOperadorSchema, type CrearOperadorFormData } from '../schemas/operador.schema'
import { createPersonaSchema, type CrearPersonaFormData } from '../schemas/persona.schema'

interface Props {
  open: boolean
  onClose: () => void
  onCreado: () => void
}

const inputCls =
  'w-full rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition font-sans'

const inputStyle = {
  background: 'var(--input-bg)',
  border: '1px solid var(--input-border)',
  color: 'var(--input-text)',
} as const

function getFotoUrl(sexo: 'M' | 'F' | null): string | undefined {
  if (sexo === 'M') return import.meta.env.VITE_AVATAR_HOMBRE_URL || undefined
  if (sexo === 'F') return import.meta.env.VITE_AVATAR_MUJER_URL || undefined
  return undefined
}

function SexoSelector({
  value,
  onChange,
  required,
  error,
}: {
  value: 'M' | 'F' | null
  onChange: (s: 'M' | 'F') => void
  required?: boolean
  error?: boolean
}) {
  const { t } = useTranslation()
  return (
    <div className="space-y-1.5">
      <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
        {t('operators.sexoLabel')}
        {required && <span className="text-red-500 ml-1">*</span>}
      </label>
      <div className="flex gap-2">
        {(['M', 'F'] as const).map(s => (
          <button
            key={s}
            type="button"
            onClick={() => onChange(s)}
            className="flex-1 py-2 rounded-lg text-sm font-medium transition"
            style={
              value === s
                ? { background: '#f97316', color: '#fff', border: '1px solid #f97316' }
                : {
                    background: 'var(--input-bg)',
                    color: 'var(--page-text)',
                    border: `1px solid ${error ? '#ef4444' : 'var(--input-border)'}`,
                  }
            }
          >
            {s === 'M' ? t('operators.sexoHombre') : t('operators.sexoMujer')}
          </button>
        ))}
      </div>
      {error && (
        <p className="text-xs text-red-500">{t('operators.sexoRequired')}</p>
      )}
    </div>
  )
}

export function CrearOperadorModal({ open, onClose, onCreado }: Props) {
  const { t } = useTranslation()

  const [ciInput, setCiInput]                           = useState('')
  const [buscando, setBuscando]                         = useState(false)
  const [ciError, setCiError]                           = useState<string | null>(null)
  const [personaSeleccionada, setPersonaSeleccionada]   = useState<Persona | null>(null)
  const [personaNoExiste, setPersonaNoExiste]           = useState(false)
  const [showPassword, setShowPassword]                 = useState(false)
  const [submitting, setSubmitting]                     = useState(false)
  const [sexoElegido, setSexoElegido]                   = useState<'M' | 'F' | null>(null)
  const [sexoError, setSexoError]                       = useState(false)

  const operadorSchema = useMemo(() => createOperadorSchema(t).omit({ id_persona: true }), [t])
  const personaSchema  = useMemo(() => createPersonaSchema(t), [t])

  const operadorForm = useForm<Omit<CrearOperadorFormData, 'id_persona'>>({ resolver: zodResolver(operadorSchema) })
  const personaForm  = useForm<CrearPersonaFormData>({ resolver: zodResolver(personaSchema) })

  const ROLES_OPCIONES = [
    { value: 'super_admin', label: 'Super Admin', desc: t('operators.roleSuperAdminDesc') },
    { value: 'soporte',     label: 'Soporte',     desc: t('operators.roleSoporteDesc') },
    { value: 'viewer',      label: 'Viewer',      desc: t('operators.roleViewerDesc') },
  ]

  // ── Reset ───────────────────────────────────────────────────────────────────

  const resetAll = () => {
    setCiInput('')
    setCiError(null)
    setPersonaSeleccionada(null)
    setPersonaNoExiste(false)
    setShowPassword(false)
    setSexoElegido(null)
    setSexoError(false)
    operadorForm.reset()
    personaForm.reset()
  }

  const handleClose = () => { resetAll(); onClose() }

  // ── Search ──────────────────────────────────────────────────────────────────

  const buscarPersona = async () => {
    const ci = ciInput.trim()
    if (!ci) return
    setBuscando(true)
    setCiError(null)
    setPersonaSeleccionada(null)
    setPersonaNoExiste(false)
    setSexoElegido(null)
    operadorForm.reset()
    try {
      const persona = await authRepository.buscarPersonaPorCI(ci)
      setPersonaSeleccionada(persona)
      if (persona.sexo) setSexoElegido(persona.sexo)
      if (persona.correo) operadorForm.setValue('correo', persona.correo)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 404) {
        setPersonaNoExiste(true)
        personaForm.reset({ ci })
      } else {
        setCiError(t('operators.personaSearchError', 'Error al buscar persona.'))
      }
    } finally {
      setBuscando(false)
    }
  }

  const limpiarBusqueda = () => {
    resetAll()
  }

  // ── Derived ─────────────────────────────────────────────────────────────────

  const personaResuelta       = !!personaSeleccionada
  const personaReady          = personaResuelta || personaNoExiste
  const necesitaCrearPersona  = personaNoExiste && !personaSeleccionada
  const submitLabel           = necesitaCrearPersona
    ? t('operators.createWithPersona')
    : t('operators.createSubmit')

  const handleSexoChange = (s: 'M' | 'F') => {
    setSexoElegido(s)
    setSexoError(false)
  }

  // ── Submit ──────────────────────────────────────────────────────────────────

  const onSubmit = operadorForm.handleSubmit(
    async (data) => {
      if (necesitaCrearPersona) {
        const personaValid = await personaForm.trigger()
        if (!personaValid) return
        if (!sexoElegido) {
          setSexoError(true)
          return
        }
      }
      setSexoError(false)

      setSubmitting(true)
      try {
        let idPersona = personaSeleccionada?.id

        if (necesitaCrearPersona) {
          const pd = personaForm.getValues()
          const persona = await authRepository.crearPersona({
            ci:               pd.ci,
            nombre:           pd.nombre,
            telefono:         pd.telefono         || undefined,
            correo:           pd.correo           || undefined,
            fecha_nacimiento: pd.fecha_nacimiento || undefined,
            sexo:             sexoElegido!,
            foto_url:         getFotoUrl(sexoElegido),
          })
          idPersona = persona.id
          setPersonaSeleccionada(persona)
          if (persona.correo && !data.correo) {
            operadorForm.setValue('correo', persona.correo)
          }
        } else if (personaSeleccionada && sexoElegido) {
          await authRepository.actualizarPersona(personaSeleccionada.id, {
            sexo:     sexoElegido,
            foto_url: getFotoUrl(sexoElegido),
          })
        }

        if (!idPersona) {
          toast.error(t('operators.noPersonaError'))
          return
        }

        await authRepository.crearOperadorPlataforma({ ...data, id_persona: idPersona })
        toast.success(t('operators.createSuccess'))
        handleClose()
        onCreado()
      } catch (err) {
        if (isAxiosError(err)) {
          if (err.response?.status === 409) {
            operadorForm.setError('correo', { message: t('operators.createError409') })
          } else {
            toast.error(t('operators.createErrorConn'))
          }
        } else {
          toast.error(t('operators.unexpectedError'))
        }
      } finally {
        setSubmitting(false)
      }
    },
    () => {
      toast.error(t('operators.credentialsRequiredError'))
    },
  )

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="w-full max-w-lg md:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t('operators.createTitle')}</DialogTitle>
        </DialogHeader>

        <div className="grid grid-cols-1 md:grid-cols-2 md:gap-x-6 max-h-[70vh] overflow-y-auto md:overflow-visible md:max-h-none pr-1 md:pr-0">

          {/* ── Columna izquierda: Persona ── */}
          <div className="space-y-4 py-2">
            <p className="text-[11px] font-semibold uppercase tracking-widest" style={{ color: 'var(--page-muted)' }}>
              {t('operators.sectionPersona')}
            </p>

            {/* CI search / found card */}
            <div className="space-y-2">
              <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                {t('operators.ciLabel')}
              </label>

              {personaResuelta ? (
                <div
                  className="flex items-center justify-between rounded-lg px-3 py-2.5"
                  style={{ border: '1px solid #86efac', background: '#f0fdf4' }}
                >
                  <div className="flex items-center gap-2.5">
                    <CheckCircle2 size={16} className="text-green-600 shrink-0" />
                    <div>
                      <p className="text-sm font-semibold" style={{ color: '#166534' }}>
                        {personaSeleccionada.nombre}
                      </p>
                      <p className="text-xs" style={{ color: '#16a34a' }}>
                        CI: {personaSeleccionada.ci}
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={limpiarBusqueda}
                    className="text-green-600 hover:text-green-800 transition"
                    title={t('operators.changePersona')}
                  >
                    <X size={14} />
                  </button>
                </div>
              ) : (
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={ciInput}
                    onChange={e => {
                      if (personaNoExiste || ciError) limpiarBusqueda()
                      setCiInput(e.target.value)
                    }}
                    onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); buscarPersona() } }}
                    placeholder={t('operators.ciPlaceholder')}
                    className={inputCls}
                    style={inputStyle}
                    autoFocus
                  />
                  <button
                    type="button"
                    onClick={buscarPersona}
                    disabled={buscando || !ciInput.trim()}
                    className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-orange-500 hover:bg-orange-600 disabled:opacity-50 text-white text-sm transition shrink-0"
                  >
                    {buscando ? <Loader2 size={14} className="animate-spin" /> : <Search size={14} />}
                  </button>
                </div>
              )}

              {ciError && (
                <p className="flex items-center gap-1.5 text-xs text-red-500">
                  <AlertCircle size={13} /> {ciError}
                </p>
              )}
            </div>

            {/* Sexo selector para persona ya existente */}
            {personaResuelta && (
              <SexoSelector
                value={sexoElegido}
                onChange={handleSexoChange}
                error={sexoError}
              />
            )}

            {/* Inline persona form when not found */}
            {personaNoExiste && !personaResuelta && (
              <div className="space-y-3">
                <div
                  className="flex items-start gap-2 rounded-lg px-3 py-2.5"
                  style={{ background: '#fffbeb', border: '1px solid #fcd34d' }}
                >
                  <AlertCircle size={14} className="text-amber-500 mt-0.5 shrink-0" />
                  <p className="text-xs leading-relaxed" style={{ color: '#92400e' }}>
                    {t('operators.personaNotFoundPre')} <strong>{ciInput.trim()}</strong>. {t('operators.personaNotFoundPost')}
                  </p>
                </div>

                <div className="space-y-1.5">
                  <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                    {t('operators.nameLabel')} <span className="text-red-500">*</span>
                  </label>
                  <input
                    autoFocus
                    placeholder={t('operators.createNamePlaceholder')}
                    {...personaForm.register('nombre')}
                    className={inputCls}
                    style={inputStyle}
                  />
                  {personaForm.formState.errors.nombre && (
                    <p className="text-xs text-red-500">{personaForm.formState.errors.nombre.message}</p>
                  )}
                </div>

                <SexoSelector
                  value={sexoElegido}
                  onChange={handleSexoChange}
                  required
                  error={sexoError}
                />

                <div className="grid grid-cols-2 gap-2">
                  <div className="space-y-1.5">
                    <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                      {t('operators.createEmail')} {t('common.optional')}
                    </label>
                    <input
                      type="email"
                      placeholder="ana@ejemplo.com"
                      {...personaForm.register('correo')}
                      className={inputCls}
                      style={inputStyle}
                    />
                    {personaForm.formState.errors.correo && (
                      <p className="text-xs text-red-500">{personaForm.formState.errors.correo.message}</p>
                    )}
                  </div>

                  <div className="space-y-1.5">
                    <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                      {t('appAccounts.phoneLabel')} {t('common.optional')}
                    </label>
                    <input
                      type="tel"
                      placeholder="0991234567"
                      {...personaForm.register('telefono')}
                      className={inputCls}
                      style={inputStyle}
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                    {t('appAccounts.birthDateLabel')} {t('common.optional')}
                  </label>
                  <input
                    type="date"
                    {...personaForm.register('fecha_nacimiento')}
                    className={inputCls}
                    style={inputStyle}
                  />
                </div>
              </div>
            )}

            {/* Placeholder on desktop when waiting for search */}
            {!personaReady && (
              <p className="hidden md:block text-xs mt-2" style={{ color: 'var(--page-muted)' }}>
                {t('operators.searchHint')}
              </p>
            )}
          </div>

          {/* ── Columna derecha: Credenciales ── */}
          <div
            className={[
              'space-y-4 py-2',
              'md:border-l md:pl-6',
              personaReady ? 'block' : 'hidden md:block',
            ].join(' ')}
            style={{ borderColor: 'var(--page-border)' }}
          >
            <p className="text-[11px] font-semibold uppercase tracking-widest" style={{ color: 'var(--page-muted)' }}>
              {t('operators.sectionCredentials')}
            </p>

            {!personaReady && (
              <p className="text-xs py-2" style={{ color: 'var(--page-muted)' }}>
                {t('operators.credentialsHint')}
              </p>
            )}

            <form
              onSubmit={onSubmit}
              noValidate
              className={personaReady ? 'space-y-4' : 'space-y-4 opacity-40 pointer-events-none select-none'}
            >
              <div className="space-y-1.5">
                <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                  {t('operators.createEmail')}
                </label>
                <input
                  type="email"
                  placeholder={t('operators.createEmailPlaceholder')}
                  {...operadorForm.register('correo')}
                  className={inputCls}
                  style={inputStyle}
                />
                {operadorForm.formState.errors.correo && (
                  <p className="text-xs text-red-500">{operadorForm.formState.errors.correo.message}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                  {t('operators.createPassword')}
                </label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    placeholder="••••••••"
                    {...operadorForm.register('password')}
                    className={`${inputCls} pr-10`}
                    style={inputStyle}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(v => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 transition-colors"
                    style={{ color: 'var(--page-muted)' }}
                    tabIndex={-1}
                    aria-label={showPassword ? t('common.hidePassword') : t('common.showPassword')}
                  >
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
                {operadorForm.formState.errors.password && (
                  <p className="text-xs text-red-500">{operadorForm.formState.errors.password.message}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                  {t('operators.createRole')}
                </label>
                <div className="space-y-2">
                  {ROLES_OPCIONES.map(opcion => (
                    <label
                      key={opcion.value}
                      className="flex items-start gap-3 p-3 rounded-lg cursor-pointer transition-colors has-[:checked]:border-orange-400"
                      style={{ border: '1px solid var(--page-border)', background: 'var(--page-bg)' }}
                    >
                      <input
                        type="radio"
                        value={opcion.value}
                        {...operadorForm.register('rol')}
                        className="mt-0.5 accent-orange-500"
                      />
                      <div>
                        <p className="text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                          {opcion.label}
                        </p>
                        <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
                          {opcion.desc}
                        </p>
                      </div>
                    </label>
                  ))}
                </div>
                {operadorForm.formState.errors.rol && (
                  <p className="text-xs text-red-500">{operadorForm.formState.errors.rol.message}</p>
                )}
              </div>
            </form>
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={handleClose}
            disabled={submitting}
            style={{ fontSize: '0.75rem' }}
          >
            {t('confirmDialog.cancel')}
          </Button>

          <Button
            type="button"
            onClick={onSubmit}
            disabled={submitting || !personaReady}
            className="bg-orange-500 hover:bg-orange-600 text-white"
            style={{ fontSize: '0.75rem' }}
          >
            {submitting && <Loader2 size={14} className="mr-2 animate-spin" />}
            {submitting ? t('operators.createSubmitting') : submitLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}