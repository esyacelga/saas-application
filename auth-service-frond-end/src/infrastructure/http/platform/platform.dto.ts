// Planes
export interface CrearPlanDto { nombre: string; descripcion: string; precioMensual: number }
export interface ActualizarPlanDto { nombre?: string; descripcion?: string; precioMensual?: number }
export interface AsignarCaracteristicasDto { caracteristicaIds: number[] }

// Características
export interface CrearCaracteristicaDto { codigo: string; nombre: string; modulo: string }

// Compañías
export interface RegistrarGymDto {
  nombre: string
  ruc: string
  logoUrl?: string
  telefono?: string
  whatsapp?: string
  correo?: string
  idPlan: number
  nombreSucursal?: string
  direccionSucursal?: string
}
export interface RegistrarGymResponse {
  idCompania: number
  idCompaniaPlan: number
  idSucursal: number
  qrToken: string
}
export interface ActualizarCompaniaDto {
  nombre?: string
  logoUrl?: string
  telefono?: string
  whatsapp?: string
  correo?: string
}
export interface SuspenderDto { motivo: string }

// Sucursales
export interface CrearSucursalDto { nombre: string; direccion?: string; esPrincipal?: boolean }
export interface ActualizarSucursalDto { nombre?: string; direccion?: string }
export interface RenovarQrDto { expiresInHours?: number }
export interface QrRenovarResponse { qrToken: string; qrTokenExpira: string | null }

// Suscripciones
export interface RenovarSuscripcionDto { idPlan?: number; meses?: number }
export interface UpgradeDto { idPlanNuevo: number }
export interface UpgradeResponse {
  idCompaniaPlanNuevo: number
  creditoAplicado: number
  montoAPagar: number
  planAnteriorCancelado: boolean
}
export interface DowngradeDto { idPlanNuevo: number }
export interface DowngradeResponse {
  idCompaniaPlanNuevo: number
  estado: string
  efectivoDe: string
  creditoGenerado: number
}

// Usuarios del gym (wizard)
export interface CrearUsuarioGymDto {
  nombre: string
  correo: string
  password: string
  esAdmin: boolean
}

export interface RegistrarGymWizardDto {
  nombre: string
  ruc: string
  logoUrl?: string
  correo?: string
  telefono?: string
  whatsapp?: string
  idPlan: number
  nombreSucursal: string
  direccionSucursal?: string
  aceptaWhatsapp?: boolean
  usuarioPrincipal: {
    id_persona?: number
    ci: string
    nombre: string
    correo: string
    telefono?: string
    sexo?: 'M' | 'F'
    foto_url?: string
    password: string
  }
  usuariosAdicionales: { id_persona?: number; ci: string; nombre: string; correo: string; telefono?: string; password: string }[]
}

export interface RegistrarGymWizardResponse {
  idCompania: number
  idCompaniaPlan: number
  idSucursal: number
  qrToken: string
  usuariosPrincipal: { id: number; nombre: string; correo: string }
  usuariosCreados: number
}

// Actividad plataforma
export interface ActividadPlataformaItem {
  id: number
  tipoEvento: string
  modulo: string
  entidadId: number | null
  entidadNombre: string | null
  detalle: string | null
  usuario: string
  ip: string | null
  fecha: string
}

export interface PaginadoActividadResponse {
  total: number
  pagina: number
  datos: ActividadPlataformaItem[]
}

export interface ActividadParams {
  modulo?: string
  tipoEvento?: string
  desde?: string
  hasta?: string
  pagina?: number
}

// Pagos
export interface RegistrarPagoDto {
  idCompaniaPlan: number
  monto: number
  metodoPago: string
  tipoPago: string
  referencia?: string
  periodoDesde?: string
  periodoHasta?: string
}
