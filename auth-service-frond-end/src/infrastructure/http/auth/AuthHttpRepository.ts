import api from '@/infrastructure/http/axios.instance'
import platformPublicApi from '@/infrastructure/http/platform/axios-platform-public.instance'
import {
  getStoredRefreshToken,
  storeRefreshToken,
  clearStoredRefreshToken,
} from '@/lib/refresh-token-storage'
import type { AuthRepositoryPort } from '@/domain/auth/ports/AuthRepository.port'
import type {
  LoginStaffRequest, LoginStaffResponse,
  LoginPlatformRequest, LoginPlatformResponse,
  LoginAppRequest, LoginAppResponse,
  RefreshResponse,
  ResetPasswordRequestBody, ResetPasswordConfirmBody, ChangePasswordBody,
  CrearUsuarioRequest, EditarUsuarioRequest, UsuarioStaff, PermisosUsuario,
  CrearRolRequest, Rol, Permiso, RolConPermisos, AsignarPermisosRequest,
  CrearPersonaRequest, ActualizarPersonaRequest, Persona, ListarPersonasParams, PersonaPageResponse,
  UsuarioStaffPorPersona, AppUsuarioPorPersona, OperadorPlataformaPorPersona,
  CrearAppUsuarioRequest, AppUsuario, ActualizarAppUsuarioRequest,
  CrearOperadorPlataformaRequest, EditarOperadorPlataformaRequest, OperadorPlataforma,
  BitacoraEntry, BitacoraParams, PaginatedResponse,
  RolPlataforma, CompaniaBasica, SucursalBasica, RolConPermisosPlataforma,
  CrearRolPlataformaRequest, ActualizarRolPlataformaRequest,
  PermisoRol, PermisoPlataforma, CrearPermisoRequest, ActualizarPermisoRequest, AsignarPermisoRolRequest,
  AutoRegistroRequest, AutoRegistroResponse,
} from './auth.dto'

export class AuthHttpRepository implements AuthRepositoryPort {
  getCompaniesByCorreo(correo: string): Promise<CompaniaBasica[]> {
    return api.get<CompaniaBasica[]>('/auth/companias-por-correo', { params: { correo } }).then(r => r.data)
  }

  loginStaff(body: LoginStaffRequest): Promise<LoginStaffResponse> {
    return api.post<LoginStaffResponse>('/auth/login', body).then(r => {
      if (r.data.refresh_token) storeRefreshToken(r.data.refresh_token)
      return r.data
    })
  }

  loginPlatform(body: LoginPlatformRequest): Promise<LoginPlatformResponse> {
    return api.post<LoginPlatformResponse>('/auth/platform/login', body).then(r => {
      if (r.data.refresh_token) storeRefreshToken(r.data.refresh_token)
      return r.data
    })
  }

  loginApp(body: LoginAppRequest): Promise<LoginAppResponse> {
    return api.post<LoginAppResponse>('/auth/app/login', body).then(r => {
      if (r.data.refresh_token) storeRefreshToken(r.data.refresh_token)
      return r.data
    })
  }

  logout(): Promise<void> {
    return api.post('/auth/logout').then(() => {
      clearStoredRefreshToken()
    })
  }

  refreshToken(): Promise<RefreshResponse> {
    const rt = getStoredRefreshToken()
    return api
      .post<RefreshResponse>('/auth/refresh', rt ? { refresh_token: rt } : undefined)
      .then(r => r.data)
  }

  requestPasswordReset(body: ResetPasswordRequestBody): Promise<void> {
    return api.post('/auth/password/reset-request', body).then(() => undefined)
  }

  confirmPasswordReset(body: ResetPasswordConfirmBody): Promise<void> {
    return api.post('/auth/password/reset', body).then(() => undefined)
  }

  changePassword(body: ChangePasswordBody): Promise<void> {
    return api.post('/auth/password/change', body).then(() => undefined)
  }

  getUsuarios(): Promise<UsuarioStaff[]> {
    return api.get<UsuarioStaff[]>('/usuarios').then(r => r.data)
  }

  crearUsuario(body: CrearUsuarioRequest): Promise<UsuarioStaff> {
    return api.post<UsuarioStaff>('/usuarios', body).then(r => r.data)
  }

  editarUsuario(id: number, body: EditarUsuarioRequest): Promise<UsuarioStaff> {
    return api.patch<UsuarioStaff>(`/usuarios/${id}`, body).then(r => r.data)
  }

  getPermisosUsuario(id: number): Promise<PermisosUsuario> {
    return api.get<PermisosUsuario>(`/usuarios/${id}/permisos`).then(r => r.data)
  }

