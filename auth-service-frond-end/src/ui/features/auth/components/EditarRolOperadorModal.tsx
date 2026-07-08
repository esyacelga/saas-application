import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Loader2 } from 'lucide-react'
import { isAxiosError } from 'axios'
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
  onActualizado: () => void
}

type Rol = 'super_admin' | 'soporte' | 'viewer'

export function EditarRolOperadorModal({ open, onClose, operador, onActualizado }: Props) {
  const { t } = useTranslation()
  const [rol, setRol] = useState<Rol>(operador.rol_plataforma)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (open) setRol(operador.rol_plataforma)
  }, [open, operador.rol_plataforma])

  const ROLES_OPCIONES: { value: Rol; label: string; desc: string }[] = [
    { value: 'super_admin', label: 'Super Admin', desc: t('operators.roleSuperAdminDesc') },
    { value: 'soporte',     label: 'Soporte',     desc: t('operators.roleSoporteDesc') },
    { value: 'viewer',      label: 'Viewer',      desc: t('operators.roleViewerDesc') },
  ]

  const handleSubmit = async () => {
    if (rol === operador.rol_plataforma) { onClose(); return }
    setSubmitting(true)
    try {
      await authRepository.editarOperadorPlataforma(operador.id, { rol })
      toast.success(t('operators.editRolSuccess'))
      onActualizado()
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
          <DialogTitle>{t('operators.editRolTitle')}</DialogTitle>
          <p className="text-sm" style={{ color: 'var(--page-muted)' }}>{operador.nombre}</p>
        </DialogHeader>

        <div className="space-y-2 py-2">
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

        <DialogFooter>
          <Button
            variant="outline"
            onClick={onClose}
            disabled={submitting}
            style={{ fontSize: '0.75rem' }}
          >
            {t('confirmDialog.cancel')}
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={submitting}
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