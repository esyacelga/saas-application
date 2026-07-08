import type { UseFormReturn } from 'react-hook-form'
import { Building2, Mail, Phone } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { WizardStep1Form } from '@/ui/features/platform/schemas/registrar-gym-wizard.schema'

interface Props {
  form: UseFormReturn<WizardStep1Form>
}

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null
  return <p className="text-xs mt-1" style={{ color: '#ef4444' }}>{msg}</p>
}

export function Step1Empresa({ form }: Props) {
  const { register, formState: { errors } } = form

  return (
    <div className="space-y-4">
      <div>
        <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Tu gimnasio</p>
        <p className="text-xs mt-0.5" style={{ color: 'var(--page-muted)' }}>
          Empieza con los datos básicos de tu negocio.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-3.5">
        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            Nombre del gimnasio <span style={{ color: '#ef4444' }}>*</span>
          </Label>
          <Input
            {...register('nombre')}
            placeholder="CrossFit Central, Sport Zone..."
            className="mt-1.5"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
          <FieldError msg={errors.nombre?.message} />
        </div>

        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            RUC <span style={{ color: '#ef4444' }}>*</span>
          </Label>
          <Input
            {...register('ruc')}
            placeholder="1234567890001"
            className="mt-1.5 font-mono"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
          <FieldError msg={errors.ruc?.message} />
        </div>

        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            <Mail size={12} className="inline mr-1 opacity-60" />
            Correo corporativo
          </Label>
          <Input
            {...register('correo')}
            type="email"
            placeholder="contacto@migimnasio.com"
            className="mt-1.5"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
          <FieldError msg={errors.correo?.message} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
              <Phone size={12} className="inline mr-1 opacity-60" />
              Teléfono
            </Label>
            <Input
              {...register('telefono')}
              placeholder="+593 99 000 0000"
              className="mt-1.5"
              style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
            />
          </div>
          <div>
            <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
              WhatsApp
            </Label>
            <Input
              {...register('whatsapp')}
              placeholder="+593 99 000 0000"
              className="mt-1.5"
              style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
