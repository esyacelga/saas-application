import { z } from 'zod'

export const registrarPagoSchema = z.object({
  monto: z.coerce.number().positive('El monto debe ser mayor a 0'),
  metodoPago: z.string().min(1, 'Selecciona un método de pago'),
  tipoPago: z.string().min(1, 'Selecciona un tipo de pago'),
  referencia: z.string().optional(),
  periodoDesde: z.string().optional(),
  periodoHasta: z.string().optional(),
})

export type RegistrarPagoForm = z.infer<typeof registrarPagoSchema>
