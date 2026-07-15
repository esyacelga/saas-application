import { z } from 'zod'
import { validarCedula } from '@/lib/sri/validarCedula'

// Paso 1 del auto-registro PÚBLICO. Deliberadamente sin RUC, teléfono ni WhatsApp:
// esos datos se piden cuando hay una razón concreta (facturación / activar WhatsApp),
// no al probar la app (disclosure progresivo). El registro por operador de plataforma
// usa su propio schema (registrar-gym-wizard.schema.ts), que sí conserva el RUC.
export const autoRegistroStep1Schema = z.object({
  nombre: z.string().min(2, 'Nombre del gimnasio requerido').max(150),
  correo: z.string().email('Correo no válido').optional().or(z.literal('')),
})

// Paso 2 del auto-registro público: el local físico. nombreSucursal llega pre-llenado
// con el nombre del gym desde el orquestador, así que en la práctica ya viene válido.
export const autoRegistroStep2Schema = z.object({
  nombreSucursal: z.string().min(2, 'Nombre del local requerido').max(150),
  direccionSucursal: z.string().optional().or(z.literal('')),
})

export const autoRegistroStep4Schema = z.object({
  ci: z.string()
    .min(1, 'CI requerido')
    .refine(validarCedula, 'Cédula ecuatoriana no válida'),
  nombre: z.string().min(2, 'Nombre requerido').max(150),
  correo: z.string().email('Correo no válido'),
  password: z.string().min(8, 'Mínimo 8 caracteres'),
  confirmarPassword: z.string().min(8, 'Mínimo 8 caracteres'),
}).refine(d => d.password === d.confirmarPassword, {
  message: 'Las contraseñas no coinciden',
  path: ['confirmarPassword'],
})

export type AutoRegistroStep1Form = z.infer<typeof autoRegistroStep1Schema>
export type AutoRegistroStep2Form = z.infer<typeof autoRegistroStep2Schema>
export type AutoRegistroStep4Form = z.infer<typeof autoRegistroStep4Schema>
