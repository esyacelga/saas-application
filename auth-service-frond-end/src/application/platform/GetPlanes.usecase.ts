import type { PlatformRepository } from '@/domain/platform/ports/PlatformRepository.port'

export class GetPlanesUseCase {
  constructor(private readonly repo: PlatformRepository) {}

  execute() {
    return this.repo.getPlanes()
  }
}
