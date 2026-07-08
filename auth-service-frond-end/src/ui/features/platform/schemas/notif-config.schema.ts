import { z } from 'zod'

export const notifConfigItemSchema = z.object({
  diasAntes: z.coerce.number().int().min(1, 'Mínimo 1 día'),
  canal: z.enum(['EMAIL', 'WHATSAPP', 'AMBOS']),
  activo: z.boolean().default(true),
})

export const notifConfigSchema = z.object({
  configs: z.array(notifConfigItemSchema),
})

export type NotifConfigItem = z.infer<typeof notifConfigItemSchema>
export type NotifConfigForm = z.infer<typeof notifConfigSchema>
