import {useCallback, useEffect, useState} from 'react'
import {Link, useNavigate} from 'react-router-dom'
import {useTranslation} from 'react-i18next'
import {toast} from 'sonner'
import {
    Activity,
    AlertTriangle,
    CheckCircle2,
    ClipboardList,
    Clock,
    Layers,
    Rocket,
    Settings2,
    UserPlus,
    Users,
    X,
} from 'lucide-react'
import type {EntradaResumen} from '@/infrastructure/http/attendance/AttendanceHttpRepository'
import type {AsistenciasHoy, EstadisticasDashboard} from '@/infrastructure/http/attendance/AttendanceHttpRepository'
import {attendanceRepository} from '@/infrastructure/http/attendance/AttendanceHttpRepository'
import type {ClienteListItem, MembresiaResumen, ContadorPendientes} from '@/infrastructure/http/core/core.dto'
import {platformRepository} from '@/infrastructure/http/platform/PlatformHttpRepository'
import {coreRepository} from '@/infrastructure/http/core/CoreRepository'
import type {Compania} from '@/domain/platform/entities/Plan.entity'
import {cn} from '@/lib/utils'
import {VenderMembresiaModal} from '@/ui/features/core/components/VenderMembresiaModal'
import {useOnboardingStore} from '@/infrastructure/store/onboarding/onboarding.store'
import {useAuthStore, useHasPermission} from '@/infrastructure/store/auth/auth.store'
import type {JwtPayloadStaff} from '@/domain/auth/entities/User.entity'

// ── Types ─────────────────────────────────────────────────────────────────────

interface DashboardState {
    empresa: Compania | null
    hoy: AsistenciasHoy | null
    stats: EstadisticasDashboard | null
    totalClientesActivos: number | null
    sinSuscripcionTotal: number | null
    proximosVencerTotal: number | null
    contadorPendientes: ContadorPendientes | null
}

// ── KPI Card (estático) ───────────────────────────────────────────────────────

interface KpiCardProps {
    icon: React.ReactNode
    label: string
    value: string | number
    sub?: string
    loading: boolean
}

function KpiCard({icon, label, value, sub, loading}: KpiCardProps) {
    return (
        <div
            className="rounded-xl border p-5 flex flex-col gap-3"
            style={{background: 'var(--page-surface)', borderColor: 'var(--page-border)'}}
        >
            <div className="flex items-center justify-between">
        <span className="text-sm font-medium" style={{color: 'var(--page-muted)'}}>
          {label}
        </span>
                <span style={{color: 'var(--page-muted)'}}>{icon}</span>
            </div>
            {loading ? (
                <div className="h-8 w-24 rounded animate-pulse" style={{background: 'var(--page-border)'}}/>
            ) : (
                <div className="flex items-end gap-2">
          <span className="text-3xl font-bold" style={{color: 'var(--page-text)'}}>
            {value}
          </span>
                    {sub && (
                        <span className="text-sm mb-1" style={{color: 'var(--page-muted)'}}>
              {sub}
            </span>
                    )}
                </div>
            )}
        </div>
    )
}


// ── KPI Card Alerta (clickable) ───────────────────────────────────────────────

interface AlertKpiCardProps {
    label: string
    value: string | number
    sub?: string
    loading: boolean
    onClick: () => void
}

function AlertKpiCard({label, value, sub, loading, onClick}: AlertKpiCardProps) {
    const {t} = useTranslation()
    const isGood = value === 0
    const accentColor = isGood ? '#10b981' : '#f59e0b'
    const borderColor = isGood ? 'rgba(16,185,129,0.5)' : 'rgba(245,158,11,0.5)'
    const iconCls = isGood ? 'text-emerald-500' : 'text-amber-500'
    return (
        <button
            onClick={onClick}
            className="rounded-xl border p-5 flex flex-col gap-3 text-left w-full transition-all hover:shadow-md active:scale-[0.99] group"
            style={{
                background: 'var(--page-surface)',
                borderColor,
                borderLeftWidth: '3px',
                borderLeftColor: accentColor,
            }}
        >
            <div className="flex items-center justify-between">
                <span className="text-sm font-medium" style={{color: 'var(--page-muted)'}}>
                    {label}
                </span>
                {isGood
                    ? <CheckCircle2 size={18} className={`${iconCls} group-hover:scale-110 transition-transform`}/>
                    : <AlertTriangle size={18} className={`${iconCls} group-hover:scale-110 transition-transform`}/>
                }
            </div>
            {loading ? (
                <div className="h-8 w-24 rounded animate-pulse" style={{background: 'var(--page-border)'}}/>
            ) : (
                <>
                    <div className="flex items-end gap-2">
                        <span className="text-3xl font-bold" style={{color: accentColor}}>
                            {value}
                        </span>
                        {sub && (
                            <span className="text-sm mb-1" style={{color: 'var(--page-muted)'}}>
                                {sub}
                            </span>
                        )}
                    </div>
                    <span className="text-xs font-medium group-hover:underline" style={{color: accentColor}}>
                        {t('dashboard.verLista')} →
                    </span>
                </>
            )}
        </button>
    )
}

