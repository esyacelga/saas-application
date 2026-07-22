import { z } from 'zod'

export const registrarGymSchema = z.object({
  nombre: z.string().min(2, 'Nombre requerido'),
  ruc: z.string().min(10, 'Mínimo 10 caracteres').max(20, 'Máximo 20 caracteres'),
  correo: z.string().email('Correo no válido').optional().or(z.literal('')),
  /* Campo telefono oculto — se conserva en el schema y DTO por compatibilidad */
  telefono: z.string().optional(),
  whatsapp: z.string()
    .optional()
    .refine((v) => !v || /^\+[1-9]\d{6,14}$/.test(v), 'Número de WhatsApp inválido'),
  idPlan: z.coerce.number().positive('Selecciona un plan'),
  nombreSucursal: z.string().min(2, 'Nombre de sede requerido'),
  direccionSucursal: z.string().optional(),
})

export type RegistrarGymForm = z.infer<typeof registrarGymSchema>
