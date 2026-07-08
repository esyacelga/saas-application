import type { PlatformRepository } from '@/domain/platform/ports/PlatformRepository.port'
import type {
  RegistrarGymDto,
  ActualizarCompaniaDto,
  SuspenderDto,
} from '@/infrastructure/http/platform/platform.dto'

export class GestionarCompaniaUseCase {
  constructor(private readonly repo: PlatformRepository) {}

  getCompanias() {
    return this.repo.getCompanias()
  }

  getCompania(id: number) {
    return this.repo.getCompania(id)
  }

  registrarGym(body: RegistrarGymDto) {
    return this.repo.registrarGym(body)
  }

  actualizarCompania(id: number, body: ActualizarCompaniaDto) {
    return this.repo.actualizarCompania(id, body)
  }

  suspenderCompania(id: number, body: SuspenderDto) {
    return this.repo.suspenderCompania(id, body)
  }
}
