import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Building2 } from 'lucide-react'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { ConfirmDialog } from '@/ui/components/ConfirmDialog'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { ClientePorPersona } from '@/infrastructure/http/core/core.dto'
import type { CompaniaBasica } from '@/infrastructure/http/auth/auth.dto'
import { getApiErrorMessage } from '@/lib/api-error'
import { AsignarGimnasioModal } from './GimnasiosTab/AsignarGimnasioModal'
import { EditarClienteModal } from './GimnasiosTab/EditarClienteModal'

const ESTADO_LABELS: Record<string, string> = {
  activo: 'Activo',
  proximo_vencer: 'Próx. vencer',
  vencido: 'Vencido',
  congelado: 'Congelado',
  riesgo_abandono: 'Riesgo abandono',
}

const ESTADO_COLORS: Record<string, string> = {
  activo: 'text-green-600 bg-green-50',
  proximo_vencer: 'text-yellow-600 bg-yellow-50',
  vencido: 'text-red-600 bg-red-50',
  congelado: 'text-blue-600 bg-blue-50',
  riesgo_abandono: 'text-orange-600 bg-orange-50',
}

interface Props {
  idPersona: number
  readonly: boolean
}

export function GimnasiosTab({ idPersona, readonly }: Props) {
  const [clientes, setClientes] = useState<ClientePorPersona[]>([])
  const [companias, setCompanias] = useState<CompaniaBasica[]>([])
  const [loading, setLoading] = useState(true)
  const [asignarOpen, setAsignarOpen] = useState(false)
  const [editarTarget, setEditarTarget] = useState<ClientePorPersona | null>(null)
  const [eliminarTarget, setEliminarTarget] = useState<ClientePorPersona | null>(null)
  const [, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([
      coreRepository.getClientesPorPersona(idPersona),
      authRepository.getCompaniasBasicas(),
    ]).then(([c, comp]) => {
      setClientes(c)
      setCompanias(comp)
    }).catch(() => toast.error('Error al cargar gimnasios'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [idPersona])

  const handleEliminar = async () => {
    if (!eliminarTarget) return
    setDeleting(true)
    try {
      await coreRepository.eliminarCliente(eliminarTarget.id)
      toast.success('Registro eliminado')
      setEliminarTarget(null)
      load()
    } catch (err) {
      toast.error(getApiErrorMessage(err))
    } finally {
      setDeleting(false)
    }
  }

  const nombreCompania = (idCompania: number) =>
    companias.find(c => c.id === idCompania)?.nombre ?? `Gym #${idCompania}`

  const estadoTemplate = (row: ClientePorPersona) => (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${ESTADO_COLORS[row.estado] ?? ''}`}>
      {ESTADO_LABELS[row.estado] ?? row.estado}
    </span>
  )

  const accionesTemplate = (row: ClientePorPersona) => (
    <div className="flex gap-1 opacity-0 group-hover/row:opacity-100 transition-opacity">
      <button
        onClick={e => { e.stopPropagation(); setEditarTarget(row) }}
        className="p-1 rounded hover:bg-orange-100 text-orange-500 transition-colors"
        title="Editar"
      >
        <Pencil size={14} />
      </button>
      {!readonly && (
        <button
          onClick={e => { e.stopPropagation(); setEliminarTarget(row) }}
          className="p-1 rounded hover:bg-red-100 text-red-500 transition-colors"
          title="Eliminar"
        >
          <Trash2 size={14} />
        </button>
      )}
    </div>
  )

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
          Gimnasios asociados ({clientes.length})
        </h3>
        {!readonly && (
          <Button
            label="Asignar gimnasio"
            icon={<Plus size={14} className="mr-1" />}
            size="small"
            onClick={() => setAsignarOpen(true)}
          />
        )}
      </div>

      <div className="rounded-xl overflow-hidden border border-[var(--page-border)]" style={{ background: 'var(--page-surface)' }}>
        <DataTable
          value={clientes}
          loading={loading}
          emptyMessage={
            <div className="flex flex-col items-center py-8 gap-2 text-[var(--page-muted)]">
              <Building2 size={32} strokeWidth={1.2} />
              <span className="text-sm">No hay registros en gimnasios</span>
            </div>
          }
          rowClassName={() => 'group/row'}
          pt={{ wrapper: { className: 'overflow-x-auto' } }}
        >
          <Column header="Gimnasio" body={(r: ClientePorPersona) => nombreCompania(r.id_compania)} />
          <Column field="estado" header="Estado" body={estadoTemplate} style={{ width: 140 }} />
          <Column field="fecha_ingreso" header="Ingreso" style={{ width: 110 }} />
          <Column field="codigo_carnet" header="Carnet" body={(r: ClientePorPersona) => r.codigo_carnet ?? '—'} style={{ width: 130 }} />
          <Column body={accionesTemplate} style={{ width: 80 }} />
        </DataTable>
      </div>

      {asignarOpen && (
        <AsignarGimnasioModal
          idPersona={idPersona}
          companias={companias}
          open={asignarOpen}
          onClose={() => setAsignarOpen(false)}
          onAsignado={() => { setAsignarOpen(false); load() }}
        />
      )}

      {editarTarget && (
        <EditarClienteModal
          cliente={editarTarget}
          companias={companias}
          open={!!editarTarget}
          readonly={readonly}
          onClose={() => setEditarTarget(null)}
          onActualizado={() => { setEditarTarget(null); load() }}
        />
      )}

      <ConfirmDialog
        open={!!eliminarTarget}
        title="Eliminar registro"
        description={`¿Seguro que deseas eliminar el registro de ${nombreCompania(eliminarTarget?.id_compania ?? 0)}? Esta acción es lógica y no borra datos de membresía.`}
        destructive
        onConfirm={handleEliminar}
        onCancel={() => setEliminarTarget(null)}
      />
    </div>
  )
}
