import { useEffect, useRef, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, UserRound, Plus } from 'lucide-react'
import { toast } from 'sonner'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { Avatar } from 'primereact/avatar'
import { PageHeader } from '@/ui/components/PageHeader'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Persona, ListarPersonasParams, PersonaPageResponse } from '@/infrastructure/http/auth/auth.dto'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'
import { CrearPersonaModal } from './PersonasPage/CrearPersonaModal'

const SEXO_OPTIONS = [
  { value: '', label: 'Todos' },
  { value: 'M', label: 'Masculino' },
  { value: 'F', label: 'Femenino' },
  { value: 'O', label: 'Otro' },
]

const PAGE_SIZE = 20

export function PersonasPage() {
  const navigate = useNavigate()
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const canCreate = user?.rol_plataforma === 'super_admin' || user?.rol_plataforma === 'soporte'

  const [page, setPage] = useState<PersonaPageResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [currentPage, setCurrentPage] = useState(0)
  const [crearOpen, setCrearOpen] = useState(false)

  const [nombre, setNombre] = useState('')
  const [ci, setCi] = useState('')
  const [correo, setCorreo] = useState('')
  const [sexo, setSexo] = useState('')

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const load = useCallback((params: ListarPersonasParams) => {
    setLoading(true)
    authRepository.listarPersonas(params)
      .then(setPage)
      .catch(() => toast.error('Error al cargar personas'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      setCurrentPage(0)
      load({
        nombre: nombre || undefined,
        ci: ci || undefined,
        correo: correo || undefined,
        sexo: (sexo as 'M' | 'F' | 'O') || undefined,
        page: 0,
        size: PAGE_SIZE,
      })
    }, 350)
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current) }
  }, [nombre, ci, correo, sexo, load])

  const onPageChange = (e: { page: number }) => {
    const p = e.page
    setCurrentPage(p)
    load({
      nombre: nombre || undefined,
      ci: ci || undefined,
      correo: correo || undefined,
      sexo: (sexo as 'M' | 'F' | 'O') || undefined,
      page: p,
      size: PAGE_SIZE,
    })
  }

  const fotoTemplate = (row: Persona) => (
    row.foto_url
      ? <img src={row.foto_url} alt={row.nombre} className="w-9 h-9 rounded-full object-cover" />
      : <Avatar icon="pi pi-user" size="normal" shape="circle" className="bg-[var(--page-muted)] text-[var(--page-text)]" />
  )

  const sexoLabel = (s?: string) => s === 'M' ? 'Masculino' : s === 'F' ? 'Femenino' : s === 'O' ? 'Otro' : '—'

  return (
    <div className="p-6 min-h-screen" style={{ background: 'var(--page-bg)', color: 'var(--page-text)' }}>
      <PageHeader
        title="Personas"
        description="Administra las personas registradas en el sistema"
        action={
          canCreate
            ? <Button label="Nueva Persona" icon={<Plus size={16} className="mr-1" />} size="small" onClick={() => setCrearOpen(true)} />
            : undefined
        }
      />

      {/* Filtros */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 mt-6 mb-5">
        <div className="relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--page-muted)]" />
          <input
            value={nombre}
            onChange={e => setNombre(e.target.value)}
            placeholder="Nombre..."
            className="w-full pl-9 pr-3 py-2 text-sm rounded-md border border-[var(--page-border)] bg-[var(--input-bg)] focus:outline-none focus:ring-1 focus:ring-orange-400"
          />
        </div>
        <div className="relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--page-muted)]" />
          <input
            value={ci}
            onChange={e => setCi(e.target.value)}
            placeholder="Cédula..."
            className="w-full pl-9 pr-3 py-2 text-sm rounded-md border border-[var(--page-border)] bg-[var(--input-bg)] focus:outline-none focus:ring-1 focus:ring-orange-400"
          />
        </div>
        <div className="relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--page-muted)]" />
          <input
            value={correo}
            onChange={e => setCorreo(e.target.value)}
            placeholder="Correo..."
            className="w-full pl-9 pr-3 py-2 text-sm rounded-md border border-[var(--page-border)] bg-[var(--input-bg)] focus:outline-none focus:ring-1 focus:ring-orange-400"
          />
        </div>
        <select
          value={sexo}
          onChange={e => setSexo(e.target.value)}
          className="w-full px-3 py-2 text-sm rounded-md border border-[var(--page-border)] bg-[var(--input-bg)] focus:outline-none focus:ring-1 focus:ring-orange-400"
        >
          {SEXO_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>

      {/* Tabla */}
      <div className="rounded-xl overflow-hidden border border-[var(--page-border)]" style={{ background: 'var(--page-surface)' }}>
        <DataTable
          value={page?.content ?? []}
          loading={loading}
          rows={PAGE_SIZE}
          totalRecords={page?.totalElements ?? 0}
          lazy
          paginator
          first={currentPage * PAGE_SIZE}
          onPage={onPageChange}
          emptyMessage={
            <div className="flex flex-col items-center py-10 gap-2 text-[var(--page-muted)]">
              <UserRound size={36} strokeWidth={1.2} />
              <span className="text-sm">No se encontraron personas</span>
            </div>
          }
          rowClassName={() => 'cursor-pointer hover:bg-orange-500/5 transition-colors'}
          onRowClick={e => navigate(`/platform/personas/${(e.data as Persona).id}`)}
          pt={{ wrapper: { className: 'overflow-x-auto' } }}
        >
          <Column header="Foto" body={fotoTemplate} style={{ width: 64 }} />
          <Column field="ci" header="Cédula" />
          <Column field="nombre" header="Nombre" />
          <Column field="telefono" header="Teléfono" body={(r: Persona) => r.telefono ?? '—'} />
          <Column field="correo" header="Correo" body={(r: Persona) => r.correo ?? '—'} />
          <Column field="sexo" header="Sexo" body={(r: Persona) => sexoLabel(r.sexo)} style={{ width: 110 }} />
          <Column
            field="fecha_nacimiento"
            header="Nacimiento"
            body={(r: Persona) => r.fecha_nacimiento ?? '—'}
            style={{ width: 120 }}
          />
        </DataTable>
      </div>

      {crearOpen && (
        <CrearPersonaModal
          open={crearOpen}
          onClose={() => setCrearOpen(false)}
          onCreada={p => {
            setCrearOpen(false)
            navigate(`/platform/personas/${p.id}`)
          }}
        />
      )}
    </div>
  )
}
