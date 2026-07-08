import { z } from 'zod'
import type { TFunction } from 'i18next'

export const createResetRequestSchema = (t: TFunction) =>
  z.object({
    correo: z
      .string()
      .min(1, t('validation.emailRequired'))
      .email(t('validation.emailInvalid')),
    id_compania: z.coerce
      .number()
      .int()
      .positive()
      .optional()
      .or(z.literal('')),
  })

export type ResetRequestForm = z.infer<ReturnType<typeof createResetRequestSchema>>
