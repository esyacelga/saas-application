import coreApi from './axios-core.instance'
import type {
  TipoMembresia, CrearTipoMembresiaDto, ActualizarTipoMembresiaDto,
  ClientesResponse, ClienteDetalle, RegistrarClienteDto, RegistrarClientePlataformaDto, RegistrarClienteResponse,
  ActualizarClienteDto, BuscarPorCiResponse, ClientePorPersona, ActualizarClientePlataformaDto,
  MembresiaHistorial, MembresiaDetalle, VenderMembresiaDto, AnularMembresiaDto,
  CongelarMembresiaDto, CongelarResponse, ReactivarResponse, CongelamientoHistorial,
} from './core.dto'

// ── Tipos de membresía ───────────────────────────────────────────────────────

async function getTiposMembresia(): Promise<TipoMembresia[]> {
  const { data } = await coreApi.get<TipoMembresia[]>('/tipos-membresia')
  return data
}

async function crearTipoMembresia(body: CrearTipoMembresiaDto): Promise<TipoMembresia> {
  const { data } = await coreApi.post<TipoMembresia>('/tipos-membresia', body)
  return data
}

async function actualizarTipoMembresia(id: number, body: ActualizarTipoMembresiaDto): Promise<TipoMembresia> {
  const { data } = await coreApi.put<TipoMembresia>(`/tipos-membresia/${id}`, body)
  return data
}

async function desactivarTipoMembresia(id: number): Promise<void> {
  await coreApi.put(`/tipos-membresia/${id}/desactivar`)
}

// ── Clientes ─────────────────────────────────────────────────────────────────

async function getClientes(params?: {
  estado?: string; buscar?: string; page?: number; limit?: number; sin_membresia?: boolean
}): Promise<ClientesResponse> {
  const { data } = await coreApi.get<ClientesResponse>('/clientes', { params })
  return data
}

async function getCliente(id: number): Promise<ClienteDetalle> {
  const { data } = await coreApi.get<ClienteDetalle>(`/clientes/${id}`)
  return data
}

async function registrarCliente(body: RegistrarClienteDto): Promise<RegistrarClienteResponse> {
  const { data } = await coreApi.post<RegistrarClienteResponse>('/clientes', body)
  return data
}

async function actualizarCliente(id: number, body: ActualizarClienteDto): Promise<ClienteDetalle> {
  const { data } = await coreApi.put<ClienteDetalle>(`/clientes/${id}`, body)
  return data
}

async function buscarPorCi(ci: string): Promise<BuscarPorCiResponse> {
  const { data } = await coreApi.get<BuscarPorCiResponse>(`/clientes/ci/${ci}`)
  return data
}

async function registrarClientePlataforma(body: RegistrarClientePlataformaDto): Promise<RegistrarClienteResponse> {
  const { data } = await coreApi.post<RegistrarClienteResponse>('/clientes/plataforma', body)
  return data
}

async function getClientesPorPersona(idPersona: number): Promise<ClientePorPersona[]> {
  const { data } = await coreApi.get<ClientePorPersona[]>(`/clientes/por-persona/${idPersona}`)
  return data
}

async function actualizarClientePlataforma(id: number, body: ActualizarClientePlataformaDto): Promise<ClientePorPersona> {
  const { data } = await coreApi.put<ClientePorPersona>(`/clientes/plataforma/${id}`, body)
  return data
}

async function eliminarCliente(id: number): Promise<void> {
  await coreApi.delete(`/clientes/${id}`)
}

// ── Membresías ────────────────────────────────────────────────────────────────

async function getMembresiasPorCliente(idCliente: number): Promise<MembresiaHistorial[]> {
  const { data } = await coreApi.get<MembresiaHistorial[]>(`/clientes/${idCliente}/membresias`)
  return data
}

async function getMembresia(id: number): Promise<MembresiaDetalle> {
  const { data } = await coreApi.get<MembresiaDetalle>(`/membresias/${id}`)
  return data
}

async function venderMembresia(idCliente: number, body: VenderMembresiaDto): Promise<MembresiaDetalle> {
  const { data } = await coreApi.post<MembresiaDetalle>(`/clientes/${idCliente}/membresias`, body)
  return data
}

async function anularMembresia(id: number, body: AnularMembresiaDto): Promise<void> {
  await coreApi.put(`/membresias/${id}/anular`, body)
}

async function actualizarAsistenciasPrevias(id: number, cantidad: number): Promise<void> {
  await coreApi.patch(`/membresias/${id}/asistencias-previas`, { cantidad })
}

// ── Congelamientos ────────────────────────────────────────────────────────────

async function congelarMembresia(idMembresia: number, body: CongelarMembresiaDto): Promise<CongelarResponse> {
  const { data } = await coreApi.post<CongelarResponse>(`/membresias/${idMembresia}/congelar`, body)
  return data
}

async function reactivarCongelamiento(idCongelamiento: number): Promise<ReactivarResponse> {
  const { data } = await coreApi.put<ReactivarResponse>(`/congelamientos/${idCongelamiento}/reactivar`)
  return data
}

async function getCongelamientos(idMembresia: number): Promise<CongelamientoHistorial[]> {
  const { data } = await coreApi.get<CongelamientoHistorial[]>(`/membresias/${idMembresia}/congelamientos`)
  return data
}

export const coreRepository = {
  getTiposMembresia,
  crearTipoMembresia,
  actualizarTipoMembresia,
  desactivarTipoMembresia,
  getClientes,
  getCliente,
  registrarCliente,
  actualizarCliente,
  buscarPorCi,
  registrarClientePlataforma,
  getClientesPorPersona,
  actualizarClientePlataforma,
  eliminarCliente,
  getMembresiasPorCliente,
  getMembresia,
  venderMembresia,
  anularMembresia,
  actualizarAsistenciasPrevias,
  congelarMembresia,
  reactivarCongelamiento,
  getCongelamientos,
}
