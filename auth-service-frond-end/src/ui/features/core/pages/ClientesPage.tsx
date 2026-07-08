import { useState, useEffect, useCallback } from 'react'
import { toast } from 'sonner'
import { isAxiosError } from 'axios'
import { Users, Search, User, CreditCard, Snowflake, History, RefreshCw, Activity, CalendarCheck, Flame, TrendingUp, Smartphone, Eye, EyeOff, Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog'
import { PageHeader } from '@/ui/components/PageHeader'
import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { AppUsuario } from '@/infrastructure/http/auth/auth.dto'
import { coreRepository } from '@/infrastructure/http/core/CoreRepository'
import type {
  ClienteListItem, ClienteDetalle, MembresiaDetalle, MembresiaHistorial,
  CongelamientoHistorial, EstadoCliente,
} from '@/infrastructure/http/core/core.dto'
import { attendanceRepository } from '@/infrastructure/http/attendance/AttendanceHttpRepository'
import type { Ultimos30DiasAdminResult, AsistenciaAdminItem } from '@/infrastructure/http/attendance/AttendanceHttpRepository'
import { RegistrarClienteModal } from '../components/RegistrarClienteModal'
import { VenderMembresiaModal } from '../components/VenderMembresiaModal'
import { CongelarMembresiaModal } from '../components/CongelarMembresiaModal'
import { CargarAsistenciasModal } from '../components/CargarAsistenciasModal'

// ── Badges ───────────────────────────────────────────────────────────────────

const ESTADO_COLORS: Record<EstadoCliente, { bg: string; text: string }> = {
  activo:           { bg: 'rgba(34,197,94,0.15)',  text: '#22c55e' },
  proximo_vencer:   { bg: 'rgba(234,179,8,0.15)',  text: '#eab308' },
  vencido:          { bg: 'rgba(239,68,68,0.15)',  text: '#ef4444' },
  congelado:        { bg: 'rgba(99,179,237,0.15)', text: '#63b3ed' },
  riesgo_abandono:  { bg: 'rgba(168,85,247,0.15)', text: '#a855f7' },
}

function BadgeEstado({ estado }: { estado: EstadoCliente }) {
  const c = ESTADO_COLORS[estado] ?? { bg: 'var(--page-surface)', text: 'var(--page-muted)' }
  return (
    <span className="inline-flex items-center font-medium px-2 py-0.5 rounded-full"
      style={{ fontSize: '0.55rem', background: c.bg, color: c.text }}>
      {estado.replace('_', ' ')}
    </span>
  )
}

// ── Tab Detalle — sección membresía activa ───────────────────────────────────

function MembresiaActivaCard({
  membresia, onVender, onAnular, onCongelar, onReactivar, onCargarHistorial
}: {
  membresia: MembresiaDetalle | null
  onVender: () => void
  onAnular: () => void
  onCongelar: () => void
  onReactivar: () => void
  onCargarHistorial: () => void
}) {
  const { t } = useTranslation()

  if (!membresia) return (
    <div className="p-4">
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--page-muted)' }}>
          {t('membresias.activaTitle')}
        </p>
        <Button label={t('membresias.venderTitle')} icon="pi pi-plus" severity="warning" size="small"
          onClick={onVender} />
      </div>
      <div className="flex flex-col items-center justify-center py-8 text-center">
        <CreditCard size={28} className="mb-2" style={{ color: 'var(--page-border)' }} />
        <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('membresias.sinMembresia')}</p>
      </div>
    </div>
  )

  const isCongelada = membresia.estado === 'congelada'
  const isAccesos = membresia.modo_control === 'accesos'

  return (
    <div className="p-4 space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--page-muted)' }}>
          {t('membresias.activaTitle')}
        </p>
        <div className="flex gap-1.5">
          {!isCongelada && (
            <Button label={t('congelamientos.congelarTitle')} icon="pi pi-pause" size="small" text
              onClick={onCongelar}
              pt={{ root: { className: 'text-blue-400 hover:text-blue-300 !text-[0.6rem] !px-1.5 !py-0.5' } }} />
          )}
          {isCongelada && (
            <Button label={t('congelamientos.reactivarTitle')} icon="pi pi-play" size="small" text
              onClick={onReactivar}
              pt={{ root: { className: 'text-green-400 hover:text-green-300 !text-[0.6rem] !px-1.5 !py-0.5' } }} />
          )}
          <Button label={t('membresias.asistPreviasBtn')} icon="pi pi-history" size="small" text
            onClick={onCargarHistorial}
            pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }} />
          <Button label={t('membresias.anularTitle')} icon="pi pi-times" size="small" text severity="danger"
            onClick={onAnular}
            pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }} />
        </div>
      </div>

      <div className="rounded-lg p-3 space-y-2"
        style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold" style={{ color: 'var(--page-text)' }}>{membresia.tipo}</span>
          <BadgeEstado estado={membresia.estado as EstadoCliente} />
        </div>
        <div className="grid grid-cols-2 gap-2 text-xs">
          <div>
            <span style={{ color: 'var(--page-muted)' }}>{t('membresias.infoDuracion')}: </span>
            <span style={{ color: 'var(--page-text)' }}>{membresia.fecha_inicio} → {membresia.fecha_fin}</span>
          </div>
          <div>
            <span style={{ color: 'var(--page-muted)' }}>{t('membresias.infoModo')}: </span>
            <span style={{ color: 'var(--page-text)' }}>{membresia.modo_control}</span>
          </div>
          {isAccesos && (
            <>
              <div>
                <span style={{ color: 'var(--page-muted)' }}>{t('membresias.infoAccesosUsados')}: </span>
                <span style={{ color: 'var(--page-text)' }}>{membresia.dias_acceso_usados ?? 0} / {membresia.dias_acceso_total}</span>
              </div>
              <div>
                <span style={{ color: 'var(--page-muted)' }}>{t('membresias.infoAccesosRestantes')}: </span>
                <span className="font-semibold" style={{ color: '#f97316' }}>{membresia.dias_acceso_restantes}</span>
              </div>
            </>
          )}
          {membresia.asistencias_previas > 0 && (
            <div>
              <span style={{ color: 'var(--page-muted)' }}>{t('membresias.infoAsistPrevias')}: </span>
              <span style={{ color: 'var(--page-text)' }}>{membresia.asistencias_previas}</span>
            </div>
          )}
          <div>
            <span style={{ color: 'var(--page-muted)' }}>{t('membresias.infoPrecio')}: </span>
            <span style={{ color: 'var(--page-text)' }}>${membresia.precio_pagado.toFixed(2)}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── Tab Detalle — historial membresías ───────────────────────────────────────

