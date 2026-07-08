export type TipoToken = 'plataforma' | 'staff' | 'cliente'

export interface JwtPayloadPlataforma {
  sub: string
  tipo: 'plataforma'
  rol_plataforma: 'super_admin' | 'soporte' | 'viewer'
  nombre: string
  iat: number
  exp: number
}

export interface JwtPayloadStaff {
  sub: string
  tipo: 'staff'
  id_compania: number
  id_sucursal: number
  id_rol: number
  nombre: string
  permisos: string[]
  iat: number
  exp: number
}

export interface JwtPayloadCliente {
  sub: string
  tipo: 'cliente'
  id_compania: number
  id_persona: number
  nombre: string
  iat: number
  exp: number
}

export type JwtPayload = JwtPayloadPlataforma | JwtPayloadStaff | JwtPayloadCliente
