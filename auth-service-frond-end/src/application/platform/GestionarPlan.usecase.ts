import type { PlatformRepository } from '@/domain/platform/ports/PlatformRepository.port'
import type {
  CrearPlanDto,
  ActualizarPlanDto,
  AsignarCaracteristicasDto,
} from '@/infrastructure/http/platform/platform.dto'

export class GestionarPlanUseCase {
  constructor(private readonly repo: PlatformRepository) {}

  crearPlan(body: CrearPlanDto) {
    return this.repo.crearPlan(body)
  }

  actualizarPlan(id: number, body: ActualizarPlanDto) {
    return this.repo.actualizarPlan(id, body)
  }

  asignarCaracteristicas(id: number, body: AsignarCaracteristicasDto) {
    return this.repo.asignarCaracteristicas(id, body)
  }

  desactivarPlan(id: number) {
    return this.repo.desactivarPlan(id)
  }
}