// ── Cliente Avatar ────────────────────────────────────────────────────────────

const AVATAR_COLORS = [
    '#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444',
    '#ec4899', '#14b8a6', '#f97316', '#6366f1', '#84cc16',
]

function avatarBg(nombre: string): string {
    return AVATAR_COLORS[nombre.charCodeAt(0) % AVATAR_COLORS.length]
}

function toNombreDisplay(nombre: string): string {
    return nombre.split(' ').filter(Boolean)
        .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
        .join(' ')
}

function ClienteAvatar({nombre, fotoUrl}: { nombre: string; fotoUrl: string | null }) {
    const [imgError, setImgError] = useState(false)
    const initials = nombre.split(' ').filter(Boolean).slice(0, 2).map(w => w[0].toUpperCase()).join('')

    if (fotoUrl && !imgError) {
        return (
            <img
                src={fotoUrl}
                alt={nombre}
                className="w-9 h-9 rounded-full object-cover shrink-0 border"
                style={{borderColor: 'var(--page-border)'}}
                onError={() => setImgError(true)}
            />
        )
    }
    return (
        <div
            className="w-9 h-9 rounded-full shrink-0 flex items-center justify-center text-xs font-bold text-white"
            style={{background: avatarBg(nombre)}}
        >
            {initials || '?'}
        </div>
    )
}

// ── Quick Link ────────────────────────────────────────────────────────────────

interface QuickLinkProps {
    to: string
    icon: React.ReactNode
    label: string
}

function QuickLink({to, icon, label}: QuickLinkProps) {
    return (
        <Link
            to={to}
            className="flex items-center gap-3 rounded-lg border px-4 py-3 transition-colors hover:opacity-80"
            style={{background: 'var(--page-surface)', borderColor: 'var(--page-border)', color: 'var(--page-text)'}}
        >
            <span style={{color: 'var(--page-muted)'}}>{icon}</span>
            <span className="text-sm font-medium">{label}</span>
        </Link>
    )
}


// ── Sin Suscripción Panel ─────────────────────────────────────────────────────

interface SinSuscripcionPanelProps {
    total: number
    onClose: () => void
    onVendida: (idCliente: number) => void
}

