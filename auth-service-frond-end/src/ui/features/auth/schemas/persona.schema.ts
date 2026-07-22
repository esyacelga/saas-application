import { z } from 'zod'
import type { TFunction } from 'i18next'

export const createPersonaSchema = (t: TFunction) =>
  z.object({
    ci: z
      .string()
      .min(6, t('validation.ciMin6'))
      .max(20, t('validation.ciTooLong')),
    nombre: z
      .string()
      .min(2, t('validation.nameMin2'))
      .max(150, t('validation.nameTooLong')),
    telefono: z.string().optional().refine((v) => !v || /^\+[1-9]\d{6,14}$/.test(v), 'Número de teléfono inválido'),
    correo: z.string().email(t('validation.emailInvalid2')).optional().or(z.literal('')),
    fecha_nacimiento: z.string().optional().or(z.literal('')),
    sexo: z.enum(['M', 'F']).optional(),
  })

export type CrearPersonaFormData = z.infer<ReturnType<typeof createPersonaSchema>>

export const createAppUsuarioSchema = (t: TFunction) =>
  z.object({
    login: z
      .string()
      .min(3, t('validation.usernameMin3'))
      .max(50, t('validation.usernameTooLong'))
      .regex(/^[a-zA-Z0-9._-]+$/, t('validation.usernamePattern')),
    password: z
      .string()
      .min(6, t('validation.appPasswordMin6')),
  })

export type CrearAppUsuarioFormData = z.infer<ReturnType<typeof createAppUsuarioSchema>>
