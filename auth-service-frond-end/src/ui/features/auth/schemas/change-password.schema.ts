import { z } from 'zod'
import type { TFunction } from 'i18next'

export const createChangePasswordSchema = (t: TFunction) =>
  z
    .object({
      password_actual: z
        .string()
        .min(1, t('validation.currentPasswordRequired')),
      nueva_password: z
        .string()
        .min(8, t('validation.passwordMin8'))
        .regex(/[A-Z]/, t('validation.passwordUppercase'))
        .regex(/[0-9]/, t('validation.passwordNumber')),
      confirmar_password: z
        .string()
        .min(1, t('validation.confirmPasswordRequired')),
    })
    .refine(
      (data) => data.nueva_password === data.confirmar_password,
      { message: t('validation.passwordMismatch'), path: ['confirmar_password'] }
    )
    .refine(
      (data) => data.password_actual !== data.nueva_password,
      { message: t('validation.passwordSameAsCurrent'), path: ['nueva_password'] }
    )

export type ChangePasswordForm = z.infer<ReturnType<typeof createChangePasswordSchema>>