  desactivarUsuario(id: number): Promise<void> {
    return api.put(`/usuarios/${id}/desactivar`).then(() => undefined)
  }

  activarUsuario(id: number): Promise<void> {
    return api.put(`/usuarios/${id}/activar`).then(() => undefined)
  }

  getRoles(): Promise<Rol[]> {
    return api.get<Rol[]>('/roles').then(r => r.data)
  }

  getRolById(id: number): Promise<Rol> {
    return api.get<Rol>(`/roles/${id}`).then(r => r.data)
  }

  crearRol(body: CrearRolRequest): Promise<Rol> {
    return api.post<Rol>('/roles', body).then(r => r.data)
  }

  getRolPermisos(id: number): Promise<RolConPermisos> {
    return api.get<RolConPermisos>(`/roles/${id}/permisos`).then(r => r.data)
  }

  actualizarRolPermisos(id: number, body: AsignarPermisosRequest): Promise<void> {
    return api.put(`/roles/${id}/permisos`, body).then(() => undefined)
  }

  eliminarRol(id: number): Promise<void> {
    return api.delete(`/roles/${id}`).then(() => undefined)
  }

  getPermisos(): Promise<Permiso[]> {
    return api.get<Permiso[]>('/permisos').then(r => r.data)
  }

  getPermisosByRol(id: number): Promise<Permiso[]> {
    return api.get<Permiso[]>(`/permisos/by-rol/${id}`).then(r => r.data)
  }

  buscarPersonaPorCI(ci: string): Promise<Persona> {
    return api.get<Persona>(`/personas/ci/${ci}`).then(r => r.data)
  }

  buscarPersonaPorCorreo(correo: string): Promise<Persona> {
    return api.get<Persona>(`/personas/correo/${encodeURIComponent(correo)}`).then(r => r.data)
  }

  getPersonaById(id: number): Promise<Persona> {
    return api.get<Persona>(`/personas/${id}`).then(r => r.data)
  }

  listarPersonas(params: ListarPersonasParams = {}): Promise<PersonaPageResponse> {
    return api.get<PersonaPageResponse>('/personas', { params }).then(r => r.data)
  }

  crearPersona(body: CrearPersonaRequest): Promise<Persona> {
    return api.post<Persona>('/personas', body).then(r => r.data)
  }

  actualizarPersona(id: number, body: ActualizarPersonaRequest): Promise<Persona> {
    return api.put<Persona>(`/personas/${id}`, body).then(r => r.data)
  }

  subirFotoPersona(id: number, file: File): Promise<Persona> {
    const form = new FormData()
    form.append('file', file)
    return api.post<Persona>(`/personas/${id}/foto`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data)
  }

  getUsuariosStaffPorPersona(idPersona: number): Promise<UsuarioStaffPorPersona[]> {
    return api.get<UsuarioStaffPorPersona[]>(`/personas/${idPersona}/usuarios-staff`).then(r => r.data)
  }

  getAppUsuariosPorPersona(idPersona: number): Promise<AppUsuarioPorPersona[]> {
    return api.get<AppUsuarioPorPersona[]>(`/personas/${idPersona}/usuarios-app`).then(r => r.data)
  }

  getOperadoresPlataformaPorPersona(idPersona: number): Promise<OperadorPlataformaPorPersona[]> {
    return api.get<OperadorPlataformaPorPersona[]>(`/personas/${idPersona}/usuarios-plataforma`).then(r => r.data)
  }

  resetStaffPassword(idCompania: number, idUsuario: number, password: string): Promise<void> {
    return api.put(`/platform/companias/${idCompania}/usuarios/${idUsuario}/password`, { password }).then(() => undefined)
  }

  crearUsuarioApp(body: CrearAppUsuarioRequest): Promise<void> {
    return api.post('/app-usuarios', body).then(() => undefined)
  }

  activarUsuarioApp(id: number): Promise<void> {
    return api.put(`/app-usuarios/${id}/activar`).then(() => undefined)
  }

  desactivarUsuarioApp(id: number): Promise<void> {
    return api.put(`/app-usuarios/${id}/desactivar`).then(() => undefined)
  }

  getAppUsuarioPorCi(ci: string): Promise<AppUsuario> {
    return api.get<AppUsuario>(`/app-usuarios/por-ci/${encodeURIComponent(ci)}`).then(r => r.data)
  }

  actualizarAppUsuario(id: number, body: ActualizarAppUsuarioRequest): Promise<void> {
    return api.patch(`/app-usuarios/${id}`, body).then(() => undefined)
  }

