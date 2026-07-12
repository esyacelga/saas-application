import api from '@/infrastructure/http/platform/axios-platform.instance'
import type { PlatformRepository } from '@/domain/platform/ports/PlatformRepository.port'
import type {
  Plan,
  Caracteristica,
  Compania,
  Sucursal,
  CompaniaPlan,
  Pago,
  NotifConfig,
  UsoLimitesResponse,
  PagoPendienteResponse,
} from '@/domain/platform/entities/Plan.entity'
import type {
  CrearPlanDto,
  ActualizarPlanDto,
  AsignarCaracteristicasDto,
  CrearCaracteristicaDto,
  RegistrarGymDto,
  RegistrarGymResponse,
  RegistrarGymWizardDto,
  RegistrarGymWizardResponse,
  ActualizarCompaniaDto,
  SuspenderDto,
  CrearSucursalDto,
  ActualizarSucursalDto,
  RenovarQrDto,
  QrRenovarResponse,
  RenovarSuscripcionDto,
  UpgradeDto,
  UpgradeResponse,
  DowngradeDto,
  DowngradeResponse,
  RegistrarPagoDto,
} from './platform.dto'

// Snake → camelCase mappers
function mapPlan(r: Record<string, unknown>): Plan {
  return {
    id: r.id as number,
    codigo: (r.codigo ?? '') as string,
    nombre: r.nombre as string,
    descripcion: (r.descripcion ?? '') as string,
    precioMensual: (r.precio_mensual ?? r.precioMensual) as number,
    activo: r.activo as boolean,
    caracteristicas: ((r.caracteristicas ?? []) as Record<string, unknown>[]).map(mapCaracteristica),
  }
}

function mapPagoPendiente(r: Record<string, unknown>): PagoPendienteResponse {
  return {
    id: r.id as number,
    idCompania: (r.id_compania ?? r.idCompania) as number,
    idPlanDestino: (r.id_plan_destino ?? r.idPlanDestino) as number,
    monto: r.monto as number,
    moneda: (r.moneda ?? 'USD') as string,
    fechaReporte: (r.fecha_reporte ?? r.fechaReporte) as string,
    fechaTransferencia: (r.fecha_transferencia ?? r.fechaTransferencia ?? null) as string | null,
    comprobanteUrl: (r.comprobante_url ?? r.comprobanteUrl ?? null) as string | null,
    bancoOrigen: (r.banco_origen ?? r.bancoOrigen ?? null) as string | null,
    referencia: (r.referencia ?? null) as string | null,
    estado: r.estado as PagoPendienteResponse['estado'],
    motivoRechazo: (r.motivo_rechazo ?? r.motivoRechazo ?? null) as string | null,
    aprobadoPor: (r.aprobado_por ?? r.aprobadoPor ?? null) as number | null,
    fechaAprobacion: (r.fecha_aprobacion ?? r.fechaAprobacion ?? null) as string | null,
    activacionProgramada: (r.activacion_programada ?? r.activacionProgramada ?? false) as boolean,
  }
}

function mapCaracteristica(r: Record<string, unknown>): Caracteristica {
  return {
    id: r.id as number,
    codigo: r.codigo as string,
    nombre: r.nombre as string,
    modulo: r.modulo as string,
    activo: r.activo as boolean,
  }
}

function mapCompania(r: Record<string, unknown>): Compania {
  const pa = r.plan_activo ?? r.planActivo
  return {
    id: r.id as number,
    nombre: r.nombre as string,
    ruc: r.ruc as string,
    telefono: (r.telefono ?? '') as string,
    whatsapp: (r.whatsapp ?? '') as string,
    correo: (r.correo ?? '') as string,
    logoUrl: (r.logo_url ?? r.logoUrl ?? null) as string | null,
    activo: r.activo as boolean,
    planActivo: pa
      ? {
          nombre: (pa as Record<string, unknown>).nombre as string,
          estado: (pa as Record<string, unknown>).estado as NonNullable<Compania['planActivo']>['estado'],
          fechaFin: ((pa as Record<string, unknown>).fecha_fin ?? (pa as Record<string, unknown>).fechaFin) as string,
          diasRestantes: ((pa as Record<string, unknown>).dias_restantes ?? (pa as Record<string, unknown>).diasRestantes) as number,
        }
      : null,
  }
}

