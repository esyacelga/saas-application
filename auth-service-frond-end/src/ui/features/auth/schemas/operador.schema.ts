import { z } from 'zod'
import type { TFunction } from 'i18next'

export const createOperadorSchema = (t: TFunction) =>
  z.object({
    id_persona: z.number({ error: t('validation.personaRequired') }).int().positive(t('validation.personaRequired')),
    correo: z
      .string()
      .min(1, t('validation.emailRequired'))
      .email(t('validation.emailInvalid2')),
    password: z
      .string()
      .min(8, t('validation.operatorPasswordMin8')),
    rol: z.enum(['super_admin', 'soporte', 'viewer'], {
      error: t('validation.roleRequired'),
    }),
  })

export type CrearOperadorFormData = z.infer<ReturnType<typeof createOperadorSchema>>
