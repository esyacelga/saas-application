import type { PlatformRepository } from '@/domain/platform/ports/PlatformRepository.port'
import type {
  CrearSucursalDto,
  ActualizarSucursalDto,
  RenovarQrDto,
} from '@/infrastructure/http/platform/platform.dto'

export class GestionarSucursalUseCase {
  constructor(private readonly repo: PlatformRepository) {}

  getSucursales(idCompania: number) {
    return this.repo.getSucursales(idCompania)
  }

  crearSucursal(idCompania: number, body: CrearSucursalDto) {
    return this.repo.crearSucursal(idCompania, body)
  }

  actualizarSucursal(id: number, body: ActualizarSucursalDto) {
    return this.repo.actualizarSucursal(id, body)
  }

  renovarQrToken(id: number, body: RenovarQrDto) {
    return this.repo.renovarQrToken(id, body)
  }
}
