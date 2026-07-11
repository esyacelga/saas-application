import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { useLimitPlanModalStore } from '@/infrastructure/store/plan/useLimitPlanModalStore'
import type { UsoLimitesResponse } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadStaff } from '@/domain/auth/entities/User.entity'

// Configuración visual por estado
interface EstadoConfig {
  badgeColor: string
  badgeBg: string
  barColor: string
  widgetBorder?: string
}

function getEstadoConfig(
  uso: UsoLimitesResponse,
  porcentaje: number,
): EstadoConfig {
  if (uso.sobreLimite) {
    return {
      badgeColor: '#ef4444',
      badgeBg: 'rgba(239,68,68,0.12)',
      barColor: '#ef4444',
      widgetBorder: 'rgba(239,68,68,0.5)',
    }
  }
  if (uso.planCodigo === 'PREMIUM' || uso.planCodigo === 'LEGACY_GRANDFATHERED') {
    return {
      badgeColor: '#10b981',
      badgeBg: 'rgba(16,185,129,0.12)',
      barColor: '#10b981',
    }
  }
  if (uso.planCodigo === 'TRIAL') {
    return {
      badgeColor: '#818cf8',
      badgeBg: 'rgba(99,102,241,0.12)',
      barColor: '#818cf8',
    }
  }
  // FREE
  if (porcentaje >= 100) {
    return {
      badgeColor: '#ef4444',
      badgeBg: 'rgba(239,68,68,0.12)',
      barColor: '#ef4444',
      widgetBorder: 'rgba(239,68,68,0.5)',
    }
  }
  if (porcentaje >= 80) {
    return {
      badgeColor: '#f59e0b',
      badgeBg: 'rgba(245,158,11,0.12)',
      barColor: '#f59e0b',
    }
  }
  return {
    badgeColor: '#f97316',
    badgeBg: 'rgba(249,115,22,0.12)',
    barColor: 'rgba(249,115,22,0.5)',
  }
}

function formatMax(maximo: number | null): string {
  return maximo === null ? '∞' : String(maximo)
}

// Skeletons durante carga
function WidgetSkeleton() {
  return (
    <div className="px-3 py-2.5 rounded-lg border border-[var(--sidebar-border)] bg-[var(--sidebar-bg)] space-y-1.5 animate-pulse">
      <div className="h-2.5 bg-[var(--sidebar-border)] rounded w-3/4" />
      <div className="h-1.5 bg-[var(--sidebar-border)] rounded w-full" />
      <div className="h-2 bg-[var(--sidebar-border)] rounded w-1/2" />
    </div>
  )
}

