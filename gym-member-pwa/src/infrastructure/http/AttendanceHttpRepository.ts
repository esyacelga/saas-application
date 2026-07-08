import attendanceApi from './attendance.instance'

export interface CheckInResult {
  id_asistencia: number
  fecha: string
  hora_entrada: string
  sucursal: string
  tipo_membresia: string
  modo_control: string
  accesos_usados: number | null
  accesos_restantes: number | null
  fecha_fin: string
}

export interface DiaDetalle {
  fecha: string
  asistio: boolean
  hora: string | null
}

export interface Ultimos30DiasResult {
  cliente_id: number
  dias_asistidos: number
  dias_ausente: number
  racha_actual: number
  racha_maxima_mes: number
  detalle: DiaDetalle[]
}

export interface AsistenciaItem {
  id: number
  fecha: string
  hora_entrada: string
  metodo_registro: string
}

export interface HistorialResponse {
  total_en_periodo: number
  asistencias: AsistenciaItem[]
}

class AttendanceHttpRepository {
  async checkInQr(qrToken: string): Promise<CheckInResult> {
    const { data } = await attendanceApi.post<CheckInResult>('/asistencias/qr', {
      qr_token: qrToken,
    })
    return data
  }

  async checkInApp(idSucursal: number, nombreSucursal: string | null): Promise<CheckInResult> {
    const { data } = await attendanceApi.post<CheckInResult>('/asistencias/app', {
      id_sucursal: idSucursal,
      nombre_sucursal: nombreSucursal,
    })
    return data
  }

  async ultimos30Dias(): Promise<Ultimos30DiasResult> {
    const { data } = await attendanceApi.get<Ultimos30DiasResult>('/asistencias/me/ultimos-30')
    return data
  }

  async historial(params?: { desde?: string; hasta?: string }): Promise<HistorialResponse> {
    const { data } = await attendanceApi.get<HistorialResponse>('/asistencias/me', { params })
    return data
  }
}

export const attendanceRepository = new AttendanceHttpRepository()
