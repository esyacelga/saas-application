import { useEffect, useState } from 'react'
import { CheckCircle2, Loader2 } from 'lucide-react'
import { isAxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import type { Persona, CrearPersonaRequest } from '@/infrastructure/http/auth/auth.dto'
import type { CrearAppUsuarioFormData } from '../schemas/persona.schema'
import type { TipoMembresia } from '@/infrastructure/http/core/core.dto'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import type { JwtPayloadStaff } from '@/domain/auth/entities/User.entity'

interface Props {
  personaExistente: Persona | null
  personaFormData: CrearPersonaRequest | null
  appUsuarioDatos: CrearAppUsuarioFormData
  onCompletado: () => void
}

function todayISO() {
  return new Date().toISOString().split('T')[0]
}

function formatDuracion(valor: number, tipo: string): string {
  const map: Record<string, [string, string]> = {
    dias:    ['día',    'días'],
    semanas: ['semana', 'semanas'],
    meses:   ['mes',    'meses'],
    años:    ['año',    'años'],
  }
  const [sing, plur] = map[tipo] ?? [tipo, tipo]
  return `${valor} ${valor === 1 ? sing : plur}`
}

export function SeleccionarMembresiaStep({ personaExistente, personaFormData, appUsuarioDatos, onCompletado }: Props) {
  const { t } = useTranslation()
  const user = useCurrentUser() as JwtPayloadStaff | null
  const idSucursal = user?.id_sucursal ?? 0

  const [loadingData, setLoadingData] = useState(true)
  const [tipos, setTipos] = useState<TipoMembresia[]>([])
  const [idClienteResuelto, setIdClienteResuelto] = useState<number | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [fechaInicio, setFechaInicio] = useState(todayISO())
  const [processing, setProcessing] = useState(false)
  const [resultado, setResultado] = useState<'ok' | 'omitido' | null>(null)
  const [errorMsg, setErrorMsg] = useState('')

  const nombre = personaExistente?.nombre ?? personaFormData?.nombre ?? ''
  const ci     = personaExistente?.ci     ?? personaFormData?.ci     ?? ''

  useEffect(() => {
    const ciExistente = personaExistente?.ci

    Promise.all([
      coreRepository.getTiposMembresia(),
      ciExistente
        ? coreRepository.buscarPorCi(ciExistente).catch(() => null)
        : Promise.resolve(null),
    ])
      .then(([tiposData, ciRes]) => {
        setTipos(tiposData.filter(t => t.activo))
        if (ciRes?.es_cliente_en_este_gym && ciRes.id_cliente) {
          setIdClienteResuelto(ciRes.id_cliente)
        }
      })
      .catch(() => setTipos([]))
      .finally(() => setLoadingData(false))
  }, [personaExistente])

  const resolverIdCliente = async (persona: Persona): Promise<number> => {
    if (idClienteResuelto) return idClienteResuelto
    const res = await coreRepository.registrarCliente({
      ci: persona.ci,
      nombre: persona.nombre,
      telefono: persona.telefono ?? undefined,
      correo: persona.correo ?? undefined,
      id_sucursal: idSucursal,
    })
    setIdClienteResuelto(res.id_cliente)
    return res.id_cliente
  }

  const executeAllSteps = async (withMembresia: boolean): Promise<void> => {
    // 1. Obtener o crear persona
    let persona: Persona
    if (personaExistente) {
      persona = personaExistente
    } else {
      try {
        persona = await authRepository.crearPersona(personaFormData!)
      } catch (err) {
        if (isAxiosError(err) && err.response?.status === 409) {
          throw new Error('persona_409')
        }
        throw new Error('persona_error')
      }
    }

    // 2. Crear usuario app
    try {
      await authRepository.crearUsuarioApp({
        id_persona: persona.id,
        login: appUsuarioDatos.login,
        password: appUsuarioDatos.password,
      })
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        throw new Error('app_409')
      }
      throw new Error('app_error')
    }

    // 3. Registrar como cliente del gym
    const idCliente = await resolverIdCliente(persona)

    // 4. Vender membresía (opcional)
    if (withMembresia && selectedId) {
      try {
        await coreRepository.venderMembresia(idCliente, {
          id_tipo_membresia: selectedId,
          fecha_inicio: fechaInicio,
        })
      } catch (err) {
        if (isAxiosError(err) && err.response?.status === 409) {
          throw new Error('membresia_409')
        }
        throw new Error('membresia_error')
      }
    }
  }

  const handleActivar = async () => {
    if (!selectedId) return
    setProcessing(true)
    setErrorMsg('')
    try {
      await executeAllSteps(true)
      setResultado('ok')
    } catch (err) {
      const code = (err as Error).message
      if (code === 'app_409') {
        setErrorMsg(t('appAccounts.membershipErrorApp409'))
      } else if (code === 'membresia_409') {
        setErrorMsg(t('appAccounts.membershipError409'))
      } else {
        setErrorMsg(t('appAccounts.membershipError'))
      }
    } finally {
      setProcessing(false)
    }
  }

  const handleOmitir = async () => {
    setProcessing(true)
    setErrorMsg('')
    try {
      await executeAllSteps(false)
      setResultado('omitido')
    } catch (err) {
      const code = (err as Error).message
      if (code === 'app_409') {
        setErrorMsg(t('appAccounts.membershipErrorApp409'))
      } else {
        setErrorMsg(t('appAccounts.membershipError'))
      }
    } finally {
      setProcessing(false)
    }
  }

  // ── Pantalla de éxito final ────────────────────────────────────────────────
  if (resultado) {
    return (
      <div className="max-w-lg bg-white rounded-2xl border border-green-200 p-8 text-center space-y-4">
        <div className="flex items-center justify-center w-16 h-16 rounded-full bg-green-100 mx-auto">
          <CheckCircle2 size={32} className="text-green-600" />
        </div>
        <div>
          <h2 className="text-xl font-bold text-slate-900">
            {t('appAccounts.membershipSuccessTitle')}
          </h2>
          <p className="text-slate-500 text-sm mt-1">
            {resultado === 'ok'
              ? t('appAccounts.membershipSuccessMsg', { name: nombre })
              : t('appAccounts.membershipSkippedMsg', { name: nombre })}
          </p>
        </div>
        <button
          onClick={onCompletado}
          className="w-full bg-orange-500 hover:bg-orange-600 text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          {t('appAccounts.registerAnother')}
        </button>
      </div>
    )
  }

  // ── Loading inicial ────────────────────────────────────────────────────────
  if (loadingData) {
    return (
      <div className="max-w-lg bg-white rounded-2xl border border-slate-200 p-10 flex items-center justify-center">
        <Loader2 size={24} className="animate-spin text-slate-300" />
      </div>
    )
  }

  // ── Sin planes disponibles ─────────────────────────────────────────────────
  if (tipos.length === 0) {
    return (
      <div className="max-w-lg bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-4">
        <p className="text-sm text-slate-400">{t('appAccounts.membershipNoPlanes')}</p>
        {errorMsg && <p className="text-xs text-red-600">{errorMsg}</p>}
        <button
          onClick={handleOmitir}
          disabled={processing}
          className="text-sm font-medium text-orange-500 hover:text-orange-600 transition-colors disabled:opacity-40"
        >
          {processing
            ? <Loader2 size={14} className="animate-spin inline mr-1" />
            : null}
          {t('appAccounts.membershipSkip')} →
        </button>
      </div>
    )
  }

  // ── Selector ───────────────────────────────────────────────────────────────
  return (
    <div className="max-w-lg bg-white rounded-2xl border border-slate-200 p-6 space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">
          {t('appAccounts.membershipTitle')}
        </h2>
        <p className="text-sm text-slate-500 mt-0.5">
          {t('appAccounts.membershipSubtitle', { name: nombre })}
        </p>
        {ci && (
          <p className="text-xs text-slate-400 mt-0.5">CI: {ci}</p>
        )}
      </div>

      {/* Cards */}
      <div className="grid grid-cols-2 gap-3">
        {tipos.map(tipo => {
          const sel = selectedId === tipo.id
          return (
            <button
              key={tipo.id}
              type="button"
              onClick={() => setSelectedId(tipo.id)}
              className="flex flex-col items-start gap-2 p-4 rounded-xl border-2 text-left transition-all hover:shadow-sm"
              style={{
                borderColor: sel ? '#f97316' : '#e2e8f0',
                background: sel ? '#fff7ed' : '#ffffff',
              }}
            >
              <span className="text-sm font-semibold text-slate-800 leading-tight">
                {tipo.nombre}
              </span>
              <span className="text-2xl font-bold text-slate-900">
                ${tipo.precio.toFixed(2)}
              </span>
              <div className="flex flex-wrap gap-1.5 mt-auto">
                <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">
                  {formatDuracion(tipo.duracion_valor, tipo.duracion_tipo)}
                </span>
                <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">
                  {tipo.modo_control === 'calendario'
                    ? '📅 Fecha'
                    : `🔢 ${tipo.dias_acceso ?? '—'} acc.`}
                </span>
              </div>
            </button>
          )
        })}
      </div>

      {/* Fecha de inicio — aparece al seleccionar un plan */}
      {selectedId && (
        <div className="space-y-1.5">
          <label className="block text-sm font-medium text-slate-700">
            {t('appAccounts.membershipFechaInicio')}
          </label>
          <input
            type="date"
            value={fechaInicio}
            onChange={e => setFechaInicio(e.target.value)}
            className="w-full border border-slate-300 rounded-lg px-3 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
          />
        </div>
      )}

      {errorMsg && (
        <p className="text-xs text-red-600">{errorMsg}</p>
      )}

      {/* Acciones */}
      <div className="flex flex-col gap-2 pt-1">
        <button
          onClick={handleActivar}
          disabled={!selectedId || processing}
          className="w-full flex items-center justify-center gap-2 bg-orange-500 hover:bg-orange-600 disabled:opacity-40 disabled:cursor-not-allowed text-white font-semibold py-2.5 rounded-lg transition-colors text-sm"
        >
          {processing && <Loader2 size={16} className="animate-spin" />}
          {processing
            ? t('appAccounts.membershipActivating')
            : t('appAccounts.membershipActivate')}
        </button>
        <button
          onClick={handleOmitir}
          disabled={processing}
          className="w-full text-sm text-slate-400 hover:text-slate-600 py-1.5 transition-colors disabled:opacity-40"
        >
          {t('appAccounts.membershipSkip')}
        </button>
      </div>
    </div>
  )
}
