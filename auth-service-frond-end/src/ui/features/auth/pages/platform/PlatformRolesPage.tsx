import { useState, useEffect, useCallback } from 'react'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { Shield, Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { RolPlataforma, CompaniaBasica, PermisoRol } from '@/infrastructure/http/auth/auth.dto'
import { CrearRolPlataformaModal } from '../../components/CrearRolPlataformaModal'
import { EditarRolPlataformaModal } from '../../components/EditarRolPlataformaModal'
import { AsignarPermisoModal } from '../../components/AsignarPermisoModal'
import { CrearPermisoModal } from '../../components/CrearPermisoModal'

export function PlatformRolesPage() {
  const { t } = useTranslation()

  // ── Roles ─────────────────────────────────────────────────────────────────
  const [roles, setRoles] = useState<RolPlataforma[]>([])
  const [companias, setCompanias] = useState<CompaniaBasica[]>([])
  const [loading, setLoading] = useState(true)
  const [companiaFilter, setCompaniaFilter] = useState<number | null>(null)
  const [globalFilter, setGlobalFilter] = useState('')
  const [crearRolOpen, setCrearRolOpen] = useState(false)
  const [rolEditar, setRolEditar] = useState<RolPlataforma | null>(null)

  // ── Tabs ──────────────────────────────────────────────────────────────────
  const [activeTab, setActiveTab] = useState<'roles' | 'permisos'>('roles')

  // ── Rol seleccionado + permisos ───────────────────────────────────────────
  const [rolSeleccionado, setRolSeleccionado] = useState<RolPlataforma | null>(null)
  const [permisos, setPermisos] = useState<PermisoRol[]>([])
  const [loadingPermisos, setLoadingPermisos] = useState(false)
  const [filtroPermisos, setFiltroPermisos] = useState('')
  const [asignarOpen, setAsignarOpen] = useState(false)
  const [crearPermisoOpen, setCrearPermisoOpen] = useState(false)

  // ── Data loading ──────────────────────────────────────────────────────────
  const cargarRoles = useCallback(async () => {
    setLoading(true)
    try {
      const data = await authRepository.getRolesPlataforma()
      setRoles(data)
    } catch {
      toast.error(t('platformRoles.toastLoadError'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    cargarRoles()
    authRepository.getCompaniasBasicas().then(setCompanias).catch(() => {})
  }, [cargarRoles])

  // Sincronizar rolSeleccionado cuando se recarga la lista de roles
  useEffect(() => {
    if (!rolSeleccionado) return
    const updated = roles.find(r => r.id === rolSeleccionado.id)
    if (updated) {
      setRolSeleccionado(updated)
    } else {
      setRolSeleccionado(null)
      setPermisos([])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roles])

  const cargarPermisosRol = useCallback(async (idRol: number) => {
    setLoadingPermisos(true)
    try {
      const data = await authRepository.getRolPermisosConSucursal(idRol)
      setPermisos(data)
    } catch {
      toast.error(t('permisosPlataforma.loadError'))
    } finally {
      setLoadingPermisos(false)
    }
  }, [t])

  const seleccionarRol = (rol: RolPlataforma) => {
    setRolSeleccionado(rol)
    setFiltroPermisos('')
    cargarPermisosRol(rol.id)
    setActiveTab('permisos')
  }

  // ── CRUD roles ────────────────────────────────────────────────────────────
  const confirmarEliminarRol = (rol: RolPlataforma) => {
    confirmDialog({
      message: t('rolesPlataformaPage.deleteConfirmMessage', { name: rol.nombre, company: rol.nombre_compania }),
      header: t('rolesPlataformaPage.deleteConfirmHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: t('rolesPlataformaPage.deleteAccept'),
      rejectLabel: t('common.cancel'),
      acceptClassName: 'p-button-danger',
      defaultFocus: 'reject',
      accept: async () => {
        try {
          await authRepository.eliminarRolPlataforma(rol.id)
          if (rolSeleccionado?.id === rol.id) {
            setRolSeleccionado(null)
            setPermisos([])
          }
          toast.success(t('rolesPlataformaPage.deleteSuccess', { name: rol.nombre }))
          cargarRoles()
        } catch (err) {
          if (isAxiosError(err) && err.response?.status === 409) {
            toast.error(t('rolesPlataformaPage.deleteUsersError'))
          } else {
            toast.error(t('rolesPlataformaPage.deleteError'))
          }
        }
      },
    })
  }

  // ── CRUD rol_permisos ─────────────────────────────────────────────────────
  const confirmarEliminarPermiso = (permiso: PermisoRol) => {
    if (!rolSeleccionado) return
    confirmDialog({
      message: t('permisosPlataforma.removeConfirmMessage', { permiso: permiso.nombre, rol: rolSeleccionado.nombre }),
      header: t('permisosPlataforma.removeConfirmHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: t('permisosPlataforma.removeConfirmHeader').split(' ')[0],
      rejectLabel: t('common.cancel'),
      acceptClassName: 'p-button-danger',
      defaultFocus: 'reject',
      accept: async () => {
        try {
          await authRepository.eliminarPermisoDeRolPlataforma(rolSeleccionado.id, permiso.id)
          toast.success(t('permisosPlataforma.removeSuccess', { name: permiso.nombre }))
          cargarPermisosRol(rolSeleccionado.id)
        } catch {
          toast.error(t('permisosPlataforma.removeError'))
        }
      },
    })
  }

  const handlePermisoCreado = async (idPermiso: number) => {
    setCrearPermisoOpen(false)
    if (!rolSeleccionado) return
    try {
      await authRepository.asignarPermisoARolPlataforma(rolSeleccionado.id, { id_permiso: idPermiso })
      toast.success(t('permisosPlataforma.createAndAssignSuccess'))
    } catch {
      toast.info(t('permisosPlataforma.createOnlyInfo'))
    }
    cargarPermisosRol(rolSeleccionado.id)
  }

  // ── Filtered data ─────────────────────────────────────────────────────────
  const filteredRoles = companiaFilter
    ? roles.filter(r => r.id_compania === companiaFilter)
    : roles

  // ── Roles table templates ─────────────────────────────────────────────────
  const rolNombreTemplate = (rol: RolPlataforma) => (
    <span className="flex items-center gap-1.5">
      {rolSeleccionado?.id === rol.id && (
        <span className="w-1.5 h-1.5 rounded-full bg-orange-500 flex-shrink-0" />
      )}
      <span className={rolSeleccionado?.id === rol.id ? 'text-orange-400 font-semibold' : ''}>
        {rol.nombre}
      </span>
    </span>
  )

  const companiaTemplate = (rol: RolPlataforma) => (
    <span className="inline-block text-xs font-medium bg-blue-50 text-blue-700 border border-blue-100 px-2 py-0.5 rounded-full whitespace-nowrap">
      {rol.nombre_compania}
    </span>
  )

  const usuariosTemplate = (rol: RolPlataforma) => (
    <span className="inline-flex items-center justify-center min-w-[2rem] text-xs font-semibold text-slate-400 bg-slate-800 border border-slate-700 px-2 py-0.5 rounded-full">
      {rol.total_usuarios}
    </span>
  )

  const rolActionsTemplate = (rol: RolPlataforma) => (
    <div className="flex items-center gap-0.5" onClick={e => e.stopPropagation()}>
      <Button
        icon="pi pi-pencil"
        text
        size="small"
        tooltip="Editar"
        tooltipOptions={{ position: 'top' }}
        onClick={() => setRolEditar(rol)}
        pt={{ root: { className: 'text-orange-500 hover:text-orange-400' } }}
      />
      <Button
        icon="pi pi-trash"
        text
        size="small"
        severity="danger"
        tooltip="Eliminar"
        tooltipOptions={{ position: 'top' }}
        onClick={() => confirmarEliminarRol(rol)}
      />
    </div>
  )

  const rolesEmptyMessage = (
    <div className="flex flex-col items-center justify-center py-14 text-center">
      <Shield size={36} className="text-slate-700 mb-3" />
      <p className="text-slate-500 text-sm font-medium">{t('platformRoles.emptyTitle')}</p>
      <p className="text-slate-600 text-xs mt-1 mb-4">{t('platformRoles.emptySubtitle')}</p>
      <Button
        label={t('rolesPlataformaPage.createFirstRole')}
        icon="pi pi-plus"
        severity="warning"
        size="small"
        onClick={() => setCrearRolOpen(true)}
      />
    </div>
  )

  const rolesTableHeader = (
    <div className="flex flex-col gap-2 p-1">
      <select
        value={companiaFilter ?? ''}
        onChange={e => setCompaniaFilter(e.target.value ? Number(e.target.value) : null)}
        className="text-xs rounded-md px-2.5 py-1.5 font-sans focus:outline-none focus:ring-2 focus:ring-orange-500"
        style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
      >
        <option value="">{t('platformRoles.filterAllCompanies')}</option>
        {companias.map(c => (
          <option key={c.id} value={c.id}>{c.nombre}</option>
        ))}
      </select>
      <div className="relative">
        <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: 'var(--input-placeholder)' }} />
        <input
          type="text"
          value={globalFilter}
          onChange={e => setGlobalFilter(e.target.value)}
          placeholder={t('rolesPlataformaPage.searchPlaceholder')}
          className="pl-7 pr-3 py-1.5 text-xs rounded-md font-sans w-full focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent"
          style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
        />
      </div>
    </div>
  )

  // ── Permisos table templates ───────────────────────────────────────────────
  const permisoNombreTemplate = (p: PermisoRol) => (
    <code className="text-xs font-mono text-orange-700">{p.nombre}</code>
  )

  const permisoSucursalTemplate = (p: PermisoRol) => (
    <span className="inline-block text-xs bg-blue-50 text-blue-700 border border-blue-100 px-2 py-0.5 rounded-full whitespace-nowrap">
      {p.nombre_sucursal}
    </span>
  )

  const permisoModuloTemplate = (p: PermisoRol) => (
    <span className="inline-block text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded whitespace-nowrap">
      {p.modulo}
    </span>
  )

  const permisoDescripcionTemplate = (p: PermisoRol) =>
    p.descripcion
      ? <span className="text-slate-600 text-xs">{p.descripcion}</span>
      : <span className="text-slate-400 italic text-xs">—</span>

  const permisoActionsTemplate = (p: PermisoRol) => (
    <Button
      icon="pi pi-times"
      text
      size="small"
      severity="danger"
      tooltip="Quitar del rol"
      tooltipOptions={{ position: 'left' }}
      onClick={() => confirmarEliminarPermiso(p)}
    />
  )

  const permisosEmptyMessage = (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <i className="pi pi-shield text-4xl text-slate-200 mb-3" />
      <p className="text-slate-500 text-sm">{t('permisosPlataforma.noPermissions')}</p>
      <p className="text-slate-400 text-xs mt-1">{t('permisosPlataforma.noPermissionsHint')}</p>
    </div>
  )

  const permisosTableHeader = (
    <div className="flex items-center gap-2 p-1">
      <div className="relative flex-1 max-w-xs">
        <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: 'var(--input-placeholder)' }} />
        <input
          type="text"
          value={filtroPermisos}
          onChange={e => setFiltroPermisos(e.target.value)}
          placeholder={t('permisosPlataforma.filterPlaceholder')}
          className="pl-7 pr-3 py-1.5 text-xs rounded-md font-sans w-full focus:outline-none focus:ring-2 focus:ring-orange-400 focus:border-transparent"
          style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
        />
      </div>
    </div>
  )

  // ─────────────────────────────────────────────────────────────────────────
  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <ConfirmDialog />

      {/* ── Page header ── */}
      <div className="flex items-center justify-between px-6 py-5 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
        <div>
          <h1 className="text-lg font-bold flex items-center gap-2" style={{ color: 'var(--page-text)' }}>
            <Shield size={20} className="text-orange-500" />
            {t('platformRoles.title')}
          </h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--page-muted)' }}>{t('platformRoles.subtitle')}</p>
        </div>
        <Button
          label={t('rolesPlataformaPage.newRole')}
          icon="pi pi-plus"
          severity="warning"
          size="small"
          onClick={() => setCrearRolOpen(true)}
        />
      </div>

      {/* ── Stats bar ── */}
      {!loading && roles.length > 0 && (
        <div className="flex items-center gap-6 px-6 py-3 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{roles.length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('rolesPlataformaPage.totalRoles')}</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>
              {roles.reduce((acc, r) => acc + r.total_usuarios, 0)}
            </span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('rolesPlataformaPage.assignedUsers')}</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>
              {new Set(roles.map(r => r.id_compania)).size}
            </span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('rolesPlataformaPage.companies')}</span>
          </div>
        </div>
      )}

      {/* ── Tabs ── */}
      <div className="flex flex-col flex-1 overflow-hidden">

        {/* Tab bar */}
        <div className="flex items-end gap-1 px-4 pt-3 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
          <button
            onClick={() => setActiveTab('roles')}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors"
            style={activeTab === 'roles' ? {
              background: 'var(--page-surface)',
              color: '#f97316',
              borderTop: '1px solid var(--page-border)',
              borderLeft: '1px solid var(--page-border)',
              borderRight: '1px solid var(--page-border)',
              borderBottom: '1px solid var(--page-surface)',
              marginBottom: '-1px',
            } : {
              color: 'var(--page-muted)',
            }}
          >
            <Shield size={12} />
            {t('nav.platformRoles')}
            {!loading && (
              <span className="ml-1 px-1.5 py-0.5 rounded-full text-[0.55rem] font-bold"
                style={{ background: activeTab === 'roles' ? '#f97316' : 'var(--page-border)', color: activeTab === 'roles' ? '#fff' : 'var(--page-muted)' }}
              >
                {filteredRoles.length}
              </span>
            )}
          </button>

          <button
            onClick={() => rolSeleccionado && setActiveTab('permisos')}
            disabled={!rolSeleccionado}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors disabled:cursor-not-allowed disabled:opacity-40"
            style={activeTab === 'permisos' ? {
              background: 'var(--page-surface)',
              color: '#f97316',
              borderTop: '1px solid var(--page-border)',
              borderLeft: '1px solid var(--page-border)',
              borderRight: '1px solid var(--page-border)',
              borderBottom: '1px solid var(--page-surface)',
              marginBottom: '-1px',
            } : {
              color: 'var(--page-muted)',
            }}
          >
            <i className="pi pi-lock text-[0.65rem]" />
            {rolSeleccionado
              ? t('rolesPlataformaPage.permissionsTitle', { name: rolSeleccionado.nombre })
              : t('rolesPlataformaPage.colPermission')}
            {rolSeleccionado && !loadingPermisos && (
              <span className="ml-1 px-1.5 py-0.5 rounded-full text-[0.55rem] font-bold"
                style={{ background: activeTab === 'permisos' ? '#f97316' : 'var(--page-border)', color: activeTab === 'permisos' ? '#fff' : 'var(--page-muted)' }}
              >
                {permisos.length}
              </span>
            )}
          </button>
        </div>

        {/* ── Tab: Roles ── */}
        {activeTab === 'roles' && (
          <div className="flex flex-col flex-1 overflow-auto">
            <div className="px-4 pb-4 pt-3">
              <DataTable
                value={filteredRoles}
                loading={loading}
                globalFilter={globalFilter}
                globalFilterFields={['nombre', 'descripcion', 'nombre_compania']}
                header={rolesTableHeader}
                emptyMessage={rolesEmptyMessage}
                onRowClick={e => seleccionarRol(e.data as RolPlataforma)}
                rowClassName={(row: RolPlataforma) =>
                  `cursor-pointer${rolSeleccionado?.id === row.id ? ' bg-orange-950/40' : ''}`
                }
                paginator
                rows={10}
                rowsPerPageOptions={[5, 10, 25]}
                sortField="nombre_compania"
                sortOrder={1}
                stripedRows
              >
                <Column field="nombre" header={t('platformRoles.colName')} body={rolNombreTemplate} sortable />
                <Column field="nombre_compania" header={t('platformRoles.colCompany')} body={companiaTemplate} sortable style={{ width: '10rem' }} />
                <Column field="total_usuarios" header={t('rolesPlataformaPage.colUsers')} body={usuariosTemplate} sortable style={{ width: '5.5rem', textAlign: 'center' }} />
                <Column header="" body={rolActionsTemplate} style={{ width: '5.5rem' }} />
              </DataTable>
            </div>
          </div>
        )}

        {/* ── Tab: Permisos ── */}
        {activeTab === 'permisos' && (
          <div className="flex flex-col flex-1 overflow-auto">
            {rolSeleccionado ? (
              <>
                {/* Permissions sub-header */}
                <div className="flex items-center justify-between px-5 py-3 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
                  <div className="flex items-center gap-2">
                    <span className="inline-block text-xs bg-blue-900/40 text-blue-300 border border-blue-800/60 px-2 py-0.5 rounded-full">
                      {rolSeleccionado.nombre_compania}
                    </span>
                    {!loadingPermisos && (
                      <span className="text-xs" style={{ color: 'var(--page-muted)' }}>
                        {permisos.length} permiso{permisos.length !== 1 ? 's' : ''} asignado{permisos.length !== 1 ? 's' : ''}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <Button icon="pi pi-plus-circle" label={t('permisosPlataforma.assignLabel')} severity="info" size="small" outlined onClick={() => setAsignarOpen(true)} />
                    <Button icon="pi pi-plus" label={t('permisosPlataforma.crear.submit')} severity="warning" size="small" outlined onClick={() => setCrearPermisoOpen(true)} />
                  </div>
                </div>

                <div className="p-4">
                  <DataTable
                    value={permisos}
                    loading={loadingPermisos}
                    globalFilter={filtroPermisos}
                    globalFilterFields={['nombre', 'modulo', 'nombre_sucursal', 'descripcion']}
                    header={permisosTableHeader}
                    emptyMessage={permisosEmptyMessage}
                    paginator
                    rows={10}
                    rowsPerPageOptions={[5, 10, 25]}
                    sortField="nombre"
                    sortOrder={1}
                    stripedRows
                  >
                    <Column field="id" header="ID" sortable style={{ width: '4rem' }} />
                    <Column field="nombre_sucursal" header={t('rolesPlataformaPage.colBranch')} body={permisoSucursalTemplate} sortable style={{ width: '10rem' }} />
                    <Column field="modulo" header={t('rolesPlataformaPage.colModule')} body={permisoModuloTemplate} sortable style={{ width: '8rem' }} />
                    <Column field="nombre" header={t('rolesPlataformaPage.colPermission')} body={permisoNombreTemplate} sortable />
                    <Column field="descripcion" header={t('rolesPlataformaPage.colDescription')} body={permisoDescripcionTemplate} />
                    <Column header="" body={permisoActionsTemplate} style={{ width: '4rem', textAlign: 'center' }} />
                  </DataTable>
                </div>
              </>
            ) : (
              <div className="flex flex-col items-center justify-center py-16 text-center gap-3 px-8">
                <div className="w-16 h-16 rounded-full flex items-center justify-center" style={{ background: 'var(--page-surface)' }}>
                  <Shield size={28} style={{ color: 'var(--page-muted)' }} />
                </div>
                <div>
                  <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>{t('rolesPlataformaPage.selectRolePlaceholder')}</p>
                  <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>{t('rolesPlataformaPage.selectRoleHint')}</p>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Modals ── */}
      <CrearRolPlataformaModal
        open={crearRolOpen}
        companias={companias}
        onClose={() => setCrearRolOpen(false)}
        onCreado={() => { setCrearRolOpen(false); cargarRoles() }}
      />

      <EditarRolPlataformaModal
        rol={rolEditar}
        onClose={() => setRolEditar(null)}
        onEditado={() => { setRolEditar(null); cargarRoles() }}
      />

      {asignarOpen && rolSeleccionado && (
        <AsignarPermisoModal
          rol={rolSeleccionado}
          permisosAsignados={permisos}
          onClose={() => setAsignarOpen(false)}
          onAsignado={() => {
            setAsignarOpen(false)
            cargarPermisosRol(rolSeleccionado.id)
          }}
        />
      )}

      <CrearPermisoModal
        open={crearPermisoOpen}
        companias={companias}
        onClose={() => setCrearPermisoOpen(false)}
        onCreado={handlePermisoCreado}
      />
    </div>
  )
}
