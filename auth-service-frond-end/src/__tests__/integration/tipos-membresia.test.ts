/**
 * Integration tests — /admin/tipos-membresia screen (core-service port 8083)
 *
 * Covers every operation the TiposMembresiaPage performs:
 *   GET    /api/v1/tipos-membresia
 *   POST   /api/v1/tipos-membresia
 *   PUT    /api/v1/tipos-membresia/{id}
 *   PUT    /api/v1/tipos-membresia/{id}/desactivar
 *
 * Jackson is configured with SNAKE_CASE strategy → all request bodies and
 * response fields use snake_case (modo_control, duracion_tipo, etc.).
 *
 * Tokens:
 *   ADMIN  → rol_plataforma='admin_compania', passes requireAdminOrDueno
 *   RECEP  → rol_plataforma='Recepción',      read-only (403 on mutations)
 */

import { describe, it, expect } from 'vitest'
import { coreHttp, bearer } from './helpers/client'
import { coreAdminToken, coreRecepcionToken } from './helpers/jwt'

const ADMIN = coreAdminToken(99991, 1)
const RECEP  = coreRecepcionToken(99992, 1)

/** Unique name prefix so parallel test runs don't collide */
const RUN = Date.now()
const nombre = (suffix: string) => `TEST_${RUN}_${suffix}`

// ─────────────────────────────────────────────────────────────────────────────
// Payload helpers — request bodies use snake_case (Jackson SNAKE_CASE strategy)
// ─────────────────────────────────────────────────────────────────────────────

const tipoCalendario = (n: string) => ({
  nombre: n,
  modo_control: 'calendario',
  duracion_tipo: 'meses',
  duracion_valor: 1,
  precio: 35.00,
})

const tipoAccesos = (n: string) => ({
  nombre: n,
  modo_control: 'accesos',
  duracion_tipo: 'dias',
  duracion_valor: 30,
  dias_acceso: 22,
  precio: 28.00,
})

// ─────────────────────────────────────────────────────────────────────────────
// GET /tipos-membresia
// ─────────────────────────────────────────────────────────────────────────────

