import type { JwtPayloadStaff, JwtPayloadPlataforma } from '../entities/User.entity'

export interface AuthStorePort {
  accessToken: string | null
  user: JwtPayloadStaff | JwtPayloadPlataforma | null
  initialized: boolean
  setSession(token: string): void
  setAccessToken(token: string): void
  logout(): void
  setInitialized(): void
}
