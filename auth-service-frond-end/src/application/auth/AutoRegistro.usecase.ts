import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { AutoRegistroRequest, AutoRegistroResponse } from '@/infrastructure/http/auth/auth.dto'

export class AutoRegistroUseCase {
  execute(body: AutoRegistroRequest): Promise<AutoRegistroResponse> {
    return authRepository.autoRegistro(body)
  }
}

export const autoRegistroUseCase = new AutoRegistroUseCase()
