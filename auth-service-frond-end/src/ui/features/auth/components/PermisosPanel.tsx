import { useEffect, useState } from 'react'
import { X, Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { PermisosUsuario } from '@/infrastructure/http/auth/auth.dto'

interface Props {
  usuarioId: number
  usuarioNombre: string
  onClose: () => void
}

export function PermisosPanel({ usuarioId, usuarioNombre, onClose }: Props) {
  const { t } = useTranslation()
  const [datos, setDatos] = useState<PermisosUsuario | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    authRepository.getPermisosUsuario(usuarioId)
      .then(setDatos)
      .catch(() => setDatos(null))
      .finally(() => setLoading(false))
  }, [usuarioId])

  const porModulo: Record<string, string[]> = {}
  datos?.permisos.forEach(p => {
    const modulo = p.split(':')[0].toUpperCase()
    if (!porModulo[modulo]) porModulo[modulo] = []
    porModulo[modulo].push(p)
  })

  return (
    <>
      <div
        className="fixed inset-0 z-20 bg-black/30"
        onClick={onClose}
        aria-hidden="true"
      />

      <aside className="fixed inset-y-0 right-0 z-30 w-80 bg-white shadow-2xl flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 border-b">
          <div>
            <h2 className="font-semibold text-slate-900 text-sm">
              {t('permissions.title', { name: usuarioNombre })}
            </h2>
            {datos && (
              <p className="text-xs text-slate-500 mt-0.5">
                {t('permissions.role', { name: datos.rol.nombre })}
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors"
            aria-label={t('permissions.closePanel')}
          >
            <X size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4">
          {loading ? (
            <div className="flex items-center justify-center h-32">
              <Loader2 size={24} className="text-slate-400 animate-spin" />
            </div>
          ) : !datos ? (
            <p className="text-sm text-slate-400 text-center mt-8">
              {t('permissions.loadError')}
            </p>
          ) : Object.keys(porModulo).length === 0 ? (
            <p className="text-sm text-slate-400 text-center mt-8">
              {t('permissions.empty')}
            </p>
          ) : (
            <div className="space-y-5">
              {Object.entries(porModulo).map(([modulo, permisos]) => (
                <div key={modulo}>
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                    {modulo}
                  </p>
                  <ul className="space-y-1">
                    {permisos.map(p => (
                      <li key={p} className="flex items-center gap-2 text-sm text-slate-700">
                        <span className="w-1.5 h-1.5 rounded-full bg-orange-500 flex-shrink-0" />
                        <code className="text-xs text-slate-600 font-mono">{p}</code>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>
      </aside>
    </>
  )
}
