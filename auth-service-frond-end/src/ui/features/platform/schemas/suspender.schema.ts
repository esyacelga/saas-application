import { z } from 'zod'

export const suspenderSchema = z.object({
  motivo: z.string().min(10, 'El motivo debe tener al menos 10 caracteres'),
})

export type SuspenderForm = z.infer<typeof suspenderSchema>
