/**
 * Integration tests — Platform module endpoints
 * Target: platform-service on port 8081 (VITE_API_PLATFORM_URL / TEST_PLATFORM_URL)
 *
 * Tokens are signed with HS256 using the base64-decoded bytes of the JWT secret.
 * The platform-service now shares the same base64 secret as the auth-service.
 *
 * Run with:
 *   TEST_PLATFORM_URL=http://localhost:8081/api/v1 npx vitest run platform-module.test.ts
 */
import { createHmac } from 'node:crypto'
import { afterAll, describe, it, expect } from 'vitest'
import { platformHttp, bearer } from './helpers/client'

// ── JWT helpers ──────────────────────────────────────────────────────────────
const PLATFORM_B64_SECRET =
  process.env.PLATFORM_JWT_SECRET ??
  'Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tdGhpcy1rZXktbXVzdC1iZS0yNTYtYml0cw=='

function b64url(buf: Buffer): string {
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}

function signHS256(payload: Record<string, unknown>): string {
  const now = Math.floor(Date.now() / 1000)
  const full = { ...payload, iat: now, exp: now + 8 * 3600 }
  const header = b64url(Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })))
  const body = b64url(Buffer.from(JSON.stringify(full)))
  const key = Buffer.from(PLATFORM_B64_SECRET, 'base64')
  const sig = b64url(createHmac('sha256', key).update(`${header}.${body}`).digest())
  return `${header}.${body}.${sig}`
}

const PT      = signHS256({ sub: '99990', tipo: 'plataforma', rol_plataforma: 'super_admin', nombre: 'Test SA' })
const SOPORTE = signHS256({ sub: '99991', tipo: 'plataforma', rol_plataforma: 'soporte',     nombre: 'Test Soporte' })
const VIEWER  = signHS256({ sub: '99992', tipo: 'plataforma', rol_plataforma: 'viewer',      nombre: 'Test Viewer' })
// Staff token — tipo:staff, should be rejected (403) on platform-only endpoints
const STAFF   = signHS256({ sub: '99993', tipo: 'staff', id_compania: 99999, id_sucursal: 99999, id_rol: 1, nombre: 'Test Staff', permisos: [] })

const TS = Date.now()

// ── Cleanup state ────────────────────────────────────────────────────────────
let createdPlanId: number | null = null
let createdCaracteristicaId: number | null = null
let createdCompaniaId: number | null = null
let createdSucursalId: number | null = null
let createdSuscripcionId: number | null = null
let createdPagoId: number | null = null

afterAll(async () => {
  if (createdCompaniaId) {
    await platformHttp.put(`/companias/${createdCompaniaId}/suspender`,
      { motivo: 'Cleanup integration test' }, { headers: bearer(PT) }).catch(() => {})
  }
  if (createdPlanId) {
    await platformHttp.put(`/planes/${createdPlanId}/desactivar`, {}, { headers: bearer(PT) }).catch(() => {})
  }
})

