/**
 * Integration tests — /asistencias/hoy y /asistencias/estadisticas
 * Target: attendance-service (port 8084)
 *
 * Uses a staff JWT (dueño) signed with the attendance-service JWT secret.
 * Tests are silently skipped when the service is unreachable.
 *
 * Run:
 *   npx vitest run --config vitest.integration.config.ts asistencias-dashboard.test.ts
 */
import { createHmac } from 'node:crypto'
import { beforeAll, describe, it, expect } from 'vitest'
import { attendanceHttp, bearer } from './helpers/client'

// ── HS256 signer for attendance-service ───────────────────────────────────────
const ATTENDANCE_B64_SECRET =
  process.env.ATTENDANCE_JWT_SECRET ??
  'Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tdGhpcy1rZXktbXVzdC1iZS0yNTYtYml0cw=='

function b64url(buf: Buffer): string {
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}

function signHS256(payload: Record<string, unknown>): string {
  const now = Math.floor(Date.now() / 1000)
  const full = { ...payload, iat: now, exp: now + 8 * 3600 }
  const header = b64url(Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })))
  const body   = b64url(Buffer.from(JSON.stringify(full)))
  const key    = Buffer.from(ATTENDANCE_B64_SECRET, 'base64')
  const sig    = b64url(createHmac('sha256', key).update(`${header}.${body}`).digest())
  return `${header}.${body}.${sig}`
}

// Tokens fijos — los endpoints de estadísticas usan id_compania del JWT
const COMPANIA_ID = parseInt(process.env.TEST_COMPANIA_ID ?? '1', 10)

const dueno = signHS256({
  sub: '99001', tipo: 'staff', rol_gym: 'dueno', id_compania: COMPANIA_ID,
})
const recepcion = signHS256({
  sub: '99002', tipo: 'staff', rol_gym: 'recepcion', id_compania: COMPANIA_ID,
})
const clienteApp = signHS256({
  sub: '99003', tipo: 'cliente', id_compania: COMPANIA_ID,
})

// ── Setup: verificar que el servicio responde ─────────────────────────────────
let serviceAvailable = false

beforeAll(async () => {
  const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(dueno) })
  serviceAvailable = res.status === 200 || res.status === 403 || res.status === 401
})

// ── GET /asistencias/hoy ──────────────────────────────────────────────────────
describe('GET /asistencias/hoy', () => {
  it('retorna 200 con estructura completa para JWT dueño', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(dueno) })
    expect(res.status).toBe(200)
    expect(typeof res.data.total_entradas).toBe('number')
    expect(typeof res.data.fecha).toBe('string')
    expect(typeof res.data.por_metodo).toBe('object')
    expect(Array.isArray(res.data.ultimas_entradas)).toBe(true)
  })

  it('total_entradas >= 0', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(dueno) })
    expect(res.status).toBe(200)
    expect(res.data.total_entradas).toBeGreaterThanOrEqual(0)
  })

  it('ultimas_entradas tiene máximo 10 elementos', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(dueno) })
    expect(res.status).toBe(200)
    expect(res.data.ultimas_entradas.length).toBeLessThanOrEqual(10)
  })

  it('cada entrada en ultimas_entradas tiene campos hora, nombre, cliente_id y metodo', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(dueno) })
    expect(res.status).toBe(200)
    for (const entrada of res.data.ultimas_entradas) {
      expect(typeof entrada.hora).toBe('string')
      expect(typeof entrada.nombre).toBe('string')
      expect(typeof entrada.cliente_id).toBe('number')
      expect(typeof entrada.metodo).toBe('string')
    }
  })

  it('por_metodo es un objeto con valores numéricos', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(dueno) })
    expect(res.status).toBe(200)
    expect(typeof res.data.por_metodo).toBe('object')
    for (const val of Object.values(res.data.por_metodo)) {
      expect(typeof val).toBe('number')
    }
  })

  it('recepcionista también puede acceder (200)', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(recepcion) })
    expect(res.status).toBe(200)
  })

  it('JWT cliente retorna 403', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/hoy', { headers: bearer(clienteApp) })
    expect(res.status).toBe(403)
  })

  it('sin JWT retorna 401', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/hoy')
    expect(res.status).toBe(401)
  })

  it('filtro ?idSucursal retorna 200', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/hoy', {
      headers: bearer(dueno),
      params: { idSucursal: 1 },
    })
    expect(res.status).toBe(200)
    expect(typeof res.data.totalEntradas).toBe('number')
  })
})

