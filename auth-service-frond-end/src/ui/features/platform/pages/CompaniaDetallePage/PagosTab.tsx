import { useEffect, useState } from 'react'
import { CheckCircle, Plus } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { ConfirmDialog } from '@/ui/components/ConfirmDialog'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarPagoUseCase } from '@/application/platform/GestionarPago.usecase'
import { GestionarSuscripcionUseCase } from '@/application/platform/GestionarSuscripcion.usecase'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import type { Pago, CompaniaPlan } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { RegistrarPagoModal } from './PagosTab/RegistrarPagoModal'

const pagoUsecase = new GestionarPagoUseCase(platformRepository)
const suscripcionUsecase = new GestionarSuscripcionUseCase(platformRepository)

function EstadoPagoBadge({ estado }: { estado: Pago['estado'] }) {
  const map = {
    PENDIENTE: 'bg-yellow-500/20 text-yellow-700 border-yellow-300',
    PAGADO: 'bg-green-500/20 text-green-700 border-green-300',
    FALLIDO: 'bg-red-500/20 text-red-700 border-red-300',
  }
  return (
    <Badge variant="outline" className={`text-xs ${map[estado] ?? ''}`}>
      {estado}
    </Badge>
  )
}

interface Props { idCompania: number }

export function PagosTab({ idCompania }: Props) {
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const canWrite = user?.rol_plataforma === 'super_admin' || user?.rol_plataforma === 'soporte'

  const [pagos, setPagos] = useState<Pago[]>([])
  const [suscripcionActiva, setSuscripcionActiva] = useState<CompaniaPlan | null>(null)
  const [loading, setLoading] = useState(true)
  const [registrarOpen, setRegistrarOpen] = useState(false)
  const [confirmarPago, setConfirmarPago] = useState<Pago | null>(null)

  const load = () => {
    setLoading(true)
    Promise.all([
      pagoUsecase.getPagos(idCompania).catch(() => []),
      suscripcionUsecase.getSuscripcionActiva(idCompania).catch(() => null),
    ]).then(([p, s]) => {
      setPagos(p)
      setSuscripcionActiva(s)
    }).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [idCompania])

  const handleConfirmar = async () => {
    if (!confirmarPago) return
    try {
      const updated = await pagoUsecase.confirmarPago(confirmarPago.id)
      setPagos(prev => prev.map(p => p.id === updated.id ? updated : p))
      toast.success('Pago confirmado')
      setConfirmarPago(null)
    } catch {
      toast.error('Error al confirmar el pago')
    }
  }

  if (loading) return (
    <div className="space-y-3 p-4">
      {[1, 2].map(i => <Skeleton key={i} className="h-12 w-full rounded-lg" />)}
    </div>
  )

  return (
    <div className="p-4 space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="text-sm font-semibold text-slate-700">Historial de pagos</h3>
        {canWrite && suscripcionActiva && (
          <Button size="sm" variant="outline" onClick={() => setRegistrarOpen(true)}>
            <Plus size={13} className="mr-1.5" /> Registrar pago
          </Button>
        )}
      </div>

      {pagos.length === 0 ? (
        <p className="text-sm text-slate-400">Sin pagos registrados.</p>
      ) : (
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full text-xs">
            <thead>
              <tr className="bg-slate-50 text-slate-500 uppercase tracking-wide">
                <th className="px-3 py-2 text-left font-medium">ID</th>
                <th className="px-3 py-2 text-left font-medium">Monto</th>
                <th className="px-3 py-2 text-left font-medium">Fecha</th>
                <th className="px-3 py-2 text-left font-medium">Método</th>
                <th className="px-3 py-2 text-left font-medium">Tipo</th>
                <th className="px-3 py-2 text-left font-medium">Estado</th>
                {canWrite && <th className="px-3 py-2 text-right font-medium">Acción</th>}
              </tr>
            </thead>
            <tbody>
              {pagos.map((p, i) => (
                <tr key={p.id} className={i % 2 === 0 ? 'bg-white' : 'bg-slate-50/50'}>
                  <td className="px-3 py-2 font-mono">{p.id}</td>
                  <td className="px-3 py-2 font-medium">${p.monto.toFixed(2)}</td>
                  <td className="px-3 py-2">{p.fechaPago?.slice(0, 10)}</td>
                  <td className="px-3 py-2 capitalize">{p.metodoPago}</td>
                  <td className="px-3 py-2">{p.tipoPago}</td>
                  <td className="px-3 py-2"><EstadoPagoBadge estado={p.estado} /></td>
                  {canWrite && (
                    <td className="px-3 py-2 text-right">
                      {p.estado === 'PENDIENTE' && (
                        <Button
                          variant="ghost" size="icon" className="h-6 w-6 text-green-600 hover:text-green-800"
                          onClick={() => setConfirmarPago(p)}
                          title="Confirmar pago"
                        >
                          <CheckCircle size={12} />
                        </Button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {suscripcionActiva && (
        <RegistrarPagoModal
          open={registrarOpen}
          idCompaniaPlan={suscripcionActiva.id}
          onClose={() => setRegistrarOpen(false)}
          onCreated={pago => { setPagos(prev => [pago, ...prev]); setRegistrarOpen(false) }}
        />
      )}
      <ConfirmDialog
        open={confirmarPago !== null}
        title="¿Confirmar este pago?"
        description="El estado cambiará a PAGADO. Esta acción no se puede deshacer."
        destructive={false}
        onConfirm={handleConfirmar}
        onCancel={() => setConfirmarPago(null)}
      />
    </div>
  )
}
