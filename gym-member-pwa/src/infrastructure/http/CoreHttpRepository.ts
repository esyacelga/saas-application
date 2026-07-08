import coreApi from './core.instance'

export interface MembresiaInfo {
  id: number
  tipo_nombre: string
  modo_control: 'calendario' | 'accesos'
  estado: 'activa' | 'vencida' | 'congelada' | 'anulada'
  fecha_inicio: string
  fecha_fin: string
  dias_acceso_usados: number | null
  dias_acceso_restantes: number | null
}

export interface CongelamientoInfo {
  id: number
  fecha_inicio: string
}

export interface MiPerfilResponse {
  id_cliente: number
  estado_cliente: 'activo' | 'proximo_vencer' | 'vencido' | 'congelado' | 'riesgo_abandono'
  membresia_activa: MembresiaInfo | null
  congelamiento_activo: CongelamientoInfo | null
}

export interface ReactivarResponse {
  fecha_fin_anterior: string
  dias_compensados: number
  fecha_fin_nueva: string
}

class CoreHttpRepository {
  async miPerfil(): Promise<MiPerfilResponse> {
    const { data } = await coreApi.get<MiPerfilResponse>('/clientes/mi-perfil')
    return data
  }

  async registrarComoCliente(idSucursal?: number): Promise<{ id_cliente: number; id_persona: number }> {
    const { data } = await coreApi.post('/clientes/app', { id_sucursal: idSucursal ?? null })
    return data
  }

  async reactivarCongelamiento(idCongelamiento: number): Promise<ReactivarResponse> {
    const { data } = await coreApi.put<ReactivarResponse>(
      `/mis-congelamientos/${idCongelamiento}/reactivar`,
    )
    return data
  }
}

export const coreRepository = new CoreHttpRepository()
