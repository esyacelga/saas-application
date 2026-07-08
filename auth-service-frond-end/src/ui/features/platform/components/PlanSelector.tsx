import { useEffect, useState } from 'react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { usePlatformStore } from '@/infrastructure/store/platform/platform.store'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import type { Plan } from '@/domain/platform/entities/Plan.entity'

interface Props {
  value?: string
  onValueChange: (value: string) => void
  filter?: (plan: Plan) => boolean
  placeholder?: string
  disabled?: boolean
}


export function PlanSelector({ value, onValueChange, filter, placeholder = 'Seleccionar plan', disabled }: Props) {
  const { planes, setPlanes } = usePlatformStore()
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (planes.length === 0) {
      setLoading(true)
      platformRepository.getPlanes()
        .then(setPlanes)
        .catch(() => {})
        .finally(() => setLoading(false))
    }
  }, [planes.length, setPlanes])

  const list = filter ? planes.filter(filter) : planes

  return (
    <Select value={value} onValueChange={(v: string | null) => v && onValueChange(v)} disabled={disabled || loading}>
      <SelectTrigger>
        <SelectValue placeholder={loading ? 'Cargando planes…' : placeholder} />
      </SelectTrigger>
      <SelectContent>
        {list.map(plan => (
          <SelectItem key={plan.id} value={String(plan.id)} label={plan.nombre}>
            <span>{plan.nombre}</span>
            <span className="ml-auto pl-4 text-[0.65rem] opacity-60">${plan.precioMensual.toFixed(2)}/mes</span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