describe('GET /tipos-membresia', () => {
  it('returns 401 without token', async () => {
    const res = await coreHttp.get('/tipos-membresia')
    expect(res.status).toBe(401)
  })

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

  it('response items have snake_case fields matching TipoMembresia DTO', async () => {
    const res = await coreHttp.get('/tipos-membresia', { headers: bearer(ADMIN) })
    expect(res.status).toBe(200)
    if ((res.data as unknown[]).length === 0) return

    const item = res.data[0]
    expect(item).toHaveProperty('id')
    expect(item).toHaveProperty('nombre')
    expect(item).toHaveProperty('modo_control')
    expect(item).toHaveProperty('duracion_tipo')
    expect(item).toHaveProperty('duracion_valor')
    expect(item).toHaveProperty('precio')
    expect(item).toHaveProperty('activo')
    // camelCase keys must NOT appear (Jackson SNAKE_CASE is active)
    expect(item).not.toHaveProperty('modoControl')
    expect(item).not.toHaveProperty('duracionTipo')
    expect(item).not.toHaveProperty('duracionValor')
    expect(item).not.toHaveProperty('diasAcceso')
  })

  it('only returns active tipos (activo=true)', async () => {
    const res = await coreHttp.get('/tipos-membresia', { headers: bearer(ADMIN) })
    expect(res.status).toBe(200)
    for (const item of res.data as { activo: boolean }[]) {
      expect(item.activo).toBe(true)
    }
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// POST /tipos-membresia
// ─────────────────────────────────────────────────────────────────────────────

describe('POST /tipos-membresia — validation', () => {
  it('returns 401 without token', async () => {
    const res = await coreHttp.post('/tipos-membresia', tipoCalendario(nombre('NOAUTH')))
    expect(res.status).toBe(401)
  })

  it('returns 403 for recepcion token', async () => {
    const res = await coreHttp.post('/tipos-membresia', tipoCalendario(nombre('RECEP')), {
      headers: bearer(RECEP),
    })
    expect(res.status).toBe(403)
  })

  it('returns 400 when body is empty', async () => {
    const res = await coreHttp.post('/tipos-membresia', {}, { headers: bearer(ADMIN) })
    expect(res.status).toBe(400)
  })

  it('returns 400 when nombre is missing', async () => {
    const { nombre: _omit, ...sinNombre } = tipoCalendario('x')
    const res = await coreHttp.post('/tipos-membresia', sinNombre, { headers: bearer(ADMIN) })
    expect(res.status).toBe(400)
  })

  it('returns 400 when modo_control is missing', async () => {
    const { modo_control: _omit, ...sinModo } = tipoCalendario(nombre('SINMODO'))
    const res = await coreHttp.post('/tipos-membresia', sinModo, { headers: bearer(ADMIN) })
    expect(res.status).toBe(400)
  })

  it('returns 400 when modo_control value is invalid enum', async () => {
    const res = await coreHttp.post('/tipos-membresia', {
      ...tipoCalendario(nombre('BADENUM')),
      modo_control: 'invalido',
    }, { headers: bearer(ADMIN) })
    expect([400, 500]).toContain(res.status)
  })

  it('returns 400 when duracion_valor is zero or negative', async () => {
    const res = await coreHttp.post('/tipos-membresia', {
      ...tipoCalendario(nombre('DURVAL0')),
      duracion_valor: 0,
    }, { headers: bearer(ADMIN) })
    expect(res.status).toBe(400)
  })

  it('returns 400 when precio is negative', async () => {
    const res = await coreHttp.post('/tipos-membresia', {
      ...tipoCalendario(nombre('PRECNEG')),
      precio: -1,
    }, { headers: bearer(ADMIN) })
    expect(res.status).toBe(400)
  })

  it('returns 422 when modo_control=accesos but dias_acceso is missing (BusinessException)', async () => {
    const res = await coreHttp.post('/tipos-membresia', {
      nombre: nombre('ACCESOS_SINDIASACCESO'),
      modo_control: 'accesos',
      duracion_tipo: 'dias',
      duracion_valor: 30,
      precio: 25,
      // dias_acceso omitted intentionally
    }, { headers: bearer(ADMIN) })
    expect(res.status).toBe(422)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// CRUD lifecycle — POST → verify in GET → PUT → desactivar
// ─────────────────────────────────────────────────────────────────────────────

describe('Tipos membresía — full CRUD lifecycle', () => {
  let createdId: number

  it('POST creates a calendario tipo and returns 201 with correct shape', async () => {
    const payload = tipoCalendario(nombre('CRUD_CAL'))
    const res = await coreHttp.post('/tipos-membresia', payload, { headers: bearer(ADMIN) })
    expect(res.status).toBe(201)

    const body = res.data
    expect(body).toHaveProperty('id')
    expect(typeof body.id).toBe('number')
    expect(body.nombre).toBe(payload.nombre)
    expect(body.modo_control).toBe('calendario')
    expect(body.duracion_tipo).toBe('meses')
    expect(body.duracion_valor).toBe(1)
    expect(body.dias_acceso).toBeNull()
    expect(Number(body.precio)).toBe(35.00)
    expect(body.activo).toBe(true)
    // camelCase fields must NOT appear
    expect(body).not.toHaveProperty('modoControl')

    createdId = body.id
  })

  it('GET returns the created tipo in the list', async () => {
    const res = await coreHttp.get('/tipos-membresia', { headers: bearer(ADMIN) })
    expect(res.status).toBe(200)
    const found = (res.data as { id: number }[]).find(t => t.id === createdId)
    expect(found).toBeDefined()
  })

  it('POST returns 409 when creating a tipo with the same nombre', async () => {
    const payload = tipoCalendario(nombre('CRUD_CAL'))
    const res = await coreHttp.post('/tipos-membresia', payload, { headers: bearer(ADMIN) })
    expect(res.status).toBe(409)
  })

  it('PUT updates nombre and precio, returns 200', async () => {
    const res = await coreHttp.put(`/tipos-membresia/${createdId}`, {
      nombre: nombre('CRUD_CAL_UPDATED'),
      precio: 45.00,
    }, { headers: bearer(ADMIN) })
    expect(res.status).toBe(200)
    expect(res.data.nombre).toBe(nombre('CRUD_CAL_UPDATED'))
    expect(Number(res.data.precio)).toBe(45.00)
  })

  it('PUT /desactivar deactivates the tipo and returns 200', async () => {
    const res = await coreHttp.put(`/tipos-membresia/${createdId}/desactivar`, {}, {
      headers: bearer(ADMIN),
    })
    expect(res.status).toBe(200)
  })

  it('GET no longer includes the deactivated tipo', async () => {
    const res = await coreHttp.get('/tipos-membresia', { headers: bearer(ADMIN) })
    expect(res.status).toBe(200)
    const found = (res.data as { id: number }[]).find(t => t.id === createdId)
    expect(found).toBeUndefined()
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// POST — modo accesos (requires dias_acceso)
// ─────────────────────────────────────────────────────────────────────────────

describe('POST /tipos-membresia — modo accesos lifecycle', () => {
  let createdId: number

  it('creates an accesos tipo with dias_acceso and returns 201', async () => {
    const payload = tipoAccesos(nombre('CRUD_ACC'))
    const res = await coreHttp.post('/tipos-membresia', payload, { headers: bearer(ADMIN) })
    expect(res.status).toBe(201)
    expect(res.data.modo_control).toBe('accesos')
    expect(res.data.dias_acceso).toBe(22)
    createdId = res.data.id
  })

  it('PUT /desactivar cleans up the accesos tipo', async () => {
    const res = await coreHttp.put(`/tipos-membresia/${createdId}/desactivar`, {}, {
      headers: bearer(ADMIN),
    })
    expect(res.status).toBe(200)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// PUT /tipos-membresia/{id} — error cases
// ─────────────────────────────────────────────────────────────────────────────

describe('PUT /tipos-membresia/{id} — error cases', () => {
  it('returns 401 without token', async () => {
    const res = await coreHttp.put('/tipos-membresia/1', { nombre: 'x', precio: 10 })
    expect(res.status).toBe(401)
  })

  it('returns 403 for recepcion token', async () => {
    const res = await coreHttp.put('/tipos-membresia/999999', { nombre: 'x', precio: 10 }, {
      headers: bearer(RECEP),
    })
    expect(res.status).toBe(403)
  })

  it('returns 404 for non-existent id', async () => {
    const res = await coreHttp.put('/tipos-membresia/999999', { nombre: 'x', precio: 10 }, {
      headers: bearer(ADMIN),
    })
    expect(res.status).toBe(404)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// PUT /tipos-membresia/{id}/desactivar — error cases
// ─────────────────────────────────────────────────────────────────────────────

describe('PUT /tipos-membresia/{id}/desactivar — error cases', () => {
  it('returns 401 without token', async () => {
    const res = await coreHttp.put('/tipos-membresia/1/desactivar', {})
    expect(res.status).toBe(401)
  })

  it('returns 403 for recepcion token', async () => {
    const res = await coreHttp.put('/tipos-membresia/999999/desactivar', {}, {
      headers: bearer(RECEP),
    })
    expect(res.status).toBe(403)
  })

  it('returns 404 for non-existent id', async () => {
    const res = await coreHttp.put('/tipos-membresia/999999/desactivar', {}, {
      headers: bearer(ADMIN),
    })
    expect(res.status).toBe(404)
  })
})
