/**
 * Integration tests — Attendance Service endpoints (port 8084)
 *
 * Hits the real running attendance-service. The service must be up before
 * running these tests (npm run test:integration).
 *
 * JWT_SECRET env var must match the secret the attendance-service was started
 * with (same .env used by the backend Java integration tests works fine).
 *
 * Token roles used:
 *   attendanceDuenoToken     → tipo=staff, rol_gym=dueno    — requireDueno, requireStaff, requireStaffOrPlataforma
 *   attendanceRecepcionToken → tipo=staff, rol_gym=recepcion — requireStaff, requireStaffOrPlataforma; blocked by requireDueno
 *   attendanceEntrenadorToken→ tipo=staff, rol_gym=entrenador — requireStaff; blocked by requireNotEntrenador
 *   attendanceClienteToken   → tipo=cliente                  — requireCliente; blocked by requireStaff
 *   attendancePlataformaToken→ tipo=plataforma, rol_plataforma=super_admin — requireStaffOrPlataforma, requireDuenoOrPlataforma
 *
 * id_compania=1 is assumed to exist in the running database (matches COMPANIA=1 in Java tests).
 */
import { describe, it, expect } from 'vitest'
import { attendanceHttp, bearer } from './helpers/client'
import {
  attendanceDuenoToken,
  attendanceRecepcionToken,
  attendanceEntrenadorToken,
  attendanceClienteToken,
  attendancePlataformaToken,
} from './helpers/jwt'

const DUENO    = attendanceDuenoToken(99991, 1)
const RECEP    = attendanceRecepcionToken(99992, 1)
const ENTRENA  = attendanceEntrenadorToken(99993, 1)
const CLIENTE  = attendanceClienteToken(99994, 1)
const PLATFORM = attendancePlataformaToken(99990)

// ──────────────────────────────────────────────────────────────────────────────
// Auth guard — every protected endpoint must 401 without token
// ──────────────────────────────────────────────────────────────────────────────

