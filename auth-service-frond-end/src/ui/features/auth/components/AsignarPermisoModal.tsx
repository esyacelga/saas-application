import { useState, useEffect, useMemo } from 'react'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Dialog } from 'primereact/dialog'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { PermisoPlataforma, PermisoRol, RolPlataforma } from '@/infrastructure/http/auth/auth.dto'

interface Props {
  rol: RolPlataforma
  permisosAsignados: PermisoRol[]
  onClose: () => void
  onAsignado: () => void
}

export function AsignarPermisoModal({ rol, permisosAsignados, onClose, onAsignado }: Props) {
  const { t } = useTranslation()
  const [permisos, setPermisos] = useState<PermisoPlataforma[]>([])
  const [loading, setLoading] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [seleccionado, setSeleccionado] = useState<PermisoPlataforma | null>(null)
  const [globalFilter, setGlobalFilter] = useState('')

  useEffect(() => {
    authRepository.getPermisosPlataforma()
      .then(setPermisos)
      .catch(() => toast.error(t('permisosPlataforma.availableLoadError')))
      .finally(() => setLoading(false))
  }, [])

  const disponibles = useMemo(() => {
    const idsAsignados = new Set(permisosAsignados.map(p => p.id))
    return permisos.filter(p => !idsAsignados.has(p.id))
  }, [permisos, permisosAsignados])

  const handleAsignar = async () => {
    if (!seleccionado) return
    setGuardando(true)
    try {
      await authRepository.asignarPermisoARolPlataforma(rol.id, { id_permiso: seleccionado.id })
      toast.success(t('permisosPlataforma.assignSuccess', { permiso: seleccionado.nombre, rol: rol.nombre }))
      onAsignado()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error(t('permisosPlataforma.alreadyAssigned'))
      } else {
        toast.error(t('permisosPlataforma.assignError'))
      }
    } finally {
      setGuardando(false)
    }
  }

  // ── Column templates ────────────────────────────────────────────────────────

  const nombreTemplate = (p: PermisoPlataforma) => (
    <code className="text-xs font-mono text-orange-700">{p.nombre}</code>
  )

  const sucursalTemplate = (p: PermisoPlataforma) => (
    <span className="inline-block text-xs bg-blue-50 text-blue-700 border border-blue-100 px-2 py-0.5 rounded-full whitespace-nowrap">
      {p.nombre_sucursal}
    </span>
  )

  const moduloTemplate = (p: PermisoPlataforma) => (
    <span className="inline-block text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded whitespace-nowrap">
      {p.modulo}
    </span>
  )

  const descripcionTemplate = (p: PermisoPlataforma) =>
    p.descripcion
      ? <span className="text-slate-500 text-xs">{p.descripcion}</span>
      : <span className="text-slate-400 italic text-xs">—</span>

  const tableHeader = (
    <div className="flex items-center gap-2 p-1">
      <div className="relative flex-1">
        <Search
          size={13}
          className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none"
        />
        <input
          type="text"
          value={globalFilter}
          onChange={e => setGlobalFilter(e.target.value)}
          placeholder={t('permisosPlataforma.searchPlaceholder')}
          className="pl-7 pr-3 py-1.5 text-xs rounded-md font-sans w-full bg-slate-50 border border-slate-200 text-slate-700 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-400 focus:border-transparent"
        />
      </div>
    </div>
  )

  const emptyMessage = !loading && disponibles.length === 0 && permisos.length > 0
    ? (
      <div className="py-6 text-center">
        <i className="pi pi-check-circle text-2xl text-green-300 mb-2 block" />
        <p className="text-sm text-slate-500">{t('permisosPlataforma.allAssigned')}</p>
      </div>
    ) : (
      <div className="py-6 text-center">
        <p className="text-sm text-slate-400">{t('permisosPlataforma.noFound')}</p>
      </div>
    )

  const footer = (
    <div className="flex justify-end gap-2">
      <Button label={t('common.cancel')} outlined onClick={onClose} disabled={guardando} />
      <Button
        label={guardando ? t('permisosPlataforma.assigning') : t('permisosPlataforma.assignLabel')}
        icon={guardando ? 'pi pi-spin pi-spinner' : 'pi pi-check'}
        severity="info"
        onClick={handleAsignar}
        disabled={!seleccionado || guardando}
      />
    </div>
  )

  return (
    <Dialog
      header={
        <div className="flex items-center gap-2">
          <i className="pi pi-plus-circle text-blue-500" />
          <span>{t('permisosPlataforma.assignTo', { rol: rol.nombre })}</span>
        </div>
      }
      visible
      onHide={onClose}
      style={{ width: '700px', maxWidth: '95vw' }}
      footer={footer}
      modal
      draggable={false}
      resizable={false}
    >
      <div className="flex flex-col gap-3">
        {seleccionado && (
          <div className="bg-blue-50 border border-blue-200 rounded-lg px-3 py-2 flex items-center gap-2">
            <i className="pi pi-check-circle text-blue-500 text-xs" />
            <span className="text-xs text-blue-700">
              {t('permisosPlataforma.selected')}{' '}
              <code className="font-mono font-semibold">{seleccionado.nombre}</code>
              {' '}—{' '}
              <span className="opacity-70">{seleccionado.nombre_sucursal}</span>
            </span>
          </div>
        )}

        <div className="rounded-lg overflow-hidden border border-slate-200">
          <DataTable
            value={disponibles}
            loading={loading}
            selection={seleccionado}
            onSelectionChange={e => setSeleccionado(e.value as PermisoPlataforma)}
            selectionMode="single"
            globalFilter={globalFilter}
            globalFilterFields={['nombre', 'modulo', 'nombre_sucursal']}
            header={tableHeader}
            emptyMessage={emptyMessage}
            scrollable
            scrollHeight="320px"
            stripedRows
            size="small"
          >
            <Column selectionMode="single" style={{ width: '3rem' }} />
            <Column
              field="modulo"
              header={t('rolesPlataformaPage.colModule')}
              body={moduloTemplate}
              sortable
              style={{ width: '8rem' }}
            />
            <Column
              field="nombre"
              header={t('platformRoles.colName')}
              body={nombreTemplate}
              sortable
            />
            <Column
              field="nombre_sucursal"
              header={t('rolesPlataformaPage.colBranch')}
              body={sucursalTemplate}
              style={{ width: '9rem' }}
            />
            <Column
              field="descripcion"
              header={t('rolesPlataformaPage.colDescription')}
              body={descripcionTemplate}
            />
          </DataTable>
        </div>

        <p className="text-xs text-slate-400">
          {disponibles.length} permiso{disponibles.length !== 1 ? 's' : ''} disponible
          {disponibles.length !== 1 ? 's' : ''} (sin asignar aún a este rol)
        </p>
      </div>
    </Dialog>
  )
}
