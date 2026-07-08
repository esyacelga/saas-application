import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { PageHeader } from '@/ui/components/PageHeader'
import { ConfirmDialog } from '@/ui/components/ConfirmDialog'
import { Button } from 'primereact/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GetPlanesUseCase } from '@/application/platform/GetPlanes.usecase'
import { GestionarPlanUseCase } from '@/application/platform/GestionarPlan.usecase'
import type { Plan } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { CrearPlanModal } from './PlanesPage/CrearPlanModal'
import { EditarPlanModal } from './PlanesPage/EditarPlanModal'
import { AsignarCaracteristicasModal } from './PlanesPage/AsignarCaracteristicasModal'

const getPlanesUseCase = new GetPlanesUseCase(platformRepository)
const gestionarPlanUseCase = new GestionarPlanUseCase(platformRepository)

export function PlanesPage() {
  const { t } = useTranslation()
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const isSuperAdmin = user?.rol_plataforma === 'super_admin'

  const [planes, setPlanes] = useState<Plan[]>([])
  const [loading, setLoading] = useState(true)
  const [crearOpen, setCrearOpen] = useState(false)
  const [editPlan, setEditPlan] = useState<Plan | null>(null)
  const [asignarPlan, setAsignarPlan] = useState<Plan | null>(null)
  const [desactivarPlan, setDesactivarPlan] = useState<Plan | null>(null)
  const [desactivando, setDesactivando] = useState(false)

  const loadPlanes = () => {
    setLoading(true)
    getPlanesUseCase.execute()
      .then(setPlanes)
      .catch(() => toast.error(t('platform.planes.loadError')))
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadPlanes() }, [])

  const handleDesactivar = async () => {
    if (!desactivarPlan) return
    setDesactivando(true)
    try {
      await gestionarPlanUseCase.desactivarPlan(desactivarPlan.id)
      toast.success('Plan desactivado')
      setDesactivarPlan(null)
      loadPlanes()
    } catch {
      toast.error('No se puede desactivar — puede tener suscriptores activos')
    } finally {
      setDesactivando(false)
    }
  }

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <PageHeader
        title="Planes de suscripción"
        description="Gestiona el catálogo de planes disponibles para los gimnasios"
        action={
          isSuperAdmin ? (
            <Button
              label="Nuevo plan"
              icon="pi pi-plus"
              severity="warning"
              size="small"
              onClick={() => setCrearOpen(true)}
            />
          ) : undefined
        }
      />

      {/* Stats bar */}
      {!loading && planes.length > 0 && (
        <div className="flex items-center gap-6 px-6 py-3 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{planes.length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>planes totales</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{planes.filter(p => p.activo).length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>activos</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{planes.filter(p => !p.activo).length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>inactivos</span>
          </div>
        </div>
      )}

      <div className="p-6 flex flex-col flex-1 overflow-auto">
        {loading ? (
          <div className="space-y-2">
            {[1, 2, 3].map(i => <Skeleton key={i} className="h-10 w-full rounded-lg" />)}
          </div>
        ) : planes.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center gap-3">
            <div className="w-16 h-16 rounded-full flex items-center justify-center" style={{ background: 'var(--page-surface)' }}>
              <i className="pi pi-box" style={{ fontSize: '1.5rem', color: 'var(--page-muted)' }} />
            </div>
            <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>{t('platform.planes.emptyTitle')}</p>
            {isSuperAdmin && <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('platform.planes.emptyHint')}</p>}
          </div>
        ) : (
          <div className="rounded-lg overflow-hidden" style={{ border: '1px solid var(--page-border)' }}>
            <table className="w-full">
              <thead>
                <tr style={{ background: 'var(--page-surface)', borderBottom: '1px solid var(--page-border)' }}>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Nombre</th>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Precio / mes</th>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Características</th>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Estado</th>
                  {isSuperAdmin && <th className="px-4 py-2 text-right font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Acciones</th>}
                </tr>
              </thead>
              <tbody>
                {planes.map((plan, i) => (
                  <tr key={plan.id} style={{
                    background: i % 2 === 0 ? 'var(--page-surface)' : 'var(--page-bg)',
                    borderBottom: '1px solid var(--page-border)',
                    fontSize: '0.64rem',
                    lineHeight: '1.2',
                  }}>
                    <td className="px-4 py-1.5 font-medium" style={{ color: 'var(--page-text)' }}>{plan.nombre}</td>
                    <td className="px-4 py-1.5 font-mono" style={{ color: 'var(--page-text)', fontSize: '0.6rem' }}>
                      ${plan.precioMensual.toFixed(2)}
                    </td>
                    <td className="px-4 py-1.5">
                      <div className="flex flex-wrap gap-1">
                        {plan.caracteristicas.length === 0 ? (
                          <span style={{ color: 'var(--page-muted)', fontSize: '0.6rem' }}>Ninguna</span>
                        ) : (
                          plan.caracteristicas.slice(0, 3).map(c => (
                            <span
                              key={c.id}
                              className="font-mono px-1.5 py-0.5 rounded"
                              style={{ fontSize: '0.55rem', background: 'var(--page-bg)', color: 'var(--page-muted)', border: '1px solid var(--page-border)' }}
                            >
                              {c.codigo}
                            </span>
                          ))
                        )}
                        {plan.caracteristicas.length > 3 && (
                          <span
                            className="px-1.5 py-0.5 rounded"
                            style={{ fontSize: '0.55rem', background: 'var(--page-border)', color: 'var(--page-muted)' }}
                          >
                            +{plan.caracteristicas.length - 3}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-1.5">
                      <span
                        className="px-1.5 py-0.5 rounded font-semibold"
                        style={{
                          fontSize: '0.55rem',
                          background: plan.activo ? 'rgba(34,197,94,0.15)' : 'var(--page-border)',
                          color: plan.activo ? '#16a34a' : 'var(--page-muted)',
                          border: `1px solid ${plan.activo ? 'rgba(34,197,94,0.3)' : 'var(--page-border)'}`,
                        }}
                      >
                        {plan.activo ? 'Activo' : 'Inactivo'}
                      </span>
                    </td>
                    {isSuperAdmin && (
                      <td className="px-4 py-1.5 text-right">
                        <div className="flex justify-end gap-0.5">
                          <Button
                            icon="pi pi-pencil"
                            text
                            size="small"
                            tooltip="Editar"
                            tooltipOptions={{ position: 'top' }}
                            onClick={() => setEditPlan(plan)}
                            pt={{ root: { className: 'text-orange-500 hover:text-orange-400' } }}
                          />
                          <Button
                            icon="pi pi-tags"
                            text
                            size="small"
                            tooltip="Asignar características"
                            tooltipOptions={{ position: 'top' }}
                            onClick={() => setAsignarPlan(plan)}
                            pt={{ root: { className: 'text-orange-500 hover:text-orange-400' } }}
                          />
                          {plan.activo && (
                            <Button
                              icon="pi pi-power-off"
                              text
                              size="small"
                              severity="danger"
                              tooltip="Desactivar"
                              tooltipOptions={{ position: 'top' }}
                              onClick={() => setDesactivarPlan(plan)}
                            />
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <CrearPlanModal
        open={crearOpen}
        onClose={() => setCrearOpen(false)}
        onCreated={plan => setPlanes(prev => [...prev, plan])}
      />
      <EditarPlanModal
        open={editPlan !== null}
        plan={editPlan}
        onClose={() => setEditPlan(null)}
        onUpdated={updated => setPlanes(prev => prev.map(p => p.id === updated.id ? updated : p))}
      />
      <AsignarCaracteristicasModal
        open={asignarPlan !== null}
        plan={asignarPlan}
        onClose={() => setAsignarPlan(null)}
        onUpdated={updated => setPlanes(prev => prev.map(p => p.id === updated.id ? updated : p))}
      />
      <ConfirmDialog
        open={desactivarPlan !== null}
        title="¿Desactivar este plan?"
        description="Solo es posible si no tiene suscriptores activos. Esta acción no se puede deshacer."
        destructive
        onConfirm={handleDesactivar}
        onCancel={() => setDesactivarPlan(null)}
      />
      {desactivando && null}
    </div>
  )
}
