export interface ClienteToken {
  sub: string
  tipo: 'cliente'
  id_compania: number
  id_persona: number
  nombre: string
  nombre_compania: string | null
  logo_url: string | null
  foto_url: string | null
  sexo: string | null
  exp: number
}

export interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: ClienteToken | null
  initialized: boolean
}
