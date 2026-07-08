import { useState } from 'react'
import { Loader2, Search, CheckCircle2, UserPlus, Eye, EyeOff, RefreshCcw, ShieldCheck, UserCircle2 } from 'lucide-react'
import { isAxiosError } from 'axios'
import type { UseFormReturn } from 'react-hook-form'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PasswordStrength } from '@/ui/features/auth/components/PasswordStrength'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Persona } from '@/infrastructure/http/auth/auth.dto'
import type { WizardStep4Form } from '../../../schemas/registrar-gym-wizard.schema'

type BuscarPor = 'ci' | 'correo'
type Fase = 'buscar' | 'encontrado' | 'nuevo'

const AVATAR_HOMBRE = import.meta.env.VITE_AVATAR_HOMBRE_URL as string | undefined
const AVATAR_MUJER  = import.meta.env.VITE_AVATAR_MUJER_URL  as string | undefined

export type PersonaAdminData = {
  id_persona?: number
  ci: string
  nombre: string
  correo: string
  telefono?: string
  sexo?: 'M' | 'F'
  foto_url?: string
}

interface Props {
  form: UseFormReturn<WizardStep4Form>
  onPersonaResolved: (data: PersonaAdminData | null) => void
  errorNoPersona: boolean
}

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null
  return <p className="text-xs mt-1" style={{ color: '#ef4444' }}>{msg}</p>
}

function PasswordInput(props: React.ComponentProps<'input'>) {
  const [show, setShow] = useState(false)
  return (
    <div className="relative">
      <input
        {...props}
        type={show ? 'text' : 'password'}
        className="w-full px-3 py-2 pr-9 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-orange-500"
        style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
      />
      <button
        type="button"
        onClick={() => setShow(s => !s)}
        className="absolute right-2.5 top-1/2 -translate-y-1/2 opacity-50 hover:opacity-100 transition-opacity"
        style={{ color: 'var(--page-text)' }}
      >
        {show ? <EyeOff size={14} /> : <Eye size={14} />}
      </button>
    </div>
  )
}

