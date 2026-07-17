import type {
  LoginManualRequest, LoginGoogleRequest, LoginFacebookRequest,
  LoginAppResponse, OAuthLoginResponse, CompletarRegistroOauthRequest,
  RefreshResponse,
  ForgotPasswordRequest, ResetPasswordRequest, GymByQrResponse,
  RegistroAppRequest, PersonaResponse, ActualizarPersonaRequest,
  ConsentimientoWaPersonaResponse,
} from '@/application/usecase/auth.types'

export interface AuthRepository {
  loginManual(req: LoginManualRequest): Promise<LoginAppResponse>
  loginGoogle(req: LoginGoogleRequest): Promise<OAuthLoginResponse>
  loginFacebook(req: LoginFacebookRequest): Promise<OAuthLoginResponse>
  completarRegistroOauth(req: CompletarRegistroOauthRequest): Promise<LoginAppResponse>
  registrar(req: RegistroAppRequest): Promise<LoginAppResponse>
  refresh(refreshToken: string): Promise<RefreshResponse>
  logout(): Promise<void>
  forgotPassword(req: ForgotPasswordRequest): Promise<void>
  resetPassword(req: ResetPasswordRequest): Promise<void>
  getGymByQr(token: string): Promise<GymByQrResponse>
  getPersona(id: number): Promise<PersonaResponse>
  actualizarPersona(id: number, req: ActualizarPersonaRequest): Promise<PersonaResponse>
  subirFotoMiembro(id: number, file: File): Promise<PersonaResponse>
  patchConsentimientoWaPersona(id: number, acepta: boolean): Promise<ConsentimientoWaPersonaResponse>
}
