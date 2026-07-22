import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import {
  Building2, MapPin, CreditCard, ShieldCheck, Users,
  ChevronRight, ChevronLeft, Check, Loader2, Copy, CheckCircle2, QrCode,
} from 'lucide-react'
import { toast } from 'sonner'
import { Dialog, DialogContent } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import { RegistrarGymWizardUseCase } from '@/application/platform/RegistrarGymWizard.usecase'
import type { RegistrarGymWizardResponse } from '@/infrastructure/http/platform/platform.dto'
import {
  wizardStep1Schema, wizardStep2Schema, wizardStep3Schema, wizardStep4Schema,
  type WizardStep1Form, type WizardStep2Form, type WizardStep3Form, type WizardStep4Form,
} from '../../schemas/registrar-gym-wizard.schema'
import { Step1Empresa } from './steps/Step1Empresa'
import { Step2Sucursal } from './steps/Step2Sucursal'
import { Step3Plan } from './steps/Step3Plan'
import { Step4UsuarioPrincipal, type PersonaAdminData } from './steps/Step4UsuarioPrincipal'
import { Step5UsuariosAdicionales, type UsuarioAdicionalResuelto } from './steps/Step5UsuariosAdicionales'

const usecase = new RegistrarGymWizardUseCase(platformRepository)

const DEFAULT_LOGO_COMPANY = import.meta.env.VITE_AVATAR_LOGO_COMPANY as string | undefined

const STEPS = [
  { id: 1, label: 'Empresa',    icon: Building2,   description: 'Datos del gimnasio'       },
  { id: 2, label: 'Sede',       icon: MapPin,       description: 'Sede principal'            },
  { id: 3, label: 'Plan',       icon: CreditCard,   description: 'Suscripción'               },
  { id: 4, label: 'Admin',      icon: ShieldCheck,  description: 'Usuario principal'         },
  { id: 5, label: 'Personal',   icon: Users,        description: 'Usuarios adicionales'      },
]

interface Props {
  open: boolean
  onClose: () => void
  onCreated: () => void
}

type AllData = WizardStep1Form & WizardStep2Form & WizardStep3Form & WizardStep4Form

