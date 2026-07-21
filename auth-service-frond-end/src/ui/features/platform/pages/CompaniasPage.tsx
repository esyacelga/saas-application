import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, Building2, ImageOff } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { PageHeader } from '@/ui/components/PageHeader'
import { Button } from 'primereact/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarCompaniaUseCase } from '@/application/platform/GestionarCompania.usecase'
import { EstadoPlanBadge } from '../components/EstadoPlanBadge'
import type { Compania } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { RegistrarGymWizard } from './RegistrarGymWizard/RegistrarGymWizard'
import { SuspenderCompaniaDialog } from './CompaniasPage/SuspenderCompaniaDialog'
import { getApiErrorCode, getApiErrorMessage } from '@/lib/api-error'

const usecase = new GestionarCompaniaUseCase(platformRepository)

function DiasRestantesBadge({ dias }: { dias: number }) {
  const cls = dias > 10
    ? 'text-green-600'
    : dias >= 1
      ? 'text-yellow-600 font-semibold'
      : 'text-red-600 font-bold'
  return <span className={`text-sm ${cls}`}>{dias}d</span>
}

export function CompaniasPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const isSuperAdmin = user?.rol_plataforma === 'super_admin'

  const [companias, setCompanias] = useState<Compania[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [registrarOpen, setRegistrarOpen] = useState(false)
  const [suspenderTarget, setSuspenderTarget] = useState<Compania | null>(null)
  const [enviandoId, setEnviandoId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    usecase.getCompanias()
      .then(setCompanias)
      .catch(() => toast.error(t('platform.companias.loadError')))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleEnviarRecordatorio = async (compania: Compania) => {
    setEnviandoId(compania.id)
    try {
      await platformRepository.enviarRecordatorioVencimiento(compania.id)
      toast.success(t('platform.companias.recordatorio.success', { nombre: compania.nombre }))
    } catch (err) {
      const codigo = getApiErrorCode(err)
      if (codigo === 'no_consentimiento') {
        toast.error(t('platform.companias.recordatorio.errorNoConsentimiento'))
      } else if (codigo === 'telefono_invalido') {
        toast.error(t('platform.companias.recordatorio.errorTelefono'))
      } else if (codigo === 'sin_suscripcion') {
        toast.error(t('platform.companias.recordatorio.errorSinSuscripcion'))
      } else {
        toast.error(getApiErrorMessage(err) || t('platform.companias.recordatorio.errorGenerico'))
      }
    } finally {
      setEnviandoId(null)
    }
  }

  const filtered = companias.filter(c =>
    c.nombre.toLowerCase().includes(search.toLowerCase()) ||
    c.ruc.includes(search)
  )

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <PageHeader
        title={t('platform.companias.title') || 'Gimnasios registrados'}
        description={`${companias.length} gimnasios en total`}
        action={
          isSuperAdmin ? (
            <Button
              label="Registrar gym"
              icon="pi pi-plus"
              severity="warning"
              size="small"
              onClick={() => setRegistrarOpen(true)}
            />
          ) : undefined
        }
      />

      {/* Stats bar */}
      {!loading && companias.length > 0 && (
        <div className="flex items-center gap-6 px-6 py-3 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{companias.length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>gimnasios totales</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{companias.filter(c => c.activo).length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>activos</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{companias.filter(c => !c.activo).length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>suspendidos</span>
          </div>
        </div>
      )}

      <div className="p-6 flex flex-col flex-1 overflow-auto">
        {/* Search */}
        <div className="mb-4 relative max-w-sm">
          <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: 'var(--input-placeholder)' }} />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder={t('platform.companias.searchPlaceholder')}
            className="pl-7 pr-3 py-1.5 text-xs rounded-md font-sans w-full focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
        </div>

        {loading ? (
          <div className="space-y-2">
            {[1, 2, 3].map(i => <Skeleton key={i} className="h-10 w-full rounded-lg" />)}
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center gap-3">
            <div className="w-16 h-16 rounded-full flex items-center justify-center" style={{ background: 'var(--page-surface)' }}>
              <Building2 size={28} style={{ color: 'var(--page-muted)' }} />
            </div>
            <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>
              {search ? t('platform.companias.noResults') : t('platform.companias.emptyTitle')}
            </p>
          </div>
        ) : (
          <div className="rounded-lg overflow-hidden" style={{ border: '1px solid var(--page-border)' }}>
            <table className="w-full">
              <thead>
                <tr style={{ background: 'var(--page-surface)', borderBottom: '1px solid var(--page-border)' }}>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Nombre</th>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>RUC</th>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Plan activo</th>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Días restantes</th>
                  <th className="px-4 py-2 text-left font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Estado</th>
                  <th className="px-4 py-2 text-right font-semibold uppercase tracking-wider" style={{ fontSize: '0.57rem', color: 'var(--page-muted)' }}>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((c, i) => (
                  <tr key={c.id} style={{
                    background: i % 2 === 0 ? 'var(--page-surface)' : 'var(--page-bg)',
                    borderBottom: '1px solid var(--page-border)',
                    fontSize: '0.64rem',
                    lineHeight: '1.2',
                  }}>
                    <td className="px-4 py-1.5">
                      <div className="flex items-center gap-2">
                        <div className="w-7 h-7 rounded-md flex-shrink-0 overflow-hidden flex items-center justify-center"
                          style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
                          {c.logoUrl
                            ? <img src={c.logoUrl} alt={c.nombre} className="w-full h-full object-cover" />
                            : <ImageOff size={13} style={{ color: 'var(--page-muted)' }} />
                          }
                        </div>
                        <span className="font-medium" style={{ color: 'var(--page-text)' }}>{c.nombre}</span>
                      </div>
                    </td>
                    <td className="px-4 py-1.5 font-mono" style={{ color: 'var(--page-muted)', fontSize: '0.6rem' }}>{c.ruc}</td>
                    <td className="px-4 py-1.5">
                      {c.planActivo ? (
                        <div className="flex items-center gap-2">
                          <EstadoPlanBadge estado={c.planActivo.estado} />
                          <span style={{ color: 'var(--page-muted)', fontSize: '0.6rem' }}>{c.planActivo.nombre}</span>
                        </div>
                      ) : (
                        <span style={{ color: 'var(--page-muted)', fontSize: '0.6rem' }}>{t('platform.companias.noPlan')}</span>
                      )}
                    </td>
                    <td className="px-4 py-1.5">
                      {c.planActivo ? <DiasRestantesBadge dias={c.planActivo.diasRestantes} /> : <span style={{ color: 'var(--page-muted)' }}>—</span>}
                    </td>
                    <td className="px-4 py-1.5">
                      <Badge variant={c.activo ? 'default' : 'secondary'} className={c.activo ? 'bg-green-500/20 text-green-700 border-green-300' : ''}>
                        {c.activo ? 'Activo' : 'Inactivo'}
                      </Badge>
                    </td>
                    <td className="px-4 py-1.5 text-right">
                      <div className="flex justify-end gap-0.5">
                        <Button
                          icon="pi pi-eye"
                          text
                          size="small"
                          tooltip="Ver detalle"
                          tooltipOptions={{ position: 'top' }}
                          onClick={() => navigate(`/platform/companias/${c.id}`)}
                          pt={{ root: { className: 'text-orange-500 hover:text-orange-400' } }}
                        />
                        <Button
                          icon={enviandoId === c.id ? 'pi pi-spin pi-spinner' : 'pi pi-whatsapp'}
                          text
                          size="small"
                          tooltip={t('platform.companias.recordatorio.tooltip')}
                          tooltipOptions={{ position: 'top' }}
                          disabled={enviandoId === c.id}
                          onClick={() => handleEnviarRecordatorio(c)}
                          pt={{ root: { className: 'text-green-600 hover:text-green-500' } }}
                        />
                        {isSuperAdmin && c.activo && (
                          <Button
                            icon="pi pi-ban"
                            text
                            size="small"
                            severity="danger"
                            tooltip="Suspender"
                            tooltipOptions={{ position: 'top' }}
                            onClick={() => setSuspenderTarget(c)}
                          />
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <RegistrarGymWizard
        open={registrarOpen}
        onClose={() => setRegistrarOpen(false)}
        onCreated={() => { load(); setRegistrarOpen(false) }}
      />
      <SuspenderCompaniaDialog
        open={suspenderTarget !== null}
        compania={suspenderTarget}
        onClose={() => setSuspenderTarget(null)}
        onSuspended={load}
      />
    </div>
  )
}
