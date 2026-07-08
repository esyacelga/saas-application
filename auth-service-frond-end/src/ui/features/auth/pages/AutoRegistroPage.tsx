import { useState, useEffect, useCallback } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Navigate, Link } from 'react-router-dom'
import { AlertCircle } from 'lucide-react'
import { isAxiosError } from 'axios'
import { useIsAuthenticated } from '@/infrastructure/store/auth/auth.store'
import { autoRegistroUseCase } from '@/application/auth/AutoRegistro.usecase'
import { PulsingDots } from '@/ui/components/PulsingDots'
import { StepperBar } from './AutoRegistro/StepperBar'
import { Step1Empresa } from './AutoRegistro/steps/Step1Empresa'
import { Step2Sucursal } from './AutoRegistro/steps/Step2Sucursal'
import { Step3Plan } from './AutoRegistro/steps/Step3Plan'
import { Step4DatosPropios } from './AutoRegistro/steps/Step4DatosPropios'
import { ResumenExito } from './AutoRegistro/ResumenExito'
import {
  wizardStep1Schema,
  wizardStep2Schema,
  wizardStep3Schema,
  type WizardStep1Form,
  type WizardStep2Form,
  type WizardStep3Form,
} from '@/ui/features/platform/schemas/registrar-gym-wizard.schema'
import {
  autoRegistroStep4Schema,
  type AutoRegistroStep4Form,
} from '../schemas/auto-registro-wizard.schema'

const STEPS = [
  { label: 'Gimnasio' },
  { label: 'Sede' },
  { label: 'Plan' },
  { label: 'Tus datos' },
]

type ServerError = { tipo: 'correo' | 'ci' | 'ruc' | 'idPlan' | 'rate_limit' | 'server' }

interface Resultado {
  nombreGimnasio: string
  qrToken: string
}

