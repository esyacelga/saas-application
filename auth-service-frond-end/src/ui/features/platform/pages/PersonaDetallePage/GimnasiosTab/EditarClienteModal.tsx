import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { X } from 'lucide-react'
import { Dialog } from 'primereact/dialog'
import { Button } from 'primereact/button'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import type { ClientePorPersona, EstadoCliente } from '@/infrastructure/http/core/core.dto'
import type { CompaniaBasica } from '@/infrastructure/http/auth/auth.dto'
import { getApiErrorMessage } from '@/lib/api-error'

const ESTADOS: EstadoCliente[] = ['activo', 'proximo_vencer', 'vencido', 'congelado', 'riesgo_abandono']

const ESTADO_LABELS: Record<EstadoCliente, string> = {
  activo: 'Activo',
  proximo_vencer: 'Próximo a vencer',
  vencido: 'Vencido',
  congelado: 'Congelado',
  riesgo_abandono: 'Riesgo de abandono',
}

const schema = z.object({
  idCompania: z.coerce.number().positive('Selecciona un gimnasio'),
  estado:     z.string().min(1, 'Selecciona un estado'),
})

type FormData = z.infer<typeof schema>

const selectClass =
  'w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-orange-400 transition bg-[var(--input-bg)] border-[var(--page-border)] text-[var(--page-text)]'

interface Props {
  cliente: ClientePorPersona
  companias: CompaniaBasica[]
  open: boolean
  readonly: boolean
  onClose: () => void
  onActualizado: () => void
}

export function EditarClienteModal({ cliente, companias, open, readonly, onClose, onActualizado }: Props) {
  const [saving, setSaving] = useState(false)

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      idCompania: cliente.id_compania,
      estado:     cliente.estado,
    },
  })

  const onSubmit = async (data: FormData) => {
    setSaving(true)
    try {
      await coreRepository.actualizarClientePlataforma(cliente.id, {
        idCompania: Number(data.idCompania),
        estado:     data.estado as EstadoCliente,
      })
      toast.success('Registro actualizado')
      onActualizado()
    } catch (err) {
      toast.error(getApiErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog
      visible={open}
      onHide={onClose}
      header={
        <div className="flex items-center justify-between w-full">
          <span className="text-base font-semibold" style={{ color: 'var(--page-text)' }}>
            {readonly ? 'Detalle de registro' : 'Editar registro de gimnasio'}
          </span>
          <button onClick={onClose} className="text-[var(--page-muted)] hover:text-[var(--page-text)]">
            <X size={18} />
          </button>
        </div>
      }
      style={{ width: '420px' }}
      modal
      closable={false}
      pt={{ root: { style: { background: 'var(--page-surface)', border: '1px solid var(--page-border)' } } }}
    >
      {readonly ? (
        <div className="space-y-3 pt-1">
          <div>
            <p className="text-xs text-[var(--page-muted)]">Gimnasio</p>
            <p className="text-sm" style={{ color: 'var(--page-text)' }}>
              {companias.find(c => c.id === cliente.id_compania)?.nombre ?? `#${cliente.id_compania}`}
            </p>
          </div>
          <div>
            <p className="text-xs text-[var(--page-muted)]">Estado</p>
            <p className="text-sm" style={{ color: 'var(--page-text)' }}>{ESTADO_LABELS[cliente.estado]}</p>
          </div>
          <div>
            <p className="text-xs text-[var(--page-muted)]">Fecha ingreso</p>
            <p className="text-sm" style={{ color: 'var(--page-text)' }}>{cliente.fecha_ingreso}</p>
          </div>
          <div>
            <p className="text-xs text-[var(--page-muted)]">Código carnet</p>
            <p className="text-sm" style={{ color: 'var(--page-text)' }}>{cliente.codigo_carnet ?? '—'}</p>
          </div>
          <div className="flex justify-end pt-2">
            <Button type="button" label="Cerrar" severity="secondary" size="small" onClick={onClose} />
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 pt-1" noValidate>
          <div className="space-y-1">
            <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
              Gimnasio <span className="text-red-500">*</span>
            </label>
            <select {...register('idCompania')} className={selectClass}>
              {companias.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
            </select>
            {errors.idCompania && <p className="text-xs text-red-500">{errors.idCompania.message}</p>}
          </div>

          <div className="space-y-1">
            <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
              Estado <span className="text-red-500">*</span>
            </label>
            <select {...register('estado')} className={selectClass}>
              {ESTADOS.map(e => <option key={e} value={e}>{ESTADO_LABELS[e]}</option>)}
            </select>
            {errors.estado && <p className="text-xs text-red-500">{errors.estado.message}</p>}
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" label="Cancelar" severity="secondary" size="small" onClick={onClose} disabled={saving} />
            <Button type="submit" label="Guardar" size="small" loading={saving} />
          </div>
        </form>
      )}
    </Dialog>
  )
}