function HistorialMembresiasTab({ historial }: { historial: MembresiaHistorial[] }) {
  const { t } = useTranslation()
  if (historial.length === 0) return (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <History size={28} className="mb-2" style={{ color: 'var(--page-border)' }} />
      <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('membresias.sinHistorial')}</p>
    </div>
  )

  return (
    <div className="p-4 space-y-2">
      {historial.map(m => (
        <div key={m.id} className="flex items-center justify-between px-3 py-2 rounded-lg"
          style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
          <div>
            <p className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>{m.tipo}</p>
            <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
              {m.fecha_inicio} → {m.fecha_fin}
            </p>
          </div>
          <div className="text-right">
            <p className="text-xs font-semibold" style={{ color: 'var(--page-text)' }}>${m.precio_pagado.toFixed(2)}</p>
            <BadgeEstado estado={m.estado as EstadoCliente} />
          </div>
        </div>
      ))}
    </div>
  )
}

// ── Tab Detalle — historial congelamientos ───────────────────────────────────

function HistorialCongelamientosTab({ historial }: { historial: CongelamientoHistorial[] }) {
  const { t } = useTranslation()
  if (historial.length === 0) return (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <Snowflake size={28} className="mb-2" style={{ color: 'var(--page-border)' }} />
      <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('congelamientos.sinHistorial')}</p>
    </div>
  )

  return (
    <div className="p-4 space-y-2">
      {historial.map(c => (
        <div key={c.id} className="flex items-center justify-between px-3 py-2 rounded-lg"
          style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
          <div>
            <p className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>{c.motivo}</p>
            <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
              {c.fecha_inicio} → {c.fecha_fin ?? t('congelamientos.activo')}
            </p>
          </div>
          {c.dias_congelados != null && (
            <span className="text-xs font-semibold" style={{ color: '#63b3ed' }}>
              {c.dias_congelados}d
            </span>
          )}
        </div>
      ))}
    </div>
  )
}

// ── Tab Detalle — asistencias ────────────────────────────────────────────────

const METODO_LABEL: Record<string, string> = { qr: 'QR', manual: 'Manual', override: 'Override' }

function formatAsistDate(iso: string) {
  return new Date(iso + 'T00:00:00').toLocaleDateString('es', {
    weekday: 'short', day: '2-digit', month: 'short',
  })
}

function heatmapCellStyle(asistio: boolean | null, isToday: boolean): React.CSSProperties {
  if (isToday) return { background: asistio ? '#4ade80' : 'rgba(99,102,241,0.25)', outline: '1px solid rgba(99,102,241,0.5)' }
  if (asistio === true) return { background: '#22c55e' }
  if (asistio === false) return { background: 'var(--page-border)' }
  return { background: 'transparent' }
}

