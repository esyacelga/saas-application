import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { platformRepository } from '@/infrastructure/http/platform/PlatformHttpRepository'
import { getApiErrorStatus } from '@/lib/api-error'
import type { Caracteristica } from '@/domain/platform/entities/Plan.entity'

const schema = z.object({
  codigo: z.string().min(2, 'Mínimo 2 caracteres').regex(/^[a-z0-9_:]+$/, 'Solo minúsculas, números, _ y :'),
  nombre: z.string().min(2, 'Nombre requerido'),
  modulo: z.string().min(1, 'Selecciona un módulo'),
})
type Form = z.infer<typeof schema>

const MODULOS: { value: string; label: string }[] = [
  { value: 'clientes', label: 'Clientes' },
  { value: 'membresias', label: 'Membresías' },
  { value: 'asistencia', label: 'Asistencia' },
  { value: 'finanzas', label: 'Finanzas' },
  { value: 'marketing', label: 'Marketing' },
  { value: 'inventario', label: 'Inventario' },
]

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (c: Caracteristica) => void
}

export function CrearCaracteristicaModal({ open, onClose, onCreated }: Props) {
  const { t } = useTranslation()
  const {
    register, handleSubmit, reset, setValue, watch,
    setError, formState: { errors, isSubmitting },
  } = useForm<Form>({ resolver: zodResolver(schema) })

  const codigoValue = watch('codigo', '')

  const onSubmit = async (values: Form) => {
    try {
      const c = await platformRepository.crearCaracteristica(values)
      toast.success(t('platform.caracteristicas.createSuccess'))
      reset()
      onCreated(c)
      onClose()
    } catch (err) {
      if (getApiErrorStatus(err) === 409) {
        setError('codigo', { message: t('platform.caracteristicas.duplicateCode') })
      } else {
        toast.error(t('platform.caracteristicas.createError'))
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nueva característica</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Label style={{ color: 'var(--page-text)' }}>Código</Label>
            <Input
              {...register('codigo')}
              value={codigoValue.toLowerCase()}
              onChange={e => setValue('codigo', e.target.value.toLowerCase())}
              placeholder="socios:leer"
              className="mt-1 font-mono"
            />
            {errors.codigo && <p className="text-xs text-red-500 mt-1">{errors.codigo.message}</p>}
          </div>
          <div>
            <Label style={{ color: 'var(--page-text)' }}>Nombre</Label>
            <Input {...register('nombre')} placeholder="Ver socios" className="mt-1" />
            {errors.nombre && <p className="text-xs text-red-500 mt-1">{errors.nombre.message}</p>}
          </div>
          <div>
            <Label style={{ color: 'var(--page-text)' }}>Módulo</Label>
            <Select onValueChange={(v: string | null) => v && setValue('modulo', v)}>
              <SelectTrigger className="mt-1">
                <SelectValue placeholder={t('platform.caracteristicas.selectModulePlaceholder')} />
              </SelectTrigger>
              <SelectContent>
                {MODULOS.map(m => (
                  <SelectItem key={m.value} value={m.value} label={m.label}>{m.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.modulo && <p className="text-xs text-red-500 mt-1">{errors.modulo.message}</p>}
          </div>
          {errors.root && (
            <Alert variant="destructive">
              <AlertDescription>{errors.root.message}</AlertDescription>
            </Alert>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} style={{ fontSize: '0.75rem' }}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting} style={{ fontSize: '0.75rem' }}>
              {isSubmitting && <Loader2 size={14} className="animate-spin mr-2" />}
              Crear característica
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