export function Step4UsuarioPrincipal({ form, onPersonaResolved, errorNoPersona }: Props) {
  const { register, watch, formState: { errors } } = form
  const password = watch('usuarioPrincipal.password') ?? ''

  const [fase, setFase] = useState<Fase>('buscar')
  const [buscarPor, setBuscarPor] = useState<BuscarPor>('ci')
  const [buscarInput, setBuscarInput] = useState('')
  const [buscando, setBuscando] = useState(false)
  const [errorBusqueda, setErrorBusqueda] = useState('')
  const [personaEncontrada, setPersonaEncontrada] = useState<Persona | null>(null)

  // campos para persona nueva
  const [nuevoCi, setNuevoCi] = useState('')
  const [nuevoNombre, setNuevoNombre] = useState('')
  const [nuevoCorreo, setNuevoCorreo] = useState('')
  const [nuevoTelefono, setNuevoTelefono] = useState('')
  const [nuevoSexo, setNuevoSexo] = useState<'M' | 'F' | null>(null)

  const buscar = async () => {
    const query = buscarInput.trim()
    if (!query) {
      setErrorBusqueda(buscarPor === 'ci' ? 'Ingresa una cédula o CI' : 'Ingresa un correo electrónico')
      return
    }
    setErrorBusqueda('')
    setBuscando(true)
    setPersonaEncontrada(null)
    onPersonaResolved(null)
    try {
      const persona = buscarPor === 'ci'
        ? await authRepository.buscarPersonaPorCI(query)
        : await authRepository.buscarPersonaPorCorreo(query)
      setPersonaEncontrada(persona)
      setFase('encontrado')
      onPersonaResolved({
        id_persona: persona.id,
        ci: persona.ci,
        nombre: persona.nombre,
        correo: persona.correo ?? '',
        telefono: persona.telefono ?? undefined,
      })
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 404) {
        setFase('nuevo')
        setNuevoCi(buscarPor === 'ci' ? query : '')
        setNuevoNombre('')
        setNuevoCorreo(buscarPor === 'correo' ? query : '')
        setNuevoTelefono('')
        onPersonaResolved(null)
      } else {
        setErrorBusqueda('Error de conexión. Intenta de nuevo.')
      }
    } finally {
      setBuscando(false)
    }
  }

  const resetBusqueda = () => {
    setFase('buscar')
    setBuscarInput('')
    setPersonaEncontrada(null)
    setNuevoCi('')
    setNuevoNombre('')
    setNuevoCorreo('')
    setNuevoTelefono('')
    onPersonaResolved(null)
  }

  const handleNuevoChange = (
    field: 'ci' | 'nombre' | 'correo' | 'telefono',
    value: string,
  ) => {
    const next = {
      ci:       field === 'ci'       ? value : nuevoCi,
      nombre:   field === 'nombre'   ? value : nuevoNombre,
      correo:   field === 'correo'   ? value : nuevoCorreo,
      telefono: field === 'telefono' ? value : nuevoTelefono,
    }
    if (field === 'ci')       setNuevoCi(value)
    if (field === 'nombre')   setNuevoNombre(value)
    if (field === 'correo')   setNuevoCorreo(value)
    if (field === 'telefono') setNuevoTelefono(value)

    if (next.ci.trim() && next.nombre.trim() && next.correo.trim()) {
      const avatarUrl = nuevoSexo === 'M' ? AVATAR_HOMBRE : nuevoSexo === 'F' ? AVATAR_MUJER : undefined
      onPersonaResolved({
        ci: next.ci.trim(),
        nombre: next.nombre.trim(),
        correo: next.correo.trim(),
        telefono: next.telefono.trim() || undefined,
        sexo: nuevoSexo ?? undefined,
        foto_url: avatarUrl,
      })
    } else {
      onPersonaResolved(null)
    }
  }

  const handleSexoChange = (sexo: 'M' | 'F') => {
    const next = nuevoSexo === sexo ? null : sexo
    setNuevoSexo(next)
    if (nuevoCi.trim() && nuevoNombre.trim() && nuevoCorreo.trim()) {
      const avatarUrl = next === 'M' ? AVATAR_HOMBRE : next === 'F' ? AVATAR_MUJER : undefined
      onPersonaResolved({
        ci: nuevoCi.trim(),
        nombre: nuevoNombre.trim(),
        correo: nuevoCorreo.trim(),
        telefono: nuevoTelefono.trim() || undefined,
        sexo: next ?? undefined,
        foto_url: avatarUrl,
      })
    }
  }

  const personaResuelta =
    fase === 'encontrado' ||
    (fase === 'nuevo' && !!nuevoCi.trim() && !!nuevoNombre.trim() && !!nuevoCorreo.trim())

  const switchBuscarPor = (modo: BuscarPor) => {
    if (modo === buscarPor) return
    setBuscarPor(modo)
    setBuscarInput('')
    setErrorBusqueda('')
  }

  return (
    <div className="space-y-5">

      {/* Header */}
      <div className="flex items-center gap-3 pb-2" style={{ borderBottom: '1px solid var(--page-border)' }}>
        <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
          style={{ background: 'var(--color-warning-subtle, #fff7ed)', color: 'var(--color-warning, #f97316)' }}>
          <ShieldCheck size={18} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Usuario administrador</p>
            <span className="text-xs font-semibold px-1.5 py-0.5 rounded"
              style={{ background: 'var(--color-warning-subtle, #fff7ed)', color: 'var(--color-warning, #f97316)', border: '1px solid var(--color-warning, #f97316)' }}>
              SUPER_ADMIN
            </span>
          </div>
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
            Acceso completo al panel. Será el dueño o administrador principal del gimnasio.
          </p>
        </div>
      </div>

      {/* ── Fase: BUSCAR ── */}
      {fase === 'buscar' && (
        <div className="space-y-3">
          {/* Toggle CI / Correo */}
          <div className="flex rounded-lg overflow-hidden border" style={{ borderColor: 'var(--page-border)' }}>
            {(['ci', 'correo'] as BuscarPor[]).map(modo => (
              <button
                key={modo}
                type="button"
                onClick={() => switchBuscarPor(modo)}
                className="flex-1 py-1.5 text-xs font-medium transition-colors"
                style={{
                  background: buscarPor === modo ? 'var(--color-warning, #f97316)' : 'var(--input-bg)',
                  color: buscarPor === modo ? '#fff' : 'var(--page-muted)',
                }}
              >
                {modo === 'ci' ? 'Buscar por CI' : 'Buscar por correo'}
              </button>
            ))}
          </div>

          <div>
            <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
              {buscarPor === 'ci' ? 'Cédula / CI' : 'Correo electrónico'}{' '}
              <span style={{ color: '#ef4444' }}>*</span>
            </Label>
            <div className="flex gap-2 mt-1">
              <Input
                value={buscarInput}
                onChange={e => { setBuscarInput(e.target.value); setErrorBusqueda('') }}
                onKeyDown={e => e.key === 'Enter' && buscar()}
                placeholder={buscarPor === 'ci' ? 'Ej: 1234567890' : 'admin@migym.com'}
                type={buscarPor === 'correo' ? 'email' : 'text'}
                disabled={buscando}
                className="flex-1"
                style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
              />
              <button
                type="button"
                onClick={buscar}
                disabled={buscando || !buscarInput.trim()}
                className="flex items-center gap-1.5 px-3 py-2 rounded-md text-sm font-medium transition-opacity disabled:opacity-50"
                style={{ background: 'var(--color-warning, #f97316)', color: '#fff' }}
              >
                {buscando ? <Loader2 size={15} className="animate-spin" /> : <Search size={15} />}
                Buscar
              </button>
            </div>
          </div>

          {errorBusqueda && (
            <p className="text-xs" style={{ color: '#ef4444' }}>{errorBusqueda}</p>
          )}
          {errorNoPersona && !errorBusqueda && (
            <p className="text-xs" style={{ color: '#ef4444' }}>
              Busca y selecciona un administrador antes de continuar.
            </p>
          )}
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
            Si ya existe en el sistema, sus datos se cargarán automáticamente.
          </p>
        </div>
      )}

      {/* ── Fase: ENCONTRADO ── */}
      {fase === 'encontrado' && personaEncontrada && (
        <div className="rounded-lg p-4 space-y-3"
          style={{ background: 'rgba(34,197,94,0.06)', border: '1px solid rgba(34,197,94,0.35)' }}>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <CheckCircle2 size={16} style={{ color: '#16a34a' }} />
              <span className="text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                Persona encontrada
              </span>
            </div>
            <button type="button" onClick={resetBusqueda}
              className="flex items-center gap-1 text-xs hover:opacity-70 transition-opacity"
              style={{ color: 'var(--page-muted)' }}>
              <RefreshCcw size={12} /> Cambiar
            </button>
          </div>
          <div className="grid grid-cols-2 gap-x-4 gap-y-2">
            {[
              { label: 'Nombre',   value: personaEncontrada.nombre },
              { label: 'CI',       value: personaEncontrada.ci },
              { label: 'Correo',   value: personaEncontrada.correo   ?? '—' },
              { label: 'Teléfono', value: personaEncontrada.telefono ?? '—' },
            ].map(({ label, value }) => (
              <div key={label}>
                <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{label}</p>
                <p className="text-xs font-medium mt-0.5" style={{ color: 'var(--page-text)' }}>{value}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Fase: NUEVO ── */}
      {fase === 'nuevo' && (
        <div className="rounded-lg p-4 space-y-4"
          style={{ background: 'rgba(245,158,11,0.06)', border: '1px solid rgba(245,158,11,0.35)' }}>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <UserPlus size={16} style={{ color: '#d97706' }} />
              <span className="text-sm font-medium" style={{ color: 'var(--page-text)' }}>
                No encontrado — ingresa los datos
              </span>
            </div>
            <button type="button" onClick={resetBusqueda}
              className="flex items-center gap-1 text-xs hover:opacity-70 transition-opacity"
              style={{ color: 'var(--page-muted)' }}>
              <RefreshCcw size={12} /> Nueva búsqueda
            </button>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                CI <span style={{ color: '#ef4444' }}>*</span>
              </Label>
              <Input
                value={nuevoCi}
                onChange={e => handleNuevoChange('ci', e.target.value)}
                placeholder="Ej: 1234567890"
                readOnly={buscarPor === 'ci'}
                className={`mt-1 ${buscarPor === 'ci' ? 'opacity-60 cursor-not-allowed' : ''}`}
                style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
              />
            </div>
            <div>
              <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                Nombre completo <span style={{ color: '#ef4444' }}>*</span>
              </Label>
              <Input
                value={nuevoNombre}
                onChange={e => handleNuevoChange('nombre', e.target.value)}
                placeholder="Ej: Carlos Mendoza"
                className="mt-1"
                style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
              />
            </div>
            <div>
              <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                Correo electrónico <span style={{ color: '#ef4444' }}>*</span>
              </Label>
              <Input
                type="email"
                value={nuevoCorreo}
                onChange={e => handleNuevoChange('correo', e.target.value)}
                placeholder="admin@migym.com"
                readOnly={buscarPor === 'correo'}
                className={`mt-1 ${buscarPor === 'correo' ? 'opacity-60 cursor-not-allowed' : ''}`}
                style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
              />
            </div>
            <div>
              <Label className="text-xs font-medium" style={{ color: 'var(--page-muted)' }}>
                Teléfono <span style={{ color: 'var(--page-muted)' }}>(opcional)</span>
              </Label>
              <Input
                value={nuevoTelefono}
                onChange={e => handleNuevoChange('telefono', e.target.value)}
                placeholder="0991234567"
                className="mt-1"
                style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
              />
            </div>
          </div>

          {/* Sexo + avatar */}
          <div>
            <Label className="text-xs font-medium" style={{ color: 'var(--page-muted)' }}>
              SEXO <span style={{ color: 'var(--page-muted)', fontWeight: 400 }}>(opcional)</span>
            </Label>
            <div className="flex items-center gap-3 mt-2">
              <div className="w-10 h-10 rounded-full overflow-hidden flex items-center justify-center flex-shrink-0"
                style={{ background: 'var(--page-bg)', border: '1px solid var(--page-border)' }}>
                {nuevoSexo && (nuevoSexo === 'M' ? AVATAR_HOMBRE : AVATAR_MUJER)
                  ? <img src={nuevoSexo === 'M' ? AVATAR_HOMBRE : AVATAR_MUJER} alt="avatar" className="w-full h-full object-cover" />
                  : <UserCircle2 size={22} style={{ color: 'var(--page-muted)' }} />
                }
              </div>
              <div className="flex gap-2">
                {(['M', 'F'] as const).map(s => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => handleSexoChange(s)}
                    className="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
                    style={{
                      background: nuevoSexo === s ? 'var(--color-warning, #f97316)' : 'var(--input-bg)',
                      color: nuevoSexo === s ? '#fff' : 'var(--page-muted)',
                      border: nuevoSexo === s ? '1.5px solid var(--color-warning, #f97316)' : '1.5px solid var(--input-border)',
                    }}
                  >
                    {s === 'M' ? 'Hombre' : 'Mujer'}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Contraseñas (solo cuando persona resuelta) ── */}
      {personaResuelta && (
        <div className="pt-1" style={{ borderTop: '1px solid var(--page-border)' }}>
          <p className="text-xs font-semibold pt-3 pb-3" style={{ color: 'var(--page-muted)' }}>
            CREDENCIALES DE ACCESO
          </p>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                Contraseña <span style={{ color: '#ef4444' }}>*</span>
              </Label>
              <div className="mt-1.5">
                <PasswordInput {...register('usuarioPrincipal.password')} placeholder="Mínimo 8 caracteres" />
              </div>
              <PasswordStrength password={password} />
              <FieldError msg={errors.usuarioPrincipal?.password?.message} />
            </div>
            <div>
              <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                Confirmar contraseña <span style={{ color: '#ef4444' }}>*</span>
              </Label>
              <div className="mt-1.5">
                <PasswordInput {...register('usuarioPrincipal.confirmarPassword')} placeholder="Repite la contraseña" />
              </div>
              <FieldError msg={errors.usuarioPrincipal?.confirmarPassword?.message} />
            </div>
          </div>
        </div>
      )}

    </div>
  )
}
