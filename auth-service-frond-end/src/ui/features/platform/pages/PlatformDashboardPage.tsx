import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { Building2, CheckCircle2, XCircle, AlertTriangle, Package, ChevronRight } from 'lucide-react'
import { toast } from 'sonner'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import type { Compania } from '@/domain/platform/entities/Plan.entity'

const PLAN_COLORS = ['#f97316', '#3b82f6', '#8b5cf6', '#10b981', '#ec4899', '#64748b']

function diasLabel(dias: number): { text: string; color: string } {
  if (dias < 0)  return { text: 'Vencido',    color: '#ef4444' }
  if (dias === 0) return { text: 'Vence hoy', color: '#ef4444' }
  if (dias <= 3)  return { text: `${dias} día${dias === 1 ? '' : 's'}`, color: '#ef4444' }
  if (dias <= 7)  return { text: `${dias} días`, color: '#f59e0b' }
  return           { text: `${dias} días`, color: '#d97706' }
}

const ESTADO_BADGE: Record<string, { label: string; color: string; bg: string }> = {
  ACTIVO:     { label: 'Activo',     color: '#16a34a', bg: 'rgba(22,163,74,0.1)'   },
  EN_GRACIA:  { label: 'En gracia',  color: '#d97706', bg: 'rgba(217,119,6,0.1)'   },
  VENCIDO:    { label: 'Vencido',    color: '#ef4444', bg: 'rgba(239,68,68,0.1)'   },
  SUSPENDIDO: { label: 'Suspendido', color: '#64748b', bg: 'rgba(100,116,139,0.1)' },
  PROGRAMADO: { label: 'Programado', color: '#3b82f6', bg: 'rgba(59,130,246,0.1)'  },
  CANCELADO:  { label: 'Cancelado',  color: '#ef4444', bg: 'rgba(239,68,68,0.1)'   },
}

interface KpiCardProps {
  icon: React.ElementType
  label: string
  value: number
  accent: string
  loading: boolean
}

function KpiCard({ icon: Icon, label, value, accent, loading }: KpiCardProps) {
  return (
    <div className="rounded-xl p-5" style={{ background: 'var(--page-surface)', border: '1px solid var(--page-border)' }}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide mb-2" style={{ color: 'var(--page-muted)' }}>
            {label}
          </p>
          {loading
            ? <div className="h-7 w-12 rounded animate-pulse" style={{ background: 'var(--page-border)' }} />
            : <p className="text-2xl font-bold" style={{ color: 'var(--page-text)' }}>{value}</p>
          }
        </div>
        <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
          style={{ background: `${accent}20`, color: accent }}>
          <Icon size={18} />
        </div>
      </div>
    </div>
  )
}

