import { useEffect, useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { CheckCircle, XCircle, RotateCcw, ClipboardCheck, MapPin, AlertCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { AxiosError } from 'axios'
import { attendanceRepository, type CheckInResult } from '@/infrastructure/http/AttendanceHttpRepository'
import { getApiErrorMessage } from '@/lib/api-error'
import { useAuthStore } from '@/infrastructure/store/auth.store'
import { PulseBackground } from '@/ui/components/PulseBackground'

type State = 'idle' | 'loading' | 'success' | 'error'
type ErrorKind = 'ya_registrado_hoy' | 'sin_membresia' | 'membresia_expirada' | 'accesos_agotados' | 'congelado' | 'generic'

const MEMBERSHIP_ERRORS: ErrorKind[] = ['sin_membresia', 'membresia_expirada', 'accesos_agotados', 'congelado']

function detectErrorKind(err: unknown): ErrorKind {
  const e = err as AxiosError<{ mensaje?: string; codigo?: string }>
  const codigo = (e?.response?.data?.codigo ?? '').toLowerCase()
  const mensaje = (e?.response?.data?.mensaje ?? '').toLowerCase()
  if (codigo === 'ya_registrado_hoy' || mensaje.includes('ya_registrado') || mensaje.includes('ya registrado'))
    return 'ya_registrado_hoy'
  if (codigo.includes('congelad') || mensaje.includes('congelad'))
    return 'congelado'
  if (codigo === 'sin_membresia' || mensaje.includes('sin_membresia') || mensaje.includes('sin membresía') || mensaje.includes('sin membresia'))
    return 'sin_membresia'
  if (codigo.includes('expir') || mensaje.includes('expir'))
    return 'membresia_expirada'
  if (codigo === 'accesos_agotados' || mensaje.includes('accesos_agotados') || mensaje.includes('accesos agotados'))
    return 'accesos_agotados'
  return 'generic'
}

export function CheckInPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const gymInfo = useAuthStore((s) => s.gymInfo)

  const autoQrToken = (location.state as { autoQrToken?: string } | null)?.autoQrToken

  const [state, setState] = useState<State>(autoQrToken ? 'loading' : 'idle')
  const [result, setResult] = useState<CheckInResult | null>(null)
  const [errorMsg, setErrorMsg] = useState('')
  const [errorKind, setErrorKind] = useState<ErrorKind>('generic')

  useEffect(() => {
    if (!autoQrToken) return
    navigate('.', { replace: true, state: {} })
    attendanceRepository.checkInQr(autoQrToken)
      .then((data) => { setResult(data); setState('success') })
      .catch((err) => {
        setErrorKind(detectErrorKind(err))
        setErrorMsg(getApiErrorMessage(err, t('checkin.error.defaultMessage')))
        setState('error')
      })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleCheckIn = async () => {
    if (!gymInfo?.id_sucursal) return
    setState('loading')
    try {
      const data = await attendanceRepository.checkInApp(gymInfo.id_sucursal, gymInfo.nombre_sucursal)
      setResult(data)
      setState('success')
    } catch (err) {
      setErrorKind(detectErrorKind(err))
      setErrorMsg(getApiErrorMessage(err, t('checkin.error.defaultMessage')))
      setState('error')
    }
  }

  const reset = () => {
    setResult(null)
    setErrorMsg('')
    setErrorKind('generic')
    setState('idle')
  }

  return (
    <div className="flex flex-col min-h-svh">
      <PulseBackground />
      <div className="px-4 pt-8 pb-4 bg-accent-900/20 border-b border-accent-800/40">
        <h1 className="text-xl font-bold text-slate-50">{t('checkin.title')}</h1>
        <p className="text-sm text-slate-400 mt-1">{t('checkin.subtitle')}</p>
      </div>

      <div className="flex-1 flex flex-col items-center justify-center px-4 pb-24">

        {(state === 'idle' || state === 'loading') && (
          <div className="w-full max-w-sm space-y-6">
            {gymInfo?.id_sucursal ? (
              <>
                <div className="flex flex-col items-center gap-3 py-6">
                  <div className="w-24 h-24 rounded-full bg-accent-900/40 border-2 border-accent-700 flex items-center justify-center">
                    <ClipboardCheck size={40} className="text-accent-400" />
                  </div>
                  {gymInfo.nombre_sucursal && (
                    <div className="flex items-center gap-1 text-xs text-slate-400">
                      <MapPin size={12} />
                      <span>{gymInfo.nombre_sucursal}</span>
                    </div>
                  )}
                </div>

                <button
                  onClick={handleCheckIn}
                  disabled={state === 'loading'}
                  aria-busy={state === 'loading'}
                  className="w-full rounded-xl bg-accent-600 py-4 text-base font-semibold text-white hover:bg-accent-500 disabled:opacity-50 transition-colors"
                >
                  {state === 'loading' ? (
                    <span className="flex items-center justify-center gap-2">
                      <span className="h-4 w-4 motion-safe:animate-spin rounded-full border-2 border-white/30 border-t-white" aria-hidden="true" />
                      {t('checkin.loading')}
                    </span>
                  ) : (
                    t('checkin.button')
                  )}
                </button>
              </>
            ) : (
              <div className="flex flex-col items-center gap-4 rounded-2xl bg-slate-800 border border-slate-700 p-8 text-center">
                <ClipboardCheck size={40} className="text-slate-500" />
                <p className="text-sm text-slate-400">{t('checkin.noGymInfo')}</p>
              </div>
            )}
          </div>
        )}

        {state === 'success' && result && (
          <div className="w-full max-w-sm space-y-4">
            <div className="flex flex-col items-center gap-2 py-4">
              <CheckCircle size={56} className="text-emerald-400" />
              <h2 className="text-xl font-bold text-slate-50">{t('checkin.success.title')}</h2>
              <p className="text-sm text-slate-400">{result.sucursal}</p>
            </div>

            <div className="rounded-xl bg-slate-800 border border-slate-700 divide-y divide-slate-700">
              <Row label={t('checkin.success.labels.date')} value={result.fecha} />
              <Row label={t('checkin.success.labels.entryTime')} value={result.hora_entrada.slice(0, 5)} />
              <Row label={t('checkin.success.labels.membership')} value={result.tipo_membresia} />
              <Row label={t('checkin.success.labels.expires')} value={result.fecha_fin} />
              {result.modo_control === 'ACCESOS' && result.accesos_restantes != null && (
                <Row
                  label={t('checkin.success.labels.remainingAccess')}
                  value={String(result.accesos_restantes)}
                  highlight={result.accesos_restantes === 0 ? 'zero' : result.accesos_restantes <= 3}
                />
              )}
            </div>

            {/*<button
              onClick={reset}
              className="w-full flex items-center justify-center gap-2 rounded-lg border border-slate-600 py-3 text-sm text-slate-300 hover:border-accent-500 hover:text-accent-400 transition-colors"
            >
              <RotateCcw size={16} />
              {t('checkin.success.scanAnother')}
            </button>*/}
          </div>
        )}

        {state === 'error' && (
          <div className="w-full max-w-sm space-y-4">

            {errorKind === 'ya_registrado_hoy' && (
              <>
                <div className="rounded-2xl bg-teal-950/60 border border-teal-800 p-6 flex flex-col items-center gap-3 text-center">
                  <div className="w-14 h-14 rounded-full bg-teal-900/60 flex items-center justify-center">
                    <CheckCircle size={32} className="text-teal-400" />
                  </div>
                  <h2 className="text-lg font-bold text-slate-50">{t('checkin.yaRegistrado.titulo')}</h2>
                  <p className="text-sm text-teal-300">{t('checkin.yaRegistrado.detalle')}</p>
                </div>
                <button
                  onClick={reset}
                  className="w-full rounded-xl bg-teal-700 py-3 text-sm font-semibold text-white hover:bg-teal-600 transition-colors"
                >
                  {t('checkin.error.dismiss')}
                </button>
              </>
            )}

            {MEMBERSHIP_ERRORS.includes(errorKind) && (
              <>
                <div className="rounded-2xl bg-amber-950/60 border border-amber-800 p-6 flex flex-col items-center gap-3 text-center">
                  <div className="w-14 h-14 rounded-full bg-amber-900/60 flex items-center justify-center">
                    <AlertCircle size={32} className="text-amber-400" />
                  </div>
                  <h2 className="text-lg font-bold text-slate-50">
                    {t(`checkin.error.razones.${errorKind}.titulo`)}
                  </h2>
                  <p className="text-sm text-amber-300">
                    {t(`checkin.error.razones.${errorKind}.detalle`)}
                  </p>
                </div>
                <button
                  onClick={reset}
                  className="w-full rounded-xl bg-amber-700 py-3 text-sm font-semibold text-white hover:bg-amber-600 transition-colors"
                >
                  {t('checkin.error.dismiss')}
                </button>
              </>
            )}

            {errorKind === 'generic' && (
              <>
                <div className="flex flex-col items-center gap-2 py-4">
                  <XCircle size={56} className="text-red-400" />
                  <h2 className="text-xl font-bold text-slate-50">{t('checkin.error.title')}</h2>
                  <p className="text-sm text-red-400 text-center">{errorMsg}</p>
                </div>
                <button
                  onClick={reset}
                  className="w-full flex items-center justify-center gap-2 rounded-lg bg-accent-600 py-3 text-sm font-semibold text-white hover:bg-accent-500 transition-colors"
                >
                  <RotateCcw size={16} />
                  {t('checkin.error.retry')}
                </button>
              </>
            )}

          </div>
        )}

      </div>
    </div>
  )
}

function Row({ label, value, highlight }: { label: string; value: string; highlight?: boolean | 'zero' }) {
  const valueCls = highlight === 'zero' ? 'text-emerald-400' : highlight ? 'text-amber-400' : 'text-slate-50'
  return (
    <div className="flex items-center justify-between px-4 py-3">
      <span className="text-sm text-slate-400">{label}</span>
      <span className={`text-sm font-medium ${valueCls}`}>{value}</span>
    </div>
  )
}