function mapSucursal(r: Record<string, unknown>): Sucursal {
  return {
    id: r.id as number,
    idCompania: (r.id_compania ?? r.idCompania) as number,
    nombre: r.nombre as string,
    direccion: (r.direccion ?? '') as string,
    esPrincipal: (r.es_principal ?? r.esPrincipal ?? false) as boolean,
    activo: r.activo as boolean,
    qrToken: (r.qr_token ?? r.qrToken ?? '') as string,
    qrTokenExpira: (r.qr_token_expira ?? r.qrTokenExpira ?? null) as string | null,
  }
}

function mapCompaniaPlan(r: Record<string, unknown>): CompaniaPlan {
  return {
    id: r.id as number,
    idPlan: (r.id_plan ?? r.idPlan) as number,
    estado: r.estado as string,
    fechaInicio: (r.fecha_inicio ?? r.fechaInicio) as string,
    fechaFin: (r.fecha_fin ?? r.fechaFin) as string,
    diasRestantes: (r.dias_restantes ?? r.diasRestantes ?? 0) as number,
    diasGracia: (r.dias_gracia ?? r.diasGracia ?? 0) as number,
    tipoCambio: (r.tipo_cambio ?? r.tipoCambio ?? '') as string,
  }
}

function mapPago(r: Record<string, unknown>): Pago {
  return {
    id: r.id as number,
    idCompaniaPlan: (r.id_compania_plan ?? r.idCompaniaPlan) as number,
    monto: r.monto as number,
    fechaPago: (r.fecha_pago ?? r.fechaPago) as string,
    periodoDesde: (r.periodo_desde ?? r.periodoDesde ?? null) as string | null,
    periodoHasta: (r.periodo_hasta ?? r.periodoHasta ?? null) as string | null,
    metodoPago: (r.metodo_pago ?? r.metodoPago) as string,
    tipoPago: (r.tipo_pago ?? r.tipoPago) as string,
    estado: r.estado as Pago['estado'],
    referencia: (r.referencia ?? null) as string | null,
  }
}

function mapNotifConfig(r: Record<string, unknown>): NotifConfig {
  return {
    idCompania: (r.id_compania ?? r.idCompania) as number,
    diasAntes: (r.dias_antes ?? r.diasAntes) as number,
    canal: r.canal as NotifConfig['canal'],
    activo: r.activo as boolean,
  }
}

class PlatformHttpRepositoryImpl implements PlatformRepository {
  // Planes
  async getPlanes(): Promise<Plan[]> {
    const { data } = await api.get<unknown[]>('/planes')
    return data.map(r => mapPlan(r as Record<string, unknown>))
  }

  async crearPlan(body: CrearPlanDto): Promise<Plan> {
    const { data } = await api.post<unknown>('/planes', {
      nombre: body.nombre,
      descripcion: body.descripcion,
      precioMensual: body.precioMensual,
    })
    return mapPlan(data as Record<string, unknown>)
  }

  async actualizarPlan(id: number, body: ActualizarPlanDto): Promise<Plan> {
    const { data } = await api.put<unknown>(`/planes/${id}`, {
      nombre: body.nombre,
      descripcion: body.descripcion,
      precioMensual: body.precioMensual,
    })
    return mapPlan(data as Record<string, unknown>)
  }

  async asignarCaracteristicas(id: number, body: AsignarCaracteristicasDto): Promise<Plan> {
    const { data } = await api.put<unknown>(`/planes/${id}/caracteristicas`, {
      caracteristicaIds: body.caracteristicaIds,
    })
    return mapPlan(data as Record<string, unknown>)
  }