export function PlatformDashboardPage() {
  const [companias, setCompanias] = useState<Compania[]>([])
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    platformRepository.getCompanias()
      .then(setCompanias)
      .catch(() => toast.error('No se pudieron cargar los datos del dashboard.'))
      .finally(() => setLoading(false))
  }, [])

  const kpis = useMemo(() => ({
    total:       companias.length,
    activos:     companias.filter(c => c.activo).length,
    suspendidos: companias.filter(c => !c.activo).length,
    porVencer:   companias.filter(c => c.activo && c.planActivo && c.planActivo.diasRestantes <= 10).length,
  }), [companias])

  const atencion = useMemo(() =>
    companias
      .filter(c => c.activo && c.planActivo && c.planActivo.diasRestantes <= 10)
      .sort((a, b) => a.planActivo!.diasRestantes - b.planActivo!.diasRestantes),
    [companias],
  )

  const planDist = useMemo(() => {
    const map: Record<string, number> = {}
    companias.forEach(c => {
      if (c.activo && c.planActivo) {
        map[c.planActivo.nombre] = (map[c.planActivo.nombre] ?? 0) + 1
      }
    })
    return Object.entries(map).sort((a, b) => b[1] - a[1])
  }, [companias])

  const maxPlan = planDist[0]?.[1] ?? 1

  return (
    <div className="p-6 space-y-6">

      {/* Header */}
      <div>
        <h1 className="text-lg font-bold" style={{ color: 'var(--page-text)' }}>Dashboard</h1>
        <p className="text-sm mt-0.5" style={{ color: 'var(--page-muted)' }}>Resumen general de la plataforma</p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-4 gap-4">
        <KpiCard icon={Building2}     label="Total gimnasios" value={kpis.total}       accent="#f97316" loading={loading} />
        <KpiCard icon={CheckCircle2}  label="Activos"         value={kpis.activos}     accent="#16a34a" loading={loading} />
        <KpiCard icon={XCircle}       label="Suspendidos"     value={kpis.suspendidos} accent="#ef4444" loading={loading} />
        <KpiCard icon={AlertTriangle} label="Por vencer"      value={kpis.porVencer}   accent="#f59e0b" loading={loading} />
      </div>

      {/* Sección inferior */}
      <div className="grid gap-6" style={{ gridTemplateColumns: '1fr 300px' }}>

        {/* Requieren atención */}
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--page-border)' }}>
          <div className="flex items-center gap-2 px-5 py-4 flex-shrink-0"
            style={{ background: 'var(--page-surface)', borderBottom: '1px solid var(--page-border)' }}>
            <AlertTriangle size={14} style={{ color: '#f59e0b' }} />
            <h2 className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Requieren atención</h2>
            <span className="ml-auto text-xs px-2 py-0.5 rounded-full font-medium"
              style={{ background: 'rgba(245,158,11,0.1)', color: '#f59e0b' }}>
              {loading ? '—' : `${atencion.length} gimnasio${atencion.length !== 1 ? 's' : ''}`}
            </span>
          </div>

          {loading ? (
            <div className="p-5 space-y-3">
              {[1, 2, 3].map(i => (
                <div key={i} className="h-12 rounded-lg animate-pulse" style={{ background: 'var(--page-border)' }} />
              ))}
            </div>
          ) : atencion.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-14 gap-2">
              <CheckCircle2 size={32} style={{ color: '#16a34a' }} />
              <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
                Ningún gimnasio próximo a vencer
              </p>
            </div>
          ) : (
            <div>
              {atencion.map(c => {
                const { text, color } = diasLabel(c.planActivo!.diasRestantes)
                const estado = ESTADO_BADGE[c.planActivo!.estado] ?? ESTADO_BADGE['ACTIVO']
                return (
                  <button
                    key={c.id}
                    type="button"
                    onClick={() => navigate(`/platform/companias/${c.id}`)}
                    className="w-full flex items-center gap-4 px-5 py-3.5 text-left transition-colors"
                    style={{ borderBottom: '1px solid var(--page-border)' }}
                    onMouseEnter={e => (e.currentTarget.style.background = 'var(--page-surface)')}
                    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                  >
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate" style={{ color: 'var(--page-text)' }}>
                        {c.nombre}
                      </p>
                      <p className="text-xs mt-0.5" style={{ color: 'var(--page-muted)' }}>
                        {c.planActivo!.nombre}
                      </p>
                    </div>
                    <span className="text-xs font-semibold px-2 py-0.5 rounded-full flex-shrink-0"
                      style={{ background: estado.bg, color: estado.color }}>
                      {estado.label}
                    </span>
                    <span className="text-xs font-bold flex-shrink-0 w-16 text-right" style={{ color }}>
                      {text}
                    </span>
                    <ChevronRight size={14} style={{ color: 'var(--page-muted)', flexShrink: 0 }} />
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* Distribución por plan */}
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--page-border)' }}>
          <div className="flex items-center gap-2 px-5 py-4"
            style={{ background: 'var(--page-surface)', borderBottom: '1px solid var(--page-border)' }}>
            <Package size={14} style={{ color: 'var(--page-muted)' }} />
            <h2 className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>Por plan</h2>
          </div>

          {loading ? (
            <div className="p-5 space-y-5">
              {[1, 2, 3].map(i => (
                <div key={i} className="space-y-2">
                  <div className="h-3 rounded animate-pulse w-1/2" style={{ background: 'var(--page-border)' }} />
                  <div className="h-1.5 rounded-full animate-pulse" style={{ background: 'var(--page-border)' }} />
                </div>
              ))}
            </div>
          ) : planDist.length === 0 ? (
            <div className="flex items-center justify-center py-14">
              <p className="text-sm" style={{ color: 'var(--page-muted)' }}>Sin datos</p>
            </div>
          ) : (
            <div className="p-5 space-y-5">
              {planDist.map(([nombre, count], i) => {
                const color = PLAN_COLORS[i % PLAN_COLORS.length]
                return (
                  <div key={nombre}>
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-medium" style={{ color: 'var(--page-text)' }}>{nombre}</span>
                      <span className="text-xs font-bold" style={{ color }}>
                        {count} gym{count !== 1 ? 's' : ''}
                      </span>
                    </div>
                    <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'var(--page-border)' }}>
                      <div
                        className="h-full rounded-full transition-all duration-700"
                        style={{ width: `${(count / maxPlan) * 100}%`, background: color }}
                      />
                    </div>
                  </div>
                )
              })}

              <div className="pt-3 mt-1" style={{ borderTop: '1px solid var(--page-border)' }}>
                <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
                  {kpis.activos} gimnasio{kpis.activos !== 1 ? 's' : ''} activo{kpis.activos !== 1 ? 's' : ''} en total
                </p>
              </div>
            </div>
          )}
        </div>

      </div>
    </div>
  )
}