export function PlanUsageWidget() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const rawUser = useCurrentUser()
  const refetchWidget = useLimitPlanModalStore((s) => s.refetchWidget)

  const [uso, setUso] = useState<UsoLimitesResponse | null>(null)
  const [cargando, setCargando] = useState(false)

  // TODO(REQ-SAAS-001): reemplazar por <IfPermission permiso="compania:leer"> cuando exista en backend
  const user = rawUser?.tipo === 'staff' ? (rawUser as JwtPayloadStaff) : null
  const esVisible = user !== null && user.id_rol === 1

  useEffect(() => {
    if (!esVisible || !user) return

    let activo = true
    setCargando(true)

    platformRepository
      .getUsoLimites(user.id_compania)
      .then((data) => {
        if (activo) setUso(data)
      })
      .catch(() => {
        // Error silencioso: el widget se oculta
        if (activo) setUso(null)
      })
      .finally(() => {
        if (activo) setCargando(false)
      })

    return () => { activo = false }
  // refetchWidget dispara un nuevo fetch cuando el interceptor o el usuario lo solicita
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [esVisible, user?.id_compania, refetchWidget])

  if (!esVisible) return null
  if (cargando) return <WidgetSkeleton />
  if (!uso) return null

  const actual = uso.clientesActivos.actual
  const maximo = uso.clientesActivos.maximo
  const porcentaje = maximo === null ? 0 : Math.min(100, Math.round((actual / maximo) * 100))

  const config = getEstadoConfig(uso, porcentaje)
  const esPremium = uso.planCodigo === 'PREMIUM' || uso.planCodigo === 'LEGACY_GRANDFATHERED'
  const esTrial = uso.planCodigo === 'TRIAL'
  const esAlerta =
    uso.sobreLimite ||
    (uso.planCodigo === 'FREE' && porcentaje >= 80) ||
    (uso.planCodigo === 'FREE' && porcentaje >= 100)

  // Determina el texto de la línea 3
  function getLinea3(): { texto: string; link: string } | null {
    if (esPremium) return null
    if (uso!.sobreLimite) {
      const fecha = uso!.sobreLimiteHasta ?? ''
      return {
        texto: t('planWidget.alert.sobreLimite', { fecha }),
        link: t('planWidget.actualizar'),
      }
    }
    if (esTrial) {
      const dias = uso!.diasRestantes ?? 0
      return {
        texto: t('planWidget.trial.vence', { dias }),
        link: '',
      }
    }
    if (porcentaje >= 100) {
      return { texto: t('planWidget.alert.limite'), link: t('planWidget.actualizar') }
    }
    if (porcentaje >= 80) {
      return { texto: t('planWidget.alert.cerca'), link: t('planWidget.verPlan') }
    }
    return null
  }

  const linea3 = getLinea3()

  const contenido = (
    <div
      role="region"
      aria-label={t('planWidget.ariaLabel')}
      className="px-3 py-2.5 rounded-lg border space-y-1"
      style={{
        borderColor: config.widgetBorder ?? 'var(--sidebar-border)',
        background: 'var(--sidebar-bg)',
      }}
    >
      {/* Línea 1: badge plan + barra progreso + contador */}
      <div className="flex items-center gap-1.5">
        <span
          className="shrink-0 uppercase font-semibold rounded px-1 py-0.5 leading-none"
          style={{
            fontSize: '0.55rem',
            color: config.badgeColor,
            background: config.badgeBg,
          }}
        >
          {t(`planWidget.plan.${uso.planCodigo}`, { defaultValue: uso.planCodigo })}
        </span>

        {!esPremium && (
          <>
            <div className="flex-1 rounded-full overflow-hidden" style={{ height: '3px', background: 'var(--sidebar-border)' }}>
              <div
                role="progressbar"
                aria-valuenow={actual}
                aria-valuemin={0}
                aria-valuemax={maximo ?? actual}
                aria-label={t('planWidget.clientes.label')}
                style={{
                  width: `${porcentaje}%`,
                  height: '100%',
                  background: config.barColor,
                  transition: 'width 0.4s ease',
                }}
              />
            </div>
            <span style={{ fontSize: '0.6rem', color: 'var(--sidebar-text)', whiteSpace: 'nowrap' }}>
              {actual}/{formatMax(maximo)} {t('planWidget.clientes.label')}
            </span>
          </>
        )}

        {esPremium && (
          <span style={{ fontSize: '0.55rem', color: 'var(--sidebar-text)' }}>
            {t('planWidget.ilimitado')}
          </span>
        )}
      </div>

      {/* Línea 2: staff + sedes */}
      <div style={{ fontSize: '0.55rem', color: 'var(--sidebar-text)' }}>
        {t('planWidget.staff.label')}{' '}
        {uso.staff.actual}/{formatMax(uso.staff.maximo)}
        {' · '}
        {t('planWidget.sedes.label')}{' '}
        {uso.sucursales.actual}/{formatMax(uso.sucursales.maximo)}
      </div>

      {/* Línea 3: alerta condicional */}
      {linea3 && (
        <div className="flex items-center gap-1" style={{ fontSize: '0.55rem', color: config.badgeColor }}>
          <TriangleAlert size={10} style={{ flexShrink: 0 }} />
          <span>{linea3.texto}</span>
          {linea3.link && (
            <button
              onClick={(e) => { e.stopPropagation(); navigate('/admin/mi-suscripcion') }}
              className="underline ml-0.5 focus:outline-none focus:ring-1 focus:ring-current rounded"
              aria-label={t('planWidget.ariaVerPlan')}
              style={{ color: config.badgeColor, fontSize: '0.55rem' }}
            >
              {linea3.link}
            </button>
          )}
        </div>
      )}
    </div>
  )

  // En estados de alerta, el widget completo es clickeable
  if (esAlerta) {
    return (
      <button
        onClick={() => navigate('/admin/mi-suscripcion')}
        className="w-full text-left focus:outline-none focus:ring-1 focus:ring-orange-500 rounded-lg"
        aria-label={t('planWidget.ariaVerPlan')}
      >
        {contenido}
      </button>
    )
  }

  return contenido
}
