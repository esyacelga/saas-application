import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { UserCog } from 'lucide-react'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Avatar } from 'primereact/avatar'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { UsuarioStaffPorPersona } from '@/infrastructure/http/auth/auth.dto'

interface Props {
  idPersona: number
  readonly: boolean
}

export function UsuarioStaffTab({ idPersona, readonly: _readonly }: Props) {
  const [usuarios, setUsuarios] = useState<UsuarioStaffPorPersona[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    authRepository.getUsuariosStaffPorPersona(idPersona)
      .then(setUsuarios)
      .catch(() => toast.error('Error al cargar usuarios staff'))
      .finally(() => setLoading(false))
  }, [idPersona])

  const avatarTemplate = (row: UsuarioStaffPorPersona) =>
    row.fotoUrl
      ? <img src={row.fotoUrl} alt={row.nombre} className="w-8 h-8 rounded-full object-cover" />
      : <Avatar icon="pi pi-user" size="normal" shape="circle" />

  const estadoTemplate = (row: UsuarioStaffPorPersona) => (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${row.activo ? 'text-green-600 bg-green-50' : 'text-slate-500 bg-slate-100'}`}>
      {row.activo ? 'Activo' : 'Inactivo'}
    </span>
  )

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
          Cuentas de staff ({usuarios.length})
        </h3>
      </div>

      <div className="rounded-xl overflow-hidden border border-[var(--page-border)]" style={{ background: 'var(--page-surface)' }}>
        <DataTable
          value={usuarios}
          loading={loading}
          emptyMessage={
            <div className="flex flex-col items-center py-8 gap-2 text-[var(--page-muted)]">
              <UserCog size={32} strokeWidth={1.2} />
              <span className="text-sm">Esta persona no tiene usuarios staff</span>
            </div>
          }
          pt={{ wrapper: { className: 'overflow-x-auto' } }}
        >
          <Column header="" body={avatarTemplate} style={{ width: 56 }} />
          <Column field="correo" header="Correo" />
          <Column field="nombreRol" header="Rol" />
          <Column field="activo" header="Estado" body={estadoTemplate} style={{ width: 110 }} />
          <Column
            field="ultimoAcceso"
            header="Último acceso"
            body={(r: UsuarioStaffPorPersona) => r.ultimoAcceso ? new Date(r.ultimoAcceso).toLocaleString('es-EC') : '—'}
            style={{ width: 180 }}
          />
        </DataTable>
      </div>

      <p className="text-xs mt-3" style={{ color: 'var(--page-muted)' }}>
        Para crear o gestionar usuarios staff, ve al módulo de administración del gimnasio correspondiente.
      </p>
    </div>
  )
}
