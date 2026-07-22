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
  NotifBucket,
  ConsentimientoWaResponse,
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
} from '@/infrastructure/http/platform/platform.dto'

export interface PlatformRepository {
  // Planes
  getPlanes(): Promise<Plan[]>
  crearPlan(body: CrearPlanDto): Promise<Plan>
  actualizarPlan(id: number, body: ActualizarPlanDto): Promise<Plan>
  asignarCaracteristicas(id: number, body: AsignarCaracteristicasDto): Promise<Plan>
  desactivarPlan(id: number): Promise<void>

  // Características
  getCaracteristicas(): Promise<Caracteristica[]>
  crearCaracteristica(body: CrearCaracteristicaDto): Promise<Caracteristica>

  // Compañías
  getCompanias(): Promise<Compania[]>
  getCompania(id: number): Promise<Compania>
  registrarGym(body: RegistrarGymDto): Promise<RegistrarGymResponse>
  registrarGymWizard(body: RegistrarGymWizardDto): Promise<RegistrarGymWizardResponse>
  actualizarCompania(id: number, body: ActualizarCompaniaDto): Promise<Compania>
  suspenderCompania(id: number, body: SuspenderDto): Promise<void>

  // Sucursales
  getSucursales(idCompania: number): Promise<Sucursal[]>
  crearSucursal(idCompania: number, body: CrearSucursalDto): Promise<Sucursal>
  actualizarSucursal(id: number, body: ActualizarSucursalDto): Promise<Sucursal>
  renovarQrToken(id: number, body: RenovarQrDto): Promise<QrRenovarResponse>

  // Suscripciones
  getSuscripcionActiva(idCompania: number): Promise<CompaniaPlan>
  getHistorialSuscripcion(idCompania: number): Promise<CompaniaPlan[]>
  renovarSuscripcion(idCompania: number, body: RenovarSuscripcionDto): Promise<CompaniaPlan>
  upgradePlan(idCompania: number, body: UpgradeDto): Promise<UpgradeResponse>
  downgradePlan(idCompania: number, body: DowngradeDto): Promise<DowngradeResponse>

  // Pagos
  getPagos(idCompania: number): Promise<Pago[]>
  registrarPago(body: RegistrarPagoDto): Promise<Pago>
  confirmarPago(id: number): Promise<Pago>

  // Notificaciones config
  getNotifConfig(idCompania: number): Promise<NotifConfig[]>
  updateNotifConfig(idCompania: number, body: NotifConfig[]): Promise<void>

  // Uso y límites del plan (REQ-SAAS-001)
  getUsoLimites(idCompania: number): Promise<UsoLimitesResponse>

  // REQ-SAAS-001 Sub-fase 1.6: Pagos pendientes de validación (vista owner)
  getPagosPendientesOwner(idCompania: number, limit?: number): Promise<PagoPendienteResponse[]>

  // REQ-SAAS-001 ítem #4: Flujos nuevos
  reportarPagoOwner(idCompania: number, formData: FormData): Promise<PagoPendienteResponse>
  getPagosPendientesRoot(params: { estado?: string; pagina: number; limit: number }): Promise<{ total: number; pagina: number; limit: number; datos: PagoPendienteResponse[] }>
  aprobarPagoPendiente(id: number): Promise<{ id_pago: number; id_compania_plan: number; estado: string }>
  rechazarPagoPendiente(id: number, motivo: string): Promise<void>

  // WhatsApp notification buckets (global platform config)
  getNotifBuckets(): Promise<NotifBucket[]>
  updateNotifBucket(destinatario: string, body: { diasPrevio: number; activo: boolean }): Promise<NotifBucket>

  // WhatsApp opt-in (compania owner)
  patchConsentimientoWaCompania(idCompania: number, acepta: boolean): Promise<ConsentimientoWaResponse>

  // Recordatorio de vencimiento por WhatsApp (plataforma → dueño del gym)
  enviarRecordatorioVencimiento(idCompania: number, forzar?: boolean): Promise<{ enviado: boolean; telefono: string; template: string }>
}
