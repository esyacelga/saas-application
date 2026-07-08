import type { PlatformRepository } from '@/domain/platform/ports/PlatformRepository.port'
import type { RegistrarPagoDto } from '@/infrastructure/http/platform/platform.dto'

export class GestionarPagoUseCase {
  constructor(private readonly repo: PlatformRepository) {}

  getPagos(idCompania: number) {
    return this.repo.getPagos(idCompania)
  }

  registrarPago(body: RegistrarPagoDto) {
    return this.repo.registrarPago(body)
  }

  confirmarPago(id: number) {
    return this.repo.confirmarPago(id)
  }
}