function SinSuscripcionPanel({total, onClose, onVendida}: SinSuscripcionPanelProps) {
    const {t} = useTranslation()
    const [clientes, setClientes] = useState<ClienteListItem[]>([])
    const [loading, setLoading] = useState(true)
    const [modalCliente, setModalCliente] = useState<{id: number; nombre: string} | null>(null)

    useEffect(() => {
        coreRepository.getClientes({sin_membresia: true, limit: 100})
            .then(r => setClientes(r.datos))
            .catch(() => toast.error(t('dashboard.loadError')))
            .finally(() => setLoading(false))
    }, [t])

    const handleVendida = (idCliente: number) => {
        setModalCliente(null)
        setClientes(prev => prev.filter(c => c.id !== idCliente))
        onVendida(idCliente)
    }

    return (
        <>
            <div
                className="fixed inset-0 z-40"
                style={{background: 'rgba(0,0,0,0.4)'}}
                onClick={onClose}
            />
            <div
                className="fixed right-0 top-0 h-full z-50 flex flex-col w-full max-w-sm shadow-2xl"
                style={{background: 'var(--page-bg)', borderLeft: '1px solid var(--page-border)'}}
            >
                <div
                    className="flex items-center justify-between px-5 py-4 border-b flex-shrink-0"
                    style={{borderColor: 'var(--page-border)'}}
                >
                    <div>
                        <p className="font-semibold text-sm" style={{color: 'var(--page-text)'}}>
                            {t('dashboard.kpi.sinSuscripcion')}
                        </p>
                        <p className="text-xs mt-0.5" style={{color: 'var(--page-muted)'}}>
                            {total} {t('dashboard.kpi.sinSuscripcionSub')}
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        className="rounded-lg p-1.5 transition-colors hover:opacity-70"
                        style={{color: 'var(--page-muted)'}}
                    >
                        <X size={18}/>
                    </button>
                </div>

                {modalCliente && (
                    <VenderMembresiaModal
                        idCliente={modalCliente.id}
                        nombreCliente={modalCliente.nombre}
                        open={true}
                        onClose={() => setModalCliente(null)}
                        onVendida={() => handleVendida(modalCliente.id)}
                    />
                )}

                <div className="flex-1 overflow-y-auto">
                    {loading ? (
                        <div className="flex flex-col">
                            {Array.from({length: 6}).map((_, i) => (
                                <div
                                    key={i}
                                    className="flex items-center gap-3 px-5 py-3 border-b animate-pulse"
                                    style={{borderColor: 'var(--page-border)'}}
                                >
                                    <div className="w-8 h-8 rounded-full shrink-0"
                                         style={{background: 'var(--page-border)'}}/>
                                    <div className="flex-1 space-y-1.5">
                                        <div className="h-3 rounded w-3/4"
                                             style={{background: 'var(--page-border)'}}/>
                                        <div className="h-2.5 rounded w-1/2"
                                             style={{background: 'var(--page-border)'}}/>
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : clientes.length === 0 ? (
                        <div className="flex items-center justify-center h-32">
                            <p className="text-sm" style={{color: 'var(--page-muted)'}}>
                                {t('dashboard.noEntriesToday')}
                            </p>
                        </div>
                    ) : (
                        <ul className="divide-y" style={{borderColor: 'var(--page-border)'}}>
                            {clientes.map(c => (
                                <li key={c.id} className="flex items-center gap-3 px-5 py-3">
                                    <div
                                        className="w-8 h-8 rounded-full shrink-0 flex items-center justify-center text-xs font-bold"
                                        style={{background: 'var(--page-border)', color: 'var(--page-muted)'}}
                                    >
                                        {c.nombre[0]}
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-sm font-medium truncate"
                                           style={{color: 'var(--page-text)'}}>{c.nombre}</p>
                                        <p className="text-xs font-mono"
                                           style={{color: 'var(--page-muted)'}}>{c.ci}</p>
                                    </div>
                                    <button
                                        onClick={() => setModalCliente({id: c.id, nombre: c.nombre})}
                                        className="text-xs font-medium px-2 py-1 rounded transition-colors hover:opacity-80 whitespace-nowrap"
                                        style={{
                                            background: 'rgba(245,158,11,0.15)',
                                            color: '#f59e0b',
                                            border: '1px solid rgba(245,158,11,0.3)',
                                        }}
                                    >
                                        {t('dashboard.asignarMembresia')}
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </div>
        </>
    )
}

// ── Próximos a Vencer Panel ───────────────────────────────────────────────────

interface ProximosVencerPanelProps {
    total: number
    onClose: () => void
    onRenovada: (idCliente: number) => void
}

function getBadge(mem: MembresiaResumen | null, t: (key: string) => string): { label: string; color: string; bg: string } {
    if (!mem) return { label: '—', color: '#9ca3af', bg: 'rgba(156,163,175,0.15)' }
    if (mem.modo_control === 'accesos') {
        const n = mem.accesos_restantes ?? 0
        const color = n <= 1 ? '#ef4444' : n === 2 ? '#f59e0b' : '#fbbf24'
        const label = n === 0 ? t('dashboard.kpi.sinAccesos')
                    : n === 1 ? '1 acceso'
                    : `${n} accesos`
        return { label, color, bg: `${color}18` }
    }
    const n = mem.dias_restantes
    const color = n <= 1 ? '#ef4444' : n === 2 ? '#f59e0b' : '#fbbf24'
    const label = n === 0 ? t('dashboard.kpi.venceHoy')
                : n === 1 ? '1 día'
                : `${n} días`
    return { label, color, bg: `${color}18` }
}

function formatFecha(iso: string): string {
    const [y, m, d] = iso.split('-')
    return `${d}/${m}/${y}`
}

function ProximosVencerPanel({total, onClose, onRenovada}: ProximosVencerPanelProps) {
    const {t} = useTranslation()
    const [clientes, setClientes] = useState<ClienteListItem[]>([])
    const [loading, setLoading] = useState(true)
    const [modalCliente, setModalCliente] = useState<{id: number; nombre: string} | null>(null)

    useEffect(() => {
        coreRepository.getClientes({estado: 'proximo_vencer', limit: 100})
            .then(r => {
                const sorted = [...r.datos].sort((a, b) => {
                    const urgA = a.membresia_activa?.modo_control === 'accesos'
                        ? (a.membresia_activa.accesos_restantes ?? 0)
                        : (a.membresia_activa?.dias_restantes ?? 0)
                    const urgB = b.membresia_activa?.modo_control === 'accesos'
                        ? (b.membresia_activa.accesos_restantes ?? 0)
                        : (b.membresia_activa?.dias_restantes ?? 0)
                    return urgA - urgB
                })
                setClientes(sorted)
            })
            .catch(() => toast.error(t('dashboard.loadError')))
            .finally(() => setLoading(false))
    }, [t])

    const handleRenovada = (idCliente: number) => {
        setModalCliente(null)
        setClientes(prev => prev.filter(c => c.id !== idCliente))
        onRenovada(idCliente)
    }

    return (
        <>
            <div
                className="fixed inset-0 z-40"
                style={{background: 'rgba(0,0,0,0.4)'}}
                onClick={onClose}
            />
            <div
                className="fixed right-0 top-0 h-full z-50 flex flex-col w-full max-w-sm shadow-2xl"
                style={{background: 'var(--page-bg)', borderLeft: '1px solid var(--page-border)'}}
            >
                {/* Header */}
                <div
                    className="flex items-center justify-between px-5 py-4 border-b flex-shrink-0"
                    style={{borderColor: 'var(--page-border)'}}
                >
                    <div>
                        <p className="font-semibold text-sm" style={{color: 'var(--page-text)'}}>
                            {t('dashboard.kpi.proximosVencer')}
                        </p>
                        <p className="text-xs mt-0.5" style={{color: 'var(--page-muted)'}}>
                            {total} {t('dashboard.kpi.proximosVencerSub')}
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        className="rounded-lg p-1.5 transition-colors hover:opacity-70"
                        style={{color: 'var(--page-muted)'}}
                        aria-label="Cerrar"
                    >
                        <X size={18}/>
                    </button>
                </div>

                {modalCliente && (
                    <VenderMembresiaModal
                        idCliente={modalCliente.id}
                        nombreCliente={modalCliente.nombre}
                        open={true}
                        onClose={() => setModalCliente(null)}
                        onVendida={() => handleRenovada(modalCliente.id)}
                    />
                )}

                {/* List */}
                <div className="flex-1 overflow-y-auto">
                    {loading ? (
                        <div className="flex flex-col">
                            {Array.from({length: 6}).map((_, i) => (
                                <div
                                    key={i}
                                    className="flex items-center gap-3 px-5 py-3.5 border-b animate-pulse"
                                    style={{borderColor: 'var(--page-border)'}}
                                >
                                    <div className="w-9 h-9 rounded-full shrink-0"
                                         style={{background: 'var(--page-border)'}}/>
                                    <div className="flex-1 space-y-2">
                                        <div className="h-3 rounded w-3/4" style={{background: 'var(--page-border)'}}/>
                                        <div className="h-2.5 rounded w-1/2" style={{background: 'var(--page-border)'}}/>
                                    </div>
                                    <div className="h-6 w-14 rounded-full" style={{background: 'var(--page-border)'}}/>
                                </div>
                            ))}
                        </div>
                    ) : clientes.length === 0 ? (
                        <div className="flex flex-col items-center justify-center h-40 gap-2">
                            <CheckCircle2 size={32} className="text-emerald-500"/>
                            <p className="text-sm font-medium" style={{color: 'var(--page-text)'}}>
                                {t('dashboard.kpi.proximosVencerEmpty')}
                            </p>
                        </div>
                    ) : (
                        <ul className="divide-y" style={{borderColor: 'var(--page-border)'}}>
                            {clientes.map(c => {
                                const badge = getBadge(c.membresia_activa, t)
                                const mem = c.membresia_activa
                                const initial = c.nombre[0]?.toUpperCase() ?? '?'
                                return (
                                    <li
                                        key={c.id}
                                        className="flex items-center gap-3 px-5 py-3 transition-colors"
                                        style={{}}
                                        onMouseEnter={e => (e.currentTarget.style.background = 'var(--page-surface)')}
                                        onMouseLeave={e => (e.currentTarget.style.background = '')}
                                    >
                                        {/* Avatar */}
                                        <div
                                            className="w-9 h-9 rounded-full shrink-0 flex items-center justify-center text-sm font-bold text-white"
                                            style={{background: avatarBg(c.nombre)}}
                                        >
                                            {initial}
                                        </div>

                                        {/* Info */}
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-1.5 flex-wrap">
                                                <p className="text-sm font-medium leading-tight truncate"
                                                   style={{color: 'var(--page-text)'}}>{c.nombre}</p>
                                                <span
                                                    className="text-xs font-semibold px-1.5 py-0.5 rounded-full shrink-0"
                                                    style={{
                                                        background: badge.bg,
                                                        color: badge.color,
                                                        border: `1px solid ${badge.color}30`,
                                                    }}
                                                >
                                                    {badge.label}
                                                </span>
                                            </div>
                                            <div className="flex items-center gap-1.5 mt-0.5 flex-wrap">
                                                <span className="text-xs font-mono"
                                                      style={{color: 'var(--page-muted)'}}>{c.ci}</span>
                                                {mem?.tipo && (
                                                    <span className="text-xs" style={{color: 'var(--page-muted)'}}>
                                                        · {mem.tipo}
                                                    </span>
                                                )}
                                                {mem?.fecha_fin && (
                                                    <span className="text-xs" style={{color: 'var(--page-muted)'}}>
                                                        · {formatFecha(mem.fecha_fin)}
                                                    </span>
                                                )}
                                            </div>
                                        </div>

                                        {/* Action */}
                                        <button
                                            onClick={() => setModalCliente({id: c.id, nombre: c.nombre})}
                                            className="text-xs font-semibold px-2.5 py-1.5 rounded-lg transition-all hover:opacity-80 active:scale-95 whitespace-nowrap shrink-0"
                                            aria-label={`${t('dashboard.kpi.renovar')} membresía de ${c.nombre}`}
                                            style={{
                                                background: 'rgba(245,158,11,0.12)',
                                                color: '#f59e0b',
                                                border: '1px solid rgba(245,158,11,0.3)',
                                            }}
                                        >
                                            {t('dashboard.kpi.renovar')}
                                        </button>
                                    </li>
                                )
                            })}
                        </ul>
                    )}
                </div>
            </div>
        </>
    )
}

// ── Onboarding Checklist ──────────────────────────────────────────────────────

function OnboardingChecklist() {
    const {t} = useTranslation()
    const {totalTipos, totalClientes, checklistOculto, cargado, marcarOculto} = useOnboardingStore()
    const user = useAuthStore(s => s.user)
    const canCreate = useHasPermission('membresias:leer')

    if (!user || user.tipo !== 'staff' || !cargado || totalTipos !== 0 || checklistOculto) {
        return null
    }

    return (
        <div
            className="rounded-lg p-4"
            style={{
                background: 'var(--page-surface)',
                border: '1px solid var(--page-border)',
                borderLeft: '3px solid #f97316',
            }}
        >
            {/* Header */}
            <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-2 flex-1 min-w-0">
                    <Rocket size={16} style={{color: '#f97316', flexShrink: 0, marginTop: '2px'}} />
                    <div className="min-w-0">
                        <p className="text-sm font-semibold" style={{color: 'var(--page-text)'}}>
                            {t('onboarding.titulo')}
                        </p>
                        <p className="text-xs mt-0.5" style={{color: 'var(--page-muted)'}}>
                            {t('onboarding.subtitulo')}
                        </p>
                    </div>
                </div>
                <button
                    type="button"
                    onClick={marcarOculto}
                    className="p-1 rounded transition-colors hover:opacity-70 flex-shrink-0"
                    style={{color: 'var(--page-muted)'}}
                    aria-label={t('onboarding.cerrar')}
                >
                    <X size={14} />
                </button>
            </div>

            {/* Divider */}
            <div className="my-3 border-t" style={{borderColor: 'var(--page-border)'}} />

            {/* Paso 1 */}
            <div className="flex items-start gap-3 mb-3">
                <div className="flex-shrink-0 mt-0.5">
                    {totalTipos === 0 ? (
                        <div
                            className="w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold text-white"
                            style={{background: '#f97316'}}
                        >
                            1
                        </div>
                    ) : (
                        <CheckCircle2 size={24} style={{color: '#22c55e'}} />
                    )}
                </div>
                <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold" style={{color: 'var(--page-text)'}}>
                        {t('onboarding.paso1Label')}
                    </p>
                    <p className="text-xs mt-0.5" style={{color: 'var(--page-muted)'}}>
                        {t('onboarding.paso1Desc')}
                    </p>
                </div>
                <div className="flex-shrink-0">
                    {canCreate ? (
                        <Link
                            to="/admin/tipos-membresia"
                            className="text-xs font-semibold"
                            style={{color: '#f97316'}}
                        >
                            {t('onboarding.paso1Cta')}
                        </Link>
                    ) : (
                        <p className="text-xs" style={{color: 'var(--page-muted)'}}>
                            {t('onboarding.paso1SinPermiso')}
                        </p>
                    )}
                </div>
            </div>

            {/* Paso 2 */}
            <div className="flex items-start gap-3">
                <div className="flex-shrink-0 mt-0.5">
                    {totalClientes === 0 ? (
                        <div
                            className="w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold text-white"
                            style={{background: 'var(--page-muted)'}}
                        >
                            2
                        </div>
                    ) : (
                        <CheckCircle2 size={24} style={{color: '#22c55e'}} />
                    )}
                </div>
                <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold" style={{color: 'var(--page-text)'}}>
                        {t('onboarding.paso2Label')}
                    </p>
                    <p className="text-xs mt-0.5" style={{color: 'var(--page-muted)'}}>
                        {t('onboarding.paso2Desc')}
                    </p>
                </div>
                <div className="flex-shrink-0">
                    <Link
                        to="/admin/clientes"
                        className="text-xs font-semibold"
                        style={{color: '#f97316'}}
                    >
                        {t('onboarding.paso2Cta')}
                    </Link>
                </div>
            </div>
        </div>
    )
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export function DashboardPage() {
    const {t} = useTranslation()
    const user = useAuthStore(s => s.user)
    const navigate = useNavigate()
    const [loading, setLoading] = useState(true)
    const [state, setState] = useState<DashboardState>({
        empresa: null, hoy: null, stats: null,
        totalClientesActivos: null, sinSuscripcionTotal: null, proximosVencerTotal: null,
        contadorPendientes: null,
    })
    const [showPanel, setShowPanel] = useState(false)
    const [showProximosPanel, setShowProximosPanel] = useState(false)

    // Hydrate onboarding store on mount
    useEffect(() => {
        if (!user || user.tipo !== 'staff') return
        const idCompania = (user as JwtPayloadStaff).id_compania
        useOnboardingStore.getState().hidratarDesdeApi(idCompania)
    }, [user])

    useEffect(() => {
        setLoading(true)
        const idCompania = user?.tipo === 'staff' ? (user as JwtPayloadStaff).id_compania : null
        Promise.allSettled([
            platformRepository.getMiEmpresa(),
            attendanceRepository.getAsistenciasHoy(),
            attendanceRepository.getEstadisticas({periodo: 'mes'}),
            coreRepository.getClientes({estado: 'activo', limit: 1}),
            coreRepository.getClientes({sin_membresia: true, limit: 1}),
            coreRepository.getClientes({estado: 'proximo_vencer', limit: 1}),
            idCompania ? coreRepository.getContadorPendientes(idCompania) : Promise.resolve(null),
        ]).then(([empresaR, hoyR, statsR, clientesR, vencidosR, proximosR, contadorR]) => {
            setState({
                empresa: empresaR.status === 'fulfilled' ? empresaR.value : null,
                hoy: hoyR.status === 'fulfilled' ? hoyR.value : null,
                stats: statsR.status === 'fulfilled' ? statsR.value : null,
                totalClientesActivos: clientesR.status === 'fulfilled' ? clientesR.value.total : null,
                sinSuscripcionTotal: vencidosR.status === 'fulfilled' ? vencidosR.value.total : null,
                proximosVencerTotal: proximosR.status === 'fulfilled' ? proximosR.value.total : null,
                contadorPendientes: contadorR.status === 'fulfilled' ? contadorR.value : null,
            })

            const anyFailed = [empresaR, hoyR, statsR, clientesR, vencidosR, proximosR].some(r => r.status === 'rejected')
            if (anyFailed) toast.error(t('dashboard.loadError'))
        }).finally(() => setLoading(false))
    }, [t])

    const handleVendida = useCallback((_idCliente: number) => {
        setState(prev => ({
            ...prev,
            sinSuscripcionTotal: Math.max(0, (prev.sinSuscripcionTotal ?? 1) - 1),
            totalClientesActivos: (prev.totalClientesActivos ?? 0) + 1,
        }))
    }, [])

    const handleRenovada = useCallback((_idCliente: number) => {
        setState(prev => ({
            ...prev,
            proximosVencerTotal: Math.max(0, (prev.proximosVencerTotal ?? 1) - 1),
        }))
    }, [])

    const {empresa, hoy, stats: _stats, totalClientesActivos, sinSuscripcionTotal, proximosVencerTotal, contadorPendientes} = state
    const planNombre = empresa?.planActivo?.nombre ?? t('dashboard.noPlan')

    const metodLabel = (m: string) =>
        t(`dashboard.method.${m}`, {defaultValue: m})

    return (
        <div className="flex flex-col gap-6 p-6" style={{color: 'var(--page-text)'}}>

            {/* ── Empresa header ─────────────────────────────────────────────────── */}
            <div
                className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-xl border px-5 py-4"
                style={{background: 'var(--page-surface)', borderColor: 'var(--page-border)'}}
            >
                <div className="flex items-center gap-3">
                    {empresa?.logoUrl ? (
                        <img src={empresa.logoUrl} alt="logo" className="h-10 w-10 rounded-full object-cover"/>
                    ) : (
                        <div
                            className="h-10 w-10 rounded-full flex items-center justify-center text-lg font-bold"
                            style={{background: 'var(--page-border)', color: 'var(--page-muted)'}}
                        >
                            {empresa?.nombre?.[0] ?? '?'}
                        </div>
                    )}
                    <div>
                        {loading ? (
                            <div className="h-5 w-40 rounded animate-pulse" style={{background: 'var(--page-border)'}}/>
                        ) : (
                            <p className="font-semibold text-base leading-tight">{empresa?.nombre ?? '—'}</p>
                        )}
                        <p className="text-xs mt-0.5" style={{color: 'var(--page-muted)'}}>
                            {t('dashboard.activePlan')}: {loading ? '…' : planNombre}
                        </p>
                    </div>
                </div>
            </div>

            {/* ── Onboarding Checklist ───────────────────────────────────────────── */}
            <OnboardingChecklist />

            {/* ── KPI cards ──────────────────────────────────────────────────────── */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
                <AlertKpiCard
                    label={t('dashboard.kpi.sinSuscripcion')}
                    value={sinSuscripcionTotal ?? 0}
                    sub={t('dashboard.kpi.sinSuscripcionSub')}
                    loading={loading}
                    onClick={() => setShowPanel(true)}
                />
                <KpiCard
                    icon={<Activity size={18}/>}
                    label={t('dashboard.kpi.todayAttendance')}
                    value={hoy?.total_entradas ?? 0}
                    loading={loading}
                />
                <KpiCard
                    icon={<Users size={18}/>}
                    label={t('dashboard.kpi.activeClients')}
                    value={totalClientesActivos ?? 0}
                    loading={loading}
                />
                <AlertKpiCard
                    label={t('dashboard.kpi.proximosVencer')}
                    value={proximosVencerTotal ?? 0}
                    sub={t('dashboard.kpi.proximosVencerSub')}
                    loading={loading}
                    onClick={() => setShowProximosPanel(true)}
                />
                <AlertKpiCard
                    label={t('dashboard.kpi.ventasPendientes')}
                    value={contadorPendientes?.total ?? 0}
                    sub={t('dashboard.kpi.ventasPendientesSub', { cliente: contadorPendientes?.porOrigenCliente ?? 0 })}
                    loading={loading}
                    onClick={() => navigate('/admin/ventas-pendientes')}
                />
            </div>

            {/* ── Bottom row ─────────────────────────────────────────────────────── */}
            <div className="grid grid-cols-1 lg:grid-cols-5 gap-4">

                {/* Últimas entradas de hoy */}
                <div
                    className="lg:col-span-3 rounded-xl border"
                    style={{background: 'var(--page-surface)', borderColor: 'var(--page-border)'}}
                >
                    <div className="px-5 py-4 border-b" style={{borderColor: 'var(--page-border)'}}>
                        <p className="font-semibold text-sm">{t('dashboard.recentEntries')}</p>
                    </div>

                    {loading ? (
                        <div className="flex flex-col divide-y" style={{borderColor: 'var(--page-border)'}}>
                            {Array.from({length: 5}).map((_, i) => (
                                <div key={i}
                                     className="flex items-center gap-3 px-4 py-3">
                                    <div className="w-9 h-9 rounded-full shrink-0 animate-pulse"
                                         style={{background: 'var(--page-border)'}}/>
                                    <div className="flex-1 space-y-1.5">
                                        <div className="h-3 rounded animate-pulse"
                                             style={{background: 'var(--page-border)', width: `${50 + i * 10}%`}}/>
                                        <div className="h-2.5 rounded animate-pulse w-1/4"
                                             style={{background: 'var(--page-border)'}}/>
                                    </div>
                                    <div className="h-5 w-16 rounded-full animate-pulse"
                                         style={{background: 'var(--page-border)'}}/>
                                </div>
                            ))}
                        </div>
                    ) : !hoy || !hoy.ultimas_entradas?.length ? (
                        <p className="px-5 py-8 text-sm text-center" style={{color: 'var(--page-muted)'}}>
                            {t('dashboard.noEntriesToday')}
                        </p>
                    ) : (
                        <ul className="divide-y" style={{borderColor: 'var(--page-border)'}}>
                            {hoy.ultimas_entradas.map((e: EntradaResumen, i: number) => (
                                <li key={i} className="flex items-center gap-3 px-4 py-2.5">
                                    <ClienteAvatar nombre={e.nombre} fotoUrl={e.foto_url}/>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-sm font-medium truncate"
                                           style={{color: 'var(--page-text)'}}>
                                            {toNombreDisplay(e.nombre)}
                                        </p>
                                        <div className="flex items-center gap-1 mt-0.5">
                                            <Clock size={11} style={{color: 'var(--page-muted)'}}/>
                                            <span className="text-xs font-mono"
                                                  style={{color: 'var(--page-muted)'}}>
                                                {e.hora.slice(0, 5)}
                                            </span>
                                        </div>
                                    </div>
                                    <span className={cn(
                                        'text-xs rounded-full px-2 py-0.5 font-medium shrink-0',
                                        e.metodo === 'qr_cliente'
                                            ? 'bg-emerald-100 text-emerald-700'
                                            : e.metodo === 'app_cliente'
                                                ? 'bg-sky-100 text-sky-700'
                                                : 'bg-orange-100 text-orange-700',
                                    )}>
                                        {metodLabel(e.metodo)}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                {/* Accesos rápidos */}
                <div
                    className="lg:col-span-2 rounded-xl border"
                    style={{background: 'var(--page-surface)', borderColor: 'var(--page-border)'}}
                >
                    <div className="px-5 py-4 border-b" style={{borderColor: 'var(--page-border)'}}>
                        <p className="font-semibold text-sm">{t('dashboard.quickAccess')}</p>
                    </div>
                    <div className="flex flex-col gap-2 p-4">
                        <QuickLink
                            to="/admin/clientes"
                            icon={<UserPlus size={16}/>}
                            label={t('dashboard.quickLinks.newClient')}
                        />
                        <QuickLink
                            to="/admin/tipos-membresia"
                            icon={<Layers size={16}/>}
                            label={t('dashboard.quickLinks.membershipTypes')}
                        />
                        <QuickLink
                            to="/admin/bitacora"
                            icon={<ClipboardList size={16}/>}
                            label={t('dashboard.quickLinks.activityLog')}
                        />
                        <QuickLink
                            to="/admin/configuracion"
                            icon={<Settings2 size={16}/>}
                            label={t('dashboard.quickLinks.settings')}
                        />
                    </div>
                </div>

            </div>

            {/* ── Panel sin suscripción ───────────────────────────────────────────── */}
            {showPanel && (
                <SinSuscripcionPanel
                    total={sinSuscripcionTotal ?? 0}
                    onClose={() => setShowPanel(false)}
                    onVendida={handleVendida}
                />
            )}

            {/* ── Panel próximos a vencer ─────────────────────────────────────────── */}
            {showProximosPanel && (
                <ProximosVencerPanel
                    total={proximosVencerTotal ?? 0}
                    onClose={() => setShowProximosPanel(false)}
                    onRenovada={handleRenovada}
                />
            )}

        </div>
    )
}
