import { Controller, type UseFormReturn } from 'react-hook-form'
import { Building2, Mail, MessageCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import type { WizardStep1Form } from '../../../schemas/registrar-gym-wizard.schema'
import { PhoneInputE164Controller } from '@/ui/components/PhoneInputE164'

interface Props {
  form: UseFormReturn<WizardStep1Form>
}

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null
  return <p className="text-xs mt-1" style={{ color: 'var(--color-error, #ef4444)' }}>{msg}</p>
}

export function Step1Empresa({ form }: Props) {
  const { t } = useTranslation()
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

        {/* Campo telefono oculto — se conserva en el schema y DTO por compatibilidad */}
        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            <MessageCircle size={12} className="inline mr-1 opacity-60" />
            WhatsApp
          </Label>
          <div className="mt-1.5">
            <PhoneInputE164Controller
              name="whatsapp"
              control={form.control}
              defaultCountry="EC"
              placeholder={t('phoneInput.placeholder')}
            />
          </div>
          {errors.whatsapp && (
            <FieldError msg={t('phoneInput.invalid')} />
          )}
        </div>

        {/* Opt-in WhatsApp — desmarcado por defecto (consentimiento afirmativo, ver schema) */}
        <label
          className="flex items-start gap-3 rounded-lg p-3 cursor-pointer"
          style={{ border: '1px solid var(--page-border)', background: 'var(--page-surface)' }}
        >
          <Controller
            control={form.control}
            name="aceptaWhatsapp"
            render={({ field }) => (
              <Checkbox
                checked={field.value}
                onCheckedChange={v => field.onChange(v === true)}
                className="mt-0.5"
              />
            )}
          />
          <span>
            <span className="text-xs font-medium block" style={{ color: 'var(--page-text)' }}>
              <MessageCircle size={12} className="inline mr-1 opacity-60" />
              El dueño acepta recibir avisos de vencimiento por WhatsApp
            </span>
            <span className="text-xs block mt-0.5" style={{ color: 'var(--page-muted)' }}>
              Se le avisa 3 días antes y el día del vencimiento, para que no pierda el acceso.
              Márcalo solo si el dueño lo autorizó; podrá cambiarlo desde su panel.
            </span>
          </span>
        </label>
      </div>
    </div>
  )
}
