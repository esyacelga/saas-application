import type { PlatformRepository } from '@/domain/platform/ports/PlatformRepository.port'
import type {
  RenovarSuscripcionDto,
  UpgradeDto,
  DowngradeDto,
} from '@/infrastructure/http/platform/platform.dto'

export class GestionarSuscripcionUseCase {
  constructor(private readonly repo: PlatformRepository) {}

  getSuscripcionActiva(idCompania: number) {
    return this.repo.getSuscripcionActiva(idCompania)
  }

  getHistorialSuscripcion(idCompania: number) {
    return this.repo.getHistorialSuscripcion(idCompania)
  }

  renovarSuscripcion(idCompania: number, body: RenovarSuscripcionDto) {
    return this.repo.renovarSuscripcion(idCompania, body)
  }

  upgradePlan(idCompania: number, body: UpgradeDto) {
    return this.repo.upgradePlan(idCompania, body)
  }

  downgradePlan(idCompania: number, body: DowngradeDto) {
    return this.repo.downgradePlan(idCompania, body)
  }
}
