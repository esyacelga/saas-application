import { z } from 'zod'

export const crearPlanSchema = z.object({
  nombre: z.string().min(2, 'Mínimo 2 caracteres').max(100, 'Máximo 100 caracteres'),
  descripcion: z.string().max(500).default(''),
  precioMensual: z.coerce.number().min(0, 'Debe ser 0 o mayor'),
})

export type CrearPlanForm = z.infer<typeof crearPlanSchema>
