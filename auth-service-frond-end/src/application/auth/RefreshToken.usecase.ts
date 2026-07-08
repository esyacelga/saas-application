import type { AuthRepositoryPort } from '@/domain/auth/ports/AuthRepository.port'
import type { AuthStorePort } from '@/domain/auth/ports/AuthStore.port'

export class RefreshTokenUseCase {
  constructor(
    private readonly repo: AuthRepositoryPort,
    private readonly store: AuthStorePort,
  ) {}

  async execute() {
    const data = await this.repo.refreshToken()
    this.store.setSession(data.access_token)
    return data
  }
}
