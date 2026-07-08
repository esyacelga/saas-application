import { useEffect, useState } from 'react'
import { RefreshCw, ArrowUpCircle, ArrowDownCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarSuscripcionUseCase } from '@/application/platform/GestionarSuscripcion.usecase'
import { EstadoPlanBadge } from '../../components/EstadoPlanBadge'
import { usePlatformStore } from '@/infrastructure/store/platform/platform.store'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import type { CompaniaPlan } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { RenovarSuscripcionModal } from './SuscripcionTab/RenovarSuscripcionModal'
import { UpgradePlanModal } from './SuscripcionTab/UpgradePlanModal'
import { DowngradePlanModal } from './SuscripcionTab/DowngradePlanModal'

const usecase = new GestionarSuscripcionUseCase(platformRepository)

interface Props { idCompania: number }

export function SuscripcionTab({ idCompania }: Props) {
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const isSuperAdmin = user?.rol_plataforma === 'super_admin'

  const { planes } = usePlatformStore()

  const [activa, setActiva] = useState<CompaniaPlan | null>(null)
  const [historial, setHistorial] = useState<CompaniaPlan[]>([])
  const [loading, setLoading] = useState(true)
  const [renovarOpen, setRenovarOpen] = useState(false)
  const [upgradeOpen, setUpgradeOpen] = useState(false)
  const [downgradeOpen, setDowngradeOpen] = useState(false)

  const loadData = () => {
    setLoading(true)
    Promise.all([
      usecase.getSuscripcionActiva(idCompania).catch(() => null),
      usecase.getHistorialSuscripcion(idCompania).catch(() => []),
    ]).then(([a, h]) => {
      setActiva(a)
      setHistorial(h)
    }).finally(() => setLoading(false))
  }

  useEffect(() => { loadData() }, [idCompania])

  const planActivo = planes.find(p => p.id === activa?.idPlan)
  const diasTotalesPeriodo = activa
    ? Math.round((new Date(activa.fechaFin).getTime() - new Date(activa.fechaInicio).getTime()) / (1000 * 60 * 60 * 24))
    : 0
  const progresoRestante = diasTotalesPeriodo > 0 ? Math.min(100, (activa!.diasRestantes / diasTotalesPeriodo) * 100) : 0

  if (loading) return (
    <div className="space-y-3 p-4">
      {[1, 2].map(i => <Skeleton key={i} className="h-20 w-full rounded-lg" />)}
    </div>
  )

  return (
    <div className="p-4 space-y-6">
      {/* Suscripción activa */}
      <div>
        <h3 className="text-sm font-semibold text-slate-700 mb-3">Suscripción activa</h3>
        {!activa ? (
          <p className="text-sm text-slate-400">No hay suscripción activa.</p>
        ) : (
          <div className="border rounded-lg p-4 space-y-3">
            <div className="flex items-start justify-between">
              <div>
                <p className="font-medium text-slate-900">{planActivo?.nombre ?? `Plan #${activa.idPlan}`}</p>
                <p className="text-sm text-slate-500">${planActivo?.precioMensual.toFixed(2)}/mes</p>
              </div>
              <div className="flex items-center gap-2">
                <EstadoPlanBadge estado={activa.estado} />
                <Badge variant="outline" className="text-xs">{activa.tipoCambio}</Badge>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <p className="text-slate-500 text-xs">Inicio</p>
                <p className="font-medium">{activa.fechaInicio?.slice(0, 10)}</p>
              </div>
              <div>
                <p className="text-slate-500 text-xs">Fin</p>
                <p className="font-medium">{activa.fechaFin?.slice(0, 10)}</p>
              </div>
              <div>
                <p className="text-slate-500 text-xs">Días restantes</p>
                <p className="font-bold text-lg">{activa.diasRestantes}</p>
              </div>
              <div>
                <p className="text-slate-500 text-xs">Días de gracia</p>
                <p className="font-medium">{activa.diasGracia}</p>
              </div>
            </div>
            <div>
              <div className="flex justify-between text-xs text-slate-400 mb-1">
                <span>Período restante</span>
                <span>{activa.diasRestantes}d / {diasTotalesPeriodo}d</span>
              </div>
              <Progress value={progresoRestante} className="h-2" />
            </div>
            {isSuperAdmin && (
              <div className="flex gap-2 pt-1">
                <Button variant="outline" size="sm" onClick={() => setRenovarOpen(true)}>
                  <RefreshCw size={13} className="mr-1.5" /> Renovar
                </Button>
                <Button variant="outline" size="sm" onClick={() => setUpgradeOpen(true)}>
                  <ArrowUpCircle size={13} className="mr-1.5" /> Upgrade
                </Button>
                <Button variant="outline" size="sm" onClick={() => setDowngradeOpen(true)}>
                  <ArrowDownCircle size={13} className="mr-1.5" /> Downgrade
                </Button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Historial */}
      <div>
        <h3 className="text-sm font-semibold text-slate-700 mb-3">Historial de suscripciones</h3>
        {historial.length === 0 ? (
          <p className="text-sm text-slate-400">Sin historial.</p>
        ) : (
          <div className="border rounded-lg overflow-hidden">
            <table className="w-full text-xs">
              <thead>
                <tr className="bg-slate-50 text-slate-500 uppercase tracking-wide">
                  <th className="px-3 py-2 text-left font-medium">ID</th>
                  <th className="px-3 py-2 text-left font-medium">Plan</th>
                  <th className="px-3 py-2 text-left font-medium">Estado</th>
                  <th className="px-3 py-2 text-left font-medium">Inicio</th>
                  <th className="px-3 py-2 text-left font-medium">Fin</th>
                  <th className="px-3 py-2 text-left font-medium">Tipo</th>
                </tr>
              </thead>
              <tbody>
                {historial.map((h, i) => {
                  const plan = planes.find(p => p.id === h.idPlan)
                  return (
                    <tr key={h.id} className={i % 2 === 0 ? 'bg-white' : 'bg-slate-50/50'}>
                      <td className="px-3 py-2 font-mono">{h.id}</td>
                      <td className="px-3 py-2">{plan?.nombre ?? `Plan #${h.idPlan}`}</td>
                      <td className="px-3 py-2"><EstadoPlanBadge estado={h.estado} /></td>
                      <td className="px-3 py-2">{h.fechaInicio?.slice(0, 10)}</td>
                      <td className="px-3 py-2">{h.fechaFin?.slice(0, 10)}</td>
                      <td className="px-3 py-2"><Badge variant="outline" className="text-xs">{h.tipoCambio}</Badge></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <RenovarSuscripcionModal
        open={renovarOpen}
        idCompania={idCompania}
        suscripcionActiva={activa}
        onClose={() => setRenovarOpen(false)}
        onRenewed={loadData}
      />
      <UpgradePlanModal
        open={upgradeOpen}
        idCompania={idCompania}
        suscripcionActiva={activa}
        onClose={() => setUpgradeOpen(false)}
        onUpgraded={loadData}
      />
      <DowngradePlanModal
        open={downgradeOpen}
        idCompania={idCompania}
        suscripcionActiva={activa}
        onClose={() => setDowngradeOpen(false)}
        onDowngraded={loadData}
      />
    </div>
  )
}
