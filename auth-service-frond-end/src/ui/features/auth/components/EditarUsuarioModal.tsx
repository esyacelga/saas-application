import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
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
import type { Rol, UsuarioStaff } from '@/infrastructure/http/auth/auth.dto'
import { createEditarUsuarioSchema, type EditarUsuarioFormData } from '../schemas/usuario.schema'

interface Props {
  usuario: UsuarioStaff | null
  onClose: () => void
  onEditado: () => void
}

const inputClass =
  'w-full border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition'

export function EditarUsuarioModal({ usuario, onClose, onEditado }: Props) {
  const { t } = useTranslation()
  const [roles, setRoles] = useState<Rol[]>([])
  const schema = useMemo(() => createEditarUsuarioSchema(t), [t])

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<EditarUsuarioFormData, unknown, EditarUsuarioFormData>({ resolver: zodResolver(schema) as never })

  useEffect(() => {
    if (usuario) {
      authRepository.getRoles().then(setRoles).catch(() => {})
      reset({ correo: usuario.correo, id_rol: usuario.id_rol })
    }
  }, [usuario, reset])

  const handleClose = () => {
    reset()
    onClose()
  }

  const onSubmit = async (data: EditarUsuarioFormData) => {
    if (!usuario) return
    try {
      await authRepository.editarUsuario(usuario.id, data)
      toast.success(t('users.editSuccess'))
      onEditado()
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 409) {
          setError('correo', { message: t('users.editError409') })
        } else if (status === 404) {
          setError('id_rol', { message: t('users.editError404') })
        } else {
          toast.error(t('users.editErrorConn'))
        }
      }
    }
  }

  return (
    <Dialog open={usuario !== null} onOpenChange={handleClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t('users.editTitle')}</DialogTitle>
        </DialogHeader>

        <form
          id="editar-usuario-form"
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4 py-2"
          noValidate
        >
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              {t('users.editEmail')}
            </label>
            <input
              type="email"
              autoComplete="off"
              {...register('correo')}
              className={inputClass}
            />
            {errors.correo && (
              <p className="text-xs text-red-600">{errors.correo.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              {t('users.editRole')}
            </label>
            <select {...register('id_rol')} className={inputClass}>
              <option value="">{t('users.createSelectRole')}</option>
              {roles.map(r => (
                <option key={r.id} value={r.id}>
                  {r.nombre}
                </option>
              ))}
            </select>
            {errors.id_rol && (
              <p className="text-xs text-red-600">{errors.id_rol.message}</p>
            )}
          </div>
        </form>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            {t('confirmDialog.cancel')}
          </Button>
          <Button
            type="submit"
            form="editar-usuario-form"
            disabled={isSubmitting}
            className="bg-orange-500 hover:bg-orange-600 text-white"
          >
            {isSubmitting && <Loader2 size={14} className="mr-2 animate-spin" />}
            {isSubmitting ? t('users.editSubmitting') : t('users.editSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
