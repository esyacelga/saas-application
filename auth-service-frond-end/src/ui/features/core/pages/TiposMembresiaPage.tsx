import { useState, useEffect, useCallback } from 'react'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { CreditCard, Search, Tag } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog'
import { PageHeader } from '@/ui/components/PageHeader'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import type { TipoMembresia } from '@/infrastructure/http/core/core.dto'
import { PLANTILLAS } from '@/ui/features/core/constants/tiposMembresia.plantillas'
import type { PlantillaOption } from '@/ui/features/core/constants/tiposMembresia.plantillas'
import { CrearTipoMembresiaModal } from '../components/CrearTipoMembresiaModal'
import { EditarTipoMembresiaModal } from '../components/EditarTipoMembresiaModal'

function BadgeModo({ modo }: { modo: string }) {
  const isCalendario = modo === 'calendario'
  return (
    <span
      className="inline-flex items-center gap-1 font-medium px-2 py-0.5 rounded-full"
      style={{
        fontSize: '0.55rem',
        background: isCalendario ? 'rgba(59,130,246,0.15)' : 'rgba(249,115,22,0.15)',
        color: isCalendario ? '#60a5fa' : '#f97316',
      }}
    >
      {modo}
    </span>
  )
}

function BadgeActivo({ activo }: { activo: boolean }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 font-medium px-2 py-0.5 rounded-full ${activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}
      style={{ fontSize: '0.55rem' }}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${activo ? 'bg-green-600' : 'bg-red-500'}`} />
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

interface EmptyStateEnriquecidoProps {
  onSelectPlantilla: (p: PlantillaOption) => void
  onAddSimple: () => void
}

function EmptyStateEnriquecido({ onSelectPlantilla, onAddSimple: _onAddSimple }: EmptyStateEnriquecidoProps) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <Tag size={36} className="mb-3" style={{ color: 'var(--page-border)' }} />
      <p style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--page-text)' }}>
        {t('tiposMembresia.emptyTitle')}
      </p>
      <p style={{ fontSize: '0.75rem', color: 'var(--page-muted)', marginTop: '4px' }}>
        {t('tiposMembresia.emptySubtitle')}
      </p>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-4 max-w-2xl mx-auto w-full">
        {PLANTILLAS.map(p => (
          <button
            key={p.id}
            type="button"
            onClick={() => onSelectPlantilla(p)}
            className={`flex items-start gap-2.5 p-3 rounded-lg text-left transition-all${p.colSpan ? ' sm:col-span-2' : ''}`}
            style={{
              background: 'var(--page-surface)',
              border: '1px solid var(--page-border)',
            }}
            onMouseEnter={e => { e.currentTarget.style.borderColor = '#f97316' }}
            onMouseLeave={e => { e.currentTarget.style.borderColor = 'var(--page-border)' }}
          >
            <span className="text-xl flex-shrink-0">{p.emoji}</span>
            <div className="min-w-0 text-left">
              <p className="text-xs font-semibold" style={{ color: 'var(--page-text)' }}>
                {p.label}
              </p>
              <p className="text-[0.65rem] mt-0.5 leading-snug" style={{ color: 'var(--page-muted)' }}>
                {p.desc}
              </p>
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}

function EmptyStateSimple({ onAdd }: { onAdd: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center py-14 text-center">
      <Tag size={36} className="mb-3" style={{ color: 'var(--page-border)' }} />
      <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>{t('tiposMembresia.emptyTitle')}</p>
      <p className="text-xs mt-1 mb-4" style={{ color: 'var(--page-muted)' }}>{t('tiposMembresia.emptyHint')}</p>
      <Button label={t('tiposMembresia.createTitle')} icon="pi pi-plus" severity="warning" size="small" onClick={onAdd} />
    </div>
  )
}

export function TiposMembresiaPage() {
  const { t } = useTranslation()
  const [tipos, setTipos] = useState<TipoMembresia[]>([])
  const [loading, setLoading] = useState(true)
  const [globalFilter, setGlobalFilter] = useState('')
  const [crearOpen, setCrearOpen] = useState(false)
  const [tipoEditar, setTipoEditar] = useState<TipoMembresia | null>(null)
  const [plantillaInicial, setPlantillaInicial] = useState<PlantillaOption | null>(null)

  const cargar = useCallback(async () => {
    setLoading(true)
    try {
      const data = await coreRepository.getTiposMembresia()
      setTipos(data)
    } catch {
      toast.error(t('tiposMembresia.loadError'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => { cargar() }, [cargar])

  const handleDesactivar = (tipo: TipoMembresia) => {
    confirmDialog({
      message: t('tiposMembresia.desactivarConfirm', { name: tipo.nombre }),
      header: t('tiposMembresia.desactivarTitle'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: t('tiposMembresia.desactivar'),
      rejectLabel: t('common.cancel'),
      acceptClassName: 'p-button-danger',
      defaultFocus: 'reject',
      accept: async () => {
        try {
          await coreRepository.desactivarTipoMembresia(tipo.id)
          toast.success(t('tiposMembresia.desactivado', { name: tipo.nombre }))
          cargar()
        } catch (err) {
          if (isAxiosError(err) && err.response?.status === 409) {
            toast.error(t('tiposMembresia.desactivarError409'))
          } else {
            toast.error(t('tiposMembresia.desactivarError'))
          }
        }
      },
    })
  }

  const handleSelectPlantilla = (p: PlantillaOption) => {
    setPlantillaInicial(p)
    setCrearOpen(true)
  }

  const handleAbrirNuevo = () => {
    setPlantillaInicial(null)
    setCrearOpen(true)
  }

  const tableHeader = (
    <div className="flex items-center justify-end">
      <div className="relative">
        <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none"
          style={{ color: 'var(--input-placeholder)' }} />
        <input
          type="text"
          value={globalFilter}
          onChange={e => setGlobalFilter(e.target.value)}
          placeholder={t('common.search', 'Buscar...')}
          className="pl-7 pr-3 py-1.5 text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent"
          style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
        />
      </div>
    </div>
  )

  const modoTemplate = (tipo: TipoMembresia) => <BadgeModo modo={tipo.modo_control} />

  const duracionTemplate = (tipo: TipoMembresia) => (
    <span style={{ fontSize: '0.64rem', color: 'var(--page-text)' }}>
      {tipo.duracion_valor} {tipo.duracion_tipo}
      {tipo.dias_acceso ? ` · ${tipo.dias_acceso} accesos` : ''}
    </span>
  )

  const precioTemplate = (tipo: TipoMembresia) => (
    <span style={{ fontSize: '0.64rem', fontWeight: 600, color: 'var(--page-text)' }}>
      ${tipo.precio.toFixed(2)}
    </span>
  )

  const activoTemplate = (tipo: TipoMembresia) => <BadgeActivo activo={tipo.activo} />

  const accionesTemplate = (tipo: TipoMembresia) => (
    <div className="flex items-center gap-0.5">
      <Button label={t('common.edit')} text size="small" onClick={() => setTipoEditar(tipo)}
        pt={{ root: { className: 'text-orange-500 hover:text-orange-600 !text-[0.6rem] !px-1.5 !py-0.5' } }} />
      {tipo.activo && (
        <Button label={t('tiposMembresia.desactivar')} text size="small" severity="danger"
          onClick={() => handleDesactivar(tipo)}
          pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }} />
      )}
    </div>
  )

  const activos = tipos.filter(t => t.activo).length
  const inactivos = tipos.length - activos

  const sinTiposCreados = !loading && tipos.length === 0

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <ConfirmDialog />

      <PageHeader
        title={t('tiposMembresia.title')}
        description={t('tiposMembresia.description')}
        action={
          sinTiposCreados ? undefined : (
            <Button label={t('tiposMembresia.createTitle')} icon="pi pi-plus"
              severity="warning" size="small" onClick={handleAbrirNuevo} />
          )
        }
      />

      {sinTiposCreados ? (
        <div className="flex-1 overflow-auto p-4">
          <EmptyStateEnriquecido onSelectPlantilla={handleSelectPlantilla} onAddSimple={handleAbrirNuevo} />
        </div>
      ) : (
        <>
          {/* Stats bar */}
          <div className="flex items-center gap-6 px-6 py-3 flex-shrink-0"
            style={{ borderBottom: '1px solid var(--page-border)' }}>
            <div className="flex items-center gap-2">
              <CreditCard size={16} style={{ color: 'var(--page-muted)' }} />
              <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{tipos.length}</span>
              <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('tiposMembresia.statTotal')}</span>
            </div>
            <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
            <div className="flex items-center gap-2">
              <span className="text-2xl font-bold text-green-500">{activos}</span>
              <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('tiposMembresia.statActivos')}</span>
            </div>
            <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
            <div className="flex items-center gap-2">
              <span className="text-2xl font-bold text-red-400">{inactivos}</span>
              <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('tiposMembresia.statInactivos')}</span>
            </div>
          </div>

          <div className="flex-1 overflow-auto p-4">
            <DataTable
              value={tipos}
              loading={loading}
              globalFilter={globalFilter}
              globalFilterFields={['nombre', 'modo_control']}
              header={tableHeader}
              emptyMessage={<EmptyStateSimple onAdd={handleAbrirNuevo} />}
              paginator
              rows={10}
              rowsPerPageOptions={[5, 10, 25]}
              sortField="nombre"
              defaultSortOrder={1}
              stripedRows
              showGridlines={false}
              size="small"
            >
              <Column field="nombre" header={t('tiposMembresia.colNombre')} sortable
                style={{ fontWeight: 600, color: 'var(--page-text)' }} />
              <Column field="modo_control" header={t('tiposMembresia.colModo')} body={modoTemplate} />
              <Column field="duracion_valor" header={t('tiposMembresia.colDuracion')} body={duracionTemplate} />
              <Column field="precio" header={t('tiposMembresia.colPrecio')} body={precioTemplate} sortable />
              <Column field="activo" header={t('tiposMembresia.colEstado')} body={activoTemplate} />
              <Column header={t('common.actions')} body={accionesTemplate} style={{ width: '10rem' }} />
            </DataTable>
          </div>
        </>
      )}

      <CrearTipoMembresiaModal
        open={crearOpen}
        onClose={() => { setCrearOpen(false); setPlantillaInicial(null) }}
        onCreado={() => { setCrearOpen(false); setPlantillaInicial(null); cargar() }}
        initialStep={plantillaInicial ? 'form' : undefined}
        initialDefaults={plantillaInicial ? plantillaInicial.defaults : undefined}
      />

      {tipoEditar && (
        <EditarTipoMembresiaModal
          tipo={tipoEditar}
          onClose={() => setTipoEditar(null)}
          onActualizado={() => { setTipoEditar(null); cargar() }}
        />
      )}
    </div>
  )
}