  async desactivarPlan(id: number): Promise<void> {
    await api.put(`/planes/${id}/desactivar`)
  }

  // Características
  async getCaracteristicas(): Promise<Caracteristica[]> {
    const { data } = await api.get<unknown[]>('/caracteristicas')
    return data.map(r => mapCaracteristica(r as Record<string, unknown>))
  }

  async crearCaracteristica(body: CrearCaracteristicaDto): Promise<Caracteristica> {
    const { data } = await api.post<unknown>('/caracteristicas', body)
    return mapCaracteristica(data as Record<string, unknown>)
  }

  // Compañías
  async getCompanias(): Promise<Compania[]> {
    const { data } = await api.get<unknown[]>('/companias')
    return data.map(r => mapCompania(r as Record<string, unknown>))
  }

  async getCompania(id: number): Promise<Compania> {
    const { data } = await api.get<unknown>(`/companias/${id}`)
    return mapCompania(data as Record<string, unknown>)
  }

  async registrarGym(body: RegistrarGymDto): Promise<RegistrarGymResponse> {
    const { data } = await api.post<Record<string, unknown>>('/companias', {
      nombre: body.nombre,
      ruc: body.ruc,
      logoUrl: body.logoUrl,
      telefono: body.telefono,
      whatsapp: body.whatsapp,
      correo: body.correo,
      idPlan: body.idPlan,
      nombreSucursal: body.nombreSucursal,
      direccionSucursal: body.direccionSucursal,
    })
    return {
      idCompania: (data.id_compania ?? data.idCompania) as number,
      idCompaniaPlan: (data.id_compania_plan ?? data.idCompaniaPlan) as number,
      idSucursal: (data.id_sucursal ?? data.idSucursal) as number,
      qrToken: (data.qr_token ?? data.qrToken) as string,
    }
  }

  async registrarGymWizard(body: RegistrarGymWizardDto): Promise<RegistrarGymWizardResponse> {
    const { data } = await api.post<Record<string, unknown>>('/companias/wizard', {
      nombre: body.nombre,
      ruc: body.ruc,
      logoUrl: body.logoUrl,
      correo: body.correo,
      telefono: body.telefono,
      whatsapp: body.whatsapp,
      idPlan: body.idPlan,
      nombreSucursal: body.nombreSucursal,
      direccionSucursal: body.direccionSucursal,
      usuarioPrincipal: {
        id_persona:  body.usuarioPrincipal.id_persona,
        ci:          body.usuarioPrincipal.ci,
        nombre:      body.usuarioPrincipal.nombre,
        correo:      body.usuarioPrincipal.correo,
        telefono:    body.usuarioPrincipal.telefono,
        password:    body.usuarioPrincipal.password,
      },
      usuariosAdicionales: body.usuariosAdicionales,
    })
    return {
      idCompania: (data.id_compania ?? data.idCompania) as number,
      idCompaniaPlan: (data.id_compania_plan ?? data.idCompaniaPlan) as number,
      idSucursal: (data.id_sucursal ?? data.idSucursal) as number,
      qrToken: (data.qr_token ?? data.qrToken) as string,
      usuariosPrincipal: (data.usuario_principal ?? data.usuariosPrincipal) as { id: number; nombre: string; correo: string },
      usuariosCreados: (data.usuarios_creados ?? data.usuariosCreados ?? 1) as number,
    }
  }

  async actualizarCompania(id: number, body: ActualizarCompaniaDto): Promise<Compania> {
    const { data } = await api.put<unknown>(`/companias/${id}`, {
      nombre: body.nombre,
      logoUrl: body.logoUrl,
      telefono: body.telefono,
      whatsapp: body.whatsapp,
      correo: body.correo,
    })
    return mapCompania(data as Record<string, unknown>)
  }

  async suspenderCompania(id: number, body: SuspenderDto): Promise<void> {
    await api.put(`/companias/${id}/suspender`, body)
  }

