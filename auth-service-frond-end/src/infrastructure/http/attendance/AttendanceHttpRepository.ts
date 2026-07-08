import attendanceApi from './axios-attendance.instance'

export interface EntradaResumen {
  hora: string
  cliente_id: number
  nombre: string
  foto_url: string | null
  metodo: string
}

export interface AsistenciasHoy {
  fecha: string
  total_entradas: number
  por_metodo: Record<string, number>
  ultimas_entradas: EntradaResumen[]
}

export interface EstadisticasDashboard {
  periodo: string
  total_entradas: number
  promedio_diario: number
  clientes_activos: number
  clientes_sin_asistir7d: number
  clientes_sin_asistir15d: number
  dia_mas_concurrido: string | null
  entradas_dia_mas_concurrido: number
  hora_pico: string
}

// ── Per-client history (staff access) ────────────────────────────────────────

export interface Ultimos30DiasAdminResult {
  cliente_id: number
  dias_asistidos: number
  dias_ausente: number
  racha_actual: number
  racha_maxima_mes: number
  detalle: Array<{ fecha: string; asistio: boolean }>
}

export interface AsistenciaAdminItem {
  id: number
  fecha: string
  hora_entrada: string
  metodo_registro: string
}

export interface HistorialAsistenciasResponse {
  cliente: string | null
  total_en_periodo: number
  asistencias: AsistenciaAdminItem[]
}

export interface RachaPerfectaResult {
  racha_perfecta: boolean
  dias_asistidos: number
  dias_con_membresia: number
}

// ─────────────────────────────────────────────────────────────────────────────

async function getAsistenciasHoy(idSucursal?: number): Promise<AsistenciasHoy> {
  const params = idSucursal ? { idSucursal } : undefined
  const { data } = await attendanceApi.get<AsistenciasHoy>('/asistencias/hoy', { params })
  return data
}

async function getEstadisticas(params?: {
  periodo?: 'mes' | 'anio'
  anio?: number
  mes?: number
}): Promise<EstadisticasDashboard> {
  const { data } = await attendanceApi.get<EstadisticasDashboard>('/asistencias/estadisticas', { params })
  return data
}

async function getAsistenciasUltimos30(idCliente: number): Promise<Ultimos30DiasAdminResult> {
  const { data } = await attendanceApi.get<Ultimos30DiasAdminResult>(`/clientes/${idCliente}/asistencias/ultimos-30`)
  return data
}

async function getHistorialCliente(
  idCliente: number,
  params?: { desde?: string; hasta?: string },
): Promise<HistorialAsistenciasResponse> {
  const { data } = await attendanceApi.get<HistorialAsistenciasResponse>(
    `/clientes/${idCliente}/asistencias`,
    { params },
  )
  return data
}

async function getRachaPerfecta(idCliente: number): Promise<RachaPerfectaResult> {
  const { data } = await attendanceApi.get<RachaPerfectaResult>(
    `/clientes/${idCliente}/asistencias/racha-perfecta`,
  )
  return data
}

async function registrarManual(idCliente: number): Promise<void> {
  await attendanceApi.post('/asistencias/manual', { id_cliente: idCliente })
}

export const attendanceRepository = {
  getAsistenciasHoy,
  getEstadisticas,
  getAsistenciasUltimos30,
  getHistorialCliente,
  getRachaPerfecta,
  registrarManual,
}
