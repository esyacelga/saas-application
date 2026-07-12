import type { UseFormReturn } from 'react-hook-form'
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { CreditCard, Check, Loader2 } from 'lucide-react'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import type { Plan } from '@/domain/platform/entities/Plan.entity'
import type { WizardStep3Form } from '../../../schemas/registrar-gym-wizard.schema'

interface Props {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  form: UseFormReturn<WizardStep3Form, any, any>
  yaUsoTrial?: boolean
}

export function Step3Plan({ form, yaUsoTrial = false }: Props) {
  const { t } = useTranslation()
  const { setValue, watch, formState: { errors } } = form
  const idPlanSeleccionado = watch('idPlan')

  const [planes, setPlanes] = useState<Plan[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    platformRepository.getPlanes()
      .then(p => {
        const activos = p.filter(pl => pl.activo)
        setPlanes(activos)

        // Auto-seleccionar Trial si existe y no fue ya usado
        if (!yaUsoTrial) {
          const trial = activos.find(pl => pl.codigo === 'TRIAL')
          if (trial && !watch('idPlan')) {
            setValue('idPlan', trial.id, { shouldValidate: false })
          }
        }
      })
      .catch(() => {
        toast.error(t('step3.errorCargaPlanes'))
      })
      .finally(() => setLoading(false))
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3 pb-2" style={{ borderBottom: '1px solid var(--page-border)' }}>
        <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
          style={{ background: 'var(--color-warning-subtle, #fff7ed)', color: 'var(--color-warning, #f97316)' }}>
          <CreditCard size={18} />
        </div>
        <div>
          <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Plan de suscripción</p>
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>Selecciona el plan inicial del gimnasio. Puede cambiarse luego.</p>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-10 gap-2" style={{ color: 'var(--page-muted)' }}>
          <Loader2 size={16} className="animate-spin" />
          <span className="text-sm">Cargando planes…</span>
        </div>
      ) : (
        <div className="space-y-3">
          {planes.map(plan => {
            const seleccionado = idPlanSeleccionado === plan.id
            const esTrial = plan.codigo === 'TRIAL'
            const esGratis = plan.precioMensual === 0
            return (
              <button
                key={plan.id}
                type="button"
                onClick={() => setValue('idPlan', plan.id, { shouldValidate: true })}
                className="w-full text-left rounded-xl p-4 transition-all duration-150 cursor-pointer"
                style={{
                  background: seleccionado ? 'var(--color-warning-subtle, #fff7ed)' : 'var(--page-surface)',
                  border: seleccionado
                    ? '2px solid var(--color-warning, #f97316)'
                    : '2px solid var(--page-border)',
                  outline: 'none',
                }}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>{plan.nombre}</span>
                      {seleccionado && (
                        <span className="flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full font-medium"
                          style={{ background: 'var(--color-warning, #f97316)', color: '#fff' }}>
                          <Check size={10} /> Seleccionado
                        </span>
                      )}
                      {esTrial && (
                        <span
                          className="text-xs px-2 py-0.5 rounded-full font-semibold"
                          style={{
                            background: 'var(--color-warning-subtle, #fff7ed)',
                            color: 'var(--color-warning, #f97316)',
                            border: '1px solid rgba(249,115,22,0.3)',
                          }}
                        >
                          {t('step3.trialBadge')}
                        </span>
                      )}
                    </div>
                    {plan.descripcion && (
                      <p className="text-xs mt-1 leading-relaxed" style={{ color: 'var(--page-muted)' }}>
                        {plan.descripcion}
                      </p>
                    )}
                    {plan.caracteristicas && plan.caracteristicas.length > 0 && (
                      <div className="flex flex-wrap gap-1 mt-2">
                        {plan.caracteristicas.map(c => (
                          <span key={c.id} className="text-xs px-2 py-0.5 rounded-full"
                            style={{ background: 'var(--page-bg)', color: 'var(--page-muted)', border: '1px solid var(--page-border)' }}>
                            {c.nombre}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                  <div className="flex-shrink-0 text-right">
                    {esGratis ? (
                      <span className="text-lg font-bold" style={{ color: '#22c55e' }}>Gratis</span>
                    ) : (
                      <>
                        <span className="text-lg font-bold" style={{ color: seleccionado ? 'var(--color-warning, #f97316)' : 'var(--page-text)' }}>
                          ${plan.precioMensual.toFixed(2)}
                        </span>
                        <p className="text-xs" style={{ color: 'var(--page-muted)' }}>/mes</p>
                      </>
                    )}
                  </div>
                </div>
              </button>
            )
          })}

          {/* Link "Prefiero el plan Free" — solo si Trial está seleccionado */}
          {(() => {
            const trialPlan = planes.find(p => p.codigo === 'TRIAL')
            const freePlan = planes.find(p => p.codigo === 'FREE')
            if (!trialPlan || !freePlan || idPlanSeleccionado !== trialPlan.id) return null
            return (
              <button
                type="button"
                onClick={() => setValue('idPlan', freePlan.id, { shouldValidate: true })}
                className="w-full text-center text-xs underline mt-1"
                style={{ color: 'var(--page-muted)' }}
              >
                {t('step3.prefiereFree')}
              </button>
            )
          })()}
        </div>
      )}

      {errors.idPlan && (
        <p className="text-xs" style={{ color: '#ef4444' }}>{errors.idPlan.message}</p>
      )}
    </div>
  )
}
