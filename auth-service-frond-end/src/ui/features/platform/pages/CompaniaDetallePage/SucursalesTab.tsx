import { useEffect, useState } from 'react'
import { Plus, Pencil, QrCode } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarSucursalUseCase } from '@/application/platform/GestionarSucursal.usecase'
import { QrTokenDisplay } from '../../components/QrTokenDisplay'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import type { Sucursal } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { CrearSucursalModal } from './SucursalesTab/CrearSucursalModal'
import { EditarSucursalModal } from './SucursalesTab/EditarSucursalModal'
import { RenovarQrModal } from './SucursalesTab/RenovarQrModal'

const usecase = new GestionarSucursalUseCase(platformRepository)

interface Props { idCompania: number }

export function SucursalesTab({ idCompania }: Props) {
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const isSuperAdmin = user?.rol_plataforma === 'super_admin'
  const canEdit = isSuperAdmin || user?.rol_plataforma === 'soporte'

  const [sucursales, setSucursales] = useState<Sucursal[]>([])
  const [loading, setLoading] = useState(true)
  const [crearOpen, setCrearOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<Sucursal | null>(null)
  const [qrTarget, setQrTarget] = useState<Sucursal | null>(null)

  const load = () => {
    setLoading(true)
    usecase.getSucursales(idCompania)
      .then(setSucursales)
      .catch(() => toast.error('Error al cargar sucursales'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [idCompania])

  if (loading) return (
    <div className="space-y-3 p-4">
      {[1, 2].map(i => <Skeleton key={i} className="h-12 w-full rounded-lg" />)}
    </div>
  )

  return (
    <div className="p-4 space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="text-sm font-semibold text-slate-700">Sucursales</h3>
        {canEdit && (
          <Button size="sm" variant="outline" onClick={() => setCrearOpen(true)}>
            <Plus size={13} className="mr-1.5" /> Nueva sucursal
          </Button>
        )}
      </div>

      {sucursales.length === 0 ? (
        <p className="text-sm text-slate-400">Sin sucursales.</p>
      ) : (
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full text-xs">
            <thead>
              <tr className="bg-slate-50 text-slate-500 uppercase tracking-wide">
                <th className="px-3 py-2 text-left font-medium">Nombre</th>
                <th className="px-3 py-2 text-left font-medium">Dirección</th>
                <th className="px-3 py-2 text-left font-medium">Tipo</th>
                <th className="px-3 py-2 text-left font-medium">QR Token</th>
                <th className="px-3 py-2 text-left font-medium">Estado</th>
                <th className="px-3 py-2 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {sucursales.map((s, i) => (
                <tr key={s.id} className={i % 2 === 0 ? 'bg-white' : 'bg-slate-50/50'}>
                  <td className="px-3 py-2 font-medium">{s.nombre}</td>
                  <td className="px-3 py-2 text-slate-500">{s.direccion || '—'}</td>
                  <td className="px-3 py-2">
                    {s.esPrincipal && <Badge variant="outline" className="text-xs bg-blue-50 text-blue-700 border-blue-300">Principal</Badge>}
                  </td>
                  <td className="px-3 py-2">
                    {s.qrToken
                      ? <QrTokenDisplay token={`${import.meta.env.VITE_CLIENT_APP_URL}/login?qr=${s.qrToken}`} />
                      : <span className="text-slate-400">—</span>}
                  </td>
                  <td className="px-3 py-2">
                    <Badge variant={s.activo ? 'default' : 'secondary'} className={s.activo ? 'bg-green-500/20 text-green-700 border-green-300' : ''}>
                      {s.activo ? 'Activo' : 'Inactivo'}
                    </Badge>
                  </td>
                  <td className="px-3 py-2 text-right">
                    <div className="flex justify-end gap-1">
                      {canEdit && (
                        <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setEditTarget(s)} title="Editar">
                          <Pencil size={11} />
                        </Button>
                      )}
                      {isSuperAdmin && (
                        <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setQrTarget(s)} title="Renovar QR">
                          <QrCode size={11} />
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <CrearSucursalModal
        open={crearOpen}
        idCompania={idCompania}
        onClose={() => setCrearOpen(false)}
        onCreated={s => { setSucursales(prev => [...prev, s]); setCrearOpen(false) }}
      />
      <EditarSucursalModal
        open={editTarget !== null}
        sucursal={editTarget}
        onClose={() => setEditTarget(null)}
        onUpdated={updated => setSucursales(prev => prev.map(s => s.id === updated.id ? updated : s))}
      />
      <RenovarQrModal
        open={qrTarget !== null}
        sucursal={qrTarget}
        onClose={() => setQrTarget(null)}
        onRenewed={(token, expira) => {
          setSucursales(prev => prev.map(s => s.id === qrTarget?.id ? { ...s, qrToken: token, qrTokenExpira: expira } : s))
        }}
      />
    </div>
  )
}
