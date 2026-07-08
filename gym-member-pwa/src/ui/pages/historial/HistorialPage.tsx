import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Flame, CalendarCheck, TrendingUp } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { useCurrentUser } from '@/infrastructure/store/auth.store'
import { PulseBackground } from '@/ui/components/PulseBackground'
import {
  attendanceRepository,
  type Ultimos30DiasResult,
  type AsistenciaItem,
} from '@/infrastructure/http/AttendanceHttpRepository'
import { getApiErrorMessage } from '@/lib/api-error'

const METODO_LABEL: Record<string, string> = {
  qr: 'QR',
  manual: 'Manual',
  override: 'Override',
}

const DOW = ['L', 'M', 'M', 'J', 'V', 'S', 'D']

export function HistorialPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const user = useCurrentUser()

  const [stats, setStats] = useState<Ultimos30DiasResult | null>(null)
  const [asistencias, setAsistencias] = useState<AsistenciaItem[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!user) return
    setLoading(true)
    Promise.all([
      attendanceRepository.ultimos30Dias(),
      attendanceRepository.historial(),
    ])
      .then(([s, h]) => {
        setStats(s)
        setAsistencias(h.asistencias)
        setTotal(h.total_en_periodo)
      })
      .catch((err) => toast.error(getApiErrorMessage(err, t('historial.errors.load'))))
      .finally(() => setLoading(false))
  }, [user])

  return (
    <div className="pb-24">
      <PulseBackground />
      <div className="flex items-center gap-3 px-4 pt-8 pb-4 bg-accent-900/20 border-b border-accent-800/40">
        <button
          onClick={() => navigate(-1)}
          aria-label={t('historial.back')}
          className="flex items-center justify-center w-10 h-10 rounded-full bg-slate-800 text-slate-400 hover:text-slate-50 transition-colors"
        >
          <ArrowLeft size={18} />
        </button>
        <h1 className="text-xl font-bold text-slate-50">{t('historial.title')}</h1>
      </div>

      {loading && <LoadingSkeleton />}

      {!loading && stats && (
        <div className="space-y-5 px-4">
          <div className="grid grid-cols-3 gap-3">
            <StatCard
              icon={<CalendarCheck size={18} className="text-emerald-400" />}
              value={stats.dias_asistidos}
              label={t('historial.stats.daysAttended')}
            />
            <StatCard
              icon={<Flame size={18} className="text-orange-400" />}
              value={stats.racha_actual}
              label={t('historial.stats.currentStreak')}
            />
            <StatCard
              icon={<TrendingUp size={18} className="text-accent-400" />}
              value={stats.racha_maxima_mes}
              label={t('historial.stats.bestStreak')}
            />
          </div>

          <Heatmap detalle={stats.detalle} />

          <div>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-semibold text-slate-300">{t('historial.records.title')}</h2>
              <span className="text-xs text-slate-500">{t('historial.records.total', { count: total })}</span>
            </div>

            {asistencias.length === 0 ? (
              <p className="text-sm text-slate-500 text-center py-6">{t('historial.records.empty')}</p>
            ) : (
              <div className="rounded-xl bg-slate-800 border border-slate-700 divide-y divide-slate-700 overflow-hidden">
                {asistencias.slice(0, 20).map((a) => (
                  <AsistenciaRow key={a.id} asistencia={a} />
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

// ── Sub-components ──────────────────────────────────────────────────────────

function StatCard({ icon, value, label }: { icon: React.ReactNode; value: number; label: string }) {
  return (
    <div className="rounded-xl bg-slate-800 border border-slate-700 p-3 flex flex-col gap-1">
      {icon}
      <p className="text-xl font-bold text-slate-50">{value}</p>
      <p className="text-[10px] text-slate-400 leading-tight">{label}</p>
    </div>
  )
}

function Heatmap({ detalle }: { detalle: Ultimos30DiasResult['detalle'] }) {
  const { t } = useTranslation()
  const sorted = [...detalle].sort((a, b) => a.fecha.localeCompare(b.fecha))
  const today = new Date().toISOString().slice(0, 10)

  const firstDay = new Date(sorted[0]?.fecha ?? today)
  const dayOffset = (firstDay.getDay() + 6) % 7
  const gridStart = new Date(firstDay)
  gridStart.setDate(gridStart.getDate() - dayOffset)

  const byDate: Record<string, boolean> = {}
  for (const d of detalle) byDate[d.fecha] = d.asistio

  const WEEKS = 5
  const cells: Array<{ date: string; asistio: boolean | null; isToday: boolean }> = []
  for (let i = 0; i < WEEKS * 7; i++) {
    const d = new Date(gridStart)
    d.setDate(d.getDate() + i)
    const iso = d.toISOString().slice(0, 10)
    const inRange = byDate[iso] !== undefined
    cells.push({ date: iso, asistio: inRange ? byDate[iso] : null, isToday: iso === today })
  }

  return (
    <div>
      <h2 className="text-sm font-semibold text-slate-300 mb-3">{t('historial.heatmap.title')}</h2>
      <div className="rounded-xl bg-slate-800 border border-slate-700 p-4">
        <div className="grid grid-cols-7 gap-1 mb-1">
          {DOW.map((d, i) => (
            <div key={i} className="text-[10px] text-slate-500 text-center">{d}</div>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-1">
          {cells.map(({ date, asistio, isToday }) => (
            <div key={date} title={date} className={`aspect-square rounded-sm ${cellColor(asistio, isToday)}`} />
          ))}
        </div>
        <div className="flex items-center gap-4 mt-3 justify-end">
          <LegendItem color="bg-emerald-500" label={t('historial.heatmap.legend.attended')} />
          <LegendItem color="bg-slate-600" label={t('historial.heatmap.legend.absent')} />
          <LegendItem color="bg-slate-700" label={t('historial.heatmap.legend.noData')} />
        </div>
      </div>
    </div>
  )
}

function cellColor(asistio: boolean | null, isToday: boolean) {
  if (isToday) return asistio ? 'bg-emerald-400 ring-2 ring-emerald-300' : 'bg-accent-800 ring-2 ring-accent-600'
  if (asistio === true) return 'bg-emerald-500'
  if (asistio === false) return 'bg-slate-600'
  return 'bg-slate-700/50'
}

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-1">
      <div className={`w-2.5 h-2.5 rounded-sm ${color}`} />
      <span className="text-[10px] text-slate-400">{label}</span>
    </div>
  )
}

function AsistenciaRow({ asistencia }: { asistencia: AsistenciaItem }) {
  return (
    <div className="flex items-center justify-between px-4 py-3">
      <div>
        <p className="text-sm font-medium text-slate-50">{formatDate(asistencia.fecha)}</p>
        <p className="text-xs text-slate-400 mt-0.5">
          {METODO_LABEL[asistencia.metodo_registro] ?? asistencia.metodo_registro}
        </p>
      </div>
      <p className="text-sm tabular-nums text-slate-300">
        {asistencia.hora_entrada.slice(0, 5)}
      </p>
    </div>
  )
}

function LoadingSkeleton() {
  return (
    <div role="status" aria-label="Cargando historial" className="px-4 space-y-5 motion-safe:animate-pulse">
      <div className="grid grid-cols-3 gap-3">
        {[1, 2, 3].map((i) => (
          <div key={i} className="rounded-xl bg-slate-800 h-20" />
        ))}
      </div>
      <div className="rounded-xl bg-slate-800 h-44" />
      <div className="rounded-xl bg-slate-800 h-48" />
    </div>
  )
}

function formatDate(iso: string) {
  return new Date(iso + 'T00:00:00').toLocaleDateString('es', {
    weekday: 'short', day: '2-digit', month: 'short',
  })
}
