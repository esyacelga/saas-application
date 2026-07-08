import { useState } from 'react'
import { Loader2, Search, AlertCircle, CheckCircle2, Smartphone } from 'lucide-react'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { Persona } from '@/infrastructure/http/auth/auth.dto'

interface Props {
  onPersonaEncontrada: (persona: Persona) => void
  onPersonaNoExiste: (ci: string) => void
}

export function BuscarPersonaStep({ onPersonaEncontrada, onPersonaNoExiste }: Props) {
  const { t } = useTranslation()
  const [ci, setCi] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [personaEncontrada, setPersonaEncontrada] = useState<Persona | null>(null)

  const buscar = async () => {
    const ciTrimmed = ci.trim()
    if (!ciTrimmed) {
      setError(t('appAccounts.searchErrorEmpty'))
      return
    }
    setError('')
    setLoading(true)
    setPersonaEncontrada(null)
    try {
      const persona = await authRepository.buscarPersonaPorCI(ciTrimmed)
      setPersonaEncontrada(persona)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 404) {
        onPersonaNoExiste(ciTrimmed)
      } else {
        setError(t('appAccounts.searchErrorConn'))
      }
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') buscar()
  }

  const FIELDS = [
    { label: t('appAccounts.fieldName'),  value: personaEncontrada?.nombre },
    { label: t('appAccounts.fieldCI'),    value: personaEncontrada?.ci },
    { label: t('appAccounts.fieldEmail'), value: personaEncontrada?.correo ?? '—' },
    { label: t('appAccounts.fieldPhone'), value: personaEncontrada?.telefono ?? '—' },
  ]

  return (
    <div className="max-w-lg">
      <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">{t('appAccounts.searchTitle')}</h2>
          <p className="text-sm text-slate-500 mt-0.5">
            {t('appAccounts.searchSubtitle')}
          </p>
        </div>

        <div className="flex gap-2">
          <input
            type="text"
            value={ci}
            onChange={e => { setCi(e.target.value); setError('') }}
            onKeyDown={handleKeyDown}
            placeholder={t('appAccounts.searchPlaceholder')}
            className="flex-1 border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
            disabled={loading}
          />
          <button
            onClick={buscar}
            disabled={loading || !ci.trim()}
            className="flex items-center gap-2 px-4 py-2.5 bg-orange-500 hover:bg-orange-600 disabled:opacity-60 text-white font-medium text-sm rounded-lg transition-colors"
          >
            {loading ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
            {t('appAccounts.searchButton')}
          </button>
        </div>

        {error && (
          <div className="flex items-center gap-2 text-sm text-red-600">
            <AlertCircle size={15} />
            {error}
          </div>
        )}
      </div>

      {personaEncontrada && (
        <div className="mt-4 bg-white rounded-2xl border border-green-200 p-6 space-y-4">
          <div className="flex items-center gap-2 text-green-700 font-semibold text-sm">
            <CheckCircle2 size={18} />
            {t('appAccounts.clientFound')}
          </div>

          <div className="grid grid-cols-2 gap-3 text-sm">
            {FIELDS.map(item => (
              <div key={item.label}>
                <p className="text-xs text-slate-400">{item.label}</p>
                <p className="font-medium text-slate-900">{item.value}</p>
              </div>
            ))}
          </div>

          <button
            onClick={() => onPersonaEncontrada(personaEncontrada)}
            className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
          >
            <Smartphone size={16} />
            {t('appAccounts.createAccountBtn')}
          </button>
        </div>
      )}
    </div>
  )
}
