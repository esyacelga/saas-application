import { useState, useEffect } from 'react'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { Dialog } from 'primereact/dialog'
import { InputText } from 'primereact/inputtext'
import { InputTextarea } from 'primereact/inputtextarea'
import { Button } from 'primereact/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { RolPlataforma } from '@/infrastructure/http/auth/auth.dto'

interface Props {
  rol: RolPlataforma | null
  onClose: () => void
  onEditado: () => void
}

export function EditarRolPlataformaModal({ rol, onClose, onEditado }: Props) {
  const [nombre, setNombre] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [loading, setLoading] = useState(false)
  const [errores, setErrores] = useState<Record<string, string>>({})

  useEffect(() => {
    if (rol) {
      setNombre(rol.nombre)
      setDescripcion(rol.descripcion ?? '')
      setErrores({})
    }
  }, [rol])

  const handleClose = () => { setErrores({}); onClose() }

  const validate = (): boolean => {
    const e: Record<string, string> = {}
    if (!nombre.trim()) e.nombre = 'El nombre es requerido'
    setErrores(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async () => {
    if (!rol || !validate()) return
    setLoading(true)
    try {
      await authRepository.actualizarRolPlataforma(rol.id, {
        nombre: nombre.trim(),
        descripcion: descripcion.trim() || undefined,
      })
      toast.success(`Rol "${nombre}" actualizado`)
      onEditado()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        setErrores({ nombre: 'Ya existe un rol con ese nombre en esta compañía' })
      } else {
        toast.error('Error al actualizar el rol')
      }
    } finally {
      setLoading(false)
    }
  }

  const footer = (
    <div className="flex justify-end gap-2">
      <Button label="Cancelar" outlined onClick={handleClose} disabled={loading} />
      <Button
        label={loading ? 'Guardando...' : 'Guardar cambios'}
        icon={loading ? 'pi pi-spin pi-spinner' : 'pi pi-check'}
        severity="warning"
        onClick={handleSubmit}
        disabled={loading}
      />
    </div>
  )

  return (
    <Dialog
      header={
        <div className="flex items-center gap-2">
          <i className="pi pi-pencil text-orange-500" />
          <span>Editar rol</span>
        </div>
      }
      visible={rol !== null}
      onHide={handleClose}
      style={{ width: '480px', maxWidth: '95vw' }}
      footer={footer}
      modal
      draggable={false}
      resizable={false}
    >
      <div className="flex flex-col gap-4">
        {rol && (
          <div className="modal-badge flex items-center gap-2 px-3 py-2 rounded-lg border">
            <i className="pi pi-building text-xs modal-badge-label" />
            <span className="text-xs modal-badge-label">Compañía:</span>
            <span className="text-xs font-semibold modal-badge-value">{rol.nombre_compania}</span>
            <span className="ml-auto text-xs modal-badge-secondary">
              {rol.total_usuarios} {rol.total_usuarios === 1 ? 'usuario' : 'usuarios'}
            </span>
          </div>
        )}

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">
            Nombre <span className="text-red-400">*</span>
          </label>
          <InputText
            value={nombre}
            onChange={e => setNombre(e.target.value)}
            className={`w-full ${errores.nombre ? 'p-invalid' : ''}`}
            autoFocus
          />
          {errores.nombre && <small className="text-red-400 text-xs">{errores.nombre}</small>}
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">
            Descripción <span className="text-xs font-normal opacity-50">(opcional)</span>
          </label>
          <InputTextarea
            value={descripcion}
            onChange={e => setDescripcion(e.target.value)}
            rows={3}
            autoResize
            className="w-full"
          />
        </div>
      </div>
    </Dialog>
  )
}
