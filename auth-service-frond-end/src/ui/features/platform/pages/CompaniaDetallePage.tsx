import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, Building2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from 'primereact/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarCompaniaUseCase } from '@/application/platform/GestionarCompania.usecase'
import { usePlatformStore } from '@/infrastructure/store/platform/platform.store'
import { EstadoPlanBadge } from '../components/EstadoPlanBadge'
import type { Compania } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { SuscripcionTab } from './CompaniaDetallePage/SuscripcionTab'
import { PagosTab } from './CompaniaDetallePage/PagosTab'
import { SucursalesTab } from './CompaniaDetallePage/SucursalesTab'
import { UsuariosTab } from './CompaniaDetallePage/UsuariosTab'
import { NotifConfigTab } from './CompaniaDetallePage/NotifConfigTab'
import { SuspenderCompaniaDialog } from './CompaniasPage/SuspenderCompaniaDialog'
import { EditarCompaniaModal } from './CompaniaDetallePage/SucursalesTab/EditarCompaniaModal'

const usecase = new GestionarCompaniaUseCase(platformRepository)

export function CompaniaDetallePage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const isSuperAdmin = user?.rol_plataforma === 'super_admin'

  const { planes, setPlanes } = usePlatformStore()

  const [compania, setCompania] = useState<Compania | null>(null)
  const [loading, setLoading] = useState(true)
  const [editarOpen, setEditarOpen] = useState(false)
  const [suspenderOpen, setSuspenderOpen] = useState(false)
  const [activeTab, setActiveTab] = useState<'suscripcion' | 'pagos' | 'sucursales' | 'usuarios' | 'notificaciones'>('suscripcion')

  useEffect(() => {
    if (!id) return
    setLoading(true)
    Promise.all([
      usecase.getCompania(Number(id)),
      planes.length === 0 ? platformRepository.getPlanes() : Promise.resolve(planes),
    ]).then(([c, p]) => {
      setCompania(c)
      if (planes.length === 0) setPlanes(p)
    }).catch(() => {
      toast.error('Error al cargar los datos')
      navigate('/platform/companias')
    }).finally(() => setLoading(false))
  }, [id])

  if (loading) return (
    <div className="p-6 space-y-4">
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-40 w-full" />
    </div>
  )

  if (!compania) return null

  const tabStyle = (tab: typeof activeTab) => activeTab === tab ? {
    color: '#f97316',
    background: 'var(--page-surface)',
    borderTop: '1px solid var(--page-border)',
    borderLeft: '1px solid var(--page-border)',
    borderRight: '1px solid var(--page-border)',
    borderBottom: '1px solid var(--page-surface)',
    marginBottom: '-1px',
  } : { color: 'var(--page-muted)' }

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      {/* Header */}
      <div className="flex items-start justify-between px-6 py-5 sticky top-0 z-10 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--page-border)', background: 'var(--page-bg)' }}
      >
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/platform/companias')}
            className="flex items-center justify-center w-8 h-8 rounded-md transition-colors"
            style={{ color: 'var(--page-muted)' }}
          >
            <ArrowLeft size={16} />
          </button>
          <div className="w-10 h-10 rounded-lg flex-shrink-0 flex items-center justify-center overflow-hidden"
            style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
            {compania.logoUrl
              ? <img src={compania.logoUrl} alt={compania.nombre} className="w-full h-full object-cover" />
              : <Building2 size={18} style={{ color: 'var(--page-muted)' }} />
            }
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-lg font-bold" style={{ color: 'var(--page-text)' }}>{compania.nombre}</h1>
              {compania.planActivo && <EstadoPlanBadge estado={compania.planActivo.estado} />}
            </div>
            <p className="text-xs font-mono mt-0.5" style={{ color: 'var(--page-muted)' }}>{compania.ruc}</p>
          </div>
        </div>
        <div className="flex gap-2">
          <Button
            icon="pi pi-pencil"
            label="Editar datos"
            size="small"
            outlined
            onClick={() => setEditarOpen(true)}
            pt={{ root: { style: { fontSize: '0.6rem', padding: '0.25rem 0.5rem', color: 'var(--page-muted)', borderColor: 'var(--page-border)' } } }}
          />
          {isSuperAdmin && compania.activo && (
            <Button
              icon="pi pi-ban"
              label="Suspender"
              size="small"
              severity="danger"
              onClick={() => setSuspenderOpen(true)}
              pt={{ root: { style: { fontSize: '0.6rem', padding: '0.25rem 0.5rem' } } }}
            />
          )}
        </div>
      </div>

      {/* Tab bar */}
      <div className="flex items-end gap-1 px-4 pt-3 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
        {(['suscripcion', 'pagos', 'sucursales', 'usuarios'] as const).map(tab => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors capitalize"
            style={tabStyle(tab)}
          >
            {tab === 'suscripcion' && <i className="pi pi-credit-card text-[0.65rem]" />}
            {tab === 'pagos' && <i className="pi pi-wallet text-[0.65rem]" />}
            {tab === 'sucursales' && <i className="pi pi-building text-[0.65rem]" />}
            {tab === 'usuarios' && <i className="pi pi-users text-[0.65rem]" />}
            {{ suscripcion: 'Suscripción', pagos: 'Pagos', sucursales: 'Sucursales', usuarios: 'Usuarios' }[tab]}
          </button>
        ))}
        {isSuperAdmin && (
          <button
            onClick={() => setActiveTab('notificaciones')}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors"
            style={tabStyle('notificaciones')}
          >
            <i className="pi pi-bell text-[0.65rem]" />
            Notificaciones
          </button>
        )}
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-auto">
        {activeTab === 'suscripcion' && <SuscripcionTab idCompania={compania.id} />}
        {activeTab === 'pagos' && <PagosTab idCompania={compania.id} />}
        {activeTab === 'sucursales' && <SucursalesTab idCompania={compania.id} />}
        {activeTab === 'usuarios' && <UsuariosTab idCompania={compania.id} />}
        {activeTab === 'notificaciones' && isSuperAdmin && <NotifConfigTab idCompania={compania.id} />}
      </div>

      <EditarCompaniaModal
        open={editarOpen}
        compania={compania}
        onClose={() => setEditarOpen(false)}
        onUpdated={updated => { setCompania(updated); setEditarOpen(false) }}
      />
      <SuspenderCompaniaDialog
        open={suspenderOpen}
        compania={compania}
        onClose={() => setSuspenderOpen(false)}
        onSuspended={() => {
          setSuspenderOpen(false)
          navigate('/platform/companias')
        }}
      />
    </div>
  )
}
