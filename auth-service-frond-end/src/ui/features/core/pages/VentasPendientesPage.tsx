import { useState, useEffect, useCallback, useMemo } from 'react'
import { toast } from 'sonner'
import { Search, CheckCircle, AlertCircle, ShieldOff, RefreshCw } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { Tooltip } from 'primereact/tooltip'
import { PageHeader } from '@/ui/components/PageHeader'
import { IfPermission } from '@/ui/router/guards/PermissionGuard'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import type { VentaPendiente } from '@/infrastructure/http/core/core.dto'
import { useAuthStore, useHasPermission } from '@/infrastructure/store/auth/auth.store'
import type { JwtPayloadStaff } from '@/domain/auth/entities/User.entity'
import { getApiErrorStatus } from '@/lib/api-error'
import { RechazarVentaPendienteModal } from '../components/RechazarVentaPendienteModal'

// ── Util: tiempo relativo ────────────────────────────────────────────────────

function diasTranscurridos(isoFecha: string): number {
  const ms = Date.now() - new Date(isoFecha).getTime()
  return Math.floor(ms / (1000 * 60 * 60 * 24))
}

// ── Skeleton ─────────────────────────────────────────────────────────────────

function SkeletonRows() {
  return (
    <div className="p-4 space-y-2">
      {Array.from({ length: 5 }).map((_, i) => (
        <div
          key={i}
          className="h-10 rounded-md motion-safe:animate-pulse"
          style={{ background: 'var(--page-surface)' }}
        />
      ))}
    </div>
  )
}

// ── Página principal ─────────────────────────────────────────────────────────

