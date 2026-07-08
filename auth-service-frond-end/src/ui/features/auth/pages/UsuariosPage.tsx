import { useState, useEffect, useCallback } from 'react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { Users, Search, Shield } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog'
import { PageHeader } from '@/ui/components/PageHeader'
import { IfPermission } from '@/ui/router/guards/PermissionGuard'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { UsuarioStaff, PermisosUsuario } from '@/infrastructure/http/auth/auth.dto'
import { CrearUsuarioModal } from '../components/CrearUsuarioModal'
import { EditarUsuarioModal } from '../components/EditarUsuarioModal'

// ── Helpers ────────────────────────────────────────────────────────────────────

function formatFecha(fecha: string | null): string {
  if (!fecha) return '—'
  try { return format(new Date(fecha), 'd MMM, HH:mm', { locale: es }) } catch { return '—' }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function EstadoBadge({ activo }: { activo: boolean }) {
  const { t } = useTranslation()
  return (
    <span
      className={`inline-flex items-center gap-1.5 font-medium px-2 py-0.5 rounded-full ${activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}
      style={{ fontSize: '0.55rem' }}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${activo ? 'bg-green-600' : 'bg-red-500'}`} />
      {activo ? t('common.active') : t('common.inactive')}
    </span>
  )
}

function PermisosDetalle({ usuario, onReload }: { usuario: UsuarioStaff; onReload: () => void }) {
  const { t } = useTranslation()
  const [datos, setDatos] = useState<PermisosUsuario | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    authRepository.getPermisosUsuario(usuario.id)
      .then(setDatos)
      .catch(() => setDatos(null))
      .finally(() => setLoading(false))
  }, [usuario.id])

  const porModulo: Record<string, string[]> = {}
  datos?.permisos.forEach(p => {
    const modulo = p.split(':')[0].toUpperCase()
    if (!porModulo[modulo]) porModulo[modulo] = []
    porModulo[modulo].push(p)
  })

  return (
    <>
      {/* Sub-header */}
      <div
        className="flex items-center justify-between px-5 py-3 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--page-border)' }}
      >
        <div className="flex items-center gap-2">
          <span
            className="inline-block text-xs font-medium px-2 py-0.5 rounded-full"
            style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)', color: 'var(--page-muted)' }}
          >
            {usuario.nombre_rol}
          </span>
          {!loading && datos && (
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>
              {datos.permisos.length} permiso{datos.permisos.length !== 1 ? 's' : ''} asignado{datos.permisos.length !== 1 ? 's' : ''}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <EstadoBadge activo={usuario.activo} />
          <IfPermission permiso="usuarios:editar">
            <Button
              label={usuario.activo ? t('users.deactivate') : t('users.activate')}
              icon={usuario.activo ? 'pi pi-ban' : 'pi pi-check-circle'}
              severity={usuario.activo ? 'danger' : 'success'}
              size="small"
              outlined
              onClick={() => {
                confirmDialog({
                  message: usuario.activo
                    ? t('users.confirmDeactivateDesc', { name: usuario.nombre })
                    : t('users.confirmActivateDesc', { name: usuario.nombre }),
                  header: usuario.activo ? t('users.confirmDeactivateTitle') : t('users.confirmActivateTitle'),
                  icon: 'pi pi-exclamation-triangle',
                  acceptLabel: usuario.activo ? t('users.deactivate') : t('users.activate'),
                  rejectLabel: t('common.cancel'),
                  acceptClassName: usuario.activo ? 'p-button-danger' : 'p-button-success',
                  defaultFocus: 'reject',
                  accept: async () => {
                    try {
                      if (usuario.activo) {
                        await authRepository.desactivarUsuario(usuario.id)
                        toast.success(t('users.toastDeactivated', { name: usuario.nombre }))
                      } else {
                        await authRepository.activarUsuario(usuario.id)
                        toast.success(t('users.toastActivated', { name: usuario.nombre }))
                      }
                      onReload()
                    } catch (err) {
                      if (isAxiosError(err) && err.response?.status === 409) {
                        toast.error(t('users.toastLastAdminError'))
                      } else {
                        toast.error(t('users.toastToggleError'))
                      }
                    }
                  },
                })
              }}
            />
          </IfPermission>
        </div>
      </div>

      {/* Permisos por módulo */}
      <div className="flex-1 overflow-y-auto p-5">
        {loading ? (
          <div className="flex items-center justify-center h-32">
            <i className="pi pi-spin pi-spinner text-2xl" style={{ color: 'var(--page-muted)' }} />
          </div>
        ) : !datos ? (
          <p className="text-sm text-center mt-8" style={{ color: 'var(--page-muted)' }}>
            {t('permissions.loadError')}
          </p>
        ) : Object.keys(porModulo).length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-center gap-2">
            <i className="pi pi-lock text-4xl" style={{ color: 'var(--page-border)' }} />
            <p className="text-sm" style={{ color: 'var(--page-muted)' }}>{t('permissions.empty')}</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4">
            {Object.entries(porModulo).map(([modulo, permisos]) => (
              <div
                key={modulo}
                className="rounded-lg p-3"
                style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}
              >
                <p
                  className="text-xs font-semibold uppercase tracking-wider mb-2"
                  style={{ color: 'var(--page-muted)' }}
                >
                  {modulo}
                </p>
                <ul className="space-y-1.5">
                  {permisos.map(p => (
                    <li key={p} className="flex items-center gap-2">
                      <span className="w-1.5 h-1.5 rounded-full bg-orange-500 flex-shrink-0" />
                      <code className="font-mono" style={{ fontSize: '0.6rem', color: 'var(--page-text)' }}>{p}</code>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  )
}

// ── Main page ──────────────────────────────────────────────────────────────────

export function UsuariosPage() {
  const { t } = useTranslation()
  const [usuarios, setUsuarios] = useState<UsuarioStaff[]>([])
  const [loading, setLoading] = useState(true)
  const [globalFilter, setGlobalFilter] = useState('')
  const [activeTab, setActiveTab] = useState<'usuarios' | 'permisos'>('usuarios')
  const [usuarioSeleccionado, setUsuarioSeleccionado] = useState<UsuarioStaff | null>(null)
  const [crearOpen, setCrearOpen] = useState(false)
  const [editarUsuario, setEditarUsuario] = useState<UsuarioStaff | null>(null)
  const [permisosKey, setPermisosKey] = useState(0)

  const cargar = useCallback(async () => {
    setLoading(true)
    try {
      const data = await authRepository.getUsuarios()
      setUsuarios(data)
    } catch {
      toast.error(t('users.toastLoadError'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => { cargar() }, [cargar])

  // Sincronizar usuarioSeleccionado cuando se recarga la lista
  useEffect(() => {
    if (!usuarioSeleccionado) return
    const updated = usuarios.find(u => u.id === usuarioSeleccionado.id)
    if (updated) setUsuarioSeleccionado(updated)
    else { setUsuarioSeleccionado(null); setActiveTab('usuarios') }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuarios])

  const seleccionarUsuario = (u: UsuarioStaff) => {
    setUsuarioSeleccionado(u)
    setActiveTab('permisos')
  }

  const handleReload = () => {
    setPermisosKey(k => k + 1)
    cargar()
  }

  // ── Stats ──────────────────────────────────────────────────────────────────
  const totalActivos = usuarios.filter(u => u.activo).length
  const totalInactivos = usuarios.length - totalActivos
  const rolesUnicos = new Set(usuarios.map(u => u.nombre_rol)).size

  // ── Table header ───────────────────────────────────────────────────────────
  const tableHeader = (
    <div className="flex items-center justify-end">
      <div className="relative">
        <Search
          size={13}
          className="absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none"
          style={{ color: 'var(--input-placeholder)' }}
        />
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

  // ── Column templates ───────────────────────────────────────────────────────
  const nombreTemplate = (u: UsuarioStaff) => (
    <span className="flex items-center gap-1.5">
      {usuarioSeleccionado?.id === u.id && (
        <span className="w-1.5 h-1.5 rounded-full bg-orange-500 flex-shrink-0" />
      )}
      <span
        style={{
          fontWeight: 600,
          color: usuarioSeleccionado?.id === u.id ? '#f97316' : 'var(--page-text)',
          whiteSpace: 'nowrap',
        }}
      >
        {u.nombre}
      </span>
    </span>
  )

  const correoTemplate = (u: UsuarioStaff) => (
    <span style={{ color: 'var(--page-muted)', whiteSpace: 'nowrap' }}>{u.correo}</span>
  )

  const rolTemplate = (u: UsuarioStaff) => (
    <span style={{ color: 'var(--page-muted)', whiteSpace: 'nowrap' }}>{u.nombre_rol}</span>
  )

  const fechaTemplate = (u: UsuarioStaff) => (
    <span style={{ color: 'var(--page-muted)', whiteSpace: 'nowrap' }}>{formatFecha(u.ultimo_acceso)}</span>
  )

  const estadoTemplate = (u: UsuarioStaff) => <EstadoBadge activo={u.activo} />

  const accionesTemplate = (u: UsuarioStaff) => (
    <div className="flex items-center gap-0.5" onClick={e => e.stopPropagation()}>
      <IfPermission permiso="usuarios:editar">
        <Button
          icon="pi pi-pencil"
          text
          size="small"
          tooltip={t('users.edit')}
          tooltipOptions={{ position: 'top' }}
          onClick={() => setEditarUsuario(u)}
          pt={{ root: { className: 'text-orange-500 hover:text-orange-400 !py-0.5 !px-1' } }}
        />
      </IfPermission>
    </div>
  )

  const emptyMessage = (
    <div className="flex flex-col items-center justify-center py-14 text-center">
      <Users size={36} className="mb-3" style={{ color: 'var(--page-border)' }} />
      <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>{t('users.emptyTitle')}</p>
      <p className="text-xs mt-1 mb-4" style={{ color: 'var(--page-muted)' }}>{t('users.emptyDescription')}</p>
      <Button label={t('users.newUser')} icon="pi pi-user-plus" severity="warning" size="small" onClick={() => setCrearOpen(true)} />
    </div>
  )

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <ConfirmDialog />

      {/* Page header */}
      <PageHeader
        title={t('users.title')}
        description={t('users.description')}
        action={
          <IfPermission permiso="usuarios:crear">
            <Button
              label={t('users.newUser')}
              icon="pi pi-user-plus"
              severity="warning"
              size="small"
              onClick={() => setCrearOpen(true)}
            />
          </IfPermission>
        }
      />

      {/* Stats bar */}
      {!loading && usuarios.length > 0 && (
        <div className="flex items-center gap-6 px-6 py-3 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{usuarios.length}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('users.statTotal', 'Usuarios')}</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold text-green-500">{totalActivos}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('common.active')}</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold text-red-400">{totalInactivos}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('common.inactive')}</span>
          </div>
          <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{rolesUnicos}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('users.statRoles', 'Roles distintos')}</span>
          </div>
        </div>
      )}

      {/* Tabs + content */}
      <div className="flex flex-col flex-1 overflow-hidden">

        {/* Tab bar */}
        <div className="flex items-end gap-1 px-4 pt-3 flex-shrink-0" style={{ borderBottom: '1px solid var(--page-border)' }}>
          <button
            onClick={() => setActiveTab('usuarios')}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors"
            style={activeTab === 'usuarios' ? {
              background: 'var(--page-surface)',
              color: '#f97316',
              borderTop: '1px solid var(--page-border)',
              borderLeft: '1px solid var(--page-border)',
              borderRight: '1px solid var(--page-border)',
              borderBottom: '1px solid var(--page-surface)',
              marginBottom: '-1px',
            } : { color: 'var(--page-muted)' }}
          >
            <Users size={12} />
            {t('users.title')}
            {!loading && (
              <span
                className="ml-1 px-1.5 py-0.5 rounded-full text-[0.55rem] font-bold"
                style={{
                  background: activeTab === 'usuarios' ? '#f97316' : 'var(--page-border)',
                  color: activeTab === 'usuarios' ? '#fff' : 'var(--page-muted)',
                }}
              >
                {usuarios.length}
              </span>
            )}
          </button>

          <button
            onClick={() => usuarioSeleccionado && setActiveTab('permisos')}
            disabled={!usuarioSeleccionado}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors disabled:cursor-not-allowed disabled:opacity-40"
            style={activeTab === 'permisos' ? {
              background: 'var(--page-surface)',
              color: '#f97316',
              borderTop: '1px solid var(--page-border)',
              borderLeft: '1px solid var(--page-border)',
              borderRight: '1px solid var(--page-border)',
              borderBottom: '1px solid var(--page-surface)',
              marginBottom: '-1px',
            } : { color: 'var(--page-muted)' }}
          >
            <Shield size={12} />
            {usuarioSeleccionado
              ? usuarioSeleccionado.nombre
              : t('users.viewPermissions')}
          </button>
        </div>

        {/* Tab: Usuarios */}
        {activeTab === 'usuarios' && (
          <div className="flex-1 overflow-auto p-4">
            <DataTable
              value={usuarios}
              loading={loading}
              globalFilter={globalFilter}
              globalFilterFields={['nombre', 'correo', 'nombre_rol']}
              header={tableHeader}
              emptyMessage={emptyMessage}
              onRowClick={e => seleccionarUsuario(e.data as UsuarioStaff)}
              rowClassName={(row: UsuarioStaff) =>
                `cursor-pointer${usuarioSeleccionado?.id === row.id ? ' bg-orange-950/40' : ''}`
              }
              paginator
              rows={10}
              rowsPerPageOptions={[5, 10, 25]}
              defaultSortOrder={1}
              sortField="nombre"
              stripedRows
              showGridlines={false}
              size="small"
            >
              <Column field="nombre" header={t('users.colName')} sortable body={nombreTemplate} />
              <Column field="correo" header={t('users.colEmail')} sortable body={correoTemplate} />
              <Column field="nombre_rol" header={t('users.colRole')} sortable body={rolTemplate} />
              <Column field="ultimo_acceso" header={t('users.colLastAccess')} sortable body={fechaTemplate} />
              <Column field="activo" header={t('users.colStatus')} sortable body={estadoTemplate} style={{ width: '7rem' }} />
              <Column header="" body={accionesTemplate} style={{ width: '4rem' }} />
            </DataTable>
          </div>
        )}

        {/* Tab: Permisos del usuario seleccionado */}
        {activeTab === 'permisos' && (
          <div className="flex flex-col flex-1 overflow-hidden">
            {usuarioSeleccionado ? (
              <PermisosDetalle
                key={`${usuarioSeleccionado.id}-${permisosKey}`}
                usuario={usuarioSeleccionado}
                onReload={handleReload}
              />
            ) : (
              <div className="flex flex-col items-center justify-center py-16 text-center gap-3 px-8">
                <div className="w-16 h-16 rounded-full flex items-center justify-center" style={{ background: 'var(--page-surface)' }}>
                  <Users size={28} style={{ color: 'var(--page-muted)' }} />
                </div>
                <div>
                  <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>
                    {t('users.selectUserPlaceholder', 'Selecciona un usuario de la lista')}
                  </p>
                  <p className="text-xs mt-1" style={{ color: 'var(--page-muted)' }}>
                    {t('users.selectUserHint', 'Haz clic en una fila para ver sus permisos')}
                  </p>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Modals */}
      <CrearUsuarioModal
        open={crearOpen}
        onClose={() => setCrearOpen(false)}
        onCreado={() => { setCrearOpen(false); cargar() }}
      />

      <EditarUsuarioModal
        usuario={editarUsuario}
        onClose={() => setEditarUsuario(null)}
        onEditado={() => { setEditarUsuario(null); cargar() }}
      />
    </div>
  )
}
