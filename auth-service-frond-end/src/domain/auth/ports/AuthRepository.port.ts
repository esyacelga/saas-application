import type {
  LoginStaffRequest, LoginStaffResponse,
  LoginPlatformRequest, LoginPlatformResponse,
  LoginAppRequest, LoginAppResponse,
  RefreshResponse,
  ResetPasswordRequestBody, ResetPasswordConfirmBody, ChangePasswordBody,
  CrearUsuarioRequest, EditarUsuarioRequest, UsuarioStaff, PermisosUsuario,
  CrearRolRequest, Rol, Permiso, RolConPermisos, AsignarPermisosRequest,
  CrearPersonaRequest, ActualizarPersonaRequest, Persona,
  CrearAppUsuarioRequest, AppUsuario, ActualizarAppUsuarioRequest,
  CrearOperadorPlataformaRequest, EditarOperadorPlataformaRequest, OperadorPlataforma,
  BitacoraEntry, BitacoraParams, PaginatedResponse,
  RolPlataforma, CompaniaBasica, SucursalBasica, RolConPermisosPlataforma,
  CrearRolPlataformaRequest, ActualizarRolPlataformaRequest,
  PermisoRol, PermisoPlataforma, CrearPermisoRequest, ActualizarPermisoRequest, AsignarPermisoRolRequest,
  AutoRegistroRequest, AutoRegistroResponse,
} from '@/infrastructure/http/auth/auth.dto'

export interface AuthRepositoryPort {
  // Auth
  getCompaniesByCorreo(correo: string): Promise<CompaniaBasica[]>
  loginStaff(body: LoginStaffRequest): Promise<LoginStaffResponse>
  loginPlatform(body: LoginPlatformRequest): Promise<LoginPlatformResponse>
  loginApp(body: LoginAppRequest): Promise<LoginAppResponse>
  logout(): Promise<void>
  refreshToken(): Promise<RefreshResponse>
  requestPasswordReset(body: ResetPasswordRequestBody): Promise<void>
  confirmPasswordReset(body: ResetPasswordConfirmBody): Promise<void>
  changePassword(body: ChangePasswordBody): Promise<void>

  // Usuarios Staff
  getUsuarios(): Promise<UsuarioStaff[]>
  crearUsuario(body: CrearUsuarioRequest): Promise<UsuarioStaff>
  editarUsuario(id: number, body: EditarUsuarioRequest): Promise<UsuarioStaff>
  getPermisosUsuario(id: number): Promise<PermisosUsuario>
  desactivarUsuario(id: number): Promise<void>
  activarUsuario(id: number): Promise<void>

  // Roles
  getRoles(): Promise<Rol[]>
  getRolById(id: number): Promise<Rol>
  crearRol(body: CrearRolRequest): Promise<Rol>
  getRolPermisos(id: number): Promise<RolConPermisos>
  actualizarRolPermisos(id: number, body: AsignarPermisosRequest): Promise<void>
  eliminarRol(id: number): Promise<void>

  // Permisos
  getPermisos(): Promise<Permiso[]>
  getPermisosByRol(id: number): Promise<Permiso[]>

  // Personas
  buscarPersonaPorCI(ci: string): Promise<Persona>
  buscarPersonaPorCorreo(correo: string): Promise<Persona>
  crearPersona(body: CrearPersonaRequest): Promise<Persona>
  actualizarPersona(id: number, body: ActualizarPersonaRequest): Promise<Persona>

  // App Usuarios (clientes)
  crearUsuarioApp(body: CrearAppUsuarioRequest): Promise<void>
  activarUsuarioApp(id: number): Promise<void>
  desactivarUsuarioApp(id: number): Promise<void>
  getAppUsuarioPorCi(ci: string): Promise<AppUsuario>
  actualizarAppUsuario(id: number, body: ActualizarAppUsuarioRequest): Promise<void>

  // Bitácora
  getBitacora(params?: BitacoraParams): Promise<PaginatedResponse<BitacoraEntry>>

  // Plataforma — Operadores
  getOperadoresPlataforma(): Promise<OperadorPlataforma[]>
  crearOperadorPlataforma(body: CrearOperadorPlataformaRequest): Promise<OperadorPlataforma>
  editarOperadorPlataforma(id: number, body: EditarOperadorPlataformaRequest): Promise<OperadorPlataforma>
  desactivarOperadorPlataforma(id: number): Promise<void>

  // Plataforma — Roles
  getRolesPlataforma(): Promise<RolPlataforma[]>
  getRolPermisosPlataforma(id: number): Promise<RolConPermisosPlataforma>
  getCompaniasBasicas(): Promise<CompaniaBasica[]>
  getSucursalesByCompania(idCompania: number): Promise<SucursalBasica[]>
  getUsuariosStaffByCompania(idCompania: number): Promise<UsuarioStaff[]>
  resetStaffPassword(idCompania: number, idUsuario: number, password: string): Promise<void>
  crearRolPlataforma(body: CrearRolPlataformaRequest): Promise<RolPlataforma>
  actualizarRolPlataforma(id: number, body: ActualizarRolPlataformaRequest): Promise<RolPlataforma>
  eliminarRolPlataforma(id: number): Promise<void>
  actualizarRolPermisosPlataforma(id: number, body: AsignarPermisosRequest): Promise<void>

  // Plataforma — Rol Permisos (granular CRUD)
  getRolPermisosConSucursal(idRol: number): Promise<PermisoRol[]>
  asignarPermisoARolPlataforma(idRol: number, body: AsignarPermisoRolRequest): Promise<void>
  eliminarPermisoDeRolPlataforma(idRol: number, idPermiso: number): Promise<void>

  // Plataforma — Gestión de Permisos
  getPermisosPlataforma(): Promise<PermisoPlataforma[]>
  crearPermisoPlataforma(body: CrearPermisoRequest): Promise<PermisoPlataforma>
  actualizarPermisoPlataforma(id: number, body: ActualizarPermisoRequest): Promise<PermisoPlataforma>
  eliminarPermisoPlataforma(id: number): Promise<void>

  // Auto-registro público
  autoRegistro(body: AutoRegistroRequest): Promise<AutoRegistroResponse>

  // Verificación pública de correo (onBlur en el registro). true = ya está en uso.
  correoEnUso(correo: string): Promise<boolean>
}