export function VentasPendientesPage() {
  const { t } = useTranslation()
  const user = useAuthStore(s => s.user)
  const tienePermiso = useHasPermission('membresias:confirmar_pago')

  const idCompania = user?.tipo === 'staff' ? (user as JwtPayloadStaff).id_compania : null

  const [items, setItems] = useState<VentaPendiente[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<'forbidden' | 'error' | null>(null)
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')

  const [loadingRowId, setLoadingRowId] = useState<number | null>(null)
  const [rechazarData, setRechazarData] = useState<{ id: number; nombreCliente: string | null } | null>(null)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 300)
    return () => clearTimeout(timer)
  }, [query])

  const cargar = useCallback(async () => {
    if (!idCompania) return
    setLoading(true)
    setError(null)
    try {
      const data = await coreRepository.getPendientes(idCompania)
      setItems(data)
    } catch (err) {
      if (getApiErrorStatus(err) === 403) {
        setError('forbidden')
      } else {
        setError('error')
      }
    } finally {
      setLoading(false)
    }
  }, [idCompania])

  useEffect(() => { cargar() }, [cargar])

  const filtered = useMemo(() => {
    if (!debouncedQuery.trim()) return items
    const q = debouncedQuery.toLowerCase()
    return items.filter(i => (i.nombreCliente ?? '').toLowerCase().includes(q))
  }, [items, debouncedQuery])

  const handleConfirmar = async (item: VentaPendiente) => {
    setLoadingRowId(item.id)
    try {
      await coreRepository.confirmarPago(item.id)
      toast.success(t('ventasPendientes.confirmadaSuccess'))
      await cargar()
    } catch (err) {
      if (getApiErrorStatus(err) === 409) {
        toast.error(t('ventasPendientes.confirmarError409'))
      } else {
        toast.error(t('ventasPendientes.accionError'))
      }
      await cargar()
    } finally {
      setLoadingRowId(null)
    }
  }

  const handleRechazada = async () => {
    setRechazarData(null)
    await cargar()
  }

  if (!tienePermiso) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center" style={{ color: 'var(--page-text)' }}>
        <ShieldOff size={40} className="mb-3" style={{ color: 'var(--page-border)' }} />
        <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>{t('noAccess.title')}</p>
        <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>{t('noAccess.description')}</p>
      </div>
    )
  }

  if (error === 'forbidden') {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center" style={{ color: 'var(--page-text)' }}>
        <ShieldOff size={40} className="mb-3" style={{ color: 'var(--page-border)' }} />
        <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>{t('noAccess.title')}</p>
        <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>{t('noAccess.description')}</p>
      </div>
    )
  }

  const accionesTemplate = (item: VentaPendiente) => {
    const isRowLoading = loadingRowId === item.id
    return (
      <IfPermission permiso="membresias:confirmar_pago">
        <div className="flex items-center gap-1">
          <Tooltip target={`.btn-confirmar-${item.id}`} content={t('ventasPendientes.tooltipConfirmar')} position="top" />
          <Button
            severity="success"
            size="small"
            icon="pi pi-check"
            label={t('ventasPendientes.accionConfirmar')}
            loading={isRowLoading}
            disabled={isRowLoading || loadingRowId !== null}
            onClick={() => handleConfirmar(item)}
            className={`btn-confirmar-${item.id}`}
            pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }}
          />
          <Tooltip target={`.btn-rechazar-${item.id}`} content={t('ventasPendientes.tooltipRechazar')} position="top" />
          <Button
            severity="danger"
            size="small"
            text
            icon="pi pi-times"
            label={t('ventasPendientes.accionRechazar')}
            disabled={isRowLoading || loadingRowId !== null}
            onClick={() => setRechazarData({ id: item.id, nombreCliente: item.nombreCliente })}
            className={`btn-rechazar-${item.id}`}
            pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }}
          />
        </div>
      </IfPermission>
    )
  }

  const precioTemplate = (item: VentaPendiente) => (
    <span style={{ color: 'var(--page-text)' }}>${parseFloat(item.precioPagado).toFixed(2)}</span>
  )

  const descuentoTemplate = (item: VentaPendiente) => {
    if (item.descuentoAplicado === '0.00' || parseFloat(item.descuentoAplicado) === 0) {
      return <span style={{ color: 'var(--page-muted)' }}>—</span>
    }
    return <span style={{ color: 'var(--page-text)' }}>${parseFloat(item.descuentoAplicado).toFixed(2)}</span>
  }

  const pendienteDesdeTemplate = (item: VentaPendiente) => {
    const dias = diasTranscurridos(item.creacionFecha)
    const label = t('ventasPendientes.haceN', { count: dias })
    const fechaAbsoluta = new Date(item.creacionFecha).toLocaleString()
    return (
      <span title={fechaAbsoluta} style={{ color: 'var(--page-muted)', cursor: 'default' }}>
        {label}
      </span>
    )
  }

  const clienteTemplate = (item: VentaPendiente) => {
    const nombre = item.nombreCliente ?? '—'
    const inicial = nombre.charAt(0).toUpperCase()
    return (
      <span className="flex items-center gap-2">
        <span
          className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0 text-white"
          style={{ background: '#f97316' }}
        >
          {inicial}
        </span>
        <span style={{ color: 'var(--page-text)', fontSize: '0.64rem', fontWeight: 600 }}>
          {nombre}
        </span>
      </span>
    )
  }

  const emptyMessage = () => {
    if (debouncedQuery) {
      return (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <CheckCircle size={40} className="mb-3" style={{ color: 'var(--page-border)' }} />
          <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>
            {t('ventasPendientes.emptyFiltered', { q: debouncedQuery })}
          </p>
        </div>
      )
    }
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <CheckCircle size={40} className="mb-3" style={{ color: 'var(--page-border)' }} />
        <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>
          {t('ventasPendientes.emptyTitle')}
        </p>
        <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>
          {t('ventasPendientes.emptySubtitle')}
        </p>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <PageHeader
        title={t('ventasPendientes.title')}
        description={t('ventasPendientes.description')}
        action={
          <Button
            icon={<RefreshCw size={14} />}
            label={t('common.refresh')}
            severity="secondary"
            size="small"
            onClick={cargar}
            disabled={loading}
          />
        }
      />

      <div className="px-6 py-3 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
        <div className="relative max-w-xs">
          <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none"
            style={{ color: 'var(--page-muted)' }} />
          <input
            type="text"
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder={t('clientes.searchPlaceholder')}
            className="pl-7 pr-3 py-1.5 text-xs rounded-md font-sans w-full focus:outline-none focus:ring-2 focus:ring-orange-500"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
          />
        </div>
      </div>

      <div className="flex-1 overflow-auto p-4">
        {loading ? (
          <SkeletonRows />
        ) : error === 'error' ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <AlertCircle size={40} className="mb-3" style={{ color: 'var(--page-border)' }} />
            <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>
              {t('ventasPendientes.loadError')}
            </p>
            <Button
              label={t('common.refresh')}
              severity="secondary"
              size="small"
              className="mt-3"
              onClick={cargar}
            />
          </div>
        ) : (
          <DataTable
            value={filtered}
            emptyMessage={emptyMessage()}
            showGridlines={false}
            stripedRows
            size="small"
          >
            <Column
              header={t('ventasPendientes.colCliente')}
              body={clienteTemplate}
            />
            <Column
              field="tipoNombre"
              header={t('ventasPendientes.colTipo')}
            />
            <Column
              header={t('ventasPendientes.colPrecio')}
              body={precioTemplate}
              style={{ width: '7rem' }}
            />
            <Column
              header={t('ventasPendientes.colDescuento')}
              body={descuentoTemplate}
              style={{ width: '7rem' }}
            />
            <Column
              header={t('ventasPendientes.colPendienteDesde')}
              body={pendienteDesdeTemplate}
              style={{ width: '10rem' }}
            />
            <Column
              header={t('ventasPendientes.colAcciones')}
              body={accionesTemplate}
              style={{ width: '8rem' }}
            />
          </DataTable>
        )}
      </div>

      {rechazarData && (
        <RechazarVentaPendienteModal
          open={true}
          idMembresia={rechazarData.id}
          nombreCliente={rechazarData.nombreCliente}
          onClose={() => setRechazarData(null)}
          onRechazada={handleRechazada}
        />
      )}
    </div>
  )
}
