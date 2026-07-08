import type {
  Plan,
  Caracteristica,
  Compania,
  Sucursal,
  CompaniaPlan,
  Pago,
  NotifConfig,
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
}
