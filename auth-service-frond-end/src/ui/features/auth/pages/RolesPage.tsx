import { useState, useEffect, useCallback } from 'react'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { Shield, Search, KeyRound } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { PageHeader } from '@/ui/components/PageHeader'
import { ConfirmDialog } from '@/ui/components/ConfirmDialog'
import { IfPermission } from '@/ui/router/guards/PermissionGuard'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Rol } from '@/infrastructure/http/auth/auth.dto'
import { CrearRolModal } from '../components/CrearRolModal'
import { RolPermisosEditor } from '../components/RolPermisosEditor'

function EmptyState({ onAdd }: { onAdd: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <Shield size={48} className="mb-4" style={{ color: 'var(--page-border)' }} />
      <h3 className="font-medium" style={{ color: 'var(--page-text)' }}>{t('roles.emptyTitle')}</h3>
      <p className="text-sm mt-1 mb-5" style={{ color: 'var(--page-muted)' }}>
        {t('roles.emptyDescription')}
      </p>
      <Button
        label={t('roles.newRole')}
        icon="pi pi-plus"
        severity="warning"
        size="small"
        onClick={onAdd}
      />
    </div>
  )
}

export function RolesPage() {
  const { t } = useTranslation()
  const [roles, setRoles] = useState<Rol[]>([])
  const [loading, setLoading] = useState(true)
  const [globalFilter, setGlobalFilter] = useState('')
  const [crearOpen, setCrearOpen] = useState(false)
  const [rolEditor, setRolEditor] = useState<Rol | null>(null)
  const [rolEliminar, setRolEliminar] = useState<Rol | null>(null)

  const cargar = useCallback(async () => {
    setLoading(true)
    try {
      const data = await authRepository.getRoles()
      setRoles(data)
    } catch {
      toast.error(t('roles.toastLoadError'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => { cargar() }, [cargar])

  const handleEliminar = async (rol: Rol) => {
    try {
      await authRepository.eliminarRol(rol.id)
      toast.success(t('roles.toastDeleted', { name: rol.nombre }))
      cargar()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error(t('roles.toastUsersAssigned'))
      } else {
        toast.error(t('roles.toastDeleteError'))
      }
    } finally {
      setRolEliminar(null)
    }
  }

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
          style={{
            background: 'var(--input-bg)',
            border: '1px solid var(--input-border)',
            color: 'var(--input-text)',
          }}
        />
      </div>
    </div>
  )

  const descripcionTemplate = (rol: Rol) =>
    rol.descripcion ?? (
      <span className="italic" style={{ color: 'var(--page-border)', fontSize: '0.64rem' }}>
        {t('roles.noDescription')}
      </span>
    )

  const accionesTemplate = (rol: Rol) => (
    <div className="flex items-center gap-0.5">
      <IfPermission permiso="roles:editar">
        <Button
          label={t('roles.editPermissions')}
          text
          size="small"
          onClick={() => setRolEditor(rol)}
          pt={{ root: { className: 'text-orange-500 hover:text-orange-600 !text-[0.6rem] !px-1.5 !py-0.5' } }}
        />
      </IfPermission>
      <IfPermission permiso="roles:eliminar">
        <Button
          label={t('roles.delete')}
          text
          size="small"
          severity="danger"
          onClick={() => setRolEliminar(rol)}
          pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }}
        />
      </IfPermission>
    </div>
  )

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <PageHeader
        title={t('roles.title')}
        description={t('roles.description')}
        action={
          <IfPermission permiso="roles:crear">
            <Button
              label={t('roles.newRole')}
              icon="pi pi-plus"
              severity="warning"
              size="small"
              onClick={() => setCrearOpen(true)}
            />
          </IfPermission>
        }
      />

      {/* Stats bar */}
      <div
        className="flex items-center gap-6 px-6 py-3 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--page-border)' }}
      >
        <div className="flex items-center gap-2">
          <KeyRound size={16} style={{ color: 'var(--page-muted)' }} />
          <span className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{roles.length}</span>
          <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('roles.statTotal', 'roles en total')}</span>
        </div>
      </div>

      <div className="flex-1 overflow-auto p-4">
        <DataTable
          value={roles}
          loading={loading}
          globalFilter={globalFilter}
          globalFilterFields={['nombre', 'descripcion']}
          header={tableHeader}
          emptyMessage={<EmptyState onAdd={() => setCrearOpen(true)} />}
          paginator
          rows={10}
          rowsPerPageOptions={[5, 10, 25]}
          defaultSortOrder={1}
          sortField="nombre"
          stripedRows
          showGridlines={false}
          size="small"
        >
          <Column
            field="nombre"
            header={t('roles.colName')}
            sortable
            style={{ fontWeight: 600, whiteSpace: 'nowrap', color: 'var(--page-text)' }}
          />
          <Column
            field="descripcion"
            header={t('roles.colDescription')}
            sortable
            body={descripcionTemplate}
            style={{ color: 'var(--page-muted)', maxWidth: '20rem' }}
          />
          <Column
            header={t('roles.colActions')}
            body={accionesTemplate}
            style={{ width: '12rem' }}
          />
        </DataTable>
      </div>

      <CrearRolModal
        open={crearOpen}
        onClose={() => setCrearOpen(false)}
        onCreado={() => { setCrearOpen(false); cargar() }}
      />

      {rolEditor && (
        <RolPermisosEditor
          rol={rolEditor}
          onClose={() => { setRolEditor(null); cargar() }}
        />
      )}

      <ConfirmDialog
        open={rolEliminar !== null}
        title={t('roles.confirmDeleteTitle')}
        description={t('roles.confirmDeleteDesc', { name: rolEliminar?.nombre })}
        onConfirm={() => rolEliminar && handleEliminar(rolEliminar)}
        onCancel={() => setRolEliminar(null)}
        destructive
      />
    </div>
  )
}
