import { useEffect, useState } from 'react'
import { Plus, Trash2, Loader2, Info } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarNotifConfigUseCase } from '@/application/platform/GestionarNotifConfig.usecase'
import { GestionarNotifBucketsUseCase } from '@/application/platform/GestionarNotifBuckets.usecase'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import type { NotifConfig, ConsentimientoWaResponse } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'

const usecase = new GestionarNotifConfigUseCase(platformRepository)
const bucketsUsecase = new GestionarNotifBucketsUseCase(platformRepository)

interface Props { idCompania: number }

// ── WhatsApp opt-in block ─────────────────────────────────────────────────────

function ConsentimientoWaBlock({ idCompania, isSuperAdmin }: { idCompania: number; isSuperAdmin: boolean }) {
  const { t } = useTranslation()
  const [consent, setConsent] = useState<ConsentimientoWaResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    // Load initial state from the compania detail — the backend PATCH also returns state.
    // We initialise with a neutral state and let the first PATCH reflect it.
    // Since there is no GET for consentimiento alone, we start with null and load
    // via a dry read from the compania. Use a sentinel: fetch without changing.
    setLoading(false)
  }, [])

  const handleToggle = async (acepta: boolean) => {
    setSaving(true)
    try {
      const updated = await bucketsUsecase.patchConsentimientoWaCompania(idCompania, acepta)
      setConsent(updated)
      toast.success(t('platform.consentimientoWa.saveSuccess'))
    } catch {
      toast.error(t('platform.consentimientoWa.saveError'))
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <Skeleton className="h-14 w-full rounded-lg" />
  }

  const fechaStr = consent?.fechaConsentimientoWa
    ? new Date(consent.fechaConsentimientoWa).toLocaleDateString()
    : null

  return (
    <div
      className="rounded-lg p-4 space-y-2"
      style={{ border: '1px solid var(--page-border)', background: 'var(--page-surface)' }}
    >
      <div className="flex items-center justify-between">
        <div className="space-y-0.5">
          <p className="text-sm font-medium" style={{ color: 'var(--page-text)' }}>
            {t('platform.consentimientoWa.label')}
          </p>
          {fechaStr && (
            <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
              {t('platform.consentimientoWa.fechaConsentimiento', { fecha: fechaStr })}
            </p>
          )}
          {!fechaStr && consent !== null && (
            <p className="text-xs" style={{ color: 'var(--page-muted)' }}>
              {t('platform.consentimientoWa.sinFecha')}
            </p>
          )}
        </div>
        <div className="flex items-center gap-2">
          {saving && <Loader2 size={13} className="animate-spin" style={{ color: 'var(--page-muted)' }} />}
          <Switch
            checked={consent?.aceptaWhatsapp ?? false}
            onCheckedChange={handleToggle}
            disabled={saving || (!isSuperAdmin)}
          />
        </div>
      </div>
    </div>
  )
}

// ── Main tab ──────────────────────────────────────────────────────────────────

export function NotifConfigTab({ idCompania }: Props) {
  const { t } = useTranslation()
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const isSuperAdmin = user?.rol_plataforma === 'super_admin'

  const [configs, setConfigs] = useState<NotifConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    usecase.getNotifConfig(idCompania)
      .then(setConfigs)
      .catch(() => toast.error(t('platform.notif.loadError')))
      .finally(() => setLoading(false))
  }, [idCompania, t])

  const handleAdd = () => {
    setConfigs(prev => [...prev, { idCompania, diasAntes: 7, canal: 'EMAIL', activo: true }])
  }

  const handleRemove = (idx: number) => {
    setConfigs(prev => prev.filter((_, i) => i !== idx))
  }

  const handleChange = (idx: number, field: keyof NotifConfig, value: unknown) => {
    setConfigs(prev => prev.map((c, i) => i === idx ? { ...c, [field]: value } : c))
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      await usecase.updateNotifConfig(idCompania, configs)
      toast.success(t('platform.notif.saveSuccess'))
    } catch {
      toast.error(t('platform.notif.saveError'))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return (
    <div className="space-y-3 p-4">
      {[1, 2].map(i => <Skeleton key={i} className="h-12 w-full rounded-lg" />)}
    </div>
  )

  return (
    <div className="p-4 space-y-6">

      {/* WhatsApp opt-in del dueño */}
      <div className="space-y-2">
        <h3 className="text-xs font-semibold uppercase tracking-wide" style={{ color: 'var(--page-muted)' }}>
          {t('platform.consentimientoWa.title')}
        </h3>
        <ConsentimientoWaBlock idCompania={idCompania} isSuperAdmin={isSuperAdmin} />
      </div>

      {/* Alertas de vencimiento por email/canal */}
      <div className="space-y-3">
        <div className="flex justify-between items-center">
          <h3 className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
            {t('platform.notif.title')}
          </h3>
          {isSuperAdmin && (
            <Button size="sm" variant="outline" onClick={handleAdd}>
              <Plus size={13} className="mr-1.5" /> {t('platform.notif.agregar')}
            </Button>
          )}
        </div>

        <Alert>
          <Info size={14} className="text-blue-500" />
          <AlertDescription className="text-xs">
            {t('platform.notif.info')}
          </AlertDescription>
        </Alert>

        {configs.length === 0 ? (
          <p className="text-sm" style={{ color: 'var(--page-muted)' }}>Sin alertas configuradas.</p>
        ) : (
          <div className="space-y-2">
            {configs.map((cfg, idx) => (
              <div
                key={idx}
                className="flex items-center gap-3 p-3 rounded-lg"
                style={{ border: '1px solid var(--page-border)', background: 'var(--page-surface)' }}
              >
                <div className="flex items-center gap-2">
                  <span className="text-xs whitespace-nowrap" style={{ color: 'var(--page-muted)' }}>
                    Días antes:
                  </span>
                  {isSuperAdmin ? (
                    <Input
                      type="number"
                      min={1}
                      value={cfg.diasAntes}
                      onChange={e => handleChange(idx, 'diasAntes', Number(e.target.value))}
                      className="h-7 w-16 text-xs"
                    />
                  ) : (
                    <span className="font-medium text-sm">{cfg.diasAntes}</span>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs" style={{ color: 'var(--page-muted)' }}>Canal:</span>
                  {isSuperAdmin ? (
                    <Select value={cfg.canal} onValueChange={(v: string | null) => v && handleChange(idx, 'canal', v)}>
                      <SelectTrigger className="h-7 text-xs w-28">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="EMAIL">EMAIL</SelectItem>
                        <SelectItem value="WHATSAPP">WHATSAPP</SelectItem>
                        <SelectItem value="AMBOS">AMBOS</SelectItem>
                      </SelectContent>
                    </Select>
                  ) : (
                    <span className="font-medium text-sm">{cfg.canal}</span>
                  )}
                </div>
                <div className="flex items-center gap-2 ml-auto">
                  <span className="text-xs" style={{ color: 'var(--page-muted)' }}>Activo</span>
                  <Switch
                    checked={cfg.activo}
                    onCheckedChange={v => handleChange(idx, 'activo', v)}
                    disabled={!isSuperAdmin}
                  />
                  {isSuperAdmin && (
                    <Button
                      variant="ghost" size="icon" className="h-6 w-6 text-red-500 hover:text-red-700"
                      onClick={() => handleRemove(idx)}
                    >
                      <Trash2 size={11} />
                    </Button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {isSuperAdmin && (
          <Button onClick={handleSave} disabled={saving}>
            {saving && <Loader2 size={14} className="animate-spin mr-2" />}
            {t('platform.notif.guardar')}
          </Button>
        )}
      </div>
    </div>
  )
}
