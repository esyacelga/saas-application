import type { PlatformRepository } from '@/domain/platform/ports/PlatformRepository.port'

export class GestionarNotifBucketsUseCase {
  constructor(private readonly repo: PlatformRepository) {}

  getNotifBuckets() {
    return this.repo.getNotifBuckets()
  }

  updateNotifBucket(destinatario: string, body: { diasPrevio: number; activo: boolean }) {
    return this.repo.updateNotifBucket(destinatario, body)
  }

  patchConsentimientoWaCompania(idCompania: number, acepta: boolean) {
    return this.repo.patchConsentimientoWaCompania(idCompania, acepta)
  }
}
