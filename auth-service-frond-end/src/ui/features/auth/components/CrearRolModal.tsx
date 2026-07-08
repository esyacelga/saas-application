import { useMemo } from 'react'
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
import { createRolSchema, type CrearRolFormData } from '../schemas/rol.schema'

interface Props {
  open: boolean
  onClose: () => void
  onCreado: () => void
}

const inputClass =
  'w-full rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition'

const inputStyle = {
  background: 'var(--input-bg)',
  border: '1px solid var(--input-border)',
  color: 'var(--input-text)',
}

export function CrearRolModal({ open, onClose, onCreado }: Props) {
  const { t } = useTranslation()
  const schema = useMemo(() => createRolSchema(t), [t])

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CrearRolFormData>({ resolver: zodResolver(schema) })

  const handleClose = () => { reset(); onClose() }

  const onSubmit = async (data: CrearRolFormData) => {
    try {
      await authRepository.crearRol({
        nombre: data.nombre,
        descripcion: data.descripcion || undefined,
      })
      toast.success(t('roles.createSuccess'))
      reset()
      onCreado()
    } catch (err) {
      if (isAxiosError(err)) {
        if (err.response?.status === 409) {
          setError('nombre', { message: t('roles.createError409') })
        } else {
          toast.error(t('roles.createErrorConn'))
        }
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>{t('roles.createTitle')}</DialogTitle>
        </DialogHeader>

        <form
          id="crear-rol-form"
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4 py-2"
          noValidate
        >
          <div className="space-y-1.5">
            <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
              {t('roles.createNameLabel')} <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              autoFocus
              placeholder={t('roles.createNamePlaceholder')}
              {...register('nombre')}
              className={inputClass}
              style={inputStyle}
            />
            {errors.nombre && (
              <p className="text-xs text-red-500">{errors.nombre.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
              {t('roles.createDescLabel')}{' '}
              <span className="font-normal" style={{ color: 'var(--page-muted)' }}>{t('common.optional')}</span>
            </label>
            <textarea
              rows={2}
              placeholder={t('roles.createDescPlaceholder')}
              {...register('descripcion')}
              className={`${inputClass} resize-none`}
              style={inputStyle}
            />
            {errors.descripcion && (
              <p className="text-xs text-red-500">{errors.descripcion.message}</p>
            )}
          </div>

          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
            {t('roles.createDescHint')}
          </p>
        </form>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            {t('confirmDialog.cancel')}
          </Button>
          <Button
            type="submit"
            form="crear-rol-form"
            disabled={isSubmitting}
            className="bg-orange-500 hover:bg-orange-600 text-white"
          >
            {isSubmitting && <Loader2 size={14} className="mr-2 animate-spin" />}
            {isSubmitting ? t('roles.createSubmitting') : t('roles.createSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
