import { z } from 'zod'
import type { TFunction } from 'i18next'

export const createUsuarioSchema = (t: TFunction) =>
  z.object({
    id_persona: z.number({ error: t('validation.personaRequired') }).int().positive(t('validation.personaRequired')),
    correo: z
      .string()
      .min(1, t('validation.emailRequired'))
      .email(t('validation.emailInvalid')),
    id_rol: z.coerce
      .number({ error: t('validation.roleRequired') })
      .int()
      .positive(t('validation.roleRequired')),
    id_sucursal: z.coerce
      .number({ error: t('validation.branchIdRequired') })
      .int()
      .positive(t('validation.branchIdPositive')),
    password_temporal: z
      .string()
      .min(6, t('validation.tempPasswordMin6')),
  })

export type CrearUsuarioFormData = z.infer<ReturnType<typeof createUsuarioSchema>>

export const createEditarUsuarioSchema = (t: TFunction) =>
  z.object({
    correo: z
      .string()
      .min(1, t('validation.emailRequired'))
      .email(t('validation.emailInvalid')),
    id_rol: z.coerce
      .number({ error: t('validation.roleRequired') })
      .int()
      .positive(t('validation.roleRequired')),
  })

export type EditarUsuarioFormData = z.infer<ReturnType<typeof createEditarUsuarioSchema>>
