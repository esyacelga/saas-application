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
import { Step3Plan } from './AutoRegistro/steps/Step3Plan'
import { Step4DatosPropios } from './AutoRegistro/steps/Step4DatosPropios'
import { ResumenExito } from './AutoRegistro/ResumenExito'
import {
  wizardStep3Schema,
  type WizardStep3Form,
} from '@/ui/features/platform/schemas/registrar-gym-wizard.schema'
import {
  autoRegistroStep1Schema,
  autoRegistroStep4Schema,
  type AutoRegistroStep1Form,
  type AutoRegistroStep4Form,
} from '../schemas/auto-registro-wizard.schema'

// El paso "Local/Sucursal" se eliminó: el nombre de la sucursal se deriva del nombre
// del gimnasio en el submit (ver handleFinal), y la dirección vive ahora en el Paso 1.
// El wizard quedó en 3 pasos. Los gyms multi-local agregan/renombran sucursales desde
// el panel. Se conserva la numeración interna del Paso 3 (Plan) y Paso 4 (Tus datos)
// para no reescribir el schema del plan compartido con el registro por operador.
const STEPS = [
  { label: 'Gimnasio' },
  { label: 'Plan' },
  { label: 'Tus datos' },
]

type ServerError = { tipo: 'correo' | 'ci' | 'idPlan' | 'rate_limit' | 'server' }

interface Resultado {
  nombreGimnasio: string
  planCodigo: string | null
}

export function AutoRegistroPage() {
  const isAuthenticated = useIsAuthenticated()

  const [currentStep, setCurrentStep] = useState(1)
  const [registroCompletado, setRegistroCompletado] = useState(false)
  const [resultado, setResultado] = useState<Resultado | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState<ServerError | null>(null)
  const [step3Blocked, setStep3Blocked] = useState(true)
  const [planCodigoSeleccionado, setPlanCodigoSeleccionado] = useState<string | null>(null)

  // State acumulado para cada paso visible: 1 = Gimnasio, 2 = Plan, 3 = Tus datos.
  const [step1Data, setStep1Data] = useState<AutoRegistroStep1Form | null>(null)
  const [planData, setPlanData] = useState<WizardStep3Form | null>(null)

  const form1 = useForm<AutoRegistroStep1Form>({
    resolver: zodResolver(autoRegistroStep1Schema),
    defaultValues: step1Data ?? { nombre: '', correo: '', direccion: '' },
  })

  const formPlan = useForm<WizardStep3Form>({
    resolver: zodResolver(wizardStep3Schema),
    defaultValues: planData ?? {},
  })

  const formDatos = useForm<AutoRegistroStep4Form>({
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

  const handleStep3PlanChange = useCallback((codigo: string | null) => {
    setPlanCodigoSeleccionado(codigo)
  }, [])

  const goBack = () => {
    setServerError(null)
    setCurrentStep(s => Math.max(1, s - 1))
  }

  const handleStep1 = form1.handleSubmit((data) => {
    setStep1Data(data)
    setCurrentStep(2)
  })

  const handlePlan = formPlan.handleSubmit((data) => {
    setPlanData(data)
    setCurrentStep(3)
  })

  const handleFinal = formDatos.handleSubmit(async (dataDatos) => {
    if (!step1Data || !planData) return

    setSubmitting(true)
    setServerError(null)

    try {
      const res = await autoRegistroUseCase.execute({
        nombre: step1Data.nombre,
        correo: step1Data.correo || undefined,
        // La sucursal no se pide en el registro: su nombre se deriva del nombre del
        // gimnasio (un solo local, el caso mayoritario) y la dirección viene del Paso 1.
        // El backend exige nombreSucursal (@NotBlank); lo rellenamos aquí.
        nombreSucursal: step1Data.nombre,
        direccionSucursal: step1Data.direccion || undefined,
        idPlan: planData.idPlan,
        usuarioPrincipal: {
          ci: dataDatos.ci,
          nombre: dataDatos.nombre,
          correo: dataDatos.correo,
          password: dataDatos.password,
        },
      })

      // qrToken de la respuesta no se muestra aquí; el owner lo genera después desde /admin/imprimir-qr
      void res
      setResultado({ nombreGimnasio: step1Data.nombre, planCodigo: planCodigoSeleccionado })
      setRegistroCompletado(true)
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status
        const conflicto = err.response?.data?.conflicto as string | undefined

        if (status === 409) {
          if (conflicto === 'idPlan') {
            setServerError({ tipo: 'idPlan' })
            setCurrentStep(2)
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
    return <ResumenExito nombreGimnasio={resultado.nombreGimnasio} planCodigo={resultado.planCodigo} />
  }

  const isFinalStep = currentStep === 3
  const isNextDisabled =
    submitting ||
    (currentStep === 2 && step3Blocked)

  const datosError =
    serverError?.tipo === 'correo' || serverError?.tipo === 'ci'
      ? serverError
      : null

  const globalBannerError: string | null =
    serverError?.tipo === 'idPlan'
      ? 'El plan seleccionado no está disponible. Por favor elige otro.'
      : serverError?.tipo === 'rate_limit'
        ? 'Demasiados intentos. Espera unos minutos e intenta de nuevo.'
        : serverError?.tipo === 'server'
          ? 'Ocurrió un error inesperado. Por favor intenta de nuevo.'
          : null

  const handleNext = () => {
    if (currentStep === 1) { handleStep1(); return }
    if (currentStep === 2) { handlePlan(); return }
    if (currentStep === 3) { handleFinal(); return }
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
        {currentStep === 2 && (
          <Step3Plan
            form={formPlan}
            onLoadingChange={handleStep3LoadingChange}
            onPlanChange={handleStep3PlanChange}
          />
        )}
        {currentStep === 3 && (
          <Step4DatosPropios
            form={formDatos}
            serverError={datosError}
            onCheckCorreo={(correo) => autoRegistroUseCase.correoEnUso(correo)}
          />
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
            : isFinalStep
              ? 'Crear mi cuenta'
              : 'Siguiente →'
          }
        </button>
      </div>
    </div>
  )
}
