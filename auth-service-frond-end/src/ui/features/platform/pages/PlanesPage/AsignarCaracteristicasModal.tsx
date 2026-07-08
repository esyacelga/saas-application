import { useState, useEffect } from 'react'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { GestionarPlanUseCase } from '@/application/platform/GestionarPlan.usecase'
import { GetCaracteristicasUseCase } from '@/application/platform/GetCaracteristicas.usecase'
import type { Plan, Caracteristica } from '@/domain/platform/entities/Plan.entity'

const planUsecase = new GestionarPlanUseCase(platformRepository)
const caracUsecase = new GetCaracteristicasUseCase(platformRepository)

interface Props {
  open: boolean
  plan: Plan | null
  onClose: () => void
  onUpdated: (plan: Plan) => void
}

export function AsignarCaracteristicasModal({ open, plan, onClose, onUpdated }: Props) {
  const { t } = useTranslation()
  const [caracteristicas, setCaracteristicas] = useState<Caracteristica[]>([])
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (open) {
      setLoading(true)
      caracUsecase.execute()
        .then(data => {
          setCaracteristicas(data)
          if (plan) setSelected(new Set(plan.caracteristicas.map(c => c.id)))
        })
        .catch(() => toast.error(t('platform.planes.featuresLoadError')))
        .finally(() => setLoading(false))
    }
  }, [open, plan])

  const toggle = (id: number) => {
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const onSubmit = async () => {
    if (!plan) return
    setSubmitting(true)
    try {
      const updated = await planUsecase.asignarCaracteristicas(plan.id, {
        caracteristicaIds: Array.from(selected),
      })
      toast.success(t('platform.planes.assignSuccess'))
      onUpdated(updated)
      onClose()
    } catch {
      toast.error(t('platform.planes.assignError'))
    } finally {
      setSubmitting(false)
    }
  }

  const byModulo = caracteristicas.reduce<Record<string, Caracteristica[]>>((acc, c) => {
    if (!acc[c.modulo]) acc[c.modulo] = []
    acc[c.modulo].push(c)
    return acc
  }, {})

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Asignar características — {plan?.nombre}</DialogTitle>
        </DialogHeader>
        {loading ? (
          <div className="flex justify-center py-8">
            <Loader2 size={20} className="animate-spin text-slate-400" />
          </div>
        ) : (
          <div className="max-h-[60vh] overflow-y-auto space-y-4 pr-1">
            {Object.entries(byModulo).map(([modulo, items]) => (
              <div key={modulo}>
                <p className="text-xs font-semibold uppercase tracking-wide mb-2" style={{ color: 'var(--page-muted)' }}>{modulo}</p>
                <div className="space-y-2">
                  {items.map(c => (
                    <div key={c.id} className="flex items-center gap-2">
                      <Checkbox
                        id={`c-${c.id}`}
                        checked={selected.has(c.id)}
                        onCheckedChange={() => toggle(c.id)}
                      />
                      <Label htmlFor={`c-${c.id}`} className="cursor-pointer text-sm" style={{ color: 'var(--page-text)' }}>
                        <code
                          className="font-mono text-xs px-1 rounded mr-1"
                          style={{ background: 'var(--page-bg)', color: 'var(--page-muted)', border: '1px solid var(--page-border)' }}
                        >{c.codigo}</code>
                        {c.nombre}
                      </Label>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
          <Button onClick={onSubmit} disabled={submitting}>
            {submitting && <Loader2 size={14} className="animate-spin mr-2" />}
            Guardar asignación
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
