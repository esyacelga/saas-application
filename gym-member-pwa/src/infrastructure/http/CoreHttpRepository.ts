import coreApi from './core.instance'

export interface TipoMembresia {
  id: number
  nombre: string
  modo_control: 'calendario' | 'accesos'
  duracion_tipo: 'DIAS' | 'SEMANAS' | 'MESES' | 'ANIOS'
  duracion_valor: number
  dias_acceso: number | null
  precio: number
  activo: boolean
}

export interface MembresiaResponse {
  id: number
  estado_pago: string
  origen: string
}

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

export interface MembresiaHistorialItem {
  id: number
  id_cliente: number
  id_tipo_membresia: number
  tipo_nombre: string
  modo_control: 'calendario' | 'accesos'
  fecha_inicio: string
  fecha_fin: string
  dias_acceso_total: number | null
  dias_acceso_usados: number | null
  dias_acceso_restantes: number | null
  precio_pagado: number
  descuento_aplicado: number
  monto_pagado: number
  saldo_pendiente: number
  estado: 'activa' | 'vencida' | 'congelada' | 'anulada'
  estado_pago: 'PAGADO' | 'PENDIENTE'
  eliminado: boolean
  motivo_eliminacion:
    | 'SOCIO_CAMBIO_OPINION'
    | 'ERROR_DE_VENTA'
    | 'DUPLICADA'
    | 'DATOS_INCORRECTOS'
    | 'OTRO'
    | null
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

  async misMembresias(): Promise<MembresiaHistorialItem[]> {
    const { data } = await coreApi.get<MembresiaHistorialItem[]>('/clientes/me/membresias')
    return data
  }

  async listarTiposMembresia(): Promise<TipoMembresia[]> {
    const { data } = await coreApi.get<TipoMembresia[]>('/tipos-membresia')
    return data
  }

  async solicitarMembresia(idTipoMembresia: number): Promise<MembresiaResponse> {
    const { data } = await coreApi.post<MembresiaResponse>(
      '/clientes/me/membresias/solicitar',
      { id_tipo_membresia: idTipoMembresia },
    )
    return data
  }
}

export const coreRepository = new CoreHttpRepository()
