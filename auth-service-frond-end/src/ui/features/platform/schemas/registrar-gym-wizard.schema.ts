import { z } from 'zod'

const passwordMin = 'Mínimo 8 caracteres'

export const wizardStep1Schema = z.object({
  nombre: z.string().min(2, 'Nombre del gimnasio requerido').max(150),
  ruc: z.string().min(10, 'Mínimo 10 caracteres').max(20, 'Máximo 20 caracteres'),
  correo: z.string().email('Correo no válido').optional().or(z.literal('')),
  telefono: z.string().optional().or(z.literal('')),
  whatsapp: z.string().optional().or(z.literal('')),
})

export const wizardStep2Schema = z.object({
  nombreSucursal: z.string().min(2, 'Nombre de la sede requerido').max(150),
  direccionSucursal: z.string().optional().or(z.literal('')),
})

export const wizardStep3Schema = z.object({
  idPlan: z.coerce.number({ error: 'Selecciona un plan' }).positive('Selecciona un plan'),
})

const usuarioBaseFields = z.object({
  nombre: z.string().min(2, 'Nombre requerido').max(100),
  correo: z.string().min(1, 'Correo requerido').email('Correo no válido'),
  password: z.string().min(8, passwordMin),
  confirmarPassword: z.string().min(8, passwordMin),
})

const refinePasswords = { message: 'Las contraseñas no coinciden', path: ['confirmarPassword'] }
const passwordsMatch = (d: { password: string; confirmarPassword: string }) =>
  d.password === d.confirmarPassword

const usuarioBaseSchema = usuarioBaseFields.refine(passwordsMatch, refinePasswords)

const passwordOnlyFields = z.object({
  password: z.string().min(8, passwordMin),
  confirmarPassword: z.string().min(8, passwordMin),
})

export const wizardStep4Schema = z.object({
  usuarioPrincipal: passwordOnlyFields.refine(passwordsMatch, refinePasswords),
})

export const usuarioAdicionalSchema = usuarioBaseFields.refine(passwordsMatch, refinePasswords)

export const wizardStep5Schema = z.object({
  usuariosAdicionales: z.array(usuarioBaseSchema),
})

export const wizardFullSchema = wizardStep1Schema
  .merge(wizardStep2Schema)
  .merge(wizardStep3Schema)
  .merge(wizardStep4Schema)
  .merge(wizardStep5Schema)

export type WizardStep1Form = z.infer<typeof wizardStep1Schema>
export type WizardStep2Form = z.infer<typeof wizardStep2Schema>
export type WizardStep3Form = z.infer<typeof wizardStep3Schema>
export type WizardStep4Form = z.infer<typeof wizardStep4Schema>
export type WizardStep5Form = z.infer<typeof wizardStep5Schema>
export type WizardFullForm = z.infer<typeof wizardFullSchema>

export type UsuarioWizard = {
  nombre: string
  correo: string
  password: string
  confirmarPassword: string
}
