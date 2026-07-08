import { z } from 'zod'

export const registrarGymSchema = z.object({
  nombre: z.string().min(2, 'Nombre requerido'),
  ruc: z.string().min(10, 'Mínimo 10 caracteres').max(20, 'Máximo 20 caracteres'),
  correo: z.string().email('Correo no válido').optional().or(z.literal('')),
  telefono: z.string().optional(),
  whatsapp: z.string().optional(),
  idPlan: z.coerce.number().positive('Selecciona un plan'),
  nombreSucursal: z.string().min(2, 'Nombre de sede requerido'),
  direccionSucursal: z.string().optional(),
})

export type RegistrarGymForm = z.infer<typeof registrarGymSchema>
