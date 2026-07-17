import api from './axios.instance'
import type { AuthRepository } from '@/domain/port/AuthRepository'
import type {
  LoginManualRequest, LoginGoogleRequest, LoginFacebookRequest,
  LoginAppResponse, OAuthLoginResponse, CompletarRegistroOauthRequest,
  RefreshResponse,
  ForgotPasswordRequest, ResetPasswordRequest, GymByQrResponse,
  RegistroAppRequest, PersonaResponse, ActualizarPersonaRequest,
  ConsentimientoWaPersonaResponse,
} from '@/application/usecase/auth.types'

class AuthHttpRepository implements AuthRepository {
  async loginManual(req: LoginManualRequest): Promise<LoginAppResponse> {
    const { data } = await api.post('/auth/app/login', req)
    return data
  }

  async loginGoogle(req: LoginGoogleRequest): Promise<OAuthLoginResponse> {
    const { data } = await api.post('/auth/app/oauth/google', req)
    return data
  }

  async loginFacebook(req: LoginFacebookRequest): Promise<OAuthLoginResponse> {
    const { data } = await api.post('/auth/app/oauth/facebook', req)
    return data
  }

  async completarRegistroOauth(req: CompletarRegistroOauthRequest): Promise<LoginAppResponse> {
    const { data } = await api.post('/auth/app/oauth/completar-registro', req)
    return data
  }

  async registrar(req: RegistroAppRequest): Promise<LoginAppResponse> {
    const { data } = await api.post('/auth/app/registro', req)
    return data
  }

  async refresh(refreshToken: string): Promise<RefreshResponse> {
    const { data } = await api.post('/auth/refresh', { refresh_token: refreshToken })
    return data
  }

  async logout(): Promise<void> {
    await api.post('/auth/logout')
  }

  async forgotPassword(req: ForgotPasswordRequest): Promise<void> {
    await api.post('/auth/password/forgot', req)
  }

  async resetPassword(req: ResetPasswordRequest): Promise<void> {
    await api.post('/auth/password/reset', req)
  }

  async getGymByQr(token: string): Promise<GymByQrResponse> {
    const { data } = await api.get(`/auth/gimnasio/by-qr/${token}`)
    return data
  }

  async getPersona(id: number): Promise<PersonaResponse> {
    const { data } = await api.get(`/personas/${id}`)
    return data
  }

  async actualizarPersona(id: number, req: ActualizarPersonaRequest): Promise<PersonaResponse> {
    const { data } = await api.put(`/personas/${id}`, req)
    return data
  }

  async subirFotoMiembro(id: number, file: File): Promise<PersonaResponse> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post(`/personas/${id}/foto`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  }

  async patchConsentimientoWaPersona(id: number, acepta: boolean): Promise<ConsentimientoWaPersonaResponse> {
    const { data } = await api.patch<Record<string, unknown>>(`/personas/${id}/consentimiento-wa`, { acepta })
    return {
      idPersona: (data.id_persona ?? data.idPersona) as number,
      aceptaWhatsapp: (data.acepta_whatsapp ?? data.aceptaWhatsapp) as boolean,
      fechaConsentimientoWa: (data.fecha_consentimiento_wa ?? data.fechaConsentimientoWa ?? null) as string | null,
    }
  }
}

export const authRepository = new AuthHttpRepository()
