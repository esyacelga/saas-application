import { useState, useEffect } from 'react'
import { Eye, EyeOff, UserCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { UsuarioStaff } from '@/infrastructure/http/auth/auth.dto'

const AVATAR_HOMBRE = import.meta.env.VITE_AVATAR_HOMBRE_URL as string | undefined
const AVATAR_MUJER  = import.meta.env.VITE_AVATAR_MUJER_URL  as string | undefined

interface Props {
  open: boolean
  idCompania: number
  usuario: UsuarioStaff | null
  onClose: () => void
  onUpdated: () => void
}

type Sexo = 'M' | 'F' | null

export function EditarUsuarioModal({ open, idCompania, usuario, onClose, onUpdated }: Props) {
  const [sexo, setSexo] = useState<Sexo>(null)
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open || !usuario) return
    if (usuario.foto_url === AVATAR_HOMBRE) setSexo('M')
    else if (usuario.foto_url === AVATAR_MUJER) setSexo('F')
    else setSexo(null)
  }, [open, usuario?.id])

  const avatarPreview = sexo === 'M' ? AVATAR_HOMBRE : sexo === 'F' ? AVATAR_MUJER : usuario?.foto_url ?? null

  const handleClose = () => {
    setSexo(null)
    setPassword('')
    setShowPwd(false)
    onClose()
  }

  const handleSave = async () => {
    if (!usuario) return
    if (!sexo && !password.trim()) {
      toast.error('No hay cambios para guardar')
      return
    }
    if (password && password.length < 6) {
      toast.error('La contraseña debe tener al menos 6 caracteres')
      return
    }

    setSaving(true)
    try {
      const promises: Promise<unknown>[] = []

      if (sexo) {
        const avatarUrl = sexo === 'M' ? AVATAR_HOMBRE : AVATAR_MUJER
        promises.push(
          authRepository.actualizarPersona(usuario.id_persona, {
            sexo,
            ...(avatarUrl ? { foto_url: avatarUrl } : {}),
          })
        )
      }

      if (password.trim()) {
        promises.push(
          authRepository.resetStaffPassword(idCompania, usuario.id, password)
        )
      }

      await Promise.all(promises)
      toast.success('Usuario actualizado correctamente')
      onUpdated()
      handleClose()
    } catch {
      toast.error('Error al actualizar el usuario')
    } finally {
      setSaving(false)
    }
  }

  if (!usuario) return null

  return (
    <Dialog open={open} onOpenChange={open => { if (!open) handleClose() }}>
      <DialogContent
        className="p-0 overflow-hidden"
        style={{
          maxWidth: '420px',
          background: 'var(--page-bg)',
          border: '1px solid var(--page-border)',
        }}
      >
        <DialogHeader className="px-6 pt-5 pb-3" style={{ borderBottom: '1px solid var(--page-border)' }}>
          <DialogTitle className="text-sm font-bold" style={{ color: 'var(--page-text)' }}>
            Editar usuario
          </DialogTitle>
          <p className="text-xs mt-0.5" style={{ color: 'var(--page-muted)' }}>{usuario.nombre}</p>
        </DialogHeader>

        <div className="px-6 py-5 space-y-5">
          {/* Avatar preview + sex selector */}
          <div>
            <label className="block text-xs font-semibold mb-3" style={{ color: 'var(--page-muted)' }}>
              SEXO Y AVATAR
            </label>
            <div className="flex items-center gap-4">
              <div className="w-[77px] h-[77px] rounded-full flex items-center justify-center flex-shrink-0"
                style={{ background: 'var(--page-surface)', border: '2px solid var(--page-border)', boxShadow: '0 0 0 3px var(--page-surface), 0 0 0 5px var(--page-border)' }}>
                {avatarPreview
                  ? <img src={avatarPreview} alt="avatar" className="w-full h-full object-cover rounded-full" />
                  : <UserCircle2 size={32} style={{ color: 'var(--page-muted)' }} />
                }
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setSexo('M')}
                  className="px-4 py-2 rounded-lg text-xs font-semibold transition-all"
                  style={{
                    background: sexo === 'M' ? 'var(--color-warning, #f97316)' : 'var(--page-surface)',
                    color: sexo === 'M' ? '#fff' : 'var(--page-text)',
                    border: sexo === 'M' ? '2px solid var(--color-warning, #f97316)' : '2px solid var(--page-border)',
                  }}
                >
                  Hombre
                </button>
                <button
                  type="button"
                  onClick={() => setSexo('F')}
                  className="px-4 py-2 rounded-lg text-xs font-semibold transition-all"
                  style={{
                    background: sexo === 'F' ? 'var(--color-warning, #f97316)' : 'var(--page-surface)',
                    color: sexo === 'F' ? '#fff' : 'var(--page-text)',
                    border: sexo === 'F' ? '2px solid var(--color-warning, #f97316)' : '2px solid var(--page-border)',
                  }}
                >
                  Mujer
                </button>
                {sexo && (
                  <button
                    type="button"
                    onClick={() => setSexo(null)}
                    className="px-3 py-2 rounded-lg text-xs transition-all"
                    style={{ color: 'var(--page-muted)', border: '2px solid var(--page-border)', background: 'var(--page-surface)' }}
                  >
                    ✕
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* Password reset */}
          <div>
            <label className="block text-xs font-semibold mb-2" style={{ color: 'var(--page-muted)' }}>
              NUEVA CONTRASEÑA <span style={{ color: 'var(--page-muted)', fontWeight: 400 }}>(opcional)</span>
            </label>
            <div className="relative">
              <input
                type={showPwd ? 'text' : 'password'}
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="Mínimo 6 caracteres"
                className="w-full pl-3 pr-9 py-2 text-xs rounded-md focus:outline-none focus:ring-2 focus:ring-orange-500"
                style={{
                  background: 'var(--input-bg)',
                  border: '1px solid var(--input-border)',
                  color: 'var(--input-text)',
                }}
              />
              <button
                type="button"
                onClick={() => setShowPwd(v => !v)}
                className="absolute right-2.5 top-1/2 -translate-y-1/2"
                style={{ color: 'var(--page-muted)' }}
              >
                {showPwd ? <EyeOff size={14} /> : <Eye size={14} />}
              </button>
            </div>
          </div>
        </div>

        <div className="flex justify-end gap-2 px-6 py-4" style={{ borderTop: '1px solid var(--page-border)', background: 'var(--page-surface)' }}>
          <Button variant="outline" size="sm" onClick={handleClose} disabled={saving}
            style={{ color: 'var(--page-text)', border: '1px solid var(--page-border)' }}>
            Cancelar
          </Button>
          <Button size="sm" onClick={handleSave} disabled={saving}
            style={{ background: 'var(--color-warning, #f97316)', color: '#fff' }}>
            {saving ? 'Guardando…' : 'Guardar'}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
