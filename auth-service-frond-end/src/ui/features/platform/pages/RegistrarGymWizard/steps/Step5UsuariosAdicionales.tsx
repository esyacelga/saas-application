import { useState } from 'react'
import {
  Loader2, Search, CheckCircle2, UserPlus, Eye, EyeOff,
  RefreshCcw, Users, Plus, Trash2, ChevronDown, ChevronUp,
} from 'lucide-react'
import { isAxiosError } from 'axios'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Persona } from '@/infrastructure/http/auth/auth.dto'
import type { PersonaAdminData } from './Step4UsuarioPrincipal'

export type UsuarioAdicionalResuelto = {
  persona: PersonaAdminData
  password: string
}

type Fase = 'buscar' | 'encontrado' | 'nuevo'

interface CardState {
  fase: Fase
  buscarInput: string
  buscando: boolean
  errorBusqueda: string
  personaEncontrada: Persona | null
  nuevoCi: string
  nuevoNombre: string
  nuevoCorreo: string
  nuevoTelefono: string
  password: string
  confirmarPassword: string
  showPass: boolean
  showConfirm: boolean
  expanded: boolean
}

function emptyCard(): CardState {
  return {
    fase: 'buscar',
    buscarInput: '',
    buscando: false,
    errorBusqueda: '',
    personaEncontrada: null,
    nuevoCi: '',
    nuevoNombre: '',
    nuevoCorreo: '',
    nuevoTelefono: '',
    password: '',
    confirmarPassword: '',
    showPass: false,
    showConfirm: false,
    expanded: true,
  }
}

function resolverCard(card: CardState): UsuarioAdicionalResuelto | null {
  let persona: PersonaAdminData | null = null

  if (card.fase === 'encontrado' && card.personaEncontrada) {
    persona = {
      id_persona: card.personaEncontrada.id,
      ci:         card.personaEncontrada.ci,
      nombre:     card.personaEncontrada.nombre,
      correo:     card.personaEncontrada.correo ?? '',
      telefono:   card.personaEncontrada.telefono ?? undefined,
    }
  } else if (
    card.fase === 'nuevo' &&
    card.nuevoCi.trim() && card.nuevoNombre.trim() && card.nuevoCorreo.trim()
  ) {
    persona = {
      ci:       card.nuevoCi.trim(),
      nombre:   card.nuevoNombre.trim(),
      correo:   card.nuevoCorreo.trim(),
      telefono: card.nuevoTelefono.trim() || undefined,
    }
  }

  if (!persona) return null
  if (card.password.length < 8 || card.password !== card.confirmarPassword) return null

  return { persona, password: card.password }
}

// ─── PasswordInput ────────────────────────────────────────────────────────────

function PasswordInput({
  value, onChange, show, onToggle, placeholder,
}: {
  value: string
  onChange: (v: string) => void
  show: boolean
  onToggle: () => void
  placeholder?: string
}) {
  return (
    <div className="relative">
      <input
        type={show ? 'text' : 'password'}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full px-3 py-2 pr-9 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-orange-500"
        style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
      />
      <button
        type="button"
        onClick={onToggle}
        className="absolute right-2.5 top-1/2 -translate-y-1/2 opacity-50 hover:opacity-100 transition-opacity"
        style={{ color: 'var(--page-text)' }}
      >
        {show ? <EyeOff size={14} /> : <Eye size={14} />}
      </button>
    </div>
  )
}

// ─── UsuarioCard ──────────────────────────────────────────────────────────────

