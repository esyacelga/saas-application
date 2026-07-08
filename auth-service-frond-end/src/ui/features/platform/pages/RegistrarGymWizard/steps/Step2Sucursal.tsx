import type { UseFormReturn } from 'react-hook-form'
import { MapPin, QrCode } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { WizardStep2Form } from '../../../schemas/registrar-gym-wizard.schema'

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
    <div className="space-y-5">
      <div className="flex items-center gap-3 pb-2" style={{ borderBottom: '1px solid var(--page-border)' }}>
        <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
          style={{ background: 'var(--color-warning-subtle, #fff7ed)', color: 'var(--color-warning, #f97316)' }}>
          <MapPin size={18} />
        </div>
        <div>
          <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Sede principal</p>
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>El gimnasio puede tener múltiples sedes. Esta es la principal.</p>
        </div>
      </div>

      <div className="space-y-4">
        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            Nombre de la sede <span style={{ color: '#ef4444' }}>*</span>
          </Label>
          <Input
            {...register('nombreSucursal')}
            placeholder="Ej: Sede Central, Matriz Norte…"
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
            placeholder="Av. Principal 123, Ciudad"
            className="mt-1.5"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
        </div>
      </div>

      <div className="rounded-lg p-3 flex items-start gap-3 mt-2"
        style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
        <QrCode size={16} className="flex-shrink-0 mt-0.5" style={{ color: 'var(--color-warning, #f97316)' }} />
        <p className="text-xs leading-relaxed" style={{ color: 'var(--page-muted)' }}>
          Al crear la sede se genera automáticamente un <strong style={{ color: 'var(--page-text)' }}>código QR</strong> único
          para el registro de asistencia de los clientes. Podrás renovarlo en cualquier momento desde la configuración de la sede.
        </p>
      </div>
    </div>
  )
}
