// ── Tipos de membresía ───────────────────────────────────────────────────────

export interface TipoMembresia {
  id: number
  nombre: string
  modo_control: 'calendario' | 'accesos'
  duracion_tipo: 'dias' | 'semanas' | 'meses' | 'años'
  duracion_valor: number
  dias_acceso: number | null
  precio: number
  activo: boolean
}

export interface CrearTipoMembresiaDto {
  nombre: string
  modo_control: 'calendario' | 'accesos'
  duracion_tipo: 'dias' | 'semanas' | 'meses' | 'años'
  duracion_valor: number
  dias_acceso?: number | null
  precio: number
}

export type ActualizarTipoMembresiaDto = Partial<CrearTipoMembresiaDto>

// ── Clientes ─────────────────────────────────────────────────────────────────

export interface MembresiaResumen {
  id: number
  tipo: string
  modo_control: 'calendario' | 'accesos'
  fecha_fin: string
  dias_restantes: number
  accesos_restantes: number | null
}

export interface ClienteListItem {
  id: number
  nombre: string
  ci: string
  telefono: string | null
  estado: EstadoCliente
  membresia_activa: MembresiaResumen | null
  foto_url: string | null
  sexo: 'M' | 'F' | 'O' | null
}

export type EstadoCliente = 'activo' | 'proximo_vencer' | 'vencido' | 'congelado' | 'riesgo_abandono'

export interface ClientesResponse {
  total: number
  pagina: number
  datos: ClienteListItem[]
}

export interface PersonaDetalle {
  ci: string
  nombre: string
  telefono: string | null
  correo: string | null
  foto_url: string | null
  sexo: 'M' | 'F' | 'O' | null
  fecha_nacimiento: string | null
}

export interface MembresiaActivaDetalle {
  id: number
  tipo: string
  modo_control: 'calendario' | 'accesos'
  fecha_inicio: string
  fecha_fin: string
  dias_restantes: number
  estado: EstadoMembresia
}

export interface ClienteDetalle {
  id: number
  id_persona: number
  persona: PersonaDetalle
  sexo: 'M' | 'F' | 'O' | null
  peso_kg: number | null
  altura_cm: number | null
  objetivos: string | null
  lesiones: string | null
  estado: EstadoCliente
  fecha_ingreso: string
  codigo_carnet: string | null
  membresia_activa: MembresiaActivaDetalle | null
}

export interface RegistrarClienteDto {
  ci: string
  nombre: string
  telefono?: string
  correo?: string
  fecha_nacimiento?: string
  peso_kg?: number
  altura_cm?: number
  objetivos?: string
  lesiones?: string
  id_compania?: number
  id_sucursal: number
  sexo?: string
}

export interface RegistrarClientePlataformaDto {
  id_compania: number
  id_sucursal: number
  ci: string
  nombre: string
  telefono?: string
  correo?: string
  sexo?: string
}

export interface RegistrarClienteResponse {
  id_cliente: number
  id_persona: number
  persona_existia: boolean
}

export interface ActualizarClienteDto {
  peso_kg?: number
  altura_cm?: number
  objetivos?: string
  lesiones?: string
  telefono?: string
}

export interface BuscarPorCiResponse {
  persona: { id: number; ci: string; nombre: string }
  es_cliente_en_este_gym: boolean
  id_cliente: number | null
}

export interface ClientePorPersona {
  id: number
  id_persona: number
  id_compania: number
  peso_kg: number | null
  altura_cm: number | null
  objetivos: string | null
  lesiones: string | null
  estado: EstadoCliente
  fecha_ingreso: string
  codigo_carnet: string | null
  sexo: 'M' | 'F' | 'O' | null
}

export interface ActualizarClientePlataformaDto {
  idCompania?: number
  estado?: EstadoCliente
}

// ── Membresías ────────────────────────────────────────────────────────────────

export type EstadoMembresia = 'activa' | 'vencida' | 'congelada' | 'anulada'

export interface MembresiaHistorial {
  id: number
  tipo: string
  modo_control: 'calendario' | 'accesos'
  fecha_inicio: string
  fecha_fin: string
  precio_pagado: number
  descuento_aplicado: number
  estado: EstadoMembresia
}

export interface MembresiaDetalle {
  id: number
  tipo: string
  modo_control: 'calendario' | 'accesos'
  fecha_inicio: string
  fecha_fin: string
  dias_acceso_total: number | null
  dias_acceso_usados: number | null
  dias_acceso_restantes: number | null
  asistencias_previas: number
  precio_pagado: number
  estado: EstadoMembresia
  congelamiento_activo: CongelamientoActivo | null
}

export interface CongelamientoActivo {
  id: number
  fecha_inicio: string
  motivo: string
}

export interface VenderMembresiaDto {
  id_tipo_membresia: number
  fecha_inicio: string
  id_metodo_pago?: number
  descuento_aplicado?: number
}

export interface AnularMembresiaDto {
  motivo: string
}

// ── Congelamientos ────────────────────────────────────────────────────────────

export type MotivoCongelamiento = 'viaje' | 'lesion' | 'enfermedad' | 'voluntario' | 'otro'

export interface CongelarMembresiaDto {
  fecha_inicio: string
  motivo: MotivoCongelamiento
  detalle?: string
  retroactivo?: boolean
  documento_respaldo?: string
  aprobado_por?: number
}

export interface CongelarResponse {
  id_congelamiento: number
  fecha_inicio: string
  fecha_fin: string | null
}

export interface ReactivarResponse {
  fecha_fin_anterior: string
  dias_compensados: number
  fecha_fin_nueva: string
}

export interface CongelamientoHistorial {
  id: number
  fecha_inicio: string
  fecha_fin: string | null
  dias_congelados: number | null
  motivo: MotivoCongelamiento
  retroactivo: boolean
}

// ── Ventas pendientes ─────────────────────────────────────────────────────────

export interface VentaPendienteRaw {
  id: number
  id_cliente: number
  nombre_cliente: string | null
  id_tipo_membresia: number
  tipo_nombre: string
  modo_control: 'calendario' | 'accesos'
  precio_pagado: string
  descuento_aplicado: string
  creacion_fecha: string
  origen: 'staff' | 'cliente'
}

export interface VentaPendiente {
  id: number
  idCliente: number
  nombreCliente: string | null
  idTipoMembresia: number
  tipoNombre: string
  modoControl: 'calendario' | 'accesos'
  precioPagado: string
  descuentoAplicado: string
  creacionFecha: string
  origen: 'staff' | 'cliente'
}

export interface ContadorPendientesRaw {
  total: number
  por_origen_cliente: number
  por_origen_staff: number
}

export interface ContadorPendientes {
  total: number
  porOrigenCliente: number
  porOrigenStaff: number
}

export interface CompletarVentaClienteDto {
  id_metodo_pago: number
  precio_pagado: number
  descuento_aplicado?: number
  fecha_inicio: string // YYYY-MM-DD
}

export interface MembresiaResponse {
  id: number
  id_cliente: number
  id_tipo_membresia: number
  fecha_inicio: string | null
  fecha_fin: string | null
  dias_acceso_total: number | null
  precio_pagado: string
  descuento_aplicado: string
  estado: string
  estado_pago: string
  eliminado: boolean
  motivo_eliminacion: string | null
}

export interface RechazarMembresiaDto {
  motivo_eliminacion: string
}
