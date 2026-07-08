import type { AuthRepositoryPort } from '@/domain/auth/ports/AuthRepository.port'
import type { AuthStorePort } from '@/domain/auth/ports/AuthStore.port'
import type { LoginStaffRequest } from '@/infrastructure/http/auth/auth.dto'

export class LoginStaffUseCase {
  constructor(
    private readonly repo: AuthRepositoryPort,
    private readonly store: AuthStorePort,
  ) {}

  async execute(body: LoginStaffRequest) {
    const data = await this.repo.loginStaff(body)
    this.store.setSession(data.access_token)
    return data
  }
}
