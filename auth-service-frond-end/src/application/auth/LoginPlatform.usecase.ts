import type { AuthRepositoryPort } from '@/domain/auth/ports/AuthRepository.port'
import type { AuthStorePort } from '@/domain/auth/ports/AuthStore.port'
import type { LoginPlatformRequest } from '@/infrastructure/http/auth/auth.dto'

export class LoginPlatformUseCase {
  constructor(
    private readonly repo: AuthRepositoryPort,
    private readonly store: AuthStorePort,
  ) {}

  async execute(body: LoginPlatformRequest) {
    const data = await this.repo.loginPlatform(body)
    this.store.setSession(data.access_token)
    return data
  }
}
