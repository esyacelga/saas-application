import { useEffect, useState } from 'react'
import { Users, UserCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from 'primereact/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { UsuarioStaff } from '@/infrastructure/http/auth/auth.dto'
import { EditarUsuarioModal } from './EditarUsuarioModal'

interface Props { idCompania: number }

function formatUltimoAcceso(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('es-EC', { dateStyle: 'short', timeStyle: 'short' })
}

function AvatarCell({ src, nombre }: { src: string | null; nombre: string }) {
  if (src) {
    return (
      <div className="w-[38px] h-[38px] rounded-full flex-shrink-0 overflow-hidden"
        style={{ border: '2px solid var(--page-border)', boxShadow: '0 0 0 2px var(--page-surface)' }}>
        <img
          src={src}
          alt={nombre}
          className="w-full h-full object-cover rounded-full"
        />
      </div>
    )
  }
  return <UserCircle2 size={34} style={{ color: 'var(--page-muted)' }} />
}

export function UsuariosTab({ idCompania }: Props) {
  const [usuarios, setUsuarios] = useState<UsuarioStaff[]>([])
  const [loading, setLoading] = useState(true)
  const [editTarget, setEditTarget] = useState<UsuarioStaff | null>(null)

  const load = () => {
    setLoading(true)
    authRepository.getUsuariosStaffByCompania(idCompania)
      .then(setUsuarios)
      .catch(() => toast.error('Error al cargar los usuarios del gimnasio'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [idCompania])

  if (loading) return (
    <div className="space-y-3 p-4">
      {[1, 2, 3].map(i => <Skeleton key={i} className="h-10 w-full rounded-lg" />)}
    </div>
  )

  return (
    <div className="p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
          Usuarios del staff
        </h3>
        <span className="text-xs" style={{ color: 'var(--page-muted)' }}>
          {usuarios.length} {usuarios.length === 1 ? 'usuario' : 'usuarios'}
        </span>
      </div>

      {usuarios.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-12 gap-3">
          <div className="w-12 h-12 rounded-full flex items-center justify-center" style={{ background: 'var(--page-surface)' }}>
            <Users size={22} style={{ color: 'var(--page-muted)' }} />
          </div>
          <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
            No hay usuarios registrados en este gimnasio
          </p>
        </div>
      ) : (
        <div className="rounded-lg overflow-hidden" style={{ border: '1px solid var(--page-border)' }}>
          <table className="w-full table-dense">
            <thead>
              <tr style={{ background: 'var(--page-surface)', borderBottom: '1px solid var(--page-border)' }}>
                <th className="text-left uppercase" style={{ color: 'var(--page-muted)', width: '44px' }} />
                <th className="text-left uppercase" style={{ color: 'var(--page-muted)' }}>Nombre</th>
                <th className="text-left uppercase" style={{ color: 'var(--page-muted)' }}>Correo</th>
                <th className="text-left uppercase" style={{ color: 'var(--page-muted)' }}>Rol</th>
                <th className="text-left uppercase" style={{ color: 'var(--page-muted)' }}>Último acceso</th>
                <th className="text-left uppercase" style={{ color: 'var(--page-muted)' }}>Estado</th>
                <th className="text-right uppercase" style={{ color: 'var(--page-muted)' }}>Acción</th>
              </tr>
            </thead>
            <tbody>
              {usuarios.map((u, i) => (
                <tr
                  key={u.id}
                  style={{
                    background: i % 2 === 0 ? 'var(--page-surface)' : 'var(--page-bg)',
                    borderBottom: '1px solid var(--page-border)',
                  }}
                >
                  <td><AvatarCell src={u.foto_url} nombre={u.nombre} /></td>
                  <td className="font-medium" style={{ color: 'var(--page-text)' }}>{u.nombre}</td>
                  <td className="font-mono" style={{ color: 'var(--page-muted)', fontSize: '0.6rem' }}>{u.correo}</td>
                  <td>
                    <span className="px-1.5 py-0.5 rounded font-medium"
                      style={{ fontSize: '0.58rem', background: 'var(--page-surface)', border: '1px solid var(--page-border)', color: 'var(--page-text)' }}>
                      {u.nombre_rol}
                    </span>
                  </td>
                  <td style={{ color: 'var(--page-muted)' }}>{formatUltimoAcceso(u.ultimo_acceso)}</td>
                  <td>
                    <Badge
                      variant={u.activo ? 'default' : 'secondary'}
                      className={u.activo ? 'bg-green-500/20 text-green-700 border-green-300' : ''}
                    >
                      {u.activo ? 'Activo' : 'Inactivo'}
                    </Badge>
                  </td>
                  <td className="text-right">
                    <Button
                      icon="pi pi-pencil"
                      text
                      size="small"
                      tooltip="Editar"
                      tooltipOptions={{ position: 'top' }}
                      onClick={() => setEditTarget(u)}
                      pt={{ root: { className: 'text-orange-500 hover:text-orange-400' } }}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <EditarUsuarioModal
        open={editTarget !== null}
        idCompania={idCompania}
        usuario={editTarget}
        onClose={() => setEditTarget(null)}
        onUpdated={() => { setEditTarget(null); load() }}
      />
    </div>
  )
}
