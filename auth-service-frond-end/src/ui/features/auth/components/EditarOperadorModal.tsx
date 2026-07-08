import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { Loader2, Upload, RefreshCw, UserCircle2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader,
  DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { OperadorPlataforma } from '@/infrastructure/http/auth/auth.dto'

interface Props {
  open: boolean
  onClose: () => void
  operador: OperadorPlataforma
  onActualizado: (updated: OperadorPlataforma) => void
}

type Rol = 'super_admin' | 'soporte' | 'viewer'

export function EditarOperadorModal({ open, onClose, operador, onActualizado }: Props) {
  const { t } = useTranslation()
  const fileRef = useRef<HTMLInputElement>(null)

  const [rol, setRol] = useState<Rol>(operador.rol_plataforma)
  const [fotoPreview, setFotoPreview] = useState<string | null>(operador.foto_url)
  const [uploadingFoto, setUploadingFoto] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (open) {
      setRol(operador.rol_plataforma)
      setFotoPreview(operador.foto_url)
    }
  }, [open, operador.rol_plataforma, operador.foto_url])

  const handleFotoChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      toast.error(t('operators.photoSizeError'))
      return
    }
    setFotoPreview(URL.createObjectURL(file))
    setUploadingFoto(true)
    try {
      const updated = await authRepository.subirFotoOperador(operador.id, file)
      setFotoPreview(updated.foto_url)
      onActualizado(updated)
      toast.success(t('operators.editFotoSuccess'))
    } catch {
      toast.error(t('operators.editFotoError'))
      setFotoPreview(operador.foto_url)
    } finally {
      setUploadingFoto(false)
      e.target.value = ''
    }
  }

  const ROLES_OPCIONES: { value: Rol; label: string; desc: string }[] = [
    { value: 'super_admin', label: 'Super Admin', desc: t('operators.roleSuperAdminDesc') },
    { value: 'soporte',     label: 'Soporte',     desc: t('operators.roleSoporteDesc') },
    { value: 'viewer',      label: 'Viewer',      desc: t('operators.roleViewerDesc') },
  ]

  const handleSubmit = async () => {
    if (rol === operador.rol_plataforma) { onClose(); return }
    setSubmitting(true)
    try {
      const updated = await authRepository.editarOperadorPlataforma(operador.id, { rol })
      onActualizado(updated)
      toast.success(t('operators.editRolSuccess'))
      onClose()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error(t('operators.toastLastAdminError'))
      } else {
        toast.error(t('operators.editRolError'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="w-full max-w-sm">
        <DialogHeader>
          <DialogTitle>{t('operators.editTitle')}</DialogTitle>
          <p className="text-sm" style={{ color: 'var(--page-muted)' }}>{operador.nombre}</p>
        </DialogHeader>

        {/* Foto */}
        <div className="flex items-center gap-4 py-2">
          <div
            className="relative w-16 h-16 rounded-full overflow-hidden flex items-center justify-center flex-shrink-0"
            style={{ background: 'var(--page-surface)', border: '2px solid var(--page-border)' }}
          >
            {fotoPreview
              ? <img src={fotoPreview} alt={operador.nombre} className="w-full h-full object-cover" />
              : <UserCircle2 size={28} style={{ color: 'var(--page-muted)' }} />
            }
            {uploadingFoto && (
              <div className="absolute inset-0 flex items-center justify-center rounded-full" style={{ background: 'rgba(0,0,0,0.5)' }}>
                <RefreshCw size={14} className="text-white animate-spin" />
              </div>
            )}
          </div>
          <div>
            <button
              type="button"
              onClick={() => fileRef.current?.click()}
              disabled={uploadingFoto}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors disabled:opacity-50"
              style={{ background: 'rgba(249,115,22,0.12)', color: '#f97316', border: '1px solid rgba(249,115,22,0.3)' }}
            >
              <Upload size={12} />
              {uploadingFoto ? t('operators.photoUploading') : fotoPreview ? t('operators.photoChange') : t('operators.photoUpload')}
            </button>
            <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>{t('operators.photoHint')}</p>
          </div>
          <input
            ref={fileRef}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            className="hidden"
            onChange={handleFotoChange}
          />
        </div>

        {/* Rol */}
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase" style={{ color: 'var(--page-muted)' }}>Rol</p>
          {ROLES_OPCIONES.map(opcion => (
            <label
              key={opcion.value}
              className="flex items-start gap-3 p-3 rounded-lg cursor-pointer transition-colors has-[:checked]:border-orange-400"
              style={{ border: '1px solid var(--page-border)', background: 'var(--page-bg)' }}
            >
              <input
                type="radio"
                name="rol-editar"
                value={opcion.value}
                checked={rol === opcion.value}
                onChange={() => setRol(opcion.value)}
                className="mt-0.5 accent-orange-500"
              />
              <div>
                <p className="text-sm font-medium" style={{ color: 'var(--page-text)' }}>{opcion.label}</p>
                <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{opcion.desc}</p>
              </div>
            </label>
          ))}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={onClose}
            disabled={submitting || uploadingFoto}
            style={{ fontSize: '0.75rem' }}
          >
            {t('confirmDialog.cancel')}
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={submitting || uploadingFoto}
            className="bg-orange-500 hover:bg-orange-600 text-white"
            style={{ fontSize: '0.75rem' }}
          >
            {submitting && <Loader2 size={14} className="mr-2 animate-spin" />}
            {submitting ? t('operators.editRolSubmitting') : t('operators.editRolSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
