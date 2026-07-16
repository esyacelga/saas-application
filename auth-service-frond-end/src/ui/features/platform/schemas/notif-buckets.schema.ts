import { z } from 'zod'

export const notifBucketItemSchema = z.object({
  destinatario: z.enum(['socio', 'dueno']),
  diasPrevio: z.coerce
    .number()
    .int('Debe ser un número entero')
    .min(1, 'Mínimo 1 día')
    .max(30, 'Máximo 30 días'),
  activo: z.boolean(),
})

export const notifBucketsFormSchema = z.object({
  socio: notifBucketItemSchema,
  dueno: notifBucketItemSchema,
})

export type NotifBucketItem = z.infer<typeof notifBucketItemSchema>
export type NotifBucketsForm = z.infer<typeof notifBucketsFormSchema>
