import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { PageHeader } from '@/ui/components/PageHeader'
import { Button } from 'primereact/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GetCaracteristicasUseCase } from '@/application/platform/GetCaracteristicas.usecase'
import type { Caracteristica } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { CrearCaracteristicaModal } from './CaracteristicasPage/CrearCaracteristicaModal'

const usecase = new GetCaracteristicasUseCase(platformRepository)

export function CaracteristicasPage() {
  const { t } = useTranslation()
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const isSuperAdmin = user?.rol_plataforma === 'super_admin'

  const [items, setItems] = useState<Caracteristica[]>([])
  const [loading, setLoading] = useState(true)
  const [crearOpen, setCrearOpen] = useState(false)

  useEffect(() => {
    usecase.execute()
      .then(setItems)
      .catch(() => toast.error(t('platform.caracteristicas.loadError')))
      .finally(() => setLoading(false))
  }, [])

  const byModulo = items.reduce<Record<string, Caracteristica[]>>((acc, c) => {
    if (!acc[c.modulo]) acc[c.modulo] = []
    acc[c.modulo].push(c)
    return acc
  }, {})

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <PageHeader
        title="Características del sistema"
        description="Catálogo global de características asignables a los planes"
        action={
          isSuperAdmin ? (
            <Button
              label="Nueva característica"
              icon="pi pi-plus"
              severity="warning"
              size="small"
              onClick={() => setCrearOpen(true)}
            />
          ) : undefined
        }
      />

      {/* Stats bar */}
      {!loading && items.length > 0 && (
        <div className="flex items-center gap-6 px-6 py-3 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{items.length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>características totales</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{Object.keys(byModulo).length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>módulos</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{items.filter(c => c.activo).length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>activas</span>
          </div>
        </div>
      )}

      <div className="p-6 flex flex-col flex-1 overflow-auto">
        {loading ? (
          <div className="space-y-2">
            {[1, 2, 3, 4].map(i => <Skeleton key={i} className="h-10 w-full rounded-lg" />)}
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center gap-3">
            <div className="w-16 h-16 rounded-full flex items-center justify-center" style={{ background: 'var(--page-surface)' }}>
              <i className="pi pi-list" style={{ fontSize: '1.5rem', color: 'var(--page-muted)' }} />
            </div>
            <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>{t('platform.caracteristicas.emptyTitle')}</p>
            {isSuperAdmin && <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('platform.caracteristicas.emptyHint')}</p>}
          </div>
        ) : (
          <div className="rounded-lg overflow-hidden" style={{ border: '1px solid var(--page-border)' }}>
            <table className="w-full">
              <thead>
                <tr style={{ background: 'var(--page-surface)', borderBottom: '1px solid var(--page-border)' }}>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Código</th>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Nombre</th>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Módulo</th>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Estado</th>
                </tr>
              </thead>
              <tbody>
                {items.map((c, i) => (
                  <tr key={c.id} style={{
                    background: i % 2 === 0 ? 'var(--page-surface)' : 'var(--page-bg)',
                    borderBottom: '1px solid var(--page-border)',
                    fontSize: '0.64rem',
                    lineHeight: '1.2',
                  }}>
                    <td className="px-4 py-1.5">
                      <code
                        className="font-mono px-1.5 py-0.5 rounded"
                        style={{ fontSize: '0.6rem', background: 'var(--page-bg)', color: 'var(--page-muted)', border: '1px solid var(--page-border)' }}
                      >
                        {c.codigo}
                      </code>
                    </td>
                    <td className="px-4 py-1.5 font-medium" style={{ color: 'var(--page-text)' }}>{c.nombre}</td>
                    <td className="px-4 py-1.5 capitalize" style={{ color: 'var(--page-muted)' }}>{c.modulo}</td>
                    <td className="px-4 py-1.5">
                      <span
                        className="px-1.5 py-0.5 rounded font-semibold"
                        style={{
                          fontSize: '0.55rem',
                          background: c.activo ? 'rgba(34,197,94,0.15)' : 'var(--page-border)',
                          color: c.activo ? '#16a34a' : 'var(--page-muted)',
                          border: `1px solid ${c.activo ? 'rgba(34,197,94,0.3)' : 'var(--page-border)'}`,
                        }}
                      >
                        {c.activo ? 'Activo' : 'Inactivo'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <CrearCaracteristicaModal
        open={crearOpen}
        onClose={() => setCrearOpen(false)}
        onCreated={c => setItems(prev => [...prev, c])}
      />
    </div>
  )
}
