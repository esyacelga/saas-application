import type { UseFormReturn } from 'react-hook-form'
import { useState } from 'react'
import { Eye, EyeOff, AlertCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PasswordStrength } from '@/ui/features/auth/components/PasswordStrength'
import type { AutoRegistroStep4Form } from '../../schemas/auto-registro-wizard.schema'

interface Props {
  form: UseFormReturn<AutoRegistroStep4Form>
  serverError?: { tipo: 'correo' | 'ci' | string } | null
}

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null
  return <p className="text-xs mt-1" style={{ color: '#ef4444' }}>{msg}</p>
}

export function Step4DatosPropios({ form, serverError }: Props) {
  const { register, watch, formState: { errors } } = form
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const password = watch('password') ?? ''

  return (
    <div className="space-y-4">
      <div>
        <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Tus datos</p>
        <p className="text-xs mt-0.5" style={{ color: 'var(--page-muted)' }}>
          Serás el administrador principal del gimnasio.
        </p>
      </div>

      {serverError?.tipo === 'correo' && (
        <div className="flex items-start gap-2.5 rounded-lg p-3 text-sm"
          style={{ background: '#fef2f2', border: '1px solid #fecaca', color: '#b91c1c' }}>
          <AlertCircle size={15} className="flex-shrink-0 mt-0.5" />
          <span>
            Este correo ya está registrado.{' '}
            <Link to="/login" className="font-semibold underline">
              Inicia sesión →
            </Link>
          </span>
        </div>
      )}

      {serverError?.tipo === 'ci' && (
        <div className="flex items-start gap-2.5 rounded-lg p-3 text-sm"
          style={{ background: '#fef2f2', border: '1px solid #fecaca', color: '#b91c1c' }}>
          <AlertCircle size={15} className="flex-shrink-0 mt-0.5" />
          <span>Esta cédula ya está registrada en el sistema.</span>
        </div>
      )}

      <div className="space-y-3.5">
        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            Nombre completo <span style={{ color: '#ef4444' }}>*</span>
          </Label>
          <Input
            {...register('nombre')}
            placeholder="Tu nombre completo"
            className="mt-1.5"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
          <FieldError msg={errors.nombre?.message} />
        </div>

        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            CI / Cédula <span style={{ color: '#ef4444' }}>*</span>
          </Label>
          <Input
            {...register('ci')}
            placeholder="Tu número de cédula"
            className="mt-1.5 font-mono"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
          <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>
            Tu número de cédula de identidad.
          </p>
          <FieldError msg={errors.ci?.message} />
        </div>

        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            Correo electrónico <span style={{ color: '#ef4444' }}>*</span>
          </Label>
          <Input
            {...register('correo')}
            type="email"
            placeholder="tu@correo.com"
            className="mt-1.5"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
          <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>
            Este correo será tu usuario de acceso al panel.
          </p>
          <FieldError msg={errors.correo?.message} />
        </div>

        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            Contraseña <span style={{ color: '#ef4444' }}>*</span>
          </Label>
          <div className="relative mt-1.5">
            <Input
              {...register('password')}
              type={showPassword ? 'text' : 'password'}
              placeholder="••••••••"
              className="pr-10"
              style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
            />
            <button
              type="button"
              tabIndex={-1}
              onClick={() => setShowPassword(v => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 transition-colors"
              style={{ color: 'var(--page-muted)' }}
            >
              {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>
          <FieldError msg={errors.password?.message} />
          <PasswordStrength password={password} />
        </div>

        <div>
          <Label className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            Confirmar contraseña <span style={{ color: '#ef4444' }}>*</span>
          </Label>
          <div className="relative mt-1.5">
            <Input
              {...register('confirmarPassword')}
              type={showConfirm ? 'text' : 'password'}
              placeholder="••••••••"
              className="pr-10"
              style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
            />
            <button
              type="button"
              tabIndex={-1}
              onClick={() => setShowConfirm(v => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 transition-colors"
              style={{ color: 'var(--page-muted)' }}
            >
              {showConfirm ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>
          <FieldError msg={errors.confirmarPassword?.message} />
        </div>
      </div>
    </div>
  )
}
