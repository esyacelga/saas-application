export interface LoginStaffRequest {
  correo: string
  password: string
  id_compania: number
}

export interface LoginPlatformRequest {
  correo: string
  password: string
}

export interface ResetPasswordRequestBody {
  correo: string
  id_compania?: number
  tipo: 'staff' | 'cliente'
}

export interface ResetPasswordConfirmBody {
  token: string
  nueva_password: string
}

export interface ChangePasswordBody {
  password_actual: string
  nueva_password: string
}

export interface CrearUsuarioRequest {
  id_persona: number
  correo: string
  id_rol: number
  id_sucursal: number
  password_temporal: string
}

export interface EditarUsuarioRequest {
  correo: string
  id_rol: number
}

export interface CrearRolRequest {
  nombre: string
  descripcion?: string
}

export interface AsignarPermisosRequest {
  id_permisos: number[]
}

export interface CrearPersonaRequest {
  ci: string
  nombre: string
  telefono?: string
  correo?: string
  sexo?: 'M' | 'F'
  fecha_nacimiento?: string
  foto_url?: string
}

export interface ActualizarPersonaRequest {
  ci?: string
  nombre?: string
  telefono?: string
  correo?: string
  foto_url?: string
  sexo?: 'M' | 'F' | 'O'
  fecha_nacimiento?: string
}

export interface ListarPersonasParams {
  nombre?: string
  ci?: string
  correo?: string
  sexo?: 'M' | 'F' | 'O'
  page?: number
  size?: number
}

export interface PersonaPageResponse {
  content: Persona[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface CrearAppUsuarioRequest {
  id_persona: number
  login: string
  password: string
}

export interface CrearOperadorPlataformaRequest {
  id_persona: number
  correo: string
  password: string
  rol: 'super_admin' | 'soporte' | 'viewer'
}

export interface LoginStaffResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  requiere_cambio_pwd: boolean
  usuario: {
    id: number
    nombre: string
    correo: string
    id_rol: number
    nombre_rol: string
  }
}

export interface LoginPlatformResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  usuario: {
    id: number
    nombre: string
    rol_plataforma: 'super_admin' | 'soporte' | 'viewer'
  }
}

export interface RefreshResponse {
  access_token: string
  expires_in: number
}

export interface UsuarioStaff {
  id: number
  id_persona: number
  nombre: string
  correo: string
  foto_url: string | null
  id_rol: number
  nombre_rol: string
  activo: boolean
  ultimo_acceso: string | null
}

export interface PermisosUsuario {
  usuario: { id: number; nombre: string }
  rol: { id: number; nombre: string }
  permisos: string[]
}

export interface Rol {
  id: number
  nombre: string
  descripcion: string | null
}

export interface Permiso {
  id: number
  nombre: string
  modulo: string
  descripcion: string | null
}

export interface RolConPermisos {
  rol: Rol
  permisos: Permiso[]
}

export interface Persona {
  id: number
  ci: string
  nombre: string
  telefono: string | null
  correo: string | null
  foto_url: string | null
  fecha_nacimiento: string | null
  sexo?: 'M' | 'F' | 'O'
}

export interface UsuarioStaffPorPersona {
  id: number
  idPersona: number
  nombre: string
  correo: string
  fotoUrl: string | null
  idRol: number
  nombreRol: string
  activo: boolean
  ultimoAcceso: string | null
}

export interface AppUsuarioPorPersona {
  id: number
  login: string
  activo: boolean
  ultimoAcceso: string | null
}

export interface OperadorPlataformaPorPersona {
  id: number
  nombre: string
  correo: string
  rolPlataforma: 'super_admin' | 'soporte' | 'viewer'
  activo: boolean
  ultimoAcceso: string | null
  fotoUrl: string | null
}

export interface EditarOperadorPlataformaRequest {
  rol: 'super_admin' | 'soporte' | 'viewer'
}

export interface OperadorPlataforma {
  id: number
  nombre: string
  correo: string
  rol_plataforma: 'super_admin' | 'soporte' | 'viewer'
  activo: boolean
  ultimo_acceso: string | null
  foto_url: string | null
}

export interface BitacoraEntry {
  id: number
  id_usuario: number
  nombre_usuario: string
  modulo: string
  accion: string
  entidad_id: number | null
  ip: string | null
  fecha: string
}

export interface BitacoraParams {
  modulo?: string
  desde?: string
  hasta?: string
  idUsuario?: number
  pagina?: number
}

export interface PaginatedResponse<T> {
  total: number
  pagina: number
  datos: T[]
}

export interface LoginAppRequest {
  login: string
  password: string
  id_compania: number
}

export interface LoginAppResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  usuario: {
    id: number
    nombre: string
    login: string
  }
}

export interface CrearRolPlataformaRequest {
  nombre: string
  descripcion?: string
  id_compania: number
  id_sucursal: number
}

export interface ActualizarRolPlataformaRequest {
  nombre: string
  descripcion?: string
}

// Platform-level role management
export interface RolPlataforma {
  id: number
  nombre: string
  descripcion: string | null
  id_compania: number
  nombre_compania: string
  total_usuarios: number
}

export interface CompaniaBasica {
  id: number
  nombre: string
}

export interface SucursalBasica {
  id: number
  nombre: string
}

export interface RolConPermisosPlataforma {
  rol: RolPlataforma
  permisos: Permiso[]
}

// Permiso con sucursal (resultado del JOIN para la tabla de permisos por rol)
export interface PermisoRol {
  id: number
  nombre_sucursal: string
  nombre: string
  descripcion: string | null
  modulo: string
}

// Permiso completo con contexto de compañía/sucursal (para listado en plataforma)
export interface PermisoPlataforma {
  id: number
  nombre: string
  modulo: string
  descripcion: string | null
  id_compania: number
  id_sucursal: number
  nombre_sucursal: string
}

export interface CrearPermisoRequest {
  nombre: string
  descripcion?: string
  modulo: string
  id_compania: number
  id_sucursal: number
}

export interface ActualizarPermisoRequest {
  nombre?: string
  descripcion?: string
  modulo?: string
}

export interface AsignarPermisoRolRequest {
  id_permiso: number
}

export interface AppUsuario {
  id: number
  login: string
  activo: boolean
  ultimo_acceso: string | null
}

export interface ActualizarAppUsuarioRequest {
  login?: string
  password?: string
}

export interface AutoRegistroRequest {
  nombre: string
  // El auto-registro público ya no envía RUC/teléfono/WhatsApp: se piden cuando hay
  // una razón concreta (facturación / activar WhatsApp). Se dejan opcionales por si el
  // backend los sigue aceptando desde otros orígenes.
  ruc?: string
  correo?: string
  telefono?: string
  whatsapp?: string
  nombreSucursal: string
  direccionSucursal?: string
  idPlan: number
  usuarioPrincipal: {
    ci: string
    nombre: string
    correo: string
    telefono?: string
    password: string
  }
}

export interface AutoRegistroResponse {
  idCompania: number
  idCompaniaPlan: number
  idSucursal: number
  qrToken: string
  usuarioPrincipal: { id: number; nombre: string; correo: string }
  usuariosCreados: number
}