function AsistenciasHeatmap({ detalle }: { detalle: Ultimos30DiasAdminResult['detalle'] }) {
  const sorted = [...detalle].sort((a, b) => a.fecha.localeCompare(b.fecha))
  const today = new Date().toISOString().slice(0, 10)
  const firstDay = new Date(sorted[0]?.fecha ?? today)
  const dayOffset = (firstDay.getDay() + 6) % 7
  const gridStart = new Date(firstDay)
  gridStart.setDate(gridStart.getDate() - dayOffset)

  const byDate: Record<string, boolean> = {}
  for (const d of detalle) byDate[d.fecha] = d.asistio

  const cells: Array<{ date: string; asistio: boolean | null; isToday: boolean }> = []
  for (let i = 0; i < 35; i++) {
    const d = new Date(gridStart)
    d.setDate(d.getDate() + i)
    const iso = d.toISOString().slice(0, 10)
    cells.push({ date: iso, asistio: byDate[iso] ?? null, isToday: iso === today })
  }

  const DOW = ['L', 'M', 'M', 'J', 'V', 'S', 'D']

  return (
    <div className="rounded-lg p-3" style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
      <div className="grid grid-cols-7 gap-0.5 mb-0.5">
        {DOW.map((d, i) => (
          <div key={i} style={{ fontSize: '0.6rem', color: 'var(--page-muted)', textAlign: 'center' }}>{d}</div>
        ))}
      </div>
      <div className="grid grid-cols-7 gap-0.5">
        {cells.map(({ date, asistio, isToday }) => (
          <div key={date} title={date} className="rounded-sm aspect-square"
            style={heatmapCellStyle(asistio, isToday)} />
        ))}
      </div>
      <div className="flex items-center gap-3 mt-2 justify-end">
        {[
          { color: '#22c55e', label: 'Asistió' },
          { color: 'var(--page-border)', label: 'Ausente' },
        ].map(({ color, label }) => (
          <div key={label} className="flex items-center gap-1">
            <div className="w-2 h-2 rounded-sm" style={{ background: color }} />
            <span style={{ fontSize: '0.6rem', color: 'var(--page-muted)' }}>{label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function AsistenciasClienteTab({
  data30,
  historial,
  totalHistorial,
  loading,
  onRegistrarManual,
}: {
  data30: Ultimos30DiasAdminResult | null
  historial: AsistenciaAdminItem[]
  totalHistorial: number
  loading: boolean
  onRegistrarManual: () => void
}) {
  const { t } = useTranslation()

  if (loading) return (
    <div className="flex items-center justify-center py-16">
      <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('common.loading')}</span>
    </div>
  )

  return (
    <div className="p-4 space-y-4">
      {/* Header con acción */}
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--page-muted)' }}>
          {t('asistencias.historialTitle')}
        </p>
        <Button
          label={t('asistencias.registrarManual')}
          icon="pi pi-user-plus"
          size="small"
          severity="secondary"
          text
          onClick={onRegistrarManual}
          pt={{ root: { className: '!text-[0.6rem] !px-1.5 !py-0.5' } }}
        />
      </div>

      {/* Stats últimos 30 días */}
      {data30 && (
        <div className="grid grid-cols-3 gap-2">
          {[
            { icon: <CalendarCheck size={14} className="mb-1" style={{ color: '#22c55e' }} />, value: data30.dias_asistidos, label: t('asistencias.diasAsistidos') },
            { icon: <Flame size={14} className="mb-1" style={{ color: '#f97316' }} />, value: data30.racha_actual, label: t('asistencias.rachaActual') },
            { icon: <TrendingUp size={14} className="mb-1" style={{ color: '#6366f1' }} />, value: data30.racha_maxima_mes, label: t('asistencias.rachaMejor') },
          ].map(({ icon, value, label }) => (
            <div key={label} className="rounded-lg p-2 flex flex-col items-center text-center"
              style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
              {icon}
              <p className="text-lg font-bold leading-none" style={{ color: 'var(--page-text)' }}>{value}</p>
              <p className="mt-1 leading-tight" style={{ fontSize: '0.55rem', color: 'var(--page-muted)' }}>{label}</p>
            </div>
          ))}
        </div>
      )}

      {/* Heatmap */}
      {data30 && <AsistenciasHeatmap detalle={data30.detalle} />}

      {/* Lista */}
      {historial.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-8 text-center">
          <Activity size={24} className="mb-2" style={{ color: 'var(--page-border)' }} />
          <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('asistencias.sinRegistros')}</p>
        </div>
      ) : (
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <p className="text-xs font-semibold" style={{ color: 'var(--page-muted)' }}>
              {t('asistencias.registrosRecientes')}
            </p>
            <span style={{ fontSize: '0.6rem', color: 'var(--page-muted)' }}>{totalHistorial} total</span>
          </div>
          <div className="rounded-lg overflow-hidden" style={{ border: '1px solid var(--page-border)' }}>
            {historial.slice(0, 15).map((a, i) => (
              <div key={a.id}
                className="flex items-center justify-between px-3 py-2"
                style={{
                  borderBottom: i < Math.min(historial.length, 15) - 1 ? '1px solid var(--page-border)' : undefined,
                  background: i % 2 === 0 ? 'var(--page-surface)' : 'transparent',
                }}>
                <div>
                  <p className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>
                    {formatAsistDate(a.fecha)}
                  </p>
                  <p style={{ fontSize: '0.6rem', color: 'var(--page-muted)' }}>
                    {METODO_LABEL[a.metodo_registro] ?? a.metodo_registro}
                  </p>
                </div>
                <p className="text-xs tabular-nums" style={{ color: 'var(--page-text)' }}>
                  {a.hora_entrada.slice(0, 5)}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ── Tab Detalle — usuario app ─────────────────────────────────────────────────

const inputClass =
  'w-full border rounded-lg px-3 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-orange-500 transition'

function UsuarioAppTab({ ci }: { ci: string }) {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(true)
  const [appUsuario, setAppUsuario] = useState<AppUsuario | 'none' | null>(null)
  const [newLogin, setNewLogin] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [saving, setSaving] = useState(false)
  const [toggling, setToggling] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setAppUsuario(null)
    setErrorMsg('')
    setNewLogin('')
    setNewPassword('')
    authRepository.getAppUsuarioPorCi(ci)
      .then(u => { if (!cancelled) setAppUsuario(u) })
      .catch(() => { if (!cancelled) setAppUsuario('none') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [ci])

  const handleGuardar = async () => {
    if (!appUsuario || appUsuario === 'none') return
    if (!newLogin.trim() && !newPassword.trim()) return
    setSaving(true)
    setErrorMsg('')
    try {
      await authRepository.actualizarAppUsuario(appUsuario.id, {
        login: newLogin.trim() || undefined,
        password: newPassword.trim() || undefined,
      })
      toast.success(t('clientes.usuarioAppGuardadoOk'))
      if (newLogin.trim()) setAppUsuario({ ...appUsuario, login: newLogin.trim() })
      setNewLogin('')
      setNewPassword('')
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        setErrorMsg(t('clientes.usuarioAppLoginConflict'))
      } else {
        setErrorMsg(t('clientes.usuarioAppError'))
      }
    } finally {
      setSaving(false)
    }
  }

  const handleToggle = async () => {
    if (!appUsuario || appUsuario === 'none') return
    setToggling(true)
    setErrorMsg('')
    try {
      if (appUsuario.activo) {
        await authRepository.desactivarUsuarioApp(appUsuario.id)
        toast.success(t('clientes.usuarioAppDesactivadoOk'))
      } else {
        await authRepository.activarUsuarioApp(appUsuario.id)
        toast.success(t('clientes.usuarioAppActivadoOk'))
      }
      setAppUsuario({ ...appUsuario, activo: !appUsuario.activo })
    } catch {
      setErrorMsg(t('clientes.usuarioAppToggleError'))
    } finally {
      setToggling(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('common.loading')}</span>
      </div>
    )
  }

  if (appUsuario === 'none') {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <Smartphone size={28} className="mb-2" style={{ color: 'var(--page-border)' }} />
        <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>{t('clientes.usuarioAppNoAccount')}</p>
      </div>
    )
  }

  if (!appUsuario) return null

  return (
    <div className="p-4 space-y-3 max-w-lg">
      {/* Datos actuales */}
      <div className="rounded-lg p-3 space-y-2"
        style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--page-muted)' }}>
          {t('clientes.sectionUsuarioApp')}
        </p>
        <div className="grid grid-cols-2 gap-2 text-xs">
          <div>
            <span style={{ color: 'var(--page-muted)' }}>{t('clientes.usuarioAppLogin')}: </span>
            <span className="font-mono font-semibold" style={{ color: 'var(--page-text)' }}>{appUsuario.login}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span style={{ color: 'var(--page-muted)' }}>{t('clientes.fieldEstado')}: </span>
            <span className="inline-flex items-center font-medium px-2 py-0.5 rounded-full"
              style={{
                fontSize: '0.55rem',
                background: appUsuario.activo ? 'rgba(34,197,94,0.15)' : 'rgba(239,68,68,0.15)',
                color: appUsuario.activo ? '#22c55e' : '#ef4444',
              }}>
              {appUsuario.activo ? t('clientes.estadoActivo') : t('clientes.usuarioAppInactivo')}
            </span>
          </div>
          {appUsuario.ultimo_acceso && (
            <div className="col-span-2">
              <span style={{ color: 'var(--page-muted)' }}>{t('clientes.usuarioAppUltimoAcceso')}: </span>
              <span style={{ color: 'var(--page-text)' }}>
                {new Date(appUsuario.ultimo_acceso).toLocaleString('es', { dateStyle: 'medium', timeStyle: 'short' })}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Formulario de edición */}
      <div className="rounded-lg p-3 space-y-3"
        style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
        <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--page-muted)' }}>
          {t('clientes.usuarioAppCambiarDatos')}
        </p>

        <div className="space-y-1">
          <label className="block text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            {t('clientes.usuarioAppNuevoLogin')}
          </label>
          <input
            type="text"
            value={newLogin}
            onChange={e => setNewLogin(e.target.value)}
            placeholder={appUsuario.login}
            className={inputClass}
            style={{ background: 'var(--input-bg)', borderColor: 'var(--page-border)', color: 'var(--page-text)' }}
          />
        </div>

        <div className="space-y-1">
          <label className="block text-xs font-medium" style={{ color: 'var(--page-text)' }}>
            {t('clientes.usuarioAppNuevaClave')}
          </label>
          <div className="relative">
            <input
              type={showPwd ? 'text' : 'password'}
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              placeholder={t('clientes.usuarioAppPlaceholderClave')}
              className={`${inputClass} pr-9`}
              style={{ background: 'var(--input-bg)', borderColor: 'var(--page-border)', color: 'var(--page-text)' }}
            />
            <button
              type="button"
              onClick={() => setShowPwd(v => !v)}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 transition-colors"
              style={{ color: 'var(--page-muted)' }}
            >
              {showPwd ? <EyeOff size={13} /> : <Eye size={13} />}
            </button>
          </div>
        </div>

        {errorMsg && <p className="text-xs text-red-500">{errorMsg}</p>}

        <div className="flex gap-2 pt-1">
          <button
            onClick={handleGuardar}
            disabled={saving || (!newLogin.trim() && !newPassword.trim())}
            className="flex-1 flex items-center justify-center gap-1.5 text-xs font-semibold py-2 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed text-white"
            style={{ background: '#f97316' }}
          >
            {saving && <Loader2 size={12} className="animate-spin" />}
            {t('clientes.usuarioAppGuardar')}
          </button>
          <button
            onClick={handleToggle}
            disabled={toggling}
            className="flex items-center justify-center gap-1.5 px-3 text-xs font-semibold py-2 rounded-lg border transition-colors disabled:opacity-40"
            style={{
              borderColor: appUsuario.activo ? '#ef4444' : '#22c55e',
              color: appUsuario.activo ? '#ef4444' : '#22c55e',
            }}
          >
            {toggling && <Loader2 size={12} className="animate-spin" />}
            {appUsuario.activo ? t('clientes.usuarioAppDesactivar') : t('clientes.usuarioAppActivar')}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Página principal ─────────────────────────────────────────────────────────

type DetTab = 'perfil' | 'membresia' | 'historial_mem' | 'congelamientos' | 'asistencias' | 'usuario_app'

export function ClientesPage() {
  const { t } = useTranslation()

  // Lista
  const [clientes, setClientes] = useState<ClienteListItem[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [globalFilter, setGlobalFilter] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('')

  // Selección + detalle
  const [seleccionado, setSeleccionado] = useState<ClienteListItem | null>(null)
  const [detalle, setDetalle] = useState<ClienteDetalle | null>(null)
  const [membresiaActiva, setMembresiaActiva] = useState<MembresiaDetalle | null>(null)
  const [historialMem, setHistorialMem] = useState<MembresiaHistorial[]>([])
  const [historialCong, setHistorialCong] = useState<CongelamientoHistorial[]>([])
  const [loadingDetalle, setLoadingDetalle] = useState(false)

  // Asistencias
  const [asistencias30, setAsistencias30] = useState<Ultimos30DiasAdminResult | null>(null)
  const [historialAsist, setHistorialAsist] = useState<AsistenciaAdminItem[]>([])
  const [totalAsist, setTotalAsist] = useState(0)
  const [loadingAsist, setLoadingAsist] = useState(false)

  // Tabs
  const [activeTab, setActiveTab] = useState<'lista' | 'detalle'>('lista')
  const [detTab, setDetTab] = useState<DetTab>('perfil')

  // Modales
  const [registrarOpen, setRegistrarOpen] = useState(false)
  const [venderOpen, setVenderOpen] = useState(false)
  const [congelarOpen, setCongelarOpen] = useState(false)
  const [cargarAsistOpen, setCargarAsistOpen] = useState(false)

  const cargarLista = useCallback(async () => {
    setLoading(true)
    try {
      const res = await coreRepository.getClientes({
        buscar: globalFilter || undefined,
        estado: filtroEstado || undefined,
      })
      setClientes(res.datos)
      setTotal(res.total)
    } catch {
      toast.error(t('clientes.loadError'))
    } finally {
      setLoading(false)
    }
  }, [globalFilter, filtroEstado, t])

  useEffect(() => { cargarLista() }, [cargarLista])

  const cargarDetalle = useCallback(async (cliente: ClienteListItem) => {
    setLoadingDetalle(true)
    try {
      const [det, mems] = await Promise.all([
        coreRepository.getCliente(cliente.id),
        coreRepository.getMembresiasPorCliente(cliente.id),
      ])
      setDetalle(det)
      setHistorialMem(mems)

      if (det.membresia_activa) {
        const mem = await coreRepository.getMembresia(det.membresia_activa.id)
        setMembresiaActiva(mem)
        const congs = await coreRepository.getCongelamientos(mem.id)
        setHistorialCong(congs)
      } else {
        setMembresiaActiva(null)
        setHistorialCong([])
      }
    } catch {
      toast.error(t('clientes.loadDetalleError'))
    } finally {
      setLoadingDetalle(false)
    }
  }, [t])

  const cargarAsistencias = useCallback(async (idCliente: number) => {
    setLoadingAsist(true)
    try {
      const [r30, hist] = await Promise.all([
        attendanceRepository.getAsistenciasUltimos30(idCliente),
        attendanceRepository.getHistorialCliente(idCliente),
      ])
      setAsistencias30(r30)
      setHistorialAsist(hist.asistencias)
      setTotalAsist(hist.total_en_periodo)
    } catch {
      toast.error(t('asistencias.loadError'))
    } finally {
      setLoadingAsist(false)
    }
  }, [t])

  const handleSelectCliente = (cliente: ClienteListItem) => {
    setSeleccionado(cliente)
    setDetTab('perfil')
    setActiveTab('detalle')
    setAsistencias30(null)
    setHistorialAsist([])
    setTotalAsist(0)
    cargarDetalle(cliente)
  }

  const handleDetTabChange = (tab: DetTab) => {
    setDetTab(tab)
    if (tab === 'asistencias' && seleccionado && !asistencias30 && !loadingAsist) {
      cargarAsistencias(seleccionado.id)
    }
  }

  const refrescarDetalle = () => {
    if (seleccionado) {
      cargarLista()
      cargarDetalle(seleccionado)
      if (detTab === 'asistencias') cargarAsistencias(seleccionado.id)
    }
  }

  const handleRegistrarManual = () => {
    if (!seleccionado) return
    confirmDialog({
      message: t('asistencias.registrarManualConfirmMsg', { nombre: seleccionado.nombre }),
      header: t('asistencias.registrarManual'),
      icon: 'pi pi-user-plus',
      acceptLabel: t('asistencias.registrarSubmit'),
      rejectLabel: t('common.cancel'),
      acceptClassName: 'p-button-warning p-button-sm',
      rejectClassName: 'p-button-outlined p-button-secondary p-button-sm',
      defaultFocus: 'reject',
      accept: async () => {
        try {
          await attendanceRepository.registrarManual(seleccionado.id)
          toast.success(t('asistencias.registradoSuccess'))
          cargarAsistencias(seleccionado.id)
        } catch {
          toast.error(t('asistencias.registradoError'))
        }
      },
    })
  }

  const handleAnularMembresia = () => {
    if (!membresiaActiva) return
    confirmDialog({
      message: t('membresias.anularConfirmMsg'),
      header: t('membresias.anularTitle'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: t('membresias.anularSubmit'),
      rejectLabel: t('common.cancel'),
      acceptClassName: 'p-button-danger p-button-sm',
      rejectClassName: 'p-button-outlined p-button-secondary p-button-sm',
      defaultFocus: 'reject',
      accept: async () => {
        try {
          await coreRepository.anularMembresia(membresiaActiva.id, { motivo: 'Solicitud del cliente' })
          toast.success(t('membresias.anulada'))
          refrescarDetalle()
        } catch {
          toast.error(t('membresias.anularError'))
        }
      },
    })
  }

  const handleReactivar = () => {
    if (!membresiaActiva?.congelamiento_activo) return
    confirmDialog({
      message: t('congelamientos.reactivarConfirmMsg'),
      header: t('congelamientos.reactivarTitle'),
      icon: 'pi pi-question-circle',
      acceptLabel: t('congelamientos.reactivarSubmit'),
      rejectLabel: t('common.cancel'),
      acceptClassName: 'p-button-success p-button-sm',
      rejectClassName: 'p-button-outlined p-button-secondary p-button-sm',
      defaultFocus: 'accept',
      accept: async () => {
        try {
          const res = await coreRepository.reactivarCongelamiento(membresiaActiva.congelamiento_activo!.id)
          toast.success(t('congelamientos.reactivadaSuccess', { dias: res.dias_compensados }))
          refrescarDetalle()
        } catch {
          toast.error(t('congelamientos.reactivadaError'))
        }
      },
    })
  }

  // ── Stats ─────────────────────────────────────────────────────────────────

  const activos = clientes.filter(c => c.estado === 'activo').length
  const proxVencer = clientes.filter(c => c.estado === 'proximo_vencer').length
  const vencidos = clientes.filter(c => c.estado === 'vencido').length

  // ── Tabla ─────────────────────────────────────────────────────────────────

  const tableHeader = (
    <div className="flex items-center gap-2 flex-wrap">
      <div className="flex-1">
        <select
          value={filtroEstado}
          onChange={e => setFiltroEstado(e.target.value)}
          className="text-xs rounded-md px-2 py-1.5 font-sans focus:outline-none focus:ring-2 focus:ring-orange-500"
          style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
        >
          <option value="">{t('clientes.filtroTodos')}</option>
          <option value="activo">{t('clientes.estadoActivo')}</option>
          <option value="proximo_vencer">{t('clientes.estadoProximo')}</option>
          <option value="vencido">{t('clientes.estadoVencido')}</option>
          <option value="congelado">{t('clientes.estadoCongelado')}</option>
          <option value="riesgo_abandono">{t('clientes.estadoRiesgo')}</option>
        </select>
      </div>
      <div className="relative">
        <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none"
          style={{ color: 'var(--input-placeholder)' }} />
        <input
          type="text"
          value={globalFilter}
          onChange={e => setGlobalFilter(e.target.value)}
          placeholder={t('clientes.searchPlaceholder')}
          className="pl-7 pr-3 py-1.5 text-xs rounded-md font-sans focus:outline-none focus:ring-2 focus:ring-orange-500"
          style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--input-text)' }}
        />
      </div>
    </div>
  )

  const nombreTemplate = (c: ClienteListItem) => (
    <span className="flex items-center gap-1.5">
      {seleccionado?.id === c.id && (
        <span className="w-1.5 h-1.5 rounded-full bg-orange-500 flex-shrink-0" />
      )}
      <span style={{ fontWeight: 600, fontSize: '0.64rem', color: seleccionado?.id === c.id ? '#f97316' : 'var(--page-text)' }}>
        {c.nombre}
      </span>
    </span>
  )

  const membresiaTemplate = (c: ClienteListItem) => {
    if (!c.membresia_activa) return (
      <span className="italic" style={{ fontSize: '0.64rem', color: 'var(--page-muted)' }}>{t('membresias.sinMembresia')}</span>
    )
    const m = c.membresia_activa
    return (
      <div style={{ fontSize: '0.64rem' }}>
        <span style={{ color: 'var(--page-text)' }}>{m.tipo}</span>
        <span style={{ color: 'var(--page-muted)' }}>
          {' · '}{m.modo_control === 'accesos' ? `${m.dias_restantes} acc` : `${m.dias_restantes}d`}
        </span>
      </div>
    )
  }

  const tabStyle = (active: boolean) => active
    ? {
        background: 'var(--page-surface)', color: '#f97316',
        borderTop: '1px solid var(--page-border)', borderLeft: '1px solid var(--page-border)',
        borderRight: '1px solid var(--page-border)', borderBottom: '1px solid var(--page-surface)',
        marginBottom: '-1px',
      }
    : { color: 'var(--page-muted)' }

  return (
    <div className="flex flex-col h-full" style={{ color: 'var(--page-text)' }}>
      <ConfirmDialog />

      <PageHeader
        title={t('clientes.title')}
        description={t('clientes.description')}
        action={
          <Button label={t('clientes.nuevo')} icon="pi pi-plus" severity="warning" size="small"
            onClick={() => setRegistrarOpen(true)} />
        }
      />

      {/* Stats */}
      <div className="flex items-center gap-6 px-6 py-3 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--page-border)' }}>
        {[
          { label: t('clientes.statTotal'), value: total, color: 'var(--page-text)' },
          { label: t('clientes.statActivos'), value: activos, color: '#22c55e' },
          { label: t('clientes.statProxVencer'), value: proxVencer, color: '#eab308' },
          { label: t('clientes.statVencidos'), value: vencidos, color: '#ef4444' },
        ].map((s, i) => (
          <div key={i} className="flex items-center gap-2">
            {i > 0 && <div className="h-4 w-px" style={{ background: 'var(--page-border)' }} />}
            <span className="text-2xl font-bold" style={{ color: s.color }}>{s.value}</span>
            <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{s.label}</span>
          </div>
        ))}
      </div>

      {/* Tabs principales */}
      <div className="flex items-end gap-1 px-4 pt-3 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--page-border)' }}>
        <button onClick={() => setActiveTab('lista')}
          className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors"
          style={tabStyle(activeTab === 'lista')}>
          <Users size={12} />
          {t('clientes.tabLista')}
          <span className="ml-1 px-1.5 py-0.5 rounded-full text-[0.55rem] font-bold"
            style={{ background: activeTab === 'lista' ? '#f97316' : 'var(--page-border)', color: activeTab === 'lista' ? '#fff' : 'var(--page-muted)' }}>
            {total}
          </span>
        </button>
        <button
          onClick={() => seleccionado && setActiveTab('detalle')}
          disabled={!seleccionado}
          className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-t-lg transition-colors disabled:cursor-not-allowed disabled:opacity-40"
          style={tabStyle(activeTab === 'detalle')}>
          <User size={12} />
          {seleccionado ? seleccionado.nombre : t('clientes.tabDetallePlaceholder')}
        </button>
      </div>

      {/* Contenido tabs */}
      {activeTab === 'lista' && (
        <div className="flex-1 overflow-auto p-4">
          <DataTable
            value={clientes}
            loading={loading}
            header={tableHeader}
            emptyMessage={
              <div className="flex flex-col items-center justify-center py-14 text-center">
                <Users size={36} className="mb-3" style={{ color: 'var(--page-border)' }} />
                <p className="text-sm font-medium" style={{ color: 'var(--page-muted)' }}>{t('clientes.emptyTitle')}</p>
                <p className="text-xs mt-1 mb-4" style={{ color: 'var(--page-muted)' }}>{t('clientes.emptyHint')}</p>
                <Button label={t('clientes.nuevo')} icon="pi pi-plus" severity="warning" size="small"
                  onClick={() => setRegistrarOpen(true)} />
              </div>
            }
            onRowClick={e => handleSelectCliente(e.data as ClienteListItem)}
            rowClassName={row => `cursor-pointer${seleccionado?.id === row.id ? ' bg-orange-950/40' : ''}`}
            paginator rows={15} rowsPerPageOptions={[10, 15, 25]}
            stripedRows showGridlines={false} size="small"
          >
            <Column field="nombre" header={t('clientes.colNombre')} body={nombreTemplate} sortable />
            <Column field="ci" header={t('clientes.colCi')} style={{ color: 'var(--page-muted)' }} />
            <Column field="telefono" header={t('clientes.colTelefono')} style={{ color: 'var(--page-muted)' }} />
            <Column field="estado" header={t('clientes.colEstado')}
              body={c => <BadgeEstado estado={c.estado} />} />
            <Column header={t('clientes.colMembresia')} body={membresiaTemplate} />
          </DataTable>
        </div>
      )}

      {activeTab === 'detalle' && seleccionado && (
        <div className="flex-1 overflow-hidden flex flex-col">
          {/* Sub-header */}
          <div className="flex items-center justify-between px-5 py-3 flex-shrink-0"
            style={{ borderBottom: '1px solid var(--page-border)' }}>
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-full bg-orange-500 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                {seleccionado.nombre[0]?.toUpperCase()}
              </div>
              <div>
                <p className="text-xs font-semibold" style={{ color: 'var(--page-text)' }}>{seleccionado.nombre}</p>
                <p className="text-xs" style={{ color: 'var(--page-muted)' }}>{seleccionado.ci}</p>
              </div>
              <BadgeEstado estado={seleccionado.estado} />
            </div>
            <button onClick={() => { setActiveTab('lista'); setSeleccionado(null) }}
              className="text-xs transition-colors" style={{ color: 'var(--page-muted)' }}>
              ← {t('clientes.tabLista')}
            </button>
          </div>

          {/* Sub-tabs detalle */}
          <div className="flex items-end gap-1 px-4 pt-2 flex-shrink-0"
            style={{ borderBottom: '1px solid var(--page-border)' }}>
            {(['perfil', 'membresia', 'historial_mem', 'congelamientos', 'asistencias', 'usuario_app'] as DetTab[]).map(tab => {
              const icons: Record<DetTab, JSX.Element> = {
                perfil: <User size={11} />,
                membresia: <CreditCard size={11} />,
                historial_mem: <History size={11} />,
                congelamientos: <Snowflake size={11} />,
                asistencias: <Activity size={11} />,
                usuario_app: <Smartphone size={11} />,
              }
              const labels: Record<DetTab, string> = {
                perfil: t('clientes.subTabPerfil'),
                membresia: t('membresias.activaTitle'),
                historial_mem: t('membresias.historialTitle'),
                congelamientos: t('congelamientos.historialTitle'),
                asistencias: t('asistencias.tabLabel'),
                usuario_app: t('clientes.subTabUsuarioApp'),
              }
              return (
                <button key={tab} onClick={() => handleDetTabChange(tab)}
                  className="flex items-center gap-1 px-3 py-1.5 text-[0.65rem] font-semibold rounded-t-lg transition-colors"
                  style={detTab === tab
                    ? { background: 'var(--page-surface)', color: '#f97316', borderTop: '1px solid var(--page-border)', borderLeft: '1px solid var(--page-border)', borderRight: '1px solid var(--page-border)', borderBottom: '1px solid var(--page-surface)', marginBottom: '-1px' }
                    : { color: 'var(--page-muted)' }}>
                  {icons[tab]}
                  {labels[tab]}
                </button>
              )
            })}
            <button onClick={refrescarDetalle} className="ml-auto mb-1 p-1 rounded transition-colors"
              style={{ color: 'var(--page-muted)' }} title={t('common.refresh')}>
              <RefreshCw size={12} />
            </button>
          </div>

          <div className="flex-1 overflow-auto">
            {loadingDetalle ? (
              <div className="flex items-center justify-center py-16">
                <span className="text-xs" style={{ color: 'var(--page-muted)' }}>{t('common.loading')}</span>
              </div>
            ) : (
              <>
                {detTab === 'perfil' && detalle && (
                  <div className="p-4 grid grid-cols-2 gap-4">
                    <div className="rounded-lg p-3 col-span-2"
                      style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
                      <p className="text-xs font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--page-muted)' }}>
                        {t('clientes.sectionPersona')}
                      </p>
                      <div className="grid grid-cols-2 gap-2 text-xs">
                        {[
                          [t('clientes.fieldCi'), detalle.persona.ci],
                          [t('clientes.fieldNombre'), detalle.persona.nombre],
                          [t('clientes.fieldTelefono'), detalle.persona.telefono ?? '—'],
                          [t('clientes.fieldCorreo'), detalle.persona.correo ?? '—'],
                          [t('clientes.fieldSexo'), detalle.persona.sexo === 'M' ? t('clientes.sexoM') : detalle.persona.sexo === 'F' ? t('clientes.sexoF') : detalle.persona.sexo === 'O' ? t('clientes.sexoO') : '—'],
                        ].map(([l, v]) => (
                          <div key={l}>
                            <span style={{ color: 'var(--page-muted)' }}>{l}: </span>
                            <span style={{ color: 'var(--page-text)' }}>{v}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                    <div className="rounded-lg p-3"
                      style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
                      <p className="text-xs font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--page-muted)' }}>
                        {t('clientes.sectionFisico')}
                      </p>
                      <div className="space-y-1 text-xs">
                        <div><span style={{ color: 'var(--page-muted)' }}>{t('clientes.fieldPeso')}: </span><span style={{ color: 'var(--page-text)' }}>{detalle.peso_kg ?? '—'} kg</span></div>
                        <div><span style={{ color: 'var(--page-muted)' }}>{t('clientes.fieldAltura')}: </span><span style={{ color: 'var(--page-text)' }}>{detalle.altura_cm ?? '—'} cm</span></div>
                      </div>
                    </div>
                    <div className="rounded-lg p-3"
                      style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
                      <p className="text-xs font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--page-muted)' }}>
                        {t('clientes.sectionGym')}
                      </p>
                      <div className="space-y-1 text-xs">
                        <div><span style={{ color: 'var(--page-muted)' }}>{t('clientes.fieldIngreso')}: </span><span style={{ color: 'var(--page-text)' }}>{detalle.fecha_ingreso}</span></div>
                        <div><span style={{ color: 'var(--page-muted)' }}>{t('clientes.fieldCarnet')}: </span><span style={{ color: 'var(--page-text)' }}>{detalle.codigo_carnet ?? '—'}</span></div>
                      </div>
                    </div>
                    {detalle.objetivos && (
                      <div className="rounded-lg p-3 col-span-2"
                        style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
                        <p className="text-xs font-semibold uppercase tracking-wider mb-1" style={{ color: 'var(--page-muted)' }}>
                          {t('clientes.fieldObjetivos')}
                        </p>
                        <p className="text-xs" style={{ color: 'var(--page-text)' }}>{detalle.objetivos}</p>
                      </div>
                    )}
                    {detalle.lesiones && (
                      <div className="rounded-lg p-3 col-span-2"
                        style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
                        <p className="text-xs font-semibold uppercase tracking-wider mb-1" style={{ color: 'var(--page-muted)' }}>
                          {t('clientes.fieldLesiones')}
                        </p>
                        <p className="text-xs" style={{ color: 'var(--page-text)' }}>{detalle.lesiones}</p>
                      </div>
                    )}
                  </div>
                )}

                {detTab === 'membresia' && (
                  <MembresiaActivaCard
                    membresia={membresiaActiva}
                    onVender={() => setVenderOpen(true)}
                    onAnular={handleAnularMembresia}
                    onCongelar={() => setCongelarOpen(true)}
                    onReactivar={handleReactivar}
                    onCargarHistorial={() => setCargarAsistOpen(true)}
                  />
                )}

                {detTab === 'historial_mem' && (
                  <HistorialMembresiasTab historial={historialMem} />
                )}

                {detTab === 'congelamientos' && (
                  <HistorialCongelamientosTab historial={historialCong} />
                )}

                {detTab === 'asistencias' && (
                  <AsistenciasClienteTab
                    data30={asistencias30}
                    historial={historialAsist}
                    totalHistorial={totalAsist}
                    loading={loadingAsist}
                    onRegistrarManual={handleRegistrarManual}
                  />
                )}

                {detTab === 'usuario_app' && detalle && (
                  <UsuarioAppTab ci={detalle.persona.ci} />
                )}
              </>
            )}
          </div>
        </div>
      )}

      {/* Modales */}
      <RegistrarClienteModal
        open={registrarOpen}
        idSucursal={1}
        onClose={() => setRegistrarOpen(false)}
        onRegistrado={id => { setRegistrarOpen(false); cargarLista(); toast.success(t('clientes.registerSuccess')) }}
      />

      {seleccionado && (
        <>
          <VenderMembresiaModal
            idCliente={seleccionado.id}
            nombreCliente={seleccionado.nombre}
            open={venderOpen}
            onClose={() => setVenderOpen(false)}
            onVendida={() => { setVenderOpen(false); refrescarDetalle() }}
          />

          {membresiaActiva && (
            <CongelarMembresiaModal
              idMembresia={membresiaActiva.id}
              open={congelarOpen}
              onClose={() => setCongelarOpen(false)}
              onCongelada={() => { setCongelarOpen(false); refrescarDetalle() }}
            />
          )}

          {membresiaActiva && (
            <CargarAsistenciasModal
              open={cargarAsistOpen}
              onClose={() => setCargarAsistOpen(false)}
              idMembresia={membresiaActiva.id}
              valorActual={membresiaActiva.asistencias_previas ?? 0}
              onActualizado={nuevaCantidad => {
                setMembresiaActiva(prev => prev ? { ...prev, asistencias_previas: nuevaCantidad } : prev)
                setCargarAsistOpen(false)
              }}
            />
          )}
        </>
      )}
    </div>
  )
}
