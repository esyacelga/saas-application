import type { PlatformRepository } from '@/domain/platform/ports/PlatformRepository.port'
import type { RegistrarGymWizardDto, RegistrarGymWizardResponse } from '@/infrastructure/http/platform/platform.dto'

export class RegistrarGymWizardUseCase {
  constructor(private readonly repo: PlatformRepository) {}

  execute(body: RegistrarGymWizardDto): Promise<RegistrarGymWizardResponse> {
    return this.repo.registrarGymWizard(body)
  }
}
