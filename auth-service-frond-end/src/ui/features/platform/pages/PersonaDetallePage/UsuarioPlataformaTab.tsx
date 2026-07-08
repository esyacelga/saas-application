import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Plus, ShieldCheck } from 'lucide-react'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Avatar } from 'primereact/avatar'
import { Button } from 'primereact/button'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { OperadorPlataformaPorPersona } from '@/infrastructure/http/auth/auth.dto'

const ROL_LABELS: Record<string, string> = {
  super_admin: 'Super admin',
  soporte: 'Soporte',
  viewer: 'Viewer',
}

interface Props {
  idPersona: number
  readonly: boolean
}

export function UsuarioPlataformaTab({ idPersona, readonly }: Props) {
  const [operadores, setOperadores] = useState<OperadorPlataformaPorPersona[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    authRepository.getOperadoresPlataformaPorPersona(idPersona)
      .then(setOperadores)
      .catch(() => toast.error('Error al cargar usuarios plataforma'))
      .finally(() => setLoading(false))
  }, [idPersona])

  const avatarTemplate = (row: OperadorPlataformaPorPersona) =>
    row.fotoUrl
      ? <img src={row.fotoUrl} alt={row.nombre} className="w-8 h-8 rounded-full object-cover" />
      : <Avatar icon="pi pi-user" size="normal" shape="circle" />

  const estadoTemplate = (row: OperadorPlataformaPorPersona) => (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${row.activo ? 'text-green-600 bg-green-50' : 'text-slate-500 bg-slate-100'}`}>
      {row.activo ? 'Activo' : 'Inactivo'}
    </span>
  )

  const rolTemplate = (row: OperadorPlataformaPorPersona) => (
    <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-purple-50 text-purple-600">
      {ROL_LABELS[row.rolPlataforma] ?? row.rolPlataforma}
    </span>
  )

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
          Cuentas de plataforma ({operadores.length})
        </h3>
        {!readonly && (
          <Button
            label="Crear operador"
            icon={<Plus size={14} className="mr-1" />}
            size="small"
            disabled
            title="Usa el módulo Operadores para crear una cuenta vinculando esta persona"
          />
        )}
      </div>

      <div className="rounded-xl overflow-hidden border border-[var(--page-border)]" style={{ background: 'var(--page-surface)' }}>
        <DataTable
          value={operadores}
          loading={loading}
          emptyMessage={
            <div className="flex flex-col items-center py-8 gap-2 text-[var(--page-muted)]">
              <ShieldCheck size={32} strokeWidth={1.2} />
              <span className="text-sm">Esta persona no tiene cuenta de plataforma</span>
            </div>
          }
          pt={{ wrapper: { className: 'overflow-x-auto' } }}
        >
          <Column header="" body={avatarTemplate} style={{ width: 56 }} />
          <Column field="correo" header="Correo" />
          <Column field="rolPlataforma" header="Rol" body={rolTemplate} style={{ width: 140 }} />
          <Column field="activo" header="Estado" body={estadoTemplate} style={{ width: 110 }} />
          <Column
            field="ultimoAcceso"
            header="Último acceso"
            body={(r: OperadorPlataformaPorPersona) => r.ultimoAcceso ? new Date(r.ultimoAcceso).toLocaleString('es-EC') : '—'}
            style={{ width: 180 }}
          />
        </DataTable>
      </div>

      <p className="text-xs mt-3" style={{ color: 'var(--page-muted)' }}>
        Para crear o editar operadores de plataforma, usa el módulo "Operadores" del menú principal.
      </p>
    </div>
  )
}
