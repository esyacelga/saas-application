import { z } from 'zod'

export const crearSucursalSchema = z.object({
  nombre: z.string().min(2, 'Nombre requerido'),
  direccion: z.string().optional(),
  esPrincipal: z.boolean().optional().default(false),
})

export type CrearSucursalForm = z.infer<typeof crearSucursalSchema>
