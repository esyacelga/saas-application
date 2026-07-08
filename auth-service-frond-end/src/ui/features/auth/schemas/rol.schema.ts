import { z } from 'zod'
import type { TFunction } from 'i18next'

export const createRolSchema = (t: TFunction) =>
  z.object({
    nombre: z
      .string()
      .min(2, t('validation.nameMin2'))
      .max(80, t('validation.nameTooLong')),
    descripcion: z
      .string()
      .max(250, t('validation.descriptionTooLong'))
      .optional()
      .or(z.literal('')),
  })

export type CrearRolFormData = z.infer<ReturnType<typeof createRolSchema>>
