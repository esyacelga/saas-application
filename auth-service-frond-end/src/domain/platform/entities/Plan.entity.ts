export interface Caracteristica {
  id: number
  codigo: string
  nombre: string
  modulo: string
  activo: boolean
}

export interface Plan {
  id: number
  codigo: string
  nombre: string
  descripcion: string
  precioMensual: number
  activo: boolean
  caracteristicas: Caracteristica[]
}

export interface PlanActivo {
  nombre: string
  estado: 'ACTIVO' | 'EN_GRACIA' | 'VENCIDO' | 'PROGRAMADO' | 'CANCELADO' | 'SUSPENDIDO'
  fechaFin: string
  diasRestantes: number
}

export interface Compania {
  id: number
  nombre: string
  ruc: string
  telefono: string
  whatsapp: string
  correo: string
  logoUrl: string | null
  activo: boolean
  planActivo: PlanActivo | null
}

export interface Sucursal {
  id: number
  idCompania: number
  nombre: string
  direccion: string
  esPrincipal: boolean
  activo: boolean
  qrToken: string
  qrTokenExpira: string | null
}

export interface CompaniaPlan {
  id: number
  idPlan: number
  estado: string
  fechaInicio: string
  fechaFin: string
  diasRestantes: number
  diasGracia: number
  tipoCambio: string
}

export interface Pago {
  id: number
  idCompaniaPlan: number
  monto: number
  fechaPago: string
  periodoDesde: string | null
  periodoHasta: string | null
  metodoPago: string
  tipoPago: string
  estado: 'PENDIENTE' | 'PAGADO' | 'FALLIDO'
  referencia: string | null
}

export interface NotifConfig {
  idCompania: number
  diasAntes: number
  canal: 'EMAIL' | 'WHATSAPP' | 'AMBOS'
  activo: boolean
}

// REQ-SAAS-001: Uso y límites del plan — respuesta de GET /companias/{id}/uso-limites
export interface UsoLimitesRecurso {
  actual: number
  maximo: number | null
}

export interface UsoLimitesResponse {
  planCodigo: 'FREE' | 'TRIAL' | 'PREMIUM' | 'LEGACY_GRANDFATHERED'
  sucursales: UsoLimitesRecurso
  clientesActivos: UsoLimitesRecurso
  staff: UsoLimitesRecurso
  sobreLimite: boolean
  sobreLimiteHasta: string | null   // LocalDate ISO, ej: "2026-01-15"
  diasRestantes: number | null      // solo para TRIAL; puede ser negativo si venció
}

// REQ-SAAS-001 Sub-fase 1.6: Pagos reportados por el propio tenant (owner)
// Espeja PagoPendienteResponse.java del platform-service
export interface PagoPendienteResponse {
  id: number
  idCompania: number
  nombreCompania: string | null   // REQ-SAAS-001 ítem #4: nuevo campo del backend
  idPlanDestino: number
  monto: number
  moneda: string
  fechaReporte: string           // Instant → ISO string
  fechaTransferencia: string | null  // LocalDate → ISO string
  comprobanteUrl: string | null
  bancoOrigen: string | null
  referencia: string | null
  estado: 'PENDIENTE' | 'RECHAZADO' | 'APROBADO' | 'PAGADO' | 'FALLIDO'
  motivoRechazo: string | null
  aprobadoPor: number | null
  fechaAprobacion: string | null  // Instant → ISO string
  activacionProgramada: boolean
}
