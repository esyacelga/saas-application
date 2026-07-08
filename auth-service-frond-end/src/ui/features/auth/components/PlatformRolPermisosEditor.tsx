import { useState, useEffect, useCallback } from 'react'
import { toast } from 'sonner'
import { X, Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { confirmDialog } from 'primereact/confirmdialog'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { PermisoRol, RolPlataforma, CompaniaBasica } from '@/infrastructure/http/auth/auth.dto'
import { AsignarPermisoModal } from './AsignarPermisoModal'
import { CrearPermisoModal } from './CrearPermisoModal'

interface Props {
  rol: RolPlataforma
  onClose: () => void
}

export function PlatformRolPermisosEditor({ rol, onClose }: Props) {
  const { t } = useTranslation()
  const [permisos, setPermisos] = useState<PermisoRol[]>([])
  const [loading, setLoading] = useState(true)
  const [companias, setCompanias] = useState<CompaniaBasica[]>([])
  const [globalFilter, setGlobalFilter] = useState('')
  const [asignarOpen, setAsignarOpen] = useState(false)
  const [crearOpen, setCrearOpen] = useState(false)

  const cargarPermisos = useCallback(async () => {
    setLoading(true)
    try {
      const data = await authRepository.getRolPermisosConSucursal(rol.id)
      setPermisos(data)
    } catch {
      toast.error(t('permisosPlataforma.loadError'))
    } finally {
      setLoading(false)
    }
  }, [rol.id])

  useEffect(() => {
    cargarPermisos()
    authRepository.getCompaniasBasicas().then(setCompanias).catch(() => {})
  }, [cargarPermisos])

  const confirmarEliminar = (permiso: PermisoRol) => {
    confirmDialog({
      message: t('permisosPlataforma.removeConfirmMessage', { permiso: permiso.nombre, rol: rol.nombre }),
      header: t('permisosPlataforma.removeConfirmHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: t('permisosPlataforma.removeConfirmHeader').split(' ')[0],
      rejectLabel: t('common.cancel'),
      acceptClassName: 'p-button-danger',
      defaultFocus: 'reject',
      accept: async () => {
        try {
          await authRepository.eliminarPermisoDeRolPlataforma(rol.id, permiso.id)
          toast.success(t('permisosPlataforma.removeSuccess', { name: permiso.nombre }))
          cargarPermisos()
        } catch {
          toast.error(t('permisosPlataforma.removeError'))
        }
      },
    })
  }

  const handleCreado = async (idPermiso: number) => {
    setCrearOpen(false)
    try {
      await authRepository.asignarPermisoARolPlataforma(rol.id, { id_permiso: idPermiso })
      toast.success(t('permisosPlataforma.createAndAssignSuccess'))
    } catch {
      toast.info(t('permisosPlataforma.createOnlyInfo'))
    }
    cargarPermisos()
  }

  // ── Column templates ────────────────────────────────────────────────────────

  const nombreTemplate = (p: PermisoRol) => (
    <code className="text-xs font-mono text-orange-700">{p.nombre}</code>
  )

  const sucursalTemplate = (p: PermisoRol) => (
    <span className="inline-block text-xs bg-blue-50 text-blue-700 border border-blue-100 px-2 py-0.5 rounded-full whitespace-nowrap">
      {p.nombre_sucursal}
    </span>
  )

  const moduloTemplate = (p: PermisoRol) => (
    <span className="inline-block text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded whitespace-nowrap">
      {p.modulo}
    </span>
  )

  const descripcionTemplate = (p: PermisoRol) =>
    p.descripcion
      ? <span className="text-slate-600 text-xs">{p.descripcion}</span>
      : <span className="text-slate-400 italic text-xs">—</span>

  const accionesTemplate = (p: PermisoRol) => (
    <Button
      icon="pi pi-times"
      text
      size="small"
      severity="danger"
      tooltip="Quitar del rol"
      tooltipOptions={{ position: 'left' }}
      onClick={() => confirmarEliminar(p)}
    />
  )

  const emptyMessage = (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <i className="pi pi-shield text-4xl text-slate-200 mb-3" />
      <p className="text-slate-500 text-sm">{t('permisosPlataforma.noPermissions')}</p>
      <p className="text-slate-400 text-xs mt-1">{t('permisosPlataforma.noPermissionsHint')}</p>
    </div>
  )

  const tableHeader = (
    <div className="flex items-center gap-2 p-1">
      <div className="relative flex-1 max-w-sm">
        <Search
          size={13}
          className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none"
        />
        <input
          type="text"
          value={globalFilter}
          onChange={e => setGlobalFilter(e.target.value)}
          placeholder={t('permisosPlataforma.filterPlaceholder')}
          className="pl-7 pr-3 py-1.5 text-xs rounded-md font-sans w-full bg-slate-50 border border-slate-200 text-slate-700 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-400 focus:border-transparent"
        />
      </div>
    </div>
  )

  return (
    <>
      <div className="fixed inset-0 z-20 bg-black/40" onClick={onClose} aria-hidden />

      <aside className="fixed inset-y-0 right-0 z-30 w-[54rem] bg-white shadow-2xl flex flex-col">
        {/* Header */}
        <div className="flex items-start justify-between px-5 py-4 border-b bg-slate-50">
          <div>
            <h2 className="font-semibold text-slate-900 text-sm leading-tight flex items-center gap-2">
              <i className="pi pi-shield text-orange-500" />
              {t('rolesPlataformaPage.permissionsTitle', { name: rol.nombre })}
            </h2>
            <p className="text-xs text-blue-600 font-medium mt-0.5">{rol.nombre_compania}</p>
            {!loading && (
              <p className="text-xs text-slate-400 mt-0.5">
                {permisos.length} permiso{permisos.length !== 1 ? 's' : ''} asignado
                {permisos.length !== 1 ? 's' : ''}
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Action bar */}
        <div className="flex items-center gap-2 px-5 py-3 border-b bg-white">
          <Button
            icon="pi pi-plus-circle"
            label="Asignar permiso existente"
            severity="info"
            size="small"
            outlined
            onClick={() => setAsignarOpen(true)}
          />
          <Button
            icon="pi pi-plus"
            label="Crear nuevo permiso"
            severity="warning"
            size="small"
            outlined
            onClick={() => setCrearOpen(true)}
          />
        </div>

        {/* Table */}
        <div className="flex-1 overflow-auto p-4">
          <div className="rounded-xl overflow-hidden border border-slate-200">
            <DataTable
              value={permisos}
              loading={loading}
              globalFilter={globalFilter}
              globalFilterFields={['nombre', 'modulo', 'nombre_sucursal', 'descripcion']}
              header={tableHeader}
              emptyMessage={emptyMessage}
              paginator
              rows={10}
              rowsPerPageOptions={[5, 10, 25]}
              stripedRows
              size="small"
              className="text-sm"
            >
              <Column
                field="id"
                header="ID"
                sortable
                style={{ width: '4rem' }}
              />
              <Column
                field="nombre_sucursal"
                header={t('rolesPlataformaPage.colBranch')}
                body={sucursalTemplate}
                sortable
                style={{ width: '10rem' }}
              />
              <Column
                field="modulo"
                header={t('rolesPlataformaPage.colModule')}
                body={moduloTemplate}
                sortable
                style={{ width: '8rem' }}
              />
              <Column
                field="nombre"
                header={t('rolesPlataformaPage.colPermission')}
                body={nombreTemplate}
                sortable
              />
              <Column
                field="descripcion"
                header={t('rolesPlataformaPage.colDescription')}
                body={descripcionTemplate}
              />
              <Column
                header=""
                body={accionesTemplate}
                style={{ width: '4rem', textAlign: 'center' }}
              />
            </DataTable>
          </div>
        </div>

        {/* Footer */}
        <div className="px-5 py-3 border-t bg-slate-50 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 border border-slate-300 rounded-lg text-sm text-slate-700 hover:bg-slate-100 transition-colors font-medium"
          >
            Cerrar
          </button>
        </div>
      </aside>

      {asignarOpen && (
        <AsignarPermisoModal
          rol={rol}
          permisosAsignados={permisos}
          onClose={() => setAsignarOpen(false)}
          onAsignado={() => { setAsignarOpen(false); cargarPermisos() }}
        />
      )}

      <CrearPermisoModal
        open={crearOpen}
        companias={companias}
        onClose={() => setCrearOpen(false)}
        onCreado={handleCreado}
      />
    </>
  )
}
