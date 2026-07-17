import { z } from 'zod'

const today = () => new Date().toISOString().slice(0, 10)

export const reportarPagoSchema = z.object({
  idPlanDestino: z.coerce.number().int().positive('Selecciona un plan de destino'),
  monto: z.coerce
    .number({ error: 'El monto debe ser un número' })
    .positive('El monto debe ser mayor a 0')
    .refine(v => Number(v.toFixed(2)) === v || String(v).replace(/^\d+\.?/, '').length <= 2, {
      message: 'El monto no puede tener más de 2 decimales',
    }),
  fechaTransferencia: z
    .string()
    .min(1, 'La fecha de transferencia es requerida')
    .refine(v => v <= today(), { message: 'La fecha no puede ser futura' }),
  referencia: z.string().min(1, 'La referencia bancaria es requerida').max(200),
  bancoOrigen: z.string().max(100).optional(),
})

export type ReportarPagoForm = z.infer<typeof reportarPagoSchema>
