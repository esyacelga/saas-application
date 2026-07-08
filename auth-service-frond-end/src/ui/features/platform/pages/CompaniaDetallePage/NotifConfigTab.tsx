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
import { useCurrentUser } from '@/infrastructure/store/auth/auth.store'
import type { NotifConfig } from '@/domain/platform/entities/Plan.entity'
import type { JwtPayloadPlataforma } from '@/domain/auth/entities/User.entity'

const usecase = new GestionarNotifConfigUseCase(platformRepository)

interface Props { idCompania: number }

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
  }, [idCompania])

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
    <div className="p-4 space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="text-sm font-semibold text-slate-700">Alertas de vencimiento</h3>
        {isSuperAdmin && (
          <Button size="sm" variant="outline" onClick={handleAdd}>
            <Plus size={13} className="mr-1.5" /> Agregar alerta
          </Button>
        )}
      </div>

      <Alert>
        <Info size={14} className="text-blue-500" />
        <AlertDescription className="text-xs">
          Las alertas se envían automáticamente cada día a las 00:05 UTC
        </AlertDescription>
      </Alert>

      {configs.length === 0 ? (
        <p className="text-sm text-slate-400">Sin alertas configuradas.</p>
      ) : (
        <div className="space-y-2">
          {configs.map((cfg, idx) => (
            <div key={idx} className="flex items-center gap-3 p-3 border rounded-lg bg-white">
              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-500 whitespace-nowrap">Días antes:</span>
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
                <span className="text-xs text-slate-500">Canal:</span>
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
                <span className="text-xs text-slate-500">Activo</span>
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
          Guardar cambios
        </Button>
      )}
    </div>
  )
}