  getBitacora(params: BitacoraParams = {}): Promise<PaginatedResponse<BitacoraEntry>> {
    return api.get<PaginatedResponse<BitacoraEntry>>('/bitacora', { params }).then(r => r.data)
  }

  getOperadoresPlataforma(): Promise<OperadorPlataforma[]> {
    return api.get<OperadorPlataforma[]>('/platform/usuarios').then(r => r.data)
  }

  crearOperadorPlataforma(body: CrearOperadorPlataformaRequest): Promise<OperadorPlataforma> {
    return api.post<OperadorPlataforma>('/platform/usuarios', body).then(r => r.data)
  }

  editarOperadorPlataforma(id: number, body: EditarOperadorPlataformaRequest): Promise<OperadorPlataforma> {
    return api.patch<OperadorPlataforma>(`/platform/usuarios/${id}`, body).then(r => r.data)
  }

  subirFotoOperador(id: number, file: File): Promise<OperadorPlataforma> {
    const form = new FormData()
    form.append('file', file)
    return api.post<OperadorPlataforma>(`/platform/usuarios/${id}/foto`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data)
  }

  desactivarOperadorPlataforma(id: number): Promise<void> {
    return api.put(`/platform/usuarios/${id}/desactivar`).then(() => undefined)
  }

  getRolesPlataforma(): Promise<RolPlataforma[]> {
    return api.get<RolPlataforma[]>('/platform/roles').then(r => r.data)
  }

  getRolPermisosPlataforma(id: number): Promise<RolConPermisosPlataforma> {
    return api.get<RolConPermisosPlataforma>(`/platform/roles/${id}/permisos`).then(r => r.data)
  }

  getCompaniasBasicas(): Promise<CompaniaBasica[]> {
    return api.get<CompaniaBasica[]>('/platform/companias').then(r => r.data)
  }

  getSucursalesByCompania(idCompania: number): Promise<SucursalBasica[]> {
    return api.get<SucursalBasica[]>(`/platform/companias/${idCompania}/sucursales`).then(r => r.data)
  }

  getUsuariosStaffByCompania(idCompania: number): Promise<UsuarioStaff[]> {
    return api.get<UsuarioStaff[]>(`/platform/companias/${idCompania}/usuarios`).then(r => r.data)
  }

  crearRolPlataforma(body: CrearRolPlataformaRequest): Promise<RolPlataforma> {
    return api.post<RolPlataforma>('/platform/roles', body).then(r => r.data)
  }

  actualizarRolPlataforma(id: number, body: ActualizarRolPlataformaRequest): Promise<RolPlataforma> {
    return api.put<RolPlataforma>(`/platform/roles/${id}`, body).then(r => r.data)
  }

  eliminarRolPlataforma(id: number): Promise<void> {
    return api.delete(`/platform/roles/${id}`).then(() => undefined)
  }

  actualizarRolPermisosPlataforma(id: number, body: AsignarPermisosRequest): Promise<void> {
    return api.put(`/platform/roles/${id}/permisos`, body).then(() => undefined)
  }

  getRolPermisosConSucursal(idRol: number): Promise<PermisoRol[]> {
    return api.get<PermisoRol[]>(`/platform/roles/${idRol}/permisos/detalle`).then(r => r.data)
  }

  asignarPermisoARolPlataforma(idRol: number, body: AsignarPermisoRolRequest): Promise<void> {
    return api.post(`/platform/roles/${idRol}/permisos`, body).then(() => undefined)
  }

  eliminarPermisoDeRolPlataforma(idRol: number, idPermiso: number): Promise<void> {
    return api.delete(`/platform/roles/${idRol}/permisos/${idPermiso}`).then(() => undefined)
  }

  getPermisosPlataforma(): Promise<PermisoPlataforma[]> {
    return api.get<PermisoPlataforma[]>('/platform/permisos').then(r => r.data)
  }

  crearPermisoPlataforma(body: CrearPermisoRequest): Promise<PermisoPlataforma> {
    return api.post<PermisoPlataforma>('/platform/permisos', body).then(r => r.data)
  }

  actualizarPermisoPlataforma(id: number, body: ActualizarPermisoRequest): Promise<PermisoPlataforma> {
    return api.put<PermisoPlataforma>(`/platform/permisos/${id}`, body).then(r => r.data)
  }

  eliminarPermisoPlataforma(id: number): Promise<void> {
    return api.delete(`/platform/permisos/${id}`).then(() => undefined)
  }

  autoRegistro(body: AutoRegistroRequest): Promise<AutoRegistroResponse> {
    return platformPublicApi.post<AutoRegistroResponse>('/companias/auto-registro', body).then(r => r.data)
  }
}

export const authRepository = new AuthHttpRepository()