export function AutoRegistroPage() {
  const isAuthenticated = useIsAuthenticated()

  const [currentStep, setCurrentStep] = useState(1)
  const [registroCompletado, setRegistroCompletado] = useState(false)
  const [resultado, setResultado] = useState<Resultado | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState<ServerError | null>(null)
  const [step3Blocked, setStep3Blocked] = useState(true)

  // State acumulado para cada paso
  const [step1Data, setStep1Data] = useState<WizardStep1Form | null>(null)
  const [step2Data, setStep2Data] = useState<WizardStep2Form | null>(null)
  const [step3Data, setStep3Data] = useState<WizardStep3Form | null>(null)

  const form1 = useForm<WizardStep1Form>({
    resolver: zodResolver(wizardStep1Schema),
    defaultValues: step1Data ?? { nombre: '', ruc: '', correo: '', telefono: '', whatsapp: '' },
  })

  const form2 = useForm<WizardStep2Form>({
    resolver: zodResolver(wizardStep2Schema),
    defaultValues: step2Data ?? { nombreSucursal: '', direccionSucursal: '' },
  })

  const form3 = useForm<WizardStep3Form>({
    resolver: zodResolver(wizardStep3Schema),
    defaultValues: step3Data ?? {},
  })

  const form4 = useForm<AutoRegistroStep4Form>({
    resolver: zodResolver(autoRegistroStep4Schema),
    defaultValues: { ci: '', nombre: '', correo: '', password: '', confirmarPassword: '' },
  })

  // beforeunload warning mientras el usuario avanzó pero no completó
  useEffect(() => {
    if (currentStep <= 1 || registroCompletado) return
    const handler = (e: BeforeUnloadEvent) => { e.preventDefault() }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [currentStep, registroCompletado])

  const handleStep3LoadingChange = useCallback((blocked: boolean) => {
    setStep3Blocked(blocked)
  }, [])

  const goBack = () => {
    setServerError(null)
    setCurrentStep(s => Math.max(1, s - 1))
  }

  const handleStep1 = form1.handleSubmit((data) => {
    setStep1Data(data)
    setCurrentStep(2)
  })

  const handleStep2 = form2.handleSubmit((data) => {
    setStep2Data(data)
    setCurrentStep(3)
  })

  const handleStep3 = form3.handleSubmit((data) => {
    setStep3Data(data)
    setCurrentStep(4)
  })

  const handleStep4 = form4.handleSubmit(async (data4) => {
    if (!step1Data || !step2Data || !step3Data) return

    setSubmitting(true)
    setServerError(null)

    try {
      const res = await autoRegistroUseCase.execute({
        nombre: step1Data.nombre,
        ruc: step1Data.ruc,
        correo: step1Data.correo || undefined,
        telefono: step1Data.telefono || undefined,
        whatsapp: step1Data.whatsapp || undefined,
        nombreSucursal: step2Data.nombreSucursal,
        direccionSucursal: step2Data.direccionSucursal || undefined,
        idPlan: step3Data.idPlan,
        usuarioPrincipal: {
          ci: data4.ci,
          nombre: data4.nombre,
          correo: data4.correo,
          password: data4.password,
        },
      })

      setResultado({ nombreGimnasio: step1Data.nombre, qrToken: res.qrToken })
      setRegistroCompletado(true)
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        const conflicto = err.response?.data?.conflicto as string | undefined

        if (status === 409) {
          if (conflicto === 'ruc') {
            setServerError({ tipo: 'ruc' })
            setCurrentStep(1)
          } else if (conflicto === 'idPlan') {
            setServerError({ tipo: 'idPlan' })
            setCurrentStep(3)
          } else if (conflicto === 'correo') {
            setServerError({ tipo: 'correo' })
          } else if (conflicto === 'ci') {
            setServerError({ tipo: 'ci' })
          } else {
            setServerError({ tipo: 'server' })
          }
        } else if (status === 429) {
          setServerError({ tipo: 'rate_limit' })
        } else {
          setServerError({ tipo: 'server' })
        }
      } else {
        setServerError({ tipo: 'server' })
      }
    } finally {
      setSubmitting(false)
    }
  })

  if (isAuthenticated) return <Navigate to="/admin/dashboard" replace />

  if (registroCompletado && resultado) {
    return <ResumenExito nombreGimnasio={resultado.nombreGimnasio} qrToken={resultado.qrToken} />
  }

  const isStep4 = currentStep === 4
  const isNextDisabled =
    submitting ||
    (currentStep === 3 && step3Blocked)

  const step4Error =
    serverError?.tipo === 'correo' || serverError?.tipo === 'ci'
      ? serverError
      : null

  const globalBannerError: string | null =
    serverError?.tipo === 'ruc'
      ? 'Ya existe una empresa registrada con ese RUC. Por favor verifica y corrige el dato.'
      : serverError?.tipo === 'idPlan'
        ? 'El plan seleccionado no está disponible. Por favor elige otro.'
        : serverError?.tipo === 'rate_limit'
          ? 'Demasiados intentos. Espera unos minutos e intenta de nuevo.'
          : serverError?.tipo === 'server'
            ? 'Ocurrió un error inesperado. Por favor intenta de nuevo.'
            : null

  const handleNext = () => {
    if (currentStep === 1) { handleStep1(); return }
    if (currentStep === 2) { handleStep2(); return }
    if (currentStep === 3) { handleStep3(); return }
    if (currentStep === 4) { handleStep4(); return }
  }

  return (
    <div className="bg-white rounded-2xl shadow-card border border-slate-100 p-6 space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Crear cuenta</h1>
          <p className="text-slate-500 text-xs mt-0.5">Registra tu gimnasio gratis en minutos.</p>
        </div>
        <Link
          to="/login"
          className="text-xs transition-colors"
          style={{ color: 'var(--page-muted)' }}
        >
          ← Volver al login
        </Link>
      </div>

      <StepperBar steps={STEPS} currentStep={currentStep} />

      {globalBannerError && (
        <div className="flex items-start gap-2.5 rounded-lg p-3 text-sm"
          style={{ background: '#fef2f2', border: '1px solid #fecaca', color: '#b91c1c' }}>
          <AlertCircle size={15} className="flex-shrink-0 mt-0.5" />
          <span>{globalBannerError}</span>
        </div>
      )}

      <div>
        {currentStep === 1 && <Step1Empresa form={form1} />}
        {currentStep === 2 && <Step2Sucursal form={form2} />}
        {currentStep === 3 && (
          <Step3Plan form={form3} onLoadingChange={handleStep3LoadingChange} />
        )}
        {currentStep === 4 && (
          <Step4DatosPropios form={form4} serverError={step4Error} />
        )}
      </div>

      <div className="flex items-center justify-between pt-2" style={{ borderTop: '1px solid var(--page-border)' }}>
        {currentStep > 1 ? (
          <button
            type="button"
            onClick={goBack}
            className="text-sm transition-colors"
            style={{ color: 'var(--page-muted)' }}
          >
            ← Volver
          </button>
        ) : (
          <span />
        )}

        <button
          type="button"
          onClick={handleNext}
          disabled={isNextDisabled}
          className="flex items-center gap-2 font-semibold py-2.5 px-5 rounded-lg text-sm transition-colors disabled:opacity-60"
          style={{ background: '#f97316', color: '#fff' }}
        >
          {submitting && <PulsingDots size="sm" />}
          {submitting
            ? 'Creando cuenta…'
            : isStep4
              ? 'Crear mi cuenta'
              : 'Siguiente →'
          }
        </button>
      </div>
    </div>
  )
}
