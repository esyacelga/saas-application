import type { AuthRepositoryPort } from '@/domain/auth/ports/AuthRepository.port'
import type { AuthStorePort } from '@/domain/auth/ports/AuthStore.port'

export class LogoutUseCase {
  constructor(
    private readonly repo: AuthRepositoryPort,
    private readonly store: AuthStorePort,
  ) {}

  async execute() {
    await this.repo.logout()
    this.store.logout()
  }
}