// ────────────────────────────────────────────────────────────────────────────
// PLANES
// ────────────────────────────────────────────────────────────────────────────
describe('GET /planes', () => {
  it('returns 200 with array for super_admin token', async () => {
    const res = await platformHttp.get('/planes', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 200 for soporte token', async () => {
    const res = await platformHttp.get('/planes', { headers: bearer(SOPORTE) })
    expect(res.status).toBe(200)
  })

  it('returns 200 for viewer token', async () => {
    const res = await platformHttp.get('/planes', { headers: bearer(VIEWER) })
    expect(res.status).toBe(200)
  })

  it('returns 403 for staff token (tipo:staff is rejected by requirePlataforma)', async () => {
    const res = await platformHttp.get('/planes', { headers: bearer(STAFF) })
    expect(res.status).toBe(403)
  })

  it('returns 401 without token', async () => {
    const res = await platformHttp.get('/planes')
    expect(res.status).toBe(401)
  })
})

describe('POST /planes', () => {
  it('returns 403 for soporte (only super_admin can create plans)', async () => {
    const res = await platformHttp.post('/planes',
      { nombre: `Plan Soporte ${TS}`, descripcion: '', precioMensual: 10 },
      { headers: bearer(SOPORTE) })
    expect(res.status).toBe(403)
  })

  it('returns 201 and creates a plan for super_admin', async () => {
    const res = await platformHttp.post('/planes',
      { nombre: `Plan IT ${TS}`, descripcion: 'Test plan', precioMensual: 29.99 },
      { headers: bearer(PT) })
    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('id')
    expect(res.data.nombre).toContain(`Plan IT ${TS}`)
    // backend devuelve camelCase
    expect(res.data.precioMensual ?? res.data.precio_mensual).toBeTruthy()
    createdPlanId = res.data.id as number
  })

  it('returns 400 when body is missing required fields', async () => {
    const res = await platformHttp.post('/planes', {}, { headers: bearer(PT) })
    expect(res.status).toBe(400)
  })
})

describe('PUT /planes/{id}', () => {
  it('returns 404 for non-existent plan', async () => {
    const res = await platformHttp.put('/planes/999999',
      { nombre: 'X', descripcion: '', precioMensual: 1 }, { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('updates plan and returns 200 (skips if no plan created)', async () => {
    if (!createdPlanId) return
    const res = await platformHttp.put(`/planes/${createdPlanId}`,
      { nombre: `Plan IT Updated ${TS}`, descripcion: 'updated', precioMensual: 39.99 },
      { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data.nombre).toContain('Updated')
  })
})

// ────────────────────────────────────────────────────────────────────────────
// CARACTERÍSTICAS
// ────────────────────────────────────────────────────────────────────────────
describe('GET /caracteristicas', () => {
  it('returns 200 with array for platform token', async () => {
    const res = await platformHttp.get('/caracteristicas', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 403 for staff token', async () => {
    const res = await platformHttp.get('/caracteristicas', { headers: bearer(STAFF) })
    expect(res.status).toBe(403)
  })
})

describe('POST /caracteristicas', () => {
  it('returns 201 and creates a caracteristica for super_admin', async () => {
    const codigo = `it_test_${TS}`
    const res = await platformHttp.post('/caracteristicas',
      { codigo, nombre: `IT Test Feature ${TS}`, modulo: 'socios' },
      { headers: bearer(PT) })
    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('id')
    expect(res.data.codigo).toBe(codigo)
    createdCaracteristicaId = res.data.id as number
  })

  it('returns 409 on duplicate código (skips if none created)', async () => {
    if (!createdCaracteristicaId) return
    const codigo = `it_test_${TS}`
    const res = await platformHttp.post('/caracteristicas',
      { codigo, nombre: 'Duplicate', modulo: 'socios' }, { headers: bearer(PT) })
    expect(res.status).toBe(409)
  })

  it('returns 403 for soporte when creating caracteristica (only super_admin)', async () => {
    const res = await platformHttp.post('/caracteristicas',
      { codigo: `soporte_${TS}`, nombre: 'X', modulo: 'socios' }, { headers: bearer(SOPORTE) })
    // Backend enforces super_admin for write operations on características
    expect(res.status).toBe(403)
  })
})

describe('PUT /planes/{id}/caracteristicas (skips if no plan/caracteristica)', () => {
  it('assigns caracteristicas to plan and returns 200', async () => {
    if (!createdPlanId || !createdCaracteristicaId) return
    const res = await platformHttp.put(`/planes/${createdPlanId}/caracteristicas`,
      { caracteristicaIds: [createdCaracteristicaId] },
      { headers: bearer(PT) })
    expect(res.status).toBe(200)
    const ids = (res.data.caracteristicas ?? []).map((c: { id: number }) => c.id)
    expect(ids).toContain(createdCaracteristicaId)
  })
})

// ────────────────────────────────────────────────────────────────────────────
// COMPAÑÍAS
// ────────────────────────────────────────────────────────────────────────────
describe('GET /companias', () => {
  it('returns 200 with array for platform token', async () => {
    const res = await platformHttp.get('/companias', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 403 for staff token', async () => {
    const res = await platformHttp.get('/companias', { headers: bearer(STAFF) })
    expect(res.status).toBe(403)
  })

  it('returns 401 without token', async () => {
    const res = await platformHttp.get('/companias')
    expect(res.status).toBe(401)
  })
})

describe('POST /companias (registrar gym)', () => {
  it('returns 201 with gym + QR token for super_admin (skips if no plan)', async () => {
    if (!createdPlanId) return
    const ruc = `${10000000000 + (TS % 1000000000)}`
    const res = await platformHttp.post('/companias', {
      nombre: `Gym IT Test ${TS}`,
      ruc,
      correo: `gym_it_${TS}@test.com`,
      idPlan: createdPlanId,
      nombreSucursal: 'Sede IT',
      direccionSucursal: 'Calle Test 123',
    }, { headers: bearer(PT) })
    expect(res.status).toBe(201)
    // backend responde con camelCase
    expect(res.data).toHaveProperty('idCompania')
    expect(res.data).toHaveProperty('qrToken')
    expect(typeof res.data.qrToken).toBe('string')
    createdCompaniaId = res.data.idCompania as number
    createdSucursalId = res.data.idSucursal as number
    createdSuscripcionId = res.data.idCompaniaPlan as number
  })

  it('returns 403 for soporte (only super_admin can register gym)', async () => {
    if (!createdPlanId) return
    const res = await platformHttp.post('/companias', {
      nombre: `Gym Soporte ${TS}`, ruc: `20000000000`, idPlan: createdPlanId, nombreSucursal: 'Sede',
    }, { headers: bearer(SOPORTE) })
    expect(res.status).toBe(403)
  })
})

describe('GET /companias/{id}', () => {
  it('returns 200 with company details (skips if no compania created)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}`, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('id')
    expect(res.data).toHaveProperty('nombre')
    expect(res.data).toHaveProperty('ruc')
  })

  it('returns 404 for non-existent company', async () => {
    const res = await platformHttp.get('/companias/999999', { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })
})

describe('PUT /companias/{id}', () => {
  it('updates company and returns 200 (skips if no compania)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.put(`/companias/${createdCompaniaId}`,
      { nombre: `Gym IT Updated ${TS}`, telefono: '+5930001234567' },
      { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data.nombre).toContain('Updated')
  })
})

// ────────────────────────────────────────────────────────────────────────────
// SUSCRIPCIÓN
// ────────────────────────────────────────────────────────────────────────────
describe('GET /companias/{id}/suscripcion', () => {
  it('returns 200 with active subscription (skips if no compania)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/suscripcion`, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('id')
    expect(res.data).toHaveProperty('estado')
    expect(res.data).toHaveProperty('fechaInicio')
    expect(res.data).toHaveProperty('fechaFin')
  })

  it('returns 200 for soporte (can read subscription)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/suscripcion`, { headers: bearer(SOPORTE) })
    expect(res.status).toBe(200)
  })

  it('returns 403 for staff token', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/suscripcion`, { headers: bearer(STAFF) })
    expect(res.status).toBe(403)
  })
})

describe('GET /companias/{id}/suscripcion/historial', () => {
  it('returns 200 with subscription history array (skips if no compania)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/suscripcion/historial`, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    expect(res.data.length).toBeGreaterThanOrEqual(1)
  })
})

// ────────────────────────────────────────────────────────────────────────────
// PAGOS
// ────────────────────────────────────────────────────────────────────────────
describe('GET /companias/{id}/pagos', () => {
  it('returns 200 with payments array (skips if no compania)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/pagos`, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 200 for soporte', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/pagos`, { headers: bearer(SOPORTE) })
    expect(res.status).toBe(200)
  })

  it('returns 403 for staff token', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/pagos`, { headers: bearer(STAFF) })
    expect(res.status).toBe(403)
  })
})

describe('POST /pagos', () => {
  it('registers a PENDIENTE payment and returns 201 (skips if no suscripcion)', async () => {
    if (!createdSuscripcionId) return
    const res = await platformHttp.post('/pagos', {
      idCompaniaPlan: createdSuscripcionId,
      monto: 29.99,
      metodoPago: 'transferencia',
      tipoPago: 'pago_completo',
      referencia: `REF-IT-${TS}`,
    }, { headers: bearer(PT) })
    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('id')
    expect(res.data.estado).toBe('PENDIENTE')
    createdPagoId = res.data.id as number
  })

  it('soporte can also register payment (skips if no suscripcion)', async () => {
    if (!createdSuscripcionId) return
    const res = await platformHttp.post('/pagos', {
      idCompaniaPlan: createdSuscripcionId,
      monto: 5.00,
      metodoPago: 'efectivo',
      tipoPago: 'pago_completo',
    }, { headers: bearer(SOPORTE) })
    expect([201, 400, 409]).toContain(res.status)
  })

  it('returns 400 when body is missing required fields', async () => {
    const res = await platformHttp.post('/pagos', {}, { headers: bearer(PT) })
    expect(res.status).toBe(400)
  })
})

describe('PUT /pagos/{id}/confirmar', () => {
  it('confirms PENDIENTE payment → PAGADO (skips if no pago)', async () => {
    if (!createdPagoId) return
    const res = await platformHttp.put(`/pagos/${createdPagoId}/confirmar`, {}, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data.estado).toBe('PAGADO')
  })

  it('returns 404 for non-existent payment', async () => {
    const res = await platformHttp.put('/pagos/999999/confirmar', {}, { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })
})

// ────────────────────────────────────────────────────────────────────────────
// SUCURSALES
// ────────────────────────────────────────────────────────────────────────────
describe('GET /companias/{id}/sucursales', () => {
  it('returns 200 with at least the principal branch (skips if no compania)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/sucursales`, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    expect(res.data.length).toBeGreaterThanOrEqual(1)
  })

  it('returns 403 for staff token', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/sucursales`, { headers: bearer(STAFF) })
    expect(res.status).toBe(403)
  })
})

describe('POST /companias/{id}/sucursales', () => {
  it('creates a new branch and returns 201 (skips if no compania)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.post(`/companias/${createdCompaniaId}/sucursales`,
      { nombre: `Sucursal IT ${TS}`, direccion: 'Calle Secundaria 456', esPrincipal: false },
      { headers: bearer(PT) })
    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('id')
    // backend returns camelCase
    expect(res.data).toHaveProperty('qrToken')
  })
})

describe('POST /sucursales/{id}/qr/renovar', () => {
  it('renews QR token and returns 200 with new token (skips if no sucursal)', async () => {
    if (!createdSucursalId) return
    const res = await platformHttp.post(`/sucursales/${createdSucursalId}/qr/renovar`,
      { expiresInHours: 24 }, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('qrToken')
    expect(typeof res.data.qrToken).toBe('string')
    expect(res.data.qrToken.length).toBeGreaterThan(10)
  })

  it('returns 403 for soporte (only super_admin can renew QR)', async () => {
    if (!createdSucursalId) return
    const res = await platformHttp.post(`/sucursales/${createdSucursalId}/qr/renovar`,
      {}, { headers: bearer(SOPORTE) })
    expect(res.status).toBe(403)
  })
})

// ────────────────────────────────────────────────────────────────────────────
// NOTIF CONFIG
// ────────────────────────────────────────────────────────────────────────────
describe('GET /companias/{id}/notif-config', () => {
  it('returns 200 with notification config array (skips if no compania)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/notif-config`, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 403 for soporte (only super_admin can read notif config)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.get(`/companias/${createdCompaniaId}/notif-config`, { headers: bearer(SOPORTE) })
    expect(res.status).toBe(403)
  })
})

describe('PUT /companias/{id}/notif-config', () => {
  it('saves notification config and returns 204 (skips if no compania)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.put(`/companias/${createdCompaniaId}/notif-config`,
      {
        configs: [
          { idCompania: createdCompaniaId, diasAntes: 7, canal: 'EMAIL', activo: true },
          { idCompania: createdCompaniaId, diasAntes: 3, canal: 'WHATSAPP', activo: true },
        ],
      }, { headers: bearer(PT) })
    expect(res.status).toBe(204)
  })

  it('returns 403 for soporte', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.put(`/companias/${createdCompaniaId}/notif-config`,
      { configs: [] }, { headers: bearer(SOPORTE) })
    expect(res.status).toBe(403)
  })
})

