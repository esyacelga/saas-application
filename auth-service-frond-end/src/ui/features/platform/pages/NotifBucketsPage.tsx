import { useEffect, useState } from 'react'
import { Loader2, Bell, Info } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { PageHeader } from '@/ui/components/PageHeader'
import { Switch } from '@/components/ui/switch'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Label } from '@/components/ui/label'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarNotifBucketsUseCase } from '@/application/platform/GestionarNotifBuckets.usecase'
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import { notifBucketItemSchema } from '../schemas/notif-buckets.schema'
import type { NotifBucket } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'

const usecase = new GestionarNotifBucketsUseCase(platformRepository)

export function NotifBucketsPage() {
  const { t } = useTranslation()
  const rawUser = useCurrentUser()
  const user = rawUser?.tipo === 'plataforma' ? (rawUser as JwtPayloadPlataforma) : null
  const isSuperAdmin = user?.rol_plataforma === 'super_admin'

  const [buckets, setBuckets] = useState<NotifBucket[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState<Record<string, boolean>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    usecase.getNotifBuckets()
      .then(setBuckets)
      .catch(() => toast.error(t('platform.notifBuckets.loadError')))
      .finally(() => setLoading(false))
  }, [t])

  const handleChange = (
    destinatario: string,
    field: 'diasPrevio' | 'activo',
    value: number | boolean,
  ) => {
    setBuckets(prev =>
      prev.map(b => b.destinatario === destinatario ? { ...b, [field]: value } : b),
    )
    setErrors(prev => ({ ...prev, [destinatario]: '' }))
  }

  const handleSave = async (bucket: NotifBucket) => {
    const parse = notifBucketItemSchema.safeParse({
      destinatario: bucket.destinatario,
      diasPrevio: bucket.diasPrevio,
      activo: bucket.activo,
    })
    if (!parse.success) {
      const msg = parse.error.issues[0]?.message ?? t('platform.notifBuckets.validationError')
      setErrors(prev => ({ ...prev, [bucket.destinatario]: msg }))
      return
    }

    setSaving(prev => ({ ...prev, [bucket.destinatario]: true }))
    try {
      const updated = await usecase.updateNotifBucket(bucket.destinatario, {
        diasPrevio: bucket.diasPrevio,
        activo: bucket.activo,
      })
      setBuckets(prev => prev.map(b => b.destinatario === updated.destinatario ? updated : b))
      toast.success(t('platform.notifBuckets.saveSuccess'))
    } catch {
      toast.error(t('platform.notifBuckets.saveError'))
    } finally {
      setSaving(prev => ({ ...prev, [bucket.destinatario]: false }))
    }
  }

  const LABEL: Record<string, string> = {
    socio: t('platform.notifBuckets.destSocio'),
    dueno: t('platform.notifBuckets.destDueno'),
  }

  if (loading) {
    return (
      <div className="p-6 space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  return (
    <div style={{ color: 'var(--page-text)' }}>
      <PageHeader
        title={t('platform.notifBuckets.title')}
        description={t('platform.notifBuckets.description')}
      />

      <div className="p-6 space-y-6 max-w-2xl">
        <Alert>
          <Info size={14} style={{ color: 'var(--page-muted)' }} />
          <AlertDescription className="text-sm" style={{ color: 'var(--page-muted)' }}>
            {t('platform.notifBuckets.info')}
          </AlertDescription>
        </Alert>

        {buckets.map(bucket => (
          <div
            key={bucket.destinatario}
            className="rounded-xl p-5 space-y-4"
            style={{
              background: 'var(--page-surface)',
              border: '1px solid var(--page-border)',
            }}
          >
            {/* Card header */}
            <div className="flex items-center gap-2">
              <Bell size={15} style={{ color: 'var(--page-muted)' }} />
              <h3 className="text-sm font-semibold" style={{ color: 'var(--page-text)' }}>
                {LABEL[bucket.destinatario] ?? bucket.destinatario}
              </h3>
            </div>

            {/* Dias previo */}
            <div className="space-y-1.5">
              <Label htmlFor={`diasPrevio-${bucket.destinatario}`} className="text-xs" style={{ color: 'var(--page-muted)' }}>
                {t('platform.notifBuckets.diasPrevioLabel')}
              </Label>
              <Input
                id={`diasPrevio-${bucket.destinatario}`}
                type="number"
                min={1}
                max={30}
                value={bucket.diasPrevio}
                onChange={e => handleChange(bucket.destinatario, 'diasPrevio', Number(e.target.value))}
                disabled={!isSuperAdmin}
                className="w-28 h-8 text-sm"
                style={{ background: 'var(--input-bg)', color: 'var(--page-text)' }}
              />
              {errors[bucket.destinatario] && (
                <p className="text-xs text-red-500">{errors[bucket.destinatario]}</p>
              )}
            </div>

            {/* Activo toggle */}
            <div className="flex items-center gap-3">
              <Switch
                id={`activo-${bucket.destinatario}`}
                checked={bucket.activo}
                onCheckedChange={v => handleChange(bucket.destinatario, 'activo', v)}
                disabled={!isSuperAdmin}
              />
              <Label htmlFor={`activo-${bucket.destinatario}`} className="text-sm cursor-pointer" style={{ color: 'var(--page-text)' }}>
                {bucket.activo
                  ? t('platform.notifBuckets.activoOn')
                  : t('platform.notifBuckets.activoOff')}
              </Label>
            </div>

            {/* Day-0 informational row */}
            <div
              className="flex items-center gap-2 rounded-lg px-3 py-2 text-xs"
              style={{ background: 'var(--page-bg)', border: '1px solid var(--page-border)', color: 'var(--page-muted)' }}
            >
              <Info size={12} />
              {t('platform.notifBuckets.diaVencimientoInfo', { dia: bucket.diaVencimiento })}
            </div>

            {/* Save button */}
            {isSuperAdmin && (
              <Button
                size="sm"
                onClick={() => handleSave(bucket)}
                disabled={!!saving[bucket.destinatario]}
              >
                {saving[bucket.destinatario] && (
                  <Loader2 size={13} className="animate-spin mr-1.5" />
                )}
                {t('platform.notifBuckets.guardar')}
              </Button>
            )}
          </div>
        ))}

        {buckets.length === 0 && !loading && (
          <p className="text-sm" style={{ color: 'var(--page-muted)' }}>
            {t('platform.notifBuckets.empty')}
          </p>
        )}
      </div>
    </div>
  )
}
