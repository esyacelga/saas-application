import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Smartphone } from 'lucide-react'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { AppUsuarioPorPersona } from '@/infrastructure/http/auth/auth.dto'

interface Props {
  idPersona: number
  readonly: boolean
}

export function UsuarioAppTab({ idPersona, readonly: _readonly }: Props) {
  const [usuarios, setUsuarios] = useState<AppUsuarioPorPersona[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    authRepository.getAppUsuariosPorPersona(idPersona)
      .then(setUsuarios)
      .catch(() => toast.error('Error al cargar usuarios app'))
      .finally(() => setLoading(false))
  }, [idPersona])

  const estadoTemplate = (row: AppUsuarioPorPersona) => (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${row.activo ? 'text-green-600 bg-green-50' : 'text-slate-500 bg-slate-100'}`}>
      {row.activo ? 'Activo' : 'Inactivo'}
    </span>
  )

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
          Cuentas de app ({usuarios.length})
        </h3>
      </div>

      <div className="rounded-xl overflow-hidden border border-[var(--page-border)]" style={{ background: 'var(--page-surface)' }}>
        <DataTable
          value={usuarios}
          loading={loading}
          emptyMessage={
            <div className="flex flex-col items-center py-8 gap-2 text-[var(--page-muted)]">
              <Smartphone size={32} strokeWidth={1.2} />
              <span className="text-sm">Esta persona no tiene cuentas de app</span>
            </div>
          }
          pt={{ wrapper: { className: 'overflow-x-auto' } }}
        >
          <Column field="login" header="Login (usuario)" />
          <Column field="activo" header="Estado" body={estadoTemplate} style={{ width: 110 }} />
          <Column
            field="ultimoAcceso"
            header="Último acceso"
            body={(r: AppUsuarioPorPersona) => r.ultimoAcceso ? new Date(r.ultimoAcceso).toLocaleString('es-EC') : '—'}
            style={{ width: 180 }}
          />
        </DataTable>
      </div>

      <p className="text-xs mt-3" style={{ color: 'var(--page-muted)' }}>
        Para gestionar cuentas app, usa el módulo "Cuentas de usuario" en el panel de administración.
      </p>
    </div>
  )
}
