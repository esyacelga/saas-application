import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, UserRound } from 'lucide-react'
import { toast } from 'sonner'
import { Skeleton } from '@/components/ui/skeleton'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Persona } from '@/infrastructure/http/auth/auth.dto'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { DatosPersonalesTab } from './PersonaDetallePage/DatosPersonalesTab'
import { GimnasiosTab } from './PersonaDetallePage/GimnasiosTab'
import { UsuarioPlataformaTab } from './PersonaDetallePage/UsuarioPlataformaTab'
import { UsuarioStaffTab } from './PersonaDetallePage/UsuarioStaffTab'
import { UsuarioAppTab } from './PersonaDetallePage/UsuarioAppTab'

type Tab = 'datos' | 'gimnasios' | 'plataforma' | 'staff' | 'app'

const TABS: { key: Tab; label: string }[] = [
  { key: 'datos',      label: 'Datos personales' },
  { key: 'gimnasios',  label: 'Gimnasios' },
  { key: 'plataforma', label: 'Usuario plataforma' },
  { key: 'staff',      label: 'Usuario staff' },
  { key: 'app',        label: 'Usuario app' },
]

export function PersonaDetallePage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const isViewer = user?.rol_plataforma === 'viewer'

  const [persona, setPersona] = useState<Persona | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<Tab>('datos')

  useEffect(() => {
    if (!id) return
    setLoading(true)
    authRepository.getPersonaById(Number(id))
      .then(setPersona)
      .catch(() => {
        toast.error('No se pudo cargar la persona')
        navigate('/platform/personas')
      })
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return (
    <div className="p-6 space-y-4">
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-32 w-full" />
    </div>
  )

  if (!persona) return null

  const tabStyle = (t: Tab) => t === activeTab
    ? {
        color: '#f97316',
        background: 'var(--page-surface)',
        borderTop: '1px solid var(--page-border)',
        borderLeft: '1px solid var(--page-border)',
        borderRight: '1px solid var(--page-border)',
        borderBottom: '1px solid var(--page-surface)',
        marginBottom: '-1px',
      }
    : { color: 'var(--page-muted)' }

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      {/* Header */}
      <div
        className="flex items-start justify-between px-6 py-5 sticky top-0 z-10 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--page-border)', background: 'var(--page-bg)' }}
      >
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/platform/personas')}
            className="p-1.5 rounded-lg transition-colors hover:bg-orange-500/10"
            style={{ color: 'var(--page-muted)' }}
          >
            <ArrowLeft size={20} />
          </button>
          <div className="flex items-center gap-3">
            {persona.foto_url ? (
              <img src={persona.foto_url} alt={persona.nombre} className="w-14 h-14 rounded-full object-cover border-2 border-orange-200" />
            ) : (
              <div className="w-14 h-14 rounded-full flex items-center justify-center" style={{ background: 'var(--page-muted)', opacity: 0.3 }}>
                <UserRound size={28} />
              </div>
            )}
            <div>
              <h1 className="text-xl font-bold leading-tight" style={{ color: 'var(--page-text)' }}>{persona.nombre}</h1>
              <p className="text-sm" style={{ color: 'var(--page-muted)' }}>CI: {persona.ci}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs nav */}
      <div
        className="flex gap-0.5 px-6 pt-4"
        style={{ borderBottom: '1px solid var(--page-border)', background: 'var(--page-bg)' }}
      >
        {TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className="px-5 py-2.5 text-sm font-medium rounded-t-lg transition-colors"
            style={tabStyle(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-6" style={{ background: 'var(--page-bg)' }}>
        {activeTab === 'datos' && (
          <DatosPersonalesTab
            persona={persona}
            readonly={isViewer}
            onActualizada={setPersona}
          />
        )}
        {activeTab === 'gimnasios' && (
          <GimnasiosTab idPersona={persona.id} readonly={isViewer} />
        )}
        {activeTab === 'plataforma' && (
          <UsuarioPlataformaTab idPersona={persona.id} readonly={isViewer} />
        )}
        {activeTab === 'staff' && (
          <UsuarioStaffTab idPersona={persona.id} readonly={isViewer} />
        )}
        {activeTab === 'app' && (
          <UsuarioAppTab idPersona={persona.id} readonly={isViewer} />
        )}
      </div>
    </div>
  )
}
