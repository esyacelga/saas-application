import type { PlatformRepository } from '@/domain/platform/ports/PlatformRepository.port'
import type { NotifConfig } from '@/domain/platform/entities/Plan.entity'

export class GestionarNotifConfigUseCase {
  constructor(private readonly repo: PlatformRepository) {}

  getNotifConfig(idCompania: number) {
    return this.repo.getNotifConfig(idCompania)
  }

  updateNotifConfig(idCompania: number, body: NotifConfig[]) {
    return this.repo.updateNotifConfig(idCompania, body)
  }
}