describe('Authentication guard', () => {
  it('GET /asistencias/hoy → 401 without token', async () => {
    const res = await attendanceHttp.get('/asistencias/hoy')
    expect(res.status).toBe(401)
  })

  it('GET /asistencias/estadisticas → 401 without token', async () => {
    const res = await attendanceHttp.get('/asistencias/estadisticas')
    expect(res.status).toBe(401)
  })

  it('GET /clientes/1/asistencias → 401 without token', async () => {
    const res = await attendanceHttp.get('/clientes/1/asistencias')
    expect(res.status).toBe(401)
  })

  it('GET /plantillas → 401 without token', async () => {
    const res = await attendanceHttp.get('/plantillas')
    expect(res.status).toBe(401)
  })

  it('GET /mensajes → 401 without token', async () => {
    const res = await attendanceHttp.get('/mensajes')
    expect(res.status).toBe(401)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Dashboard del día — GET /asistencias/hoy
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /asistencias/hoy', () => {
  it('returns 200 with expected shape for dueno token', async () => {
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(DUENO) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('fecha')
    expect(res.data).toHaveProperty('total_entradas')
    expect(res.data).toHaveProperty('por_metodo')
    expect(res.data).toHaveProperty('ultimas_entradas')
    expect(Array.isArray(res.data.ultimas_entradas)).toBe(true)
  })

  it('returns 200 for recepcion token', async () => {
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(RECEP) })
    expect(res.status).toBe(200)
  })

  it('returns 200 for plataforma token', async () => {
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(PLATFORM) })
    expect(res.status).toBe(200)
  })

  it('returns 403 for cliente token (requireStaffOrPlataforma)', async () => {
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(CLIENTE) })
    expect(res.status).toBe(403)
  })

  it('accepts optional idSucursal param', async () => {
    const res = await attendanceHttp.get('/asistencias/hoy', {
      params: { idSucursal: 1 },
      headers: bearer(DUENO),
    })
    expect(res.status).toBe(200)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Estadísticas KPI — GET /asistencias/estadisticas
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /asistencias/estadisticas', () => {
  it('returns 200 with KPI shape for dueno token', async () => {
    const res = await attendanceHttp.get('/asistencias/estadisticas', { headers: bearer(DUENO) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('periodo')
    expect(res.data).toHaveProperty('total_entradas')
    expect(res.data).toHaveProperty('promedio_diario')
    expect(res.data).toHaveProperty('clientes_activos')
  })

  it('returns 200 for recepcion token', async () => {
    const res = await attendanceHttp.get('/asistencias/estadisticas', { headers: bearer(RECEP) })
    expect(res.status).toBe(200)
  })

  it('accepts periodo, anio, mes query params', async () => {
    const now = new Date()
    const res = await attendanceHttp.get('/asistencias/estadisticas', {
      params: { periodo: 'mes', anio: now.getFullYear(), mes: now.getMonth() + 1 },
      headers: bearer(DUENO),
    })
    expect(res.status).toBe(200)
  })

  it('returns 403 for cliente token', async () => {
    const res = await attendanceHttp.get('/asistencias/estadisticas', { headers: bearer(CLIENTE) })
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Historial cliente — GET /clientes/{id}/asistencias
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /clientes/{id}/asistencias', () => {
  it('returns 200 with paginated shape for staff token (non-existent client → empty list)', async () => {
    const res = await attendanceHttp.get('/clientes/999999/asistencias', { headers: bearer(DUENO) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('cliente')
    expect(res.data).toHaveProperty('total_en_periodo')
    expect(res.data).toHaveProperty('asistencias')
    expect(Array.isArray(res.data.asistencias)).toBe(true)
    expect(res.data.total_en_periodo).toBe(0)
  })

  it('returns 200 for recepcion token', async () => {
    const res = await attendanceHttp.get('/clientes/999999/asistencias', { headers: bearer(RECEP) })
    expect(res.status).toBe(200)
  })

  it('accepts date range params', async () => {
    const today = new Date().toISOString().split('T')[0]
    const res = await attendanceHttp.get('/clientes/999999/asistencias', {
      params: { desde: today, hasta: today },
      headers: bearer(DUENO),
    })
    expect(res.status).toBe(200)
  })

  it('cliente token can view their own attendance (sub matches path id)', async () => {
    // CLIENTE token has sub=99994, so requesting /clientes/99994/asistencias is allowed
    const res = await attendanceHttp.get('/clientes/99994/asistencias', { headers: bearer(CLIENTE) })
    expect(res.status).toBe(200)
  })

  it('cliente token is 403 when requesting another client\'s attendance', async () => {
    // CLIENTE token has sub=99994, requesting a different id → forbidden
    const res = await attendanceHttp.get('/clientes/1/asistencias', { headers: bearer(CLIENTE) })
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Últimos 30 días — GET /clientes/{id}/asistencias/ultimos-30
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /clientes/{id}/asistencias/ultimos-30', () => {
  it('returns 200 with heat-map shape for staff token', async () => {
    const res = await attendanceHttp.get('/clientes/999999/asistencias/ultimos-30', { headers: bearer(DUENO) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('clienteId')
    expect(res.data).toHaveProperty('diasAsistidos')
    expect(res.data).toHaveProperty('diasAusente')
    expect(res.data).toHaveProperty('rachaActual')
    expect(res.data).toHaveProperty('rachaMaximaMes')
    expect(res.data).toHaveProperty('detalle')
    expect(Array.isArray(res.data.detalle)).toBe(true)
  })

  it('cliente can view their own ultimos-30', async () => {
    const res = await attendanceHttp.get('/clientes/99994/asistencias/ultimos-30', { headers: bearer(CLIENTE) })
    expect(res.status).toBe(200)
  })

  it('cliente is 403 viewing another client\'s ultimos-30', async () => {
    const res = await attendanceHttp.get('/clientes/1/asistencias/ultimos-30', { headers: bearer(CLIENTE) })
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Racha perfecta — GET /clientes/{id}/asistencias/racha-perfecta
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /clientes/{id}/asistencias/racha-perfecta', () => {
  it('returns 200 with racha shape for staff token', async () => {
    const res = await attendanceHttp.get('/clientes/999999/asistencias/racha-perfecta', { headers: bearer(DUENO) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('rachaPerfecta')
    expect(res.data).toHaveProperty('diasAsistidos')
    expect(res.data).toHaveProperty('diasConMembresia')
    expect(typeof res.data.rachaPerfecta).toBe('boolean')
  })

  it('accepts meses param', async () => {
    const res = await attendanceHttp.get('/clientes/999999/asistencias/racha-perfecta', {
      params: { meses: 3 },
      headers: bearer(DUENO),
    })
    expect(res.status).toBe(200)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Registro manual — POST /asistencias/manual
// ──────────────────────────────────────────────────────────────────────────────

describe('POST /asistencias/manual', () => {
  it('returns 400 when body is missing required id_cliente field', async () => {
    const res = await attendanceHttp.post('/asistencias/manual', {}, { headers: bearer(RECEP) })
    expect(res.status).toBe(400)
  })

  it('returns 403 for cliente token (requireStaff)', async () => {
    const res = await attendanceHttp.post('/asistencias/manual',
      { idCliente: 1 },
      { headers: bearer(CLIENTE) },
    )
    expect(res.status).toBe(403)
  })

  it('returns 403 for entrenador token (requireNotEntrenador)', async () => {
    const res = await attendanceHttp.post('/asistencias/manual',
      { idCliente: 1 },
      { headers: bearer(ENTRENA) },
    )
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Override — POST /asistencias/manual/override
// ──────────────────────────────────────────────────────────────────────────────

describe('POST /asistencias/manual/override', () => {
  it('returns 400 when motivo_override is missing', async () => {
    const res = await attendanceHttp.post('/asistencias/manual/override',
      { idCliente: 1 },
      { headers: bearer(DUENO) },
    )
    expect(res.status).toBe(400)
  })

  it('returns 403 for recepcion token (requireDueno)', async () => {
    const res = await attendanceHttp.post('/asistencias/manual/override',
      { idCliente: 1, motivoOverride: 'test' },
      { headers: bearer(RECEP) },
    )
    expect(res.status).toBe(403)
  })

  it('returns 403 for cliente token', async () => {
    const res = await attendanceHttp.post('/asistencias/manual/override',
      { idCliente: 1, motivoOverride: 'test' },
      { headers: bearer(CLIENTE) },
    )
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// QR scan — POST /asistencias/qr
// ──────────────────────────────────────────────────────────────────────────────

describe('POST /asistencias/qr', () => {
  it('returns 400 when qr_token is missing', async () => {
    const res = await attendanceHttp.post('/asistencias/qr', {}, { headers: bearer(CLIENTE) })
    expect(res.status).toBe(400)
  })

  it('returns 403 for staff token (requireCliente)', async () => {
    const res = await attendanceHttp.post('/asistencias/qr',
      { qrToken: 'dummy-token' },
      { headers: bearer(DUENO) },
    )
    expect(res.status).toBe(403)
  })

  it('returns 403 for recepcion token (requireCliente)', async () => {
    const res = await attendanceHttp.post('/asistencias/qr',
      { qrToken: 'dummy-token' },
      { headers: bearer(RECEP) },
    )
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Plantillas — GET /plantillas
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /plantillas', () => {
  it('returns 200 with array for dueno token', async () => {
    const res = await attendanceHttp.get('/plantillas', { headers: bearer(DUENO) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 200 for recepcion token', async () => {
    const res = await attendanceHttp.get('/plantillas', { headers: bearer(RECEP) })
    expect(res.status).toBe(200)
  })

  it('returns 200 for plataforma token', async () => {
    const res = await attendanceHttp.get('/plantillas', { headers: bearer(PLATFORM) })
    expect(res.status).toBe(200)
  })

  it('returns 403 for cliente token', async () => {
    const res = await attendanceHttp.get('/plantillas', { headers: bearer(CLIENTE) })
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Plantillas — POST /plantillas
// ──────────────────────────────────────────────────────────────────────────────

describe('POST /plantillas', () => {
  it('returns 400 when required fields are missing', async () => {
    const res = await attendanceHttp.post('/plantillas', {}, { headers: bearer(DUENO) })
    expect(res.status).toBe(400)
  })

  it('returns 403 for recepcion token (requireDuenoOrPlataforma)', async () => {
    const res = await attendanceHttp.post('/plantillas',
      { tipo: 'ausencia', nombre: 'Test', contenido: 'Hola {nombre}' },
      { headers: bearer(RECEP) },
    )
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Plantillas — PUT /plantillas/{id}
// ──────────────────────────────────────────────────────────────────────────────

describe('PUT /plantillas/{id}', () => {
  it('returns 404 for non-existent plantilla', async () => {
    const res = await attendanceHttp.put('/plantillas/999999',
      { contenido: 'Nuevo contenido', activo: true },
      { headers: bearer(DUENO) },
    )
    expect(res.status).toBe(404)
  })

  it('returns 403 for recepcion token', async () => {
    const res = await attendanceHttp.put('/plantillas/999999',
      { contenido: 'Nuevo contenido' },
      { headers: bearer(RECEP) },
    )
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Plantillas — DELETE /plantillas/{id}
// ──────────────────────────────────────────────────────────────────────────────

describe('DELETE /plantillas/{id}', () => {
  it('returns 404 for non-existent plantilla', async () => {
    const res = await attendanceHttp.delete('/plantillas/999999', { headers: bearer(DUENO) })
    expect(res.status).toBe(404)
  })

  it('returns 403 for recepcion token', async () => {
    const res = await attendanceHttp.delete('/plantillas/999999', { headers: bearer(RECEP) })
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Mensajes — GET /mensajes
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /mensajes', () => {
  it('returns 200 with {total, datos} shape for staff token', async () => {
    const res = await attendanceHttp.get('/mensajes', { headers: bearer(DUENO) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('total')
    expect(res.data).toHaveProperty('datos')
    expect(Array.isArray(res.data.datos)).toBe(true)
    expect(typeof res.data.total).toBe('number')
  })

  it('returns 200 for recepcion token', async () => {
    const res = await attendanceHttp.get('/mensajes', { headers: bearer(RECEP) })
    expect(res.status).toBe(200)
  })

  it('returns 200 for plataforma token', async () => {
    const res = await attendanceHttp.get('/mensajes', { headers: bearer(PLATFORM) })
    expect(res.status).toBe(200)
  })

  it('returns 403 for cliente token', async () => {
    const res = await attendanceHttp.get('/mensajes', { headers: bearer(CLIENTE) })
    expect(res.status).toBe(403)
  })

  it('accepts optional filter params', async () => {
    const today = new Date().toISOString().split('T')[0]
    const res = await attendanceHttp.get('/mensajes', {
      params: { estado: 'enviado', desde: today },
      headers: bearer(DUENO),
    })
    expect(res.status).toBe(200)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Mensajes — POST /mensajes/enviar
// ──────────────────────────────────────────────────────────────────────────────

describe('POST /mensajes/enviar', () => {
  it('returns 400 when required fields are missing', async () => {
    const res = await attendanceHttp.post('/mensajes/enviar', {}, { headers: bearer(DUENO) })
    expect(res.status).toBe(400)
  })

  it('returns 403 for cliente token', async () => {
    const res = await attendanceHttp.post('/mensajes/enviar',
      { idCliente: 1, canal: 'whatsapp', idPlantilla: 1 },
      { headers: bearer(CLIENTE) },
    )
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Mensajes — POST /mensajes/reenviar/{id}
// ──────────────────────────────────────────────────────────────────────────────

describe('POST /mensajes/reenviar/{id}', () => {
  it('returns 404 for non-existent mensaje', async () => {
    const res = await attendanceHttp.post('/mensajes/reenviar/999999', {}, { headers: bearer(DUENO) })
    expect(res.status).toBe(404)
  })

  it('returns 403 for cliente token', async () => {
    const res = await attendanceHttp.post('/mensajes/reenviar/999999', {}, { headers: bearer(CLIENTE) })
    expect(res.status).toBe(403)
  })
})