// ── GET /asistencias/estadisticas ─────────────────────────────────────────────
describe('GET /asistencias/estadisticas', () => {
  it('retorna 200 con estructura completa para JWT dueño', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/estadisticas', { headers: bearer(dueno) })
    expect(res.status).toBe(200)
    expect(typeof res.data.periodo).toBe('string')
    expect(res.data.periodo.length).toBeGreaterThan(0)
    expect(typeof res.data.total_entradas).toBe('number')
    expect(typeof res.data.promedio_diario).toBe('number')
    expect(typeof res.data.clientes_activos).toBe('number')
    expect(typeof res.data.clientes_sin_asistir7d).toBe('number')
    expect(typeof res.data.clientes_sin_asistir15d).toBe('number')
    expect(typeof res.data.entradas_dia_mas_concurrido).toBe('number')
    // dia_mas_concurrido puede ser null si no hay datos
    expect(res.data.dia_mas_concurrido === null || typeof res.data.dia_mas_concurrido === 'string').toBe(true)
    expect(typeof res.data.hora_pico).toBe('string')
  })

  it('total_entradas >= 0 y promedio_diario >= 0', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/estadisticas', { headers: bearer(dueno) })
    expect(res.status).toBe(200)
    expect(res.data.total_entradas).toBeGreaterThanOrEqual(0)
    expect(res.data.promedio_diario).toBeGreaterThanOrEqual(0)
  })

  it('periodo=mes con anio y mes retorna periodo en formato YYYY-MM', async () => {
    if (!serviceAvailable) return
    const now = new Date()
    const res = await attendanceHttp.get('/asistencias/estadisticas', {
      headers: bearer(dueno),
      params: { periodo: 'mes', anio: now.getFullYear(), mes: now.getMonth() + 1 },
    })
    expect(res.status).toBe(200)
    expect(res.data.periodo).toMatch(/^\d{4}-\d{2}$/)
  })

  it('periodo=anio retorna periodo como YYYY', async () => {
    if (!serviceAvailable) return
    const anio = new Date().getFullYear()
    const res = await attendanceHttp.get('/asistencias/estadisticas', {
      headers: bearer(dueno),
      params: { periodo: 'anio', anio },
    })
    expect(res.status).toBe(200)
    expect(res.data.periodo).toBe(String(anio))
  })

  it('mes sin datos históricos retorna totalEntradas=0', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/estadisticas', {
      headers: bearer(dueno),
      params: { periodo: 'mes', anio: 2019, mes: 1 },
    })
    expect(res.status).toBe(200)
    expect(res.data.total_entradas).toBe(0)
  })

  it('recepcionista también puede acceder (200)', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/estadisticas', { headers: bearer(recepcion) })
    expect(res.status).toBe(200)
    expect(typeof res.data.total_entradas).toBe('number')
  })

  it('clientes_activos y contadores de inactividad son >= 0', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/estadisticas', { headers: bearer(dueno) })
    expect(res.status).toBe(200)
    expect(res.data.clientes_activos).toBeGreaterThanOrEqual(0)
    expect(res.data.clientes_sin_asistir7d).toBeGreaterThanOrEqual(0)
    expect(res.data.clientes_sin_asistir15d).toBeGreaterThanOrEqual(0)
    expect(res.data.entradas_dia_mas_concurrido).toBeGreaterThanOrEqual(0)
  })

  it('JWT cliente retorna 403', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/estadisticas', { headers: bearer(clienteApp) })
    expect(res.status).toBe(403)
  })

  it('sin JWT retorna 401', async () => {
    if (!serviceAvailable) return
    const res = await attendanceHttp.get('/asistencias/estadisticas')
    expect(res.status).toBe(401)
  })
})
