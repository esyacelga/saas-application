import { z } from 'zod'

// Paso 1 del auto-registro PÚBLICO. Deliberadamente sin RUC, teléfono ni WhatsApp:
// esos datos se piden cuando hay una razón concreta (facturación / activar WhatsApp),
// no al probar la app (disclosure progresivo). El registro por operador de plataforma
// usa su propio schema (registrar-gym-wizard.schema.ts), que sí conserva el RUC.
//
// El paso de "local/sucursal" se eliminó de la UI: el 90% de los gyms tienen un solo
// local que se llama igual que el gimnasio, así que el nombre de la sucursal se deriva
// en código del nombre del gym (ver AutoRegistroPage). Solo la dirección (opcional)
// sobrevive aquí; los gyms multi-local renombran/agregan sucursales desde el panel.
export const autoRegistroStep1Schema = z.object({
  nombre: z.string().min(2, 'Nombre del gimnasio requerido').max(150),
  correo: z.string().email('Correo no válido').optional().or(z.literal('')),
  direccion: z.string().optional().or(z.literal('')),
})

export const autoRegistroStep4Schema = z.object({
  // Se acepta CUALQUIER número de documento: no se bloquea el registro por una cédula
  // que no pase el algoritmo (puede ser pasaporte, doc. extranjero, o simplemente aún
  // no verificado). El backend calcula identidad.personas.ci_validada con el algoritmo
  // del dígito verificador ecuatoriano y la deja en true/false según corresponda.
  ci: z.string()
    .min(1, 'CI requerido')
    .max(20, 'Máximo 20 caracteres'),
  nombre: z.string().min(2, 'Nombre requerido').max(150),
  correo: z.string().email('Correo no válido'),
  password: z.string().min(8, 'Mínimo 8 caracteres'),
  confirmarPassword: z.string().min(8, 'Mínimo 8 caracteres'),
}).refine(d => d.password === d.confirmarPassword, {
  message: 'Las contraseñas no coinciden',
  path: ['confirmarPassword'],
})

export type AutoRegistroStep1Form = z.infer<typeof autoRegistroStep1Schema>
export type AutoRegistroStep4Form = z.infer<typeof autoRegistroStep4Schema>