// ────────────────────────────────────────────────────────────────────────────
// SUSPEND COMPAÑÍA
// ────────────────────────────────────────────────────────────────────────────
describe('PUT /companias/{id}/suspender', () => {
  it('returns 403 for soporte (only super_admin can suspend)', async () => {
    if (!createdCompaniaId) return
    const res = await platformHttp.put(`/companias/${createdCompaniaId}/suspender`,
      { motivo: 'Prueba de integración IT' }, { headers: bearer(SOPORTE) })
    expect(res.status).toBe(403)
  })

  it('returns 404 for non-existent company', async () => {
    const res = await platformHttp.put('/companias/999999/suspender',
      { motivo: 'No existe esta compania' }, { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })
})

// ────────────────────────────────────────────────────────────────────────────
// DEACTIVATE PLAN
// ────────────────────────────────────────────────────────────────────────────
describe('PUT /planes/{id}/desactivar', () => {
  it('returns 403 for soporte (only super_admin can deactivate)', async () => {
    if (!createdPlanId) return
    const res = await platformHttp.put(`/planes/${createdPlanId}/desactivar`, {}, { headers: bearer(SOPORTE) })
    expect(res.status).toBe(403)
  })

  it('returns 404 for non-existent plan', async () => {
    const res = await platformHttp.put('/planes/999999/desactivar', {}, { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })
})
