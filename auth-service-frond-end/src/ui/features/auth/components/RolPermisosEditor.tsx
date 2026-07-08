import { useState, useEffect, useMemo } from 'react'
import { toast } from 'sonner'
import { X, Loader2, ChevronDown, ChevronUp } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Permiso, Rol } from '@/infrastructure/http/auth/auth.dto'

interface Props {
  rol: Rol
  onClose: () => void
}

export function RolPermisosEditor({ rol, onClose }: Props) {
  const { t } = useTranslation()
  const [todosPermisos, setTodosPermisos] = useState<Permiso[]>([])
  const [seleccionados, setSeleccionados] = useState<Set<number>>(new Set())
  const [loading, setLoading] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [modulosExpandidos, setModulosExpandidos] = useState<Set<string>>(new Set())

  useEffect(() => {
    setLoading(true)
    Promise.all([authRepository.getPermisos(), authRepository.getRolPermisos(rol.id)])
      .then(([todos, rolData]) => {
        setTodosPermisos(todos)
        setSeleccionados(new Set(rolData.permisos.map(p => p.id)))
        const mods = new Set(todos.map(p => p.modulo))
        setModulosExpandidos(mods)
      })
      .catch(() => toast.error(t('roles.permissionsLoadError')))
      .finally(() => setLoading(false))
  }, [rol.id, t])

  const porModulo = useMemo(() => {
    return todosPermisos.reduce<Record<string, Permiso[]>>((acc, p) => {
      if (!acc[p.modulo]) acc[p.modulo] = []
      acc[p.modulo].push(p)
      return acc
    }, {})
  }, [todosPermisos])

  const togglePermiso = (id: number) => {
    setSeleccionados(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const toggleModulo = (modulo: string) => {
    const permisosDelModulo = porModulo[modulo] ?? []
    const todosSeleccionados = permisosDelModulo.every(p => seleccionados.has(p.id))
    setSeleccionados(prev => {
      const next = new Set(prev)
      if (todosSeleccionados) {
        permisosDelModulo.forEach(p => next.delete(p.id))
      } else {
        permisosDelModulo.forEach(p => next.add(p.id))
      }
      return next
    })
  }

  const toggleExpandirModulo = (modulo: string) => {
    setModulosExpandidos(prev => {
      const next = new Set(prev)
      if (next.has(modulo)) next.delete(modulo)
      else next.add(modulo)
      return next
    })
  }

  const guardar = async () => {
    setGuardando(true)
    try {
      await authRepository.actualizarRolPermisos(rol.id, {
        id_permisos: Array.from(seleccionados),
      })
      toast.success(t('roles.permissionsSaved', { name: rol.nombre }))
      onClose()
    } catch {
      toast.error(t('roles.permissionsSaveError'))
    } finally {
      setGuardando(false)
    }
  }

  const totalSeleccionados = seleccionados.size
  const totalPermisos = todosPermisos.length

  return (
    <>
      <div
        className="fixed inset-0 z-20 bg-black/30"
        onClick={onClose}
        aria-hidden="true"
      />

      <aside
        className="fixed inset-y-0 right-0 z-30 w-96 shadow-2xl flex flex-col"
        style={{ background: 'var(--page-surface)', color: 'var(--page-text)' }}
      >
        <div
          className="flex items-start justify-between px-5 py-4"
          style={{ borderBottom: '1px solid var(--page-border)' }}
        >
          <div>
            <h2 className="font-semibold" style={{ color: 'var(--page-text)' }}>
              {t('roles.permissionsTitle', { name: rol.nombre })}
            </h2>
            {!loading && (
              <p className="text-xs mt-0.5" style={{ color: 'var(--page-muted)' }}>
                {t('roles.permissionsSelected', { count: totalSeleccionados, total: totalPermisos })}
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg transition-colors hover:bg-[var(--page-border)]"
            style={{ color: 'var(--page-muted)' }}
          >
            <X size={18} />
          </button>
        </div>

        {!loading && totalPermisos > 0 && (
          <div
            className="px-5 py-2"
            style={{ borderBottom: '1px solid var(--page-border)', background: 'var(--page-bg)' }}
          >
            <div className="w-full rounded-full h-1.5" style={{ background: 'var(--page-border)' }}>
              <div
                className="bg-orange-500 h-1.5 rounded-full transition-all duration-300"
                style={{ width: `${(totalSeleccionados / totalPermisos) * 100}%` }}
              />
            </div>
          </div>
        )}

        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <Loader2 size={24} className="animate-spin" style={{ color: 'var(--page-muted)' }} />
            </div>
          ) : (
            <div className="divide-y divide-[var(--page-border)]">
              {Object.entries(porModulo).map(([modulo, permisos]) => {
                const todosChecked = permisos.every(p => seleccionados.has(p.id))
                const algunoChecked = permisos.some(p => seleccionados.has(p.id))
                const expandido = modulosExpandidos.has(modulo)

                return (
                  <div key={modulo}>
                    <div
                      className="flex items-center gap-2 px-5 py-3"
                      style={{ background: 'var(--page-bg)' }}
                    >
                      <input
                        type="checkbox"
                        checked={todosChecked}
                        ref={el => {
                          if (el) el.indeterminate = algunoChecked && !todosChecked
                        }}
                        onChange={() => toggleModulo(modulo)}
                        className="w-4 h-4 rounded accent-orange-500 cursor-pointer"
                        id={`modulo-${modulo}`}
                      />
                      <label
                        htmlFor={`modulo-${modulo}`}
                        className="flex-1 text-xs font-bold uppercase tracking-wider cursor-pointer select-none"
                        style={{ color: 'var(--page-muted)' }}
                      >
                        {modulo}
                      </label>
                      <span className="text-xs" style={{ color: 'var(--page-muted)' }}>
                        {permisos.filter(p => seleccionados.has(p.id)).length}/{permisos.length}
                      </span>
                      <button
                        onClick={() => toggleExpandirModulo(modulo)}
                        className="transition-colors hover:text-orange-500"
                        style={{ color: 'var(--page-muted)' }}
                      >
                        {expandido ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                      </button>
                    </div>

                    {expandido && (
                      <div className="px-5 py-2 space-y-2">
                        {permisos.map(p => (
                          <label
                            key={p.id}
                            className="flex items-start gap-3 cursor-pointer group py-1"
                          >
                            <input
                              type="checkbox"
                              checked={seleccionados.has(p.id)}
                              onChange={() => togglePermiso(p.id)}
                              className="w-4 h-4 mt-0.5 rounded accent-orange-500 cursor-pointer flex-shrink-0"
                            />
                            <div>
                              <code className="text-xs font-mono text-orange-500 group-hover:text-orange-400">
                                {p.nombre}
                              </code>
                              {p.descripcion && (
                                <p className="text-xs mt-0.5" style={{ color: 'var(--page-muted)' }}>
                                  {p.descripcion}
                                </p>
                              )}
                            </div>
                          </label>
                        ))}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>

        <div className="px-5 py-4 flex gap-3" style={{ borderTop: '1px solid var(--page-border)' }}>
          <button
            onClick={onClose}
            className="flex-1 rounded-lg py-2.5 text-sm transition-colors font-medium hover:bg-[var(--page-border)]"
            style={{
              border: '1px solid var(--page-border)',
              color: 'var(--page-text)',
            }}
          >
            {t('roles.permissionsCancel')}
          </button>
          <button
            onClick={guardar}
            disabled={guardando || loading}
            className="flex-1 flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 disabled:opacity-60 text-white rounded-lg py-2.5 text-sm font-semibold transition-colors"
          >
            {guardando && <Loader2 size={14} className="animate-spin" />}
            {guardando ? t('roles.permissionsSaving') : t('roles.permissionsSave')}
          </button>
        </div>
      </aside>
    </>
  )
}
