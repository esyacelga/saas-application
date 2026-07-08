import type { UseFormReturn } from 'react-hook-form'
import { useState, useEffect, useCallback } from 'react'
import { Check, AlertCircle, RefreshCw } from 'lucide-react'
import { PulsingDots } from '@/ui/components/PulsingDots'
import platformPublicApi from '@/infrastructure/http/platform/axios-platform-public.instance'
import type { Plan } from '@/domain/platform/entities/Plan.entity'
import type { WizardStep3Form } from '@/ui/features/platform/schemas/registrar-gym-wizard.schema'

interface Props {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  form: UseFormReturn<WizardStep3Form, any, any>
  onLoadingChange: (loading: boolean) => void
}

export function Step3Plan({ form, onLoadingChange }: Props) {
  const { setValue, watch, formState: { errors } } = form
  const idPlanSeleccionado = watch('idPlan')

  const [planes, setPlanes] = useState<Plan[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  const loadPlanes = useCallback(async () => {
    setLoading(true)
    setError(false)
    try {
      const res = await platformPublicApi.get<Plan[]>('/planes/publicos')
      const activos = res.data.filter(p => p.activo)
      setPlanes(activos)
      if (activos.length === 1 && activos[0].precioMensual === 0) {
        setValue('idPlan', activos[0].id, { shouldValidate: true })
      }
    } catch {
      setError(true)
    } finally {
      setLoading(false)
    }
  }, [setValue])

  useEffect(() => {
    loadPlanes()
  }, [loadPlanes])

  useEffect(() => {
    onLoadingChange(loading || error)
  }, [loading, error, onLoadingChange])

  return (
    <div className="space-y-4">
      <div>
        <p className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Elige tu plan</p>
        <p className="text-xs mt-0.5" style={{ color: 'var(--page-muted)' }}>
          Selecciona el plan que mejor se adapte a tu gimnasio. Puedes cambiarlo cuando quieras.
        </p>
      </div>

      {loading && (
        <div className="space-y-3">
          {[1, 2].map(i => (
            <div
              key={i}
              className="rounded-xl p-4 animate-pulse"
              style={{ background: 'var(--page-surface)', border: '2px solid var(--page-border)', height: '80px' }}
            />
          ))}
        </div>
      )}

      {!loading && error && (
        <div
          className="rounded-lg p-4 flex flex-col items-center gap-3 text-center"
          style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}
        >
          <AlertCircle size={20} style={{ color: '#f97316' }} />
          <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
            No pudimos cargar los planes disponibles.
          </p>
          <button
            type="button"
            onClick={loadPlanes}
            className="inline-flex items-center gap-2 text-xs font-medium px-3 py-1.5 rounded-lg transition-colors"
            style={{ background: '#f97316', color: '#fff' }}
          >
            <RefreshCw size={12} />
            Reintentar
          </button>
        </div>
      )}

      {!loading && !error && planes.length === 0 && (
        <div
          className="rounded-lg p-4 text-center"
          style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}
        >
          <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
            En este momento no hay planes gratuitos disponibles.{' '}
            <a
              href="https://wa.me/593958832436"
              target="_blank"
              rel="noopener noreferrer"
              className="font-medium"
              style={{ color: '#f97316' }}
            >
              Contáctanos
            </a>
          </p>
        </div>
      )}

      {!loading && !error && planes.length > 0 && (
        <>
          {planes.length === 1 && planes[0].precioMensual === 0 && (
            <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
              Este es el único plan disponible en este momento.
            </p>
          )}

          <div className="space-y-3">
            {planes.map(plan => {
              const esGratuito = plan.precioMensual === 0
              const esDePago = !esGratuito
              const seleccionado = idPlanSeleccionado === plan.id

              return (
                <button
                  key={plan.id}
                  type="button"
                  disabled={esDePago}
                  onClick={() => !esDePago && setValue('idPlan', plan.id, { shouldValidate: true })}
                  className="w-full text-left rounded-xl p-4 transition-all duration-150"
                  style={{
                    background: seleccionado ? 'var(--color-warning-subtle, #fff7ed)' : 'var(--page-surface)',
                    border: seleccionado
                      ? '2px solid #f97316'
                      : '2px solid var(--page-border)',
                    opacity: esDePago ? 0.5 : 1,
                    cursor: esDePago ? 'not-allowed' : 'pointer',
                    outline: 'none',
                  }}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
                          {plan.nombre}
                        </span>
                        {esGratuito && (
                          <span className="text-xs px-1.5 py-0.5 rounded-full font-medium"
                            style={{ background: '#f0fdf4', color: '#16a34a', border: '1px solid #bbf7d0' }}>
                            Gratis
                          </span>
                        )}
                        {esDePago && (
                          <span className="text-xs px-1.5 py-0.5 rounded-full font-medium"
                            style={{ background: 'var(--page-bg)', color: 'var(--page-muted)', border: '1px solid var(--page-border)' }}>
                            Próximamente
                          </span>
                        )}
                        {seleccionado && (
                          <span className="flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full font-medium"
                            style={{ background: '#f97316', color: '#fff' }}>
                            <Check size={10} />
                            Seleccionado
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
                      <span className="text-lg font-bold"
                        style={{ color: seleccionado ? '#f97316' : 'var(--page-text)' }}>
                        {esGratuito ? 'Gratis' : `$${plan.precioMensual.toFixed(2)}`}
                      </span>
                      {!esGratuito && (
                        <p className="text-xs" style={{ color: 'var(--page-muted)' }}>/mes</p>
                      )}
                    </div>
                  </div>
                </button>
              )
            })}
          </div>

          {planes.some(p => p.precioMensual > 0) && (
            <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
              Los planes de pago estarán disponibles próximamente.
            </p>
          )}
        </>
      )}

      {loading && (
        <div className="flex items-center justify-center gap-2 py-2">
          <PulsingDots size="sm" />
        </div>
      )}

      {errors.idPlan && (
        <p className="text-xs" style={{ color: '#ef4444' }}>{errors.idPlan.message}</p>
      )}
    </div>
  )
}
