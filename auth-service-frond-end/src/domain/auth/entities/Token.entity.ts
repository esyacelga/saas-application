import type { JwtPayload } from './User.entity'

export interface AuthSession {
  accessToken: string
  user: JwtPayload
}