function StepBar({ current }: { current: number }) {
  return (
    <div className="flex items-center justify-between px-6 py-4 flex-shrink-0"
      style={{ borderBottom: '1px solid var(--page-border)', background: 'var(--page-surface)' }}>
      {STEPS.map((step, i) => {
        const Icon = step.icon
        const done = current > step.id
        const active = current === step.id
        return (
          <div key={step.id} className="flex items-center flex-1">
            <div className="flex flex-col items-center gap-1">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center transition-all duration-200 flex-shrink-0`}
                style={{
                  background: done ? 'var(--color-warning, #f97316)' : active ? 'var(--color-warning-subtle, #fff7ed)' : 'var(--page-bg)',
                  border: done
                    ? '2px solid var(--color-warning, #f97316)'
                    : active
                      ? '2px solid var(--color-warning, #f97316)'
                      : '2px solid var(--page-border)',
                  color: done ? '#fff' : active ? 'var(--color-warning, #f97316)' : 'var(--page-muted)',
                }}>
                {done ? <Check size={14} /> : <Icon size={14} />}
              </div>
              <span className="text-xs font-medium hidden sm:block"
                style={{ color: active ? 'var(--color-warning, #f97316)' : done ? 'var(--page-text)' : 'var(--page-muted)' }}>
                {step.label}
              </span>
            </div>
            {i < STEPS.length - 1 && (
              <div className="flex-1 mx-2 h-px transition-all duration-300"
                style={{ background: done ? 'var(--color-warning, #f97316)' : 'var(--page-border)' }} />
            )}
          </div>
        )
      })}
    </div>
  )
}

function ResumenFinal({ data, onClose }: { data: RegistrarGymWizardResponse; onClose: () => void }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    await navigator.clipboard.writeText(data.qrToken)
    setCopied(true)
    toast.success('QR Token copiado')
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex flex-col items-center justify-center py-8 px-6 gap-4 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--page-border)' }}>
        <div className="w-14 h-14 rounded-full flex items-center justify-center"
          style={{ background: '#dcfce7', color: '#16a34a' }}>
          <CheckCircle2 size={28} />
        </div>
        <div className="text-center">
          <h3 className="text-base font-bold" style={{ color: 'var(--page-text)' }}>¡Gimnasio registrado!</h3>
          <p className="text-sm mt-1" style={{ color: 'var(--page-muted)' }}>
            Todo está listo para que el equipo empiece a trabajar.
          </p>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-lg p-3" style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
            <p className="text-xs font-semibold uppercase tracking-wide mb-1" style={{ color: 'var(--page-muted)' }}>Compañía</p>
            <p className="text-sm font-mono" style={{ color: 'var(--page-text)' }}>#{data.idCompania}</p>
          </div>
          <div className="rounded-lg p-3" style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
            <p className="text-xs font-semibold uppercase tracking-wide mb-1" style={{ color: 'var(--page-muted)' }}>Sede</p>
            <p className="text-sm font-mono" style={{ color: 'var(--page-text)' }}>#{data.idSucursal}</p>
          </div>
        </div>

        {data.usuariosPrincipal && (
          <div className="rounded-lg p-3 flex items-center gap-3"
            style={{ background: 'var(--color-warning-subtle, #fff7ed)', border: '1px solid var(--color-warning, #f97316)' }}>
            <ShieldCheck size={16} style={{ color: 'var(--color-warning, #f97316)', flexShrink: 0 }} />
            <div className="min-w-0">
              <p className="text-xs font-semibold" style={{ color: 'var(--page-text)' }}>Admin creado</p>
              <p className="text-xs truncate" style={{ color: 'var(--page-muted)' }}>{data.usuariosPrincipal.nombre}</p>
              <p className="text-xs truncate font-mono" style={{ color: 'var(--page-muted)' }}>{data.usuariosPrincipal.correo}</p>
            </div>
          </div>
        )}

        {data.usuariosCreados > 1 && (
          <div className="rounded-lg p-3 flex items-center gap-3"
            style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
            <Users size={16} style={{ color: 'var(--page-muted)', flexShrink: 0 }} />
            <p className="text-xs" style={{ color: 'var(--page-text)' }}>
              <strong>{data.usuariosCreados}</strong> usuarios creados en total
            </p>
          </div>
        )}

        <div className="rounded-lg overflow-hidden" style={{ border: '1px solid var(--page-border)' }}>
          <div className="flex items-center gap-2 px-3 py-2" style={{ background: 'var(--page-surface)', borderBottom: '1px solid var(--page-border)' }}>
            <QrCode size={14} style={{ color: 'var(--color-warning, #f97316)' }} />
            <p className="text-xs font-semibold" style={{ color: 'var(--page-text)' }}>QR Token — Sede principal</p>
          </div>
          <div className="p-3" style={{ background: '#0f172a' }}>
            <code className="text-xs break-all font-mono" style={{ color: '#4ade80' }}>{data.qrToken}</code>
          </div>
          <button
            type="button"
            onClick={handleCopy}
            className="w-full flex items-center justify-center gap-2 py-2 text-xs transition-colors"
            style={{
              background: 'var(--page-surface)',
              borderTop: '1px solid var(--page-border)',
              color: copied ? '#16a34a' : 'var(--page-text)',
            }}
          >
            {copied ? <Check size={12} /> : <Copy size={12} />}
            {copied ? 'Copiado' : 'Copiar QR Token'}
          </button>
        </div>

        <p className="text-xs text-center" style={{ color: 'var(--page-muted)' }}>
          El administrador puede ingresar en <strong style={{ color: 'var(--page-text)' }}>/login</strong> con su correo y contraseña.
        </p>
      </div>

      <div className="p-4 flex-shrink-0" style={{ borderTop: '1px solid var(--page-border)' }}>
        <Button className="w-full" onClick={onClose}
          style={{ background: 'var(--color-warning, #f97316)', color: '#fff' }}>
          Finalizar
        </Button>
      </div>
    </div>
  )
}

export function RegistrarGymWizard({ open, onClose, onCreated }: Props) {
  const [step, setStep] = useState(1)
  const [allData, setAllData] = useState<Partial<AllData>>({})
  const [result, setResult] = useState<RegistrarGymWizardResponse | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [personaAdmin, setPersonaAdmin] = useState<PersonaAdminData | null>(null)
  const [personaError, setPersonaError] = useState(false)
  const [usuariosAdicionales, setUsuariosAdicionales] = useState<(UsuarioAdicionalResuelto | null)[]>([])
  const [errorCards, setErrorCards] = useState(false)

  // aceptaWhatsapp arranca en false explícitamente: el opt-in debe ser una acción del dueño,
  // nunca un default heredado (ver registrar-gym-wizard.schema.ts).
  const form1 = useForm<WizardStep1Form>({
    resolver: zodResolver(wizardStep1Schema),
    defaultValues: { aceptaWhatsapp: false, ...allData },
  })
  const form2 = useForm<WizardStep2Form>({ resolver: zodResolver(wizardStep2Schema), defaultValues: allData })
  const form3 = useForm<WizardStep3Form>({ resolver: zodResolver(wizardStep3Schema) as never, defaultValues: allData })
  const form4 = useForm<WizardStep4Form>({ resolver: zodResolver(wizardStep4Schema) })

  const handleClose = () => {
    if (submitting) return
    setStep(1)
    setAllData({})
    setResult(null)
    setPersonaAdmin(null)
    setPersonaError(false)
    setUsuariosAdicionales([])
    setErrorCards(false)
    form1.reset(); form2.reset(); form3.reset(); form4.reset()
    onClose()
  }

  const goNext = async () => {
    let valid = false
    let stepData: Partial<AllData> = {}

    if (step === 1) {
      valid = await form1.trigger()
      if (valid) stepData = form1.getValues()
    } else if (step === 2) {
      valid = await form2.trigger()
      if (valid) stepData = form2.getValues()
    } else if (step === 3) {
      valid = await form3.trigger()
      if (valid) stepData = form3.getValues()
    } else if (step === 4) {
      if (!personaAdmin) {
        setPersonaError(true)
        return
      }
      setPersonaError(false)
      valid = await form4.trigger()
      if (!valid) return
      stepData = form4.getValues()

      if (personaAdmin.id_persona === undefined) {
        setSubmitting(true)
        try {
          const persona = await authRepository.crearPersona({
            ci:       personaAdmin.ci,
            nombre:   personaAdmin.nombre,
            correo:   personaAdmin.correo,
            telefono: personaAdmin.telefono,
            sexo:     personaAdmin.sexo,
            foto_url: personaAdmin.foto_url,
          })
          setPersonaAdmin(prev => prev ? { ...prev, id_persona: persona.id } : prev)
          toast.success('Persona registrada exitosamente')
        } catch {
          toast.error('No se pudo registrar la persona. Verifica los datos e intenta de nuevo.')
          setSubmitting(false)
          return
        }
        setSubmitting(false)
      }
    } else if (step === 5) {
      const hayIncompletos = usuariosAdicionales.some(u => u === null)
      if (hayIncompletos) {
        setErrorCards(true)
        return
      }
      setErrorCards(false)
      valid = true

      // Crear personas nuevas (sin id_persona) antes del submit final
      setSubmitting(true)
      try {
        const resueltos = await Promise.all(
          usuariosAdicionales.map(async u => {
            if (!u) throw new Error('unresolved')
            if (u.persona.id_persona !== undefined) return u
            const persona = await authRepository.crearPersona({
              ci:       u.persona.ci,
              nombre:   u.persona.nombre,
              correo:   u.persona.correo,
              telefono: u.persona.telefono,
            })
            return { ...u, persona: { ...u.persona, id_persona: persona.id } }
          }),
        )
        await handleSubmit(allData as AllData, resueltos)
      } catch {
        toast.error('No se pudo registrar uno de los usuarios adicionales.')
        setSubmitting(false)
      }
      return
    }

    if (!valid) return
    setAllData(prev => ({ ...prev, ...stepData }))
    setStep(s => s + 1)
  }

  const goPrev = () => setStep(s => s - 1)

  const handleSubmit = async (data: AllData, adicionales: UsuarioAdicionalResuelto[]) => {
    setSubmitting(true)
    try {
      const res = await usecase.execute({
        nombre: data.nombre,
        ruc: data.ruc,
        logoUrl: DEFAULT_LOGO_COMPANY,
        correo: data.correo || undefined,
        telefono: data.telefono || undefined,
        whatsapp: data.whatsapp || undefined,
        aceptaWhatsapp: data.aceptaWhatsapp,
        idPlan: data.idPlan,
        nombreSucursal: data.nombreSucursal,
        direccionSucursal: data.direccionSucursal || undefined,
        usuarioPrincipal: {
          id_persona: personaAdmin?.id_persona,
          ci:         personaAdmin?.ci ?? '',
          nombre:     personaAdmin?.nombre ?? '',
          correo:     personaAdmin?.correo ?? '',
          telefono:   personaAdmin?.telefono,
          sexo:       personaAdmin?.sexo,
          foto_url:   personaAdmin?.foto_url,
          password:   data.usuarioPrincipal.password,
        },
        usuariosAdicionales: adicionales.map(u => ({
          id_persona: u.persona.id_persona,
          ci:         u.persona.ci,
          nombre:     u.persona.nombre,
          correo:     u.persona.correo,
          telefono:   u.persona.telefono,
          password:   u.password,
        })),
      })
      setResult(res)
      onCreated()
    } catch {
      toast.error('Error al registrar el gimnasio. Verifica los datos e inténtalo de nuevo.')
    } finally {
      setSubmitting(false)
    }
  }

  const stepLabel = STEPS[step - 1]

  return (
    <Dialog open={open} onOpenChange={open => { if (!open) handleClose() }}>
      <DialogContent
        className="p-0 overflow-hidden flex flex-col"
        style={{
          maxWidth: '680px',
          width: '100%',
          height: '90vh',
          maxHeight: '720px',
          background: 'var(--page-bg)',
          border: '1px solid var(--page-border)',
        }}
      >
        {result ? (
          <ResumenFinal data={result} onClose={handleClose} />
        ) : (
          <div className="flex flex-col h-full overflow-hidden">
            {/* Header */}
            <div className="px-6 pt-5 pb-3 flex-shrink-0">
              <h2 className="text-base font-bold" style={{ color: 'var(--page-text)' }}>
                Registrar nuevo gimnasio
              </h2>
              <p className="text-xs mt-0.5" style={{ color: 'var(--page-muted)' }}>
                Paso {step} de {STEPS.length} — {stepLabel.description}
              </p>
            </div>

            {/* Step bar */}
            <StepBar current={step} />

            {/* Content */}
            <div className="flex-1 overflow-y-auto px-6 py-5">
              {step === 1 && <Step1Empresa form={form1} />}
              {step === 2 && <Step2Sucursal form={form2} />}
              {step === 3 && <Step3Plan form={form3} yaUsoTrial={false} />}
              {step === 4 && (
                <Step4UsuarioPrincipal
                  form={form4}
                  onPersonaResolved={data => { setPersonaAdmin(data); if (data) setPersonaError(false) }}
                  errorNoPersona={personaError}
                />
              )}
              {step === 5 && (
                <Step5UsuariosAdicionales
                  onUsersChange={setUsuariosAdicionales}
                  errorCards={errorCards}
                />
              )}
            </div>

            {/* Footer nav */}
            <div className="flex items-center justify-between px-6 py-4 flex-shrink-0"
              style={{ borderTop: '1px solid var(--page-border)', background: 'var(--page-surface)' }}>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={step === 1 ? handleClose : goPrev}
                disabled={submitting}
                className="gap-1.5"
                style={{ color: 'var(--page-text)', border: '1px solid var(--page-border)' }}
              >
                {step === 1 ? 'Cancelar' : <><ChevronLeft size={14} /> Anterior</>}
              </Button>

              <div className="flex items-center gap-2">
                {STEPS.map(s => (
                  <div key={s.id} className="w-1.5 h-1.5 rounded-full transition-all duration-200"
                    style={{
                      background: s.id === step
                        ? 'var(--color-warning, #f97316)'
                        : s.id < step
                          ? 'var(--color-warning, #f97316)'
                          : 'var(--page-border)',
                      opacity: s.id < step ? 0.4 : 1,
                    }} />
                ))}
              </div>

              <Button
                type="button"
                size="sm"
                onClick={goNext}
                disabled={submitting}
                className="gap-1.5 min-w-[110px]"
                style={{ background: 'var(--color-warning, #f97316)', color: '#fff' }}
              >
                {submitting ? (
                  <><Loader2 size={14} className="animate-spin" /> Creando…</>
                ) : step === STEPS.length ? (
                  <><Check size={14} /> Finalizar</>
                ) : (
                  <>Siguiente <ChevronRight size={14} /></>
                )}
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
