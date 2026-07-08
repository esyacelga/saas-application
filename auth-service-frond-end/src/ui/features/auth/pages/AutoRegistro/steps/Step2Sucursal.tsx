import type { UseFormReturn } from 'react-hook-form'
import { MapPin } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { WizardStep2Form } from '@/ui/features/platform/schemas/registrar-gym-wizard.schema'

interface Props {
  form: UseFormReturn<WizardStep2Form>
}

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null
  return <p className="text-xs mt-1" style={{ color: '#ef4444' }}>{msg}</p>
}

export function Step2Sucursal({ form }: Props) {
  const { register, formState: { errors } } = form

  return (
    <div className="space-y-4">
      <div>
        <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Sede principal</p>
        <p className="text-xs mt-0.5" style={{ color: 'var(--page-muted)' }}>
          ¿Dónde está ubicado tu gimnasio?
        </p>
      </div>

      <div className="space-y-3.5">
        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            Nombre de la sede <span style={{ color: '#ef4444' }}>*</span>
          </Label>
          <Input
            {...register('nombreSucursal')}
            placeholder="Sede Central, Sucursal Norte..."
            className="mt-1.5"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
          <FieldError msg={errors.nombreSucursal?.message} />
        </div>

        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            <MapPin size={12} className="inline mr-1 opacity-60" />
            Dirección
          </Label>
          <Input
            {...register('direccionSucursal')}
            placeholder="Av. Amazonas N23-45..."
            className="mt-1.5"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
        </div>
      </div>

      <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
        Puedes agregar más sedes desde el panel una vez registrado.
      </p>
    </div>
  )
}
