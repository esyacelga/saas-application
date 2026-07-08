import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

const CONFIG: Record<string, { label: string; className: string }> = {
  ACTIVO:     { label: 'Activo',     className: 'bg-green-500/20 text-green-400 border-green-500/30' },
  EN_GRACIA:  { label: 'En gracia',  className: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30' },
  VENCIDO:    { label: 'Vencido',    className: 'bg-red-500/20 text-red-400 border-red-500/30' },
  SUSPENDIDO: { label: 'Suspendido', className: 'bg-red-500/20 text-red-400 border-red-500/30' },
  PROGRAMADO: { label: 'Programado', className: 'bg-slate-500/20 text-slate-400 border-slate-500/30' },
  CANCELADO:  { label: 'Cancelado',  className: 'bg-slate-500/20 text-slate-400 border-slate-500/30' },
}

interface Props { estado: string; className?: string }

export function EstadoPlanBadge({ estado, className }: Props) {
  const cfg = CONFIG[estado?.toUpperCase()] ?? { label: estado, className: 'bg-slate-500/20 text-slate-400 border-slate-500/30' }
  return (
    <Badge variant="outline" className={cn('text-xs font-medium border', cfg.className, className)}>
      {cfg.label}
    </Badge>
  )
}
