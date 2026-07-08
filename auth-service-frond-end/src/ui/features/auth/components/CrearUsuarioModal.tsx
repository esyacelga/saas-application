import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { Loader2, Search, X } from 'lucide-react'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader,
  DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Persona, Rol } from '@/infrastructure/http/auth/auth.dto'
import { createUsuarioSchema, type CrearUsuarioFormData } from '../schemas/usuario.schema'

interface Props {
  open: boolean
  onClose: () => void
  onCreado: () => void
}

const inputClass =
  'w-full border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition'

export function CrearUsuarioModal({ open, onClose, onCreado }: Props) {
  const { t } = useTranslation()
  const [roles, setRoles] = useState<Rol[]>([])
  const [ciInput, setCiInput] = useState('')
  const [buscando, setBuscando] = useState(false)
  const [personaSeleccionada, setPersonaSeleccionada] = useState<Persona | null>(null)
  const [ciError, setCiError] = useState<string | null>(null)

  useEffect(() => {
    if (open) {
      authRepository.getRoles().then(setRoles).catch(() => {})
    }
  }, [open])

  const schema = useMemo(() => createUsuarioSchema(t), [t])

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CrearUsuarioFormData, unknown, CrearUsuarioFormData>({ resolver: zodResolver(schema) as never })

  const handleClose = () => {
    reset()
    setCiInput('')
    setPersonaSeleccionada(null)
    setCiError(null)
    onClose()
  }

  const buscarPersona = async () => {
    const ci = ciInput.trim()
    if (!ci) return
    setBuscando(true)
    setCiError(null)
    try {
      const persona = await authRepository.buscarPersonaPorCI(ci)
      setPersonaSeleccionada(persona)
      setValue('id_persona', persona.id)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 404) {
        setCiError(t('users.personaNotFound', 'Persona no encontrada. Créala primero desde Clientes App.'))
      } else {
        setCiError(t('users.personaSearchError', 'Error al buscar persona.'))
      }
      setPersonaSeleccionada(null)
    } finally {
      setBuscando(false)
    }
  }

  const limpiarPersona = () => {
    setPersonaSeleccionada(null)
    setCiInput('')
    setCiError(null)
    setValue('id_persona', 0)
  }

  const onSubmit = async (data: CrearUsuarioFormData) => {
    try {
      await authRepository.crearUsuario(data)
      toast.success(t('users.createSuccess'))
      reset()
      handleClose()
      onCreado()
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 409) {
          setError('correo', { message: t('users.createError409') })
        } else if (status === 404) {
          setCiError(t('users.personaNotFound', 'Persona no encontrada.'))
        } else {
          toast.error(t('users.createErrorConn'))
        }
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t('users.createTitle')}</DialogTitle>
        </DialogHeader>

        <form
          id="crear-usuario-form"
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4 py-2"
          noValidate
        >
          {/* Búsqueda de persona */}
          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              {t('users.personaLabel', 'Persona (CI)')}
            </label>
            {personaSeleccionada ? (
              <div className="flex items-center justify-between rounded-lg border border-green-300 bg-green-50 px-3 py-2">
                <div>
                  <p className="text-sm font-medium text-green-800">{personaSeleccionada.nombre}</p>
                  <p className="text-xs text-green-600">{t('common.ci', 'CI')}: {personaSeleccionada.ci}</p>
                </div>
                <button type="button" onClick={limpiarPersona} className="text-green-600 hover:text-green-800">
                  <X size={14} />
                </button>
              </div>
            ) : (
              <div className="flex gap-2">
                <input
                  type="text"
                  value={ciInput}
                  onChange={e => setCiInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); buscarPersona() } }}
                  placeholder={t('users.ciPlaceholder', 'Ingresa el CI')}
                  className={inputClass}
                />
                <button
                  type="button"
                  onClick={buscarPersona}
                  disabled={buscando || !ciInput.trim()}
                  className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-orange-500 hover:bg-orange-600 disabled:opacity-50 text-white text-sm transition"
                >
                  {buscando ? <Loader2 size={14} className="animate-spin" /> : <Search size={14} />}
                </button>
              </div>
            )}
            {ciError && <p className="text-xs text-red-600">{ciError}</p>}
            {errors.id_persona && !ciError && (
              <p className="text-xs text-red-600">{t('users.personaRequired', 'Selecciona una persona válida.')}</p>
            )}
            <input type="hidden" {...register('id_persona', { valueAsNumber: true })} />
          </div>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              {t('common.email')}
            </label>
            <input
              type="email"
              autoComplete="off"
              placeholder={t('users.createEmailPlaceholder')}
              {...register('correo')}
              className={inputClass}
            />
            {errors.correo && (
              <p className="text-xs text-red-600">{errors.correo.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">{t('users.createRole')}</label>
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

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              {t('users.createBranchId')}
            </label>
            <input
              type="number"
              min={1}
              placeholder={t('users.createBranchPlaceholder')}
              {...register('id_sucursal')}
              className={inputClass}
            />
            {errors.id_sucursal && (
              <p className="text-xs text-red-600">{errors.id_sucursal.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <label className="block text-sm font-medium text-slate-700">
              {t('users.createTempPassword')}
            </label>
            <input
              type="text"
              autoComplete="off"
              placeholder={t('users.createTempPlaceholder')}
              {...register('password_temporal')}
              className={inputClass}
            />
            {errors.password_temporal && (
              <p className="text-xs text-red-600">{errors.password_temporal.message}</p>
            )}
            <p className="text-xs text-slate-400">
              {t('users.createTempHint')}
            </p>
          </div>
        </form>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            {t('confirmDialog.cancel')}
          </Button>
          <Button
            type="submit"
            form="crear-usuario-form"
            disabled={isSubmitting || !personaSeleccionada}
            className="bg-orange-500 hover:bg-orange-600 text-white"
          >
            {isSubmitting && <Loader2 size={14} className="mr-2 animate-spin" />}
            {isSubmitting ? t('users.createSubmitting') : t('users.createSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
