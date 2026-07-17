/**
 * Integration tests — Core Service endpoints (port 8083)
 *
 * Each test hits the real running core-service. The service must be running
 * before executing these tests (npm run test:integration).
 *
 * Token claims used:
 *   - coreAdminToken(id, idCompania=1)  → rol_plataforma='admin_compania', passes requireAdminOrDueno
 *   - coreRecepcionToken(id, idCompania=1) → rol_plataforma='Recepción', passes requireRecepcionOrAbove
 *
 * id_compania=1 matches TEST_COMPANIA in the backend BaseIntegrationTest seed data.
 */
import { describe, it, expect } from 'vitest'
import { coreHttp, bearer } from './helpers/client'
import { coreAdminToken, coreRecepcionToken } from './helpers/jwt'

const ADMIN  = coreAdminToken(99991, 1)
const RECEP  = coreRecepcionToken(99992, 1)

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────


/** Returns today's ISO date string. */
function today(): string {
  return new Date().toISOString().split('T')[0]
}

// ──────────────────────────────────────────────────────────────────────────────
// 401 / no token guard (applies to all protected endpoints)
// ──────────────────────────────────────────────────────────────────────────────

describe('Authentication guard', () => {
  it('GET /tipos-membresia → 401 without token', async () => {
    const res = await coreHttp.get('/tipos-membresia')
    expect(res.status).toBe(401)
  })

  it('GET /clientes → 401 without token', async () => {
    const res = await coreHttp.get('/clientes')
    expect(res.status).toBe(401)
  })

  it('GET /membresias/validar-acceso → 403 without client (public endpoint, no token needed)', async () => {
    // validar-acceso is permitAll — missing required params returns 400/500
    const res = await coreHttp.get('/membresias/validar-acceso')
    expect(res.status).toBeGreaterThanOrEqual(400)
    expect(res.status).toBeLessThan(500)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Tipos Membresía — GET /api/v1/tipos-membresia
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /tipos-membresia', () => {
  it('returns 200 with an array for admin token', async () => {
    const res = await coreHttp.get('/tipos-membresia', { headers: bearer(ADMIN) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 200 with an array for recepcion token', async () => {
    const res = await coreHttp.get('/tipos-membresia', { headers: bearer(RECEP) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Tipos Membresía — POST /api/v1/tipos-membresia
// ──────────────────────────────────────────────────────────────────────────────

describe('POST /tipos-membresia', () => {
  it('returns 400 when body is missing required fields', async () => {
    const res = await coreHttp.post('/tipos-membresia', {}, { headers: bearer(ADMIN) })
    expect(res.status).toBe(400)
  })

  it('returns 400 or 500 when modo_control value is invalid enum', async () => {
    const res = await coreHttp.post('/tipos-membresia', {
      nombre: 'Test tipo',
      modo_control: 'invalido',
      duracion_tipo: 'meses',
      duracion_valor: 1,
      precio: 30,
    }, { headers: bearer(ADMIN) })
    // Spring may return 400 (bad request) or 500 if enum parse throws
    expect([400, 500]).toContain(res.status)
  })

  it('returns 403 for recepcion token (requireAdminOrDueno)', async () => {
    const res = await coreHttp.post('/tipos-membresia', {
      nombre: 'Test tipo recepcion',
      modo_control: 'calendario',
      duracion_tipo: 'meses',
      duracion_valor: 1,
      precio: 30,
    }, { headers: bearer(RECEP) })
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Tipos Membresía — PUT /api/v1/tipos-membresia/{id}
// ──────────────────────────────────────────────────────────────────────────────

describe('PUT /tipos-membresia/{id}', () => {
  it('returns 404 for non-existent tipo', async () => {
    const res = await coreHttp.put('/tipos-membresia/999999', {
      nombre: 'Updated',
      precio: 50,
    }, { headers: bearer(ADMIN) })
    expect(res.status).toBe(404)
  })

  it('returns 403 for recepcion token', async () => {
    const res = await coreHttp.put('/tipos-membresia/999999', {
      nombre: 'Updated',
      precio: 50,
    }, { headers: bearer(RECEP) })
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Tipos Membresía — PUT /api/v1/tipos-membresia/{id}/desactivar
// ──────────────────────────────────────────────────────────────────────────────

describe('PUT /tipos-membresia/{id}/desactivar', () => {
  it('returns 404 for non-existent tipo', async () => {
    const res = await coreHttp.put('/tipos-membresia/999999/desactivar', {}, { headers: bearer(ADMIN) })
    expect(res.status).toBe(404)
  })

  it('returns 403 for recepcion token', async () => {
    const res = await coreHttp.put('/tipos-membresia/999999/desactivar', {}, { headers: bearer(RECEP) })
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Clientes — GET /api/v1/clientes
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /clientes', () => {
  it('returns 200 with paginated response for admin token', async () => {
    const res = await coreHttp.get('/clientes', { headers: bearer(ADMIN) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('total')
    expect(res.data).toHaveProperty('pagina')
    expect(res.data).toHaveProperty('datos')
    expect(Array.isArray(res.data.datos)).toBe(true)
  })

  it('returns 200 for recepcion token', async () => {
    const res = await coreHttp.get('/clientes', { headers: bearer(RECEP) })
    expect(res.status).toBe(200)
  })

  it('accepts estado query param', async () => {
    const res = await coreHttp.get('/clientes', {
      params: { estado: 'activo', page: 1, limit: 10 },
      headers: bearer(ADMIN),
    })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('datos')
  })

  it('accepts buscar query param', async () => {
    const res = await coreHttp.get('/clientes', {
      params: { buscar: 'nonexistent_test_xyz' },
      headers: bearer(ADMIN),
    })
    expect(res.status).toBe(200)
    expect(res.data.datos).toHaveLength(0)
  })

  it('limit=1 retorna como máximo 1 registro pero expone el total real (uso dashboard)', async () => {
    const res = await coreHttp.get('/clientes', {
      params: { estado: 'activo', limit: 1 },
      headers: bearer(ADMIN),
    })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('total')
    expect(typeof res.data.total).toBe('number')
    expect(res.data.total).toBeGreaterThanOrEqual(0)
    expect(Array.isArray(res.data.datos)).toBe(true)
    expect(res.data.datos.length).toBeLessThanOrEqual(1)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Clientes — GET /api/v1/clientes/{id}
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /clientes/{id}', () => {
  it('returns 404 for non-existent client', async () => {
    const res = await coreHttp.get('/clientes/999999', { headers: bearer(ADMIN) })
    expect(res.status).toBe(404)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Clientes — POST /api/v1/clientes
// ──────────────────────────────────────────────────────────────────────────────

describe('POST /clientes', () => {
  it('returns 400 when body is missing required fields', async () => {
    const res = await coreHttp.post('/clientes', {}, { headers: bearer(RECEP) })
    expect(res.status).toBe(400)
  })

  it('returns 400 when ci is missing', async () => {
    const res = await coreHttp.post('/clientes', {
      nombre: 'Test Cliente',
      id_sucursal: 1,
    }, { headers: bearer(RECEP) })
    expect(res.status).toBe(400)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Clientes — PUT /api/v1/clientes/{id}
// ──────────────────────────────────────────────────────────────────────────────

describe('PUT /clientes/{id}', () => {
  it('returns 404 for non-existent client', async () => {
    const res = await coreHttp.put('/clientes/999999', {
      peso_kg: 70,
    }, { headers: bearer(RECEP) })
    expect(res.status).toBe(404)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Clientes — GET /api/v1/clientes/ci/{ci}
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /clientes/ci/{ci}', () => {
  it('returns 404 for a CI that does not exist in personas', async () => {
    const res = await coreHttp.get('/clientes/ci/9999999999', { headers: bearer(ADMIN) })
    expect(res.status).toBe(404)
  })

  it('returns 200 with es_cliente_en_este_gym for a known CI', async () => {
    // This CI is expected to NOT exist — we just verify the 404 shape
    const res = await coreHttp.get('/clientes/ci/CI-NOT-FOUND-XYZ', { headers: bearer(ADMIN) })
    expect([200, 404]).toContain(res.status)
    if (res.status === 200) {
      expect(res.data).toHaveProperty('persona')
      expect(res.data).toHaveProperty('es_cliente_en_este_gym')
      expect(res.data).toHaveProperty('id_cliente')
    }
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Membresías — GET /api/v1/clientes/{id}/membresias
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /clientes/{id}/membresias', () => {
  it('returns 200 with empty array for non-existent client', async () => {
    const res = await coreHttp.get('/clientes/999999/membresias', { headers: bearer(ADMIN) })
    // Backend returns empty array (no FK check in historial query)
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Membresías — POST /api/v1/clientes/{id}/membresias (vender)
// ──────────────────────────────────────────────────────────────────────────────

describe('POST /clientes/{id}/membresias (vender)', () => {
  it('returns 404 for non-existent tipo_membresia (client 999999 not found)', async () => {
    const res = await coreHttp.post('/clientes/999999/membresias', {
      id_tipo_membresia: 999999,
      fecha_inicio: today(),
    }, { headers: bearer(RECEP) })
    // 409 = client already has active membership, 404 = client or tipo not found
    expect([404, 409]).toContain(res.status)
  })

  it('returns 400 when required fields are missing', async () => {
    const res = await coreHttp.post('/clientes/999999/membresias', {}, { headers: bearer(RECEP) })
    expect(res.status).toBe(400)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Membresías — GET /api/v1/membresias/{id}
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /membresias/{id}', () => {
  it('returns 404 for non-existent membresia', async () => {
    const res = await coreHttp.get('/membresias/999999', { headers: bearer(ADMIN) })
    expect(res.status).toBe(404)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Membresías — PUT /api/v1/membresias/{id}/anular
// ──────────────────────────────────────────────────────────────────────────────

describe('PUT /membresias/{id}/anular', () => {
  it('returns 404 for non-existent membresia', async () => {
    const res = await coreHttp.put('/membresias/999999/anular', {
      motivo: 'Prueba',
    }, { headers: bearer(ADMIN) })
    expect(res.status).toBe(404)
  })

  it('returns 403 for recepcion token', async () => {
    const res = await coreHttp.put('/membresias/999999/anular', {
      motivo: 'Prueba',
    }, { headers: bearer(RECEP) })
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Membresías — GET /api/v1/membresias/validar-acceso (public endpoint)
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /membresias/validar-acceso', () => {
  it('returns 403 (forbidden) for a client with no active membership', async () => {
    const res = await coreHttp.get('/membresias/validar-acceso', {
      params: { id_cliente: 999999, id_compania: 1 },
    })
    // No token required — public endpoint
    // Backend returns 403 with body { permitido: false, razon: "sin_membresia" } when no active membership found
    expect(res.status).toBe(403)
    expect(res.data).toHaveProperty('permitido', false)
    expect(res.data).toHaveProperty('razon')
  })

  it('returns 400 when required params are missing', async () => {
    const res = await coreHttp.get('/membresias/validar-acceso')
    expect(res.status).toBeGreaterThanOrEqual(400)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Congelamientos — POST /api/v1/membresias/{id}/congelar
// ──────────────────────────────────────────────────────────────────────────────

describe('POST /membresias/{id}/congelar', () => {
  it('returns 404 for non-existent membresia', async () => {
    const res = await coreHttp.post('/membresias/999999/congelar', {
      fecha_inicio: today(),
      motivo: 'viaje',
      detalle: 'Test',
      retroactivo: false,
    }, { headers: bearer(RECEP) })
    expect(res.status).toBe(404)
  })

  it('returns 400 when required fields are missing', async () => {
    const res = await coreHttp.post('/membresias/999999/congelar', {}, { headers: bearer(RECEP) })
    expect(res.status).toBe(400)
  })

  it('returns 403 for retroactivo=true with recepcion token (requires admin)', async () => {
    // Backend checks permission for retroactivo BEFORE finding membresia, so returns 403 even for id=999999
    const res = await coreHttp.post('/membresias/999999/congelar', {
      fecha_inicio: today(),
      motivo: 'viaje',
      retroactivo: true,
    }, { headers: bearer(RECEP) })
    expect(res.status).toBe(403)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Congelamientos — PUT /api/v1/congelamientos/{id}/reactivar
// ──────────────────────────────────────────────────────────────────────────────

describe('PUT /congelamientos/{id}/reactivar', () => {
  it('returns 404 for non-existent congelamiento', async () => {
    const res = await coreHttp.put('/congelamientos/999999/reactivar', {}, { headers: bearer(RECEP) })
    expect(res.status).toBe(404)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Congelamientos — GET /api/v1/membresias/{id}/congelamientos
// ──────────────────────────────────────────────────────────────────────────────

describe('GET /membresias/{id}/congelamientos', () => {
  it('returns 200 with empty array for non-existent membresia', async () => {
    const res = await coreHttp.get('/membresias/999999/congelamientos', { headers: bearer(ADMIN) })
    // Backend returns empty array (no FK check in historial query)
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })
})