  // Sucursales
  async getSucursales(idCompania: number): Promise<Sucursal[]> {
    const { data } = await api.get<unknown[]>(`/companias/${idCompania}/sucursales`)
    return data.map(r => mapSucursal(r as Record<string, unknown>))
  }

  async crearSucursal(idCompania: number, body: CrearSucursalDto): Promise<Sucursal> {
    const { data } = await api.post<unknown>(`/companias/${idCompania}/sucursales`, {
      nombre: body.nombre,
      direccion: body.direccion,
      esPrincipal: body.esPrincipal,
    })
    return mapSucursal(data as Record<string, unknown>)
  }

  async actualizarSucursal(id: number, body: ActualizarSucursalDto): Promise<Sucursal> {
    const { data } = await api.put<unknown>(`/sucursales/${id}`, body)
    return mapSucursal(data as Record<string, unknown>)
  }

  async renovarQrToken(id: number, body: RenovarQrDto): Promise<QrRenovarResponse> {
    const { data } = await api.post<Record<string, unknown>>(`/sucursales/${id}/qr/renovar`, {
      expiresInHours: body.expiresInHours,
    })
    return {
      qrToken: (data.qr_token ?? data.qrToken) as string,
      qrTokenExpira: (data.qr_token_expira ?? data.qrTokenExpira ?? null) as string | null,
    }
  }

  // Suscripciones
  async getSuscripcionActiva(idCompania: number): Promise<CompaniaPlan> {
    const { data } = await api.get<unknown>(`/companias/${idCompania}/suscripcion`)
    return mapCompaniaPlan(data as Record<string, unknown>)
  }

  async getHistorialSuscripcion(idCompania: number): Promise<CompaniaPlan[]> {
    const { data } = await api.get<unknown[]>(`/companias/${idCompania}/suscripcion/historial`)
    return data.map(r => mapCompaniaPlan(r as Record<string, unknown>))
  }

  async renovarSuscripcion(idCompania: number, body: RenovarSuscripcionDto): Promise<CompaniaPlan> {
    const { data } = await api.post<unknown>(`/companias/${idCompania}/suscripcion/renovar`, {
      idPlan: body.idPlan,
      meses: body.meses,
    })
    return mapCompaniaPlan(data as Record<string, unknown>)
  }

  async upgradePlan(idCompania: number, body: UpgradeDto): Promise<UpgradeResponse> {
    const { data } = await api.post<Record<string, unknown>>(`/companias/${idCompania}/suscripcion/upgrade`, {
      idPlanNuevo: body.idPlanNuevo,
    })
    return {
      idCompaniaPlanNuevo: (data.idCompaniaPlanNuevo ?? data.id_compania_plan_nuevo) as number,
      creditoAplicado: (data.creditoAplicado ?? data.credito_aplicado) as number,
      montoAPagar: (data.montoAPagar ?? data.monto_a_pagar) as number,
      planAnteriorCancelado: (data.planAnteriorCancelado ?? data.plan_anterior_cancelado) as boolean,
    }
  }

  async downgradePlan(idCompania: number, body: DowngradeDto): Promise<DowngradeResponse> {
    const { data } = await api.post<Record<string, unknown>>(`/companias/${idCompania}/suscripcion/downgrade`, {
      idPlanNuevo: body.idPlanNuevo,
    })
    return {
      idCompaniaPlanNuevo: (data.idCompaniaPlanNuevo ?? data.id_compania_plan_nuevo) as number,
      estado: data.estado as string,
      efectivoDe: (data.efectivoDe ?? data.efectivo_de) as string,
      creditoGenerado: (data.creditoGenerado ?? data.credito_generado) as number,
    }
  }

  // Pagos
  async getPagos(idCompania: number): Promise<Pago[]> {
    const { data } = await api.get<unknown[]>(`/companias/${idCompania}/pagos`)
    return data.map(r => mapPago(r as Record<string, unknown>))
  }

