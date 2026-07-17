import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { X } from 'lucide-react'
import { Dialog } from 'primereact/dialog'
import { Button } from 'primereact/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import type { CompaniaBasica, SucursalBasica } from '@/infrastructure/http/auth/auth.dto'
import { getApiErrorMessage } from '@/lib/api-error'

const schema = z.object({
  idCompania: z.coerce.number().positive('Selecciona un gimnasio'),
  idSucursal: z.coerce.number().positive('Selecciona una sucursal'),
})

type FormData = z.infer<typeof schema>

const selectClass =
  'w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-orange-400 transition bg-[var(--input-bg)] border-[var(--page-border)] text-[var(--page-text)]'

interface Props {
  idPersona: number
  companias: CompaniaBasica[]
  open: boolean
  onClose: () => void
  onAsignado: () => void
}

export function AsignarGimnasioModal({ idPersona, companias, open, onClose, onAsignado }: Props) {
  const [saving, setSaving] = useState(false)
  const [sucursales, setSucursales] = useState<SucursalBasica[]>([])
  const [loadingSuc, setLoadingSuc] = useState(false)

  const { register, handleSubmit, watch, formState: { errors } } = useForm<z.input<typeof schema>, unknown, FormData>({
    resolver: zodResolver(schema),
  })

  const idCompania = watch('idCompania')

  useEffect(() => {
    if (!idCompania) return
    setLoadingSuc(true)
    authRepository.getSucursalesByCompania(Number(idCompania))
      .then(setSucursales)
      .finally(() => setLoadingSuc(false))
  }, [idCompania])

  const onSubmit = async (data: FormData) => {
    setSaving(true)
    try {
      const persona = await authRepository.getPersonaById(idPersona)
      await coreRepository.registrarClientePlataforma({
        id_compania: data.idCompania,
        id_sucursal: data.idSucursal,
        ci:          persona.ci,
        nombre:      persona.nombre,
        telefono:    persona.telefono ?? undefined,
        correo:      persona.correo ?? undefined,
        sexo:        persona.sexo ?? undefined,
      })
      toast.success('Persona asignada al gimnasio')
      onAsignado()
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
          <span className="text-base font-semibold" style={{ color: 'var(--page-text)' }}>Asignar a gimnasio</span>
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
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 pt-1" noValidate>
        <div className="space-y-1">
          <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
            Gimnasio <span className="text-red-500">*</span>
          </label>
          <select {...register('idCompania')} className={selectClass} defaultValue="">
            <option value="">Selecciona un gimnasio...</option>
            {companias.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
          {errors.idCompania && <p className="text-xs text-red-500">{errors.idCompania.message}</p>}
        </div>

        <div className="space-y-1">
          <label className="block text-sm font-medium" style={{ color: 'var(--page-text)' }}>
            Sucursal <span className="text-red-500">*</span>
          </label>
          <select {...register('idSucursal')} className={selectClass} defaultValue="" disabled={!idCompania || loadingSuc}>
            <option value="">Selecciona una sucursal...</option>
            {sucursales.map(s => <option key={s.id} value={s.id}>{s.nombre}</option>)}
          </select>
          {errors.idSucursal && <p className="text-xs text-red-500">{errors.idSucursal.message}</p>}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" label="Cancelar" severity="secondary" size="small" onClick={onClose} disabled={saving} />
          <Button type="submit" label="Asignar" size="small" loading={saving} />
        </div>
      </form>
    </Dialog>
  )
}
