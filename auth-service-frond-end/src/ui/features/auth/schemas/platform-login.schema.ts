import { z } from 'zod'
import type { TFunction } from 'i18next'

export const createPlatformLoginSchema = (t: TFunction) =>
  z.object({
    correo: z
      .string()
      .min(1, t('validation.emailRequired'))
      .email(t('validation.emailInvalid')),
    password: z
      .string()
      .min(1, t('validation.passwordRequired')),
  })

export type PlatformLoginForm = z.infer<ReturnType<typeof createPlatformLoginSchema>>
