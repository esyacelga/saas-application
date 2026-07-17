export interface LoginManualRequest {
  login: string
  password: string
  id_compania: number
}

export interface LoginGoogleRequest {
  id_token: string
  id_compania: number
}

export interface LoginFacebookRequest {
  access_token: string
  id_compania: number
}

export interface LoginAppResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  persona: { id: number; nombre: string; foto_url: string | null; sexo?: string | null }
  compania: { id: number; nombre: string; logo_url: string | null }
}

export interface OAuthLoginResponse {
  status: 'logged_in' | 'registro_pendiente'
  access_token: string | null
  refresh_token: string | null
  expires_in: number | null
  persona: { id: number; nombre: string; foto_url: string | null; sexo?: string | null } | null
  compania: { id: number; nombre: string; logo_url: string | null } | null
  email: string | null
  nombre: string | null
}

export interface CompletarRegistroOauthRequest {
  provider: 'google' | 'facebook'
  token: string
  id_compania: number
  ci: string
  nombre: string
  telefono?: string
}

export interface RefreshResponse {
  access_token: string
  expires_in: number
}

export interface ForgotPasswordRequest {
  email: string
  id_compania: number
}

export interface ResetPasswordRequest {
  token: string
  new_password: string
  id_compania: number
}

export interface RegistroAppRequest {
  nombre: string
  correo: string
  password: string
  id_compania: number
  telefono?: string
}

export interface GymByQrResponse {
  id_compania: number
  id_sucursal: number
  nombre_compania: string
  nombre_sucursal: string
  logo_url: string | null
}

export interface PersonaResponse {
  id: number
  ci: string | null
  nombre: string
  telefono: string | null
  correo: string | null
  foto_url: string | null
  sexo: string | null
  fecha_nacimiento: string | null
}

export interface ActualizarPersonaRequest {
  nombre?: string
  ci?: string
  telefono?: string
  sexo?: string
  fecha_nacimiento?: string
}

export interface ConsentimientoWaPersonaResponse {
  idPersona: number
  aceptaWhatsapp: boolean
  fechaConsentimientoWa: string | null
}
