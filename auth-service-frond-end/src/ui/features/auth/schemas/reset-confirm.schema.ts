import { z } from 'zod'
import type { TFunction } from 'i18next'

export const createResetConfirmSchema = (t: TFunction) =>
  z
    .object({
      nueva_password: z
        .string()
        .min(8, t('validation.passwordMin8'))
        .regex(/[A-Z]/, t('validation.passwordUppercase'))
        .regex(/[0-9]/, t('validation.passwordNumber')),
      confirmar_password: z
        .string()
        .min(1, t('validation.confirmPasswordRequired2')),
    })
    .refine(
      (data) => data.nueva_password === data.confirmar_password,
      { message: t('validation.passwordMismatch'), path: ['confirmar_password'] }
    )

export type ResetConfirmForm = z.infer<ReturnType<typeof createResetConfirmSchema>>
