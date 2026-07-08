import { z } from 'zod'

export const autoRegistroStep4Schema = z.object({
  ci: z.string().min(1, 'CI requerido').max(20, 'Máximo 20 caracteres'),
  nombre: z.string().min(2, 'Nombre requerido').max(150),
  correo: z.string().email('Correo no válido'),
  password: z.string().min(8, 'Mínimo 8 caracteres'),
  confirmarPassword: z.string().min(8, 'Mínimo 8 caracteres'),
}).refine(d => d.password === d.confirmarPassword, {
  message: 'Las contraseñas no coinciden',
  path: ['confirmarPassword'],
})

export type AutoRegistroStep4Form = z.infer<typeof autoRegistroStep4Schema>
