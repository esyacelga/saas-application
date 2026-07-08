import type { UseFormReturn } from 'react-hook-form'
import { Building2, Mail, Phone } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { WizardStep1Form } from '../../../schemas/registrar-gym-wizard.schema'

interface Props {
  form: UseFormReturn<WizardStep1Form>
}

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null
  return <p className="text-xs mt-1" style={{ color: 'var(--color-error, #ef4444)' }}>{msg}</p>
}

export function Step1Empresa({ form }: Props) {
  const { register, formState: { errors } } = form

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3 pb-2" style={{ borderBottom: '1px solid var(--page-border)' }}>
        <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
          style={{ background: 'var(--color-warning-subtle, #fff7ed)', color: 'var(--color-warning, #f97316)' }}>
          <Building2 size={18} />
        </div>
        <div>
          <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Datos del gimnasio</p>
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>Información legal e identificación de la empresa</p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4">
        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            Nombre del gimnasio <span style={{ color: '#ef4444' }}>*</span>
          </Label>
          <Input
            {...register('nombre')}
            placeholder="Ej: Gym Elite Fitness"
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
            placeholder="Ej: 1234567890001"
            className="mt-1.5 font-mono"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
          <FieldError msg={errors.ruc?.message} />
        </div>

        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            <Mail size={12} className="inline mr-1 opacity-60" />
            Correo electrónico
          </Label>
          <Input
            {...register('correo')}
            type="email"
            placeholder="gym@empresa.com"
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
