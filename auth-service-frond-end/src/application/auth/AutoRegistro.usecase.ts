import { authRepository } from '@/infrastructure/http/auth/AuthHttpRepository'
import type { AutoRegistroRequest, AutoRegistroResponse } from '@/infrastructure/http/auth/auth.dto'

export class AutoRegistroUseCase {
  execute(body: AutoRegistroRequest): Promise<AutoRegistroResponse> {
    return authRepository.autoRegistro(body)
  }

  // Verificación de disponibilidad de correo (onBlur en el registro). true = ya en uso.
  correoEnUso(correo: string): Promise<boolean> {
    return authRepository.correoEnUso(correo)
  }
}

export const autoRegistroUseCase = new AutoRegistroUseCase()