function UsuarioCard({
  index, card, onChange, onRemove,
}: {
  index: number
  card: CardState
  onChange: (patch: Partial<CardState>) => void
  onRemove: () => void
}) {
  const update = (patch: Partial<CardState>) => onChange(patch)

  const buscar = async () => {
    const query = card.buscarInput.trim()
    if (!query) {
      update({ errorBusqueda: 'Ingresa una cédula / CI' })
      return
    }
    update({ errorBusqueda: '', buscando: true, personaEncontrada: null })
    try {
      const persona = await authRepository.buscarPersonaPorCI(query)
      update({
        buscando: false,
        fase: 'encontrado',
        personaEncontrada: persona,
      })
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 404) {
        update({
          buscando: false,
          fase: 'nuevo',
          nuevoCi:     query,
          nuevoNombre: '',
          nuevoCorreo: '',
          nuevoTelefono: '',
        })
      } else {
        update({ buscando: false, errorBusqueda: 'Error de conexión. Intenta de nuevo.' })
      }
    }
  }

  const resetBusqueda = () =>
    update({ fase: 'buscar', buscarInput: '', personaEncontrada: null, nuevoCi: '', nuevoNombre: '', nuevoCorreo: '', nuevoTelefono: '', password: '', confirmarPassword: '' })

  const resuelto = resolverCard(card) !== null
  const nombreDisplay =
    card.fase === 'encontrado' ? card.personaEncontrada?.nombre :
    card.fase === 'nuevo'      ? card.nuevoNombre || undefined :
    undefined

  return (
    <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--page-border)' }}>

      {/* Header del card */}
      <div
        className="w-full flex items-center justify-between px-4 py-3"
        style={{ background: 'var(--page-surface)' }}
      >
        <button
          type="button"
          className="flex items-center gap-2 flex-1 text-left"
          onClick={() => update({ expanded: !card.expanded })}
        >
          <div
            className="w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0"
            style={{ background: resuelto ? '#16a34a' : 'var(--color-warning, #f97316)', color: '#fff' }}
          >
            {resuelto ? <CheckCircle2 size={13} /> : index + 2}
          </div>
          <span className="text-sm font-medium" style={{ color: 'var(--page-text)' }}>
            {nombreDisplay ?? `Usuario adicional ${index + 1}`}
          </span>
        </button>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onRemove}
            className="p-1 rounded hover:bg-red-50 transition-colors"
            title="Eliminar"
          >
            <Trash2 size={13} style={{ color: '#ef4444' }} />
          </button>
          <button type="button" onClick={() => update({ expanded: !card.expanded })}>
            {card.expanded
              ? <ChevronUp size={14} style={{ color: 'var(--page-muted)' }} />
              : <ChevronDown size={14} style={{ color: 'var(--page-muted)' }} />}
          </button>
        </div>
      </div>

      {card.expanded && (
        <div className="px-4 pb-4 pt-3 space-y-4" style={{ background: 'var(--page-bg)' }}>

          {/* ── Fase: BUSCAR ── */}
          {card.fase === 'buscar' && (
            <div className="space-y-2">
              <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                Cédula / CI <span style={{ color: '#ef4444' }}>*</span>
              </Label>
              <div className="flex gap-2">
                <Input
                  value={card.buscarInput}
                  onChange={e => update({ buscarInput: e.target.value, errorBusqueda: '' })}
                  onKeyDown={e => e.key === 'Enter' && buscar()}
                  placeholder="Ej: 1234567890"
                  disabled={card.buscando}
                  className="flex-1"
                  style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
                />
                <button
                  type="button"
                  onClick={buscar}
                  disabled={card.buscando || !card.buscarInput.trim()}
                  className="flex items-center gap-1.5 px-3 py-2 rounded-md text-sm font-medium transition-opacity disabled:opacity-50"
                  style={{ background: 'var(--color-warning, #f97316)', color: '#fff' }}
                >
                  {card.buscando ? <Loader2 size={14} className="animate-spin" /> : <Search size={14} />}
                  Buscar
                </button>
              </div>
              {card.errorBusqueda && (
                <p className="text-xs" style={{ color: '#ef4444' }}>{card.errorBusqueda}</p>
              )}
            </div>
          )}

          {/* ── Fase: ENCONTRADO ── */}
          {card.fase === 'encontrado' && card.personaEncontrada && (
            <div className="rounded-lg p-3 space-y-2"
              style={{ background: 'rgba(34,197,94,0.06)', border: '1px solid rgba(34,197,94,0.35)' }}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <CheckCircle2 size={14} style={{ color: '#16a34a' }} />
                  <span className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>Persona encontrada</span>
                </div>
                <button type="button" onClick={resetBusqueda}
                  className="flex items-center gap-1 text-xs hover:opacity-70 transition-opacity"
                  style={{ color: 'var(--page-muted)' }}>
                  <RefreshCcw size={11} /> Cambiar
                </button>
              </div>
              <div className="grid grid-cols-2 gap-x-4 gap-y-1">
                {[
                  { label: 'Nombre',   value: card.personaEncontrada.nombre },
                  { label: 'CI',       value: card.personaEncontrada.ci },
                  { label: 'Correo',   value: card.personaEncontrada.correo   ?? '—' },
                  { label: 'Teléfono', value: card.personaEncontrada.telefono ?? '—' },
                ].map(({ label, value }) => (
                  <div key={label}>
                    <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{label}</p>
                    <p className="text-xs font-medium mt-0.5 truncate" style={{ color: 'var(--page-text)' }}>{value}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Fase: NUEVO ── */}
          {card.fase === 'nuevo' && (
            <div className="rounded-lg p-3 space-y-3"
              style={{ background: 'rgba(245,158,11,0.06)', border: '1px solid rgba(245,158,11,0.35)' }}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <UserPlus size={14} style={{ color: '#d97706' }} />
                  <span className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>No encontrado — ingresa los datos</span>
                </div>
                <button type="button" onClick={resetBusqueda}
                  className="flex items-center gap-1 text-xs hover:opacity-70 transition-opacity"
                  style={{ color: 'var(--page-muted)' }}>
                  <RefreshCcw size={11} /> Nueva búsqueda
                </button>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                    CI <span style={{ color: '#ef4444' }}>*</span>
                  </Label>
                  <Input
                    value={card.nuevoCi}
                    onChange={e => update({ nuevoCi: e.target.value })}
                    readOnly
                    className="mt-1 opacity-60 cursor-not-allowed"
                    style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
                  />
                </div>
                <div>
                  <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                    Nombre completo <span style={{ color: '#ef4444' }}>*</span>
                  </Label>
                  <Input
                    value={card.nuevoNombre}
                    onChange={e => update({ nuevoNombre: e.target.value })}
                    placeholder="Nombre completo"
                    className="mt-1"
                    style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
                  />
                </div>
                <div>
                  <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                    Correo <span style={{ color: '#ef4444' }}>*</span>
                  </Label>
                  <Input
                    type="email"
                    value={card.nuevoCorreo}
                    onChange={e => update({ nuevoCorreo: e.target.value })}
                    placeholder="correo@gym.com"
                    className="mt-1"
                    style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
                  />
                </div>
                <div>
                  <Label className="text-xs font-medium" style={{ color: 'var(--page-muted)' }}>Teléfono</Label>
                  <Input
                    value={card.nuevoTelefono}
                    onChange={e => update({ nuevoTelefono: e.target.value })}
                    placeholder="0991234567"
                    className="mt-1"
                    style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
                  />
                </div>
              </div>
            </div>
          )}

          {/* ── Contraseña (visible cuando persona resuelta) ── */}
          {(card.fase === 'encontrado' || card.fase === 'nuevo') && (
            <div className="space-y-3 pt-1" style={{ borderTop: '1px solid var(--page-border)' }}>
              <p className="text-xs font-semibold pt-2" style={{ color: 'var(--page-muted)' }}>CREDENCIALES</p>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                    Contraseña <span style={{ color: '#ef4444' }}>*</span>
                  </Label>
                  <div className="mt-1">
                    <PasswordInput
                      value={card.password}
                      onChange={v => update({ password: v })}
                      show={card.showPass}
                      onToggle={() => update({ showPass: !card.showPass })}
                      placeholder="Mín. 8 caracteres"
                    />
                  </div>
                  {card.password.length > 0 && card.password.length < 8 && (
                    <p className="text-xs mt-1" style={{ color: '#ef4444' }}>Mínimo 8 caracteres</p>
                  )}
                </div>
                <div>
                  <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                    Confirmar <span style={{ color: '#ef4444' }}>*</span>
                  </Label>
                  <div className="mt-1">
                    <PasswordInput
                      value={card.confirmarPassword}
                      onChange={v => update({ confirmarPassword: v })}
                      show={card.showConfirm}
                      onToggle={() => update({ showConfirm: !card.showConfirm })}
                      placeholder="Repite la clave"
                    />
                  </div>
                  {card.confirmarPassword.length > 0 && card.password !== card.confirmarPassword && (
                    <p className="text-xs mt-1" style={{ color: '#ef4444' }}>Las contraseñas no coinciden</p>
                  )}
                </div>
              </div>
            </div>
          )}

        </div>
      )}
    </div>
  )
}

// ─── Step5UsuariosAdicionales ─────────────────────────────────────────────────

interface Props {
  onUsersChange: (users: (UsuarioAdicionalResuelto | null)[]) => void
  errorCards?: boolean
}

export function Step5UsuariosAdicionales({ onUsersChange, errorCards }: Props) {
  const [cards, setCards] = useState<CardState[]>([])

  const updateCards = (next: CardState[]) => {
    setCards(next)
    onUsersChange(next.map(resolverCard))
  }

  const patchCard = (index: number, patch: Partial<CardState>) => {
    const next = cards.map((c, i) => i === index ? { ...c, ...patch } : c)
    updateCards(next)
  }

  const addCard = () => {
    if (cards.length >= 5) return
    updateCards([...cards, emptyCard()])
  }

  const removeCard = (index: number) => {
    updateCards(cards.filter((_, i) => i !== index))
  }

  return (
    <div className="space-y-5">

      {/* Header */}
      <div className="flex items-center gap-3 pb-2" style={{ borderBottom: '1px solid var(--page-border)' }}>
        <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
          style={{ background: 'var(--color-warning-subtle, #fff7ed)', color: 'var(--color-warning, #f97316)' }}>
          <Users size={18} />
        </div>
        <div>
          <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Usuarios adicionales</p>
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
            Opcional — agrega empleados, instructores u otros staff del gimnasio.
          </p>
        </div>
      </div>

      {/* Cards */}
      {cards.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-8 rounded-xl gap-3"
          style={{ border: '2px dashed var(--page-border)', background: 'var(--page-surface)' }}>
          <Users size={28} style={{ color: 'var(--page-muted)' }} />
          <div className="text-center">
            <p className="text-sm font-medium" style={{ color: 'var(--page-text)' }}>Sin usuarios adicionales</p>
            <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>
              Puedes agregarlos ahora o hacerlo más adelante desde el panel del gimnasio.
            </p>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          {cards.map((card, index) => (
            <UsuarioCard
              key={index}
              index={index}
              card={card}
              onChange={patch => patchCard(index, patch)}
              onRemove={() => removeCard(index)}
            />
          ))}
        </div>
      )}

      {errorCards && cards.some(c => resolverCard(c) === null) && (
        <p className="text-xs" style={{ color: '#ef4444' }}>
          Completa los datos y contraseña de todos los usuarios antes de continuar.
        </p>
      )}

      <Button
        type="button"
        variant="outline"
        size="sm"
        className="w-full gap-2"
        onClick={addCard}
        disabled={cards.length >= 5}
        style={{ border: '1px dashed var(--page-border)', color: 'var(--page-text)' }}
      >
        <Plus size={14} />
        Agregar usuario {cards.length > 0 ? 'adicional' : ''}
      </Button>

      {cards.length >= 5 && (
        <p className="text-xs text-center" style={{ color: 'var(--page-muted)' }}>
          Máximo 5 usuarios adicionales en el wizard. Agrega más desde el panel del gimnasio.
        </p>
      )}

    </div>
  )
}
