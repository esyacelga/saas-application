import { z } from 'zod'
import type { TFunction } from 'i18next'

export const createLoginStaffSchema = (t: TFunction) =>
  z.object({
    correo: z
      .string()
      .min(1, t('validation.emailRequired'))
      .email(t('validation.emailInvalid')),
    password: z
      .string()
      .min(1, t('validation.passwordRequired')),
    id_compania: z.coerce
      .number({ error: t('validation.gymIdRequired') })
      .int()
      .positive(t('validation.gymIdPositive')),
  })

export type LoginStaffForm = z.infer<ReturnType<typeof createLoginStaffSchema>>