  async registrarPago(body: RegistrarPagoDto): Promise<Pago> {
    const { data } = await api.post<unknown>('/pagos', {
      idCompaniaPlan: body.idCompaniaPlan,
      monto: body.monto,
      metodoPago: body.metodoPago,
      tipoPago: body.tipoPago,
      referencia: body.referencia,
      periodoDesde: body.periodoDesde,
      periodoHasta: body.periodoHasta,
    })
    return mapPago(data as Record<string, unknown>)
  }

  async confirmarPago(id: number): Promise<Pago> {
    const { data } = await api.put<unknown>(`/pagos/${id}/confirmar`)
    return mapPago(data as Record<string, unknown>)
  }

  // Notificaciones config
  async getNotifConfig(idCompania: number): Promise<NotifConfig[]> {
    const { data } = await api.get<unknown[]>(`/companias/${idCompania}/notif-config`)
    return data.map(r => mapNotifConfig(r as Record<string, unknown>))
  }

  async updateNotifConfig(idCompania: number, body: NotifConfig[]): Promise<void> {
    await api.put(`/companias/${idCompania}/notif-config`, {
      configs: body.map(c => ({
        id_compania: c.idCompania,
        dias_antes: c.diasAntes,
        canal: c.canal,
        activo: c.activo,
      })),
    })
  }

  // Mi Empresa — endpoints para usuario staff
  async getMiEmpresa(): Promise<Compania> {
    const { data } = await api.get<unknown>('/mi-empresa')
    return mapCompania(data as Record<string, unknown>)
  }

  async actualizarMiEmpresa(body: { nombre?: string; telefono?: string; whatsapp?: string; correo?: string }): Promise<Compania> {
    const { data } = await api.patch<unknown>('/mi-empresa', body)
    return mapCompania(data as Record<string, unknown>)
  }

  async subirLogoCompania(id: number, file: File): Promise<Compania> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post<unknown>(`/companias/${id}/logo`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return mapCompania(data as Record<string, unknown>)
  }

  async subirLogoEmpresa(file: File): Promise<Compania> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post<unknown>('/mi-empresa/logo', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return mapCompania(data as Record<string, unknown>)
  }

  async getMiSucursal(): Promise<Sucursal> {
    const { data } = await api.get<unknown>('/mi-empresa/sucursal')
    return mapSucursal(data as Record<string, unknown>)
  }

  async actualizarMiSucursal(body: { nombre?: string; direccion?: string }): Promise<Sucursal> {
    const { data } = await api.patch<unknown>('/mi-empresa/sucursal', body)
    return mapSucursal(data as Record<string, unknown>)
  }

  async renovarMiQr(): Promise<Sucursal> {
    const { data } = await api.post<unknown>('/mi-empresa/sucursal/qr/renovar')
    return mapSucursal(data as Record<string, unknown>)
  }

  async getActividad(params: import('./platform.dto').ActividadParams = {}): Promise<import('./platform.dto').PaginadoActividadResponse> {
    const { data } = await api.get<import('./platform.dto').PaginadoActividadResponse>('/actividad', { params })
    return data
  }

  // REQ-SAAS-001: Uso y límites del plan
  async getUsoLimites(idCompania: number): Promise<UsoLimitesResponse> {
    const { data } = await api.get<UsoLimitesResponse>(`/companias/${idCompania}/uso-limites`)
    // El backend ya devuelve camelCase en este endpoint según la spec
    return data
  }

  // REQ-SAAS-001 Sub-fase 1.6: Pagos pendientes de validación (vista owner)
  async getPagosPendientesOwner(idCompania: number, limit = 10): Promise<PagoPendienteResponse[]> {
    const { data } = await api.get<unknown[]>(`/companias/${idCompania}/pagos-pendientes`, {
      params: { limit },
    })
    return data.map(r => mapPagoPendiente(r as Record<string, unknown>))
  }
}

export const platformRepository = new PlatformHttpRepositoryImpl()
