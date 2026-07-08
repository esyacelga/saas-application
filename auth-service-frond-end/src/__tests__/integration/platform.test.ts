/**
 * Integration tests — Platform endpoints (existing + 7 new ones).
 *
 * JWT tokens are generated from the well-known dev secret in application.yml.
 * Write tests discover a real compañía/sucursal via GET in beforeAll, then create
 * and clean up their own data.
 */
import { beforeAll, afterAll, describe, it, expect } from 'vitest'
import { http, bearer } from './helpers/client'
import { platformToken, staffToken } from './helpers/jwt'

const PT = platformToken()
const ST = staffToken()
const TS = Date.now()

// ──────────────────────────────────────────────────────────────
// Shared state — populated in beforeAll
// ──────────────────────────────────────────────────────────────
let companiaId: number | null = null
let sucursalId: number | null = null
let createdRolId: number | null = null
let createdPermisoId: number | null = null

beforeAll(async () => {
  const companias = await http.get('/platform/companias', { headers: bearer(PT) })
  if (companias.status === 200 && Array.isArray(companias.data) && companias.data.length > 0) {
    companiaId = companias.data[0].id as number
    const sucursales = await http.get(`/platform/companias/${companiaId}/sucursales`, { headers: bearer(PT) })
    if (sucursales.status === 200 && Array.isArray(sucursales.data) && sucursales.data.length > 0) {
      sucursalId = sucursales.data[0].id as number
    }
  }
})

afterAll(async () => {
  if (createdPermisoId !== null) {
    await http.delete(`/platform/permisos/${createdPermisoId}`, { headers: bearer(PT) })
    createdPermisoId = null
  }
  if (createdRolId !== null) {
    await http.delete(`/platform/roles/${createdRolId}`, { headers: bearer(PT) })
    createdRolId = null
  }
})

// ──────────────────────────────────────────────────────────────
// Compañías & Sucursales
// ──────────────────────────────────────────────────────────────
describe('GET /platform/companias', () => {
  it('returns 200 with array for platform token', async () => {
    const res = await http.get('/platform/companias', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 403 for staff token', async () => {
    const res = await http.get('/platform/companias', { headers: bearer(ST) })
    expect(res.status).toBe(403)
  })
})

describe('GET /platform/companias/{id}/sucursales', () => {
  it('returns 404 for non-existent compañía', async () => {
    const res = await http.get('/platform/companias/999999/sucursales', { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('returns 200 with array for existing compañía (skips if none in DB)', async () => {
    if (!companiaId) return
    const res = await http.get(`/platform/companias/${companiaId}/sucursales`, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })
})

// ──────────────────────────────────────────────────────────────
// Platform Roles — read
// ──────────────────────────────────────────────────────────────
describe('GET /platform/roles', () => {
  it('returns 200 with array for platform token', async () => {
    const res = await http.get('/platform/roles', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 403 for staff token', async () => {
    const res = await http.get('/platform/roles', { headers: bearer(ST) })
    expect(res.status).toBe(403)
  })

  it('returns 401 with no token', async () => {
    const res = await http.get('/platform/roles')
    expect(res.status).toBe(401)
  })
})

describe('GET /platform/roles/{id}/permisos (legacy full object)', () => {
  it('returns 404 for non-existent role', async () => {
    const res = await http.get('/platform/roles/999999/permisos', { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })
})

// ──────────────────────────────────────────────────────────────
// Platform Roles — write (skips when no compañía in DB)
// ──────────────────────────────────────────────────────────────
describe('POST /platform/roles', () => {
  it('returns 400 when body is missing required fields', async () => {
    const res = await http.post('/platform/roles', {}, { headers: bearer(PT) })
    expect(res.status).toBe(400)
  })

  it('returns 403 for non-super-admin platform token', async () => {
    const tok = platformToken(99990, 'Test', 'operador')
    const res = await http.post('/platform/roles', { nombre: 'X', id_compania: 1, id_sucursal: 1 }, { headers: bearer(tok) })
    expect(res.status).toBe(403)
  })

  it('creates a role and returns 201 (skips if no compañía/sucursal in DB)', async () => {
    if (!companiaId || !sucursalId) return
    const nombre = `Rol IT Frontend ${TS}`
    const res = await http.post('/platform/roles',
      { nombre, descripcion: 'Test', id_compania: companiaId, id_sucursal: sucursalId },
      { headers: bearer(PT) })
    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('id')
    expect(res.data.nombre).toBe(nombre)
    createdRolId = res.data.id as number
  })
})

describe('PUT /platform/roles/{id}', () => {
  it('returns 404 for non-existent role', async () => {
    const res = await http.put('/platform/roles/999999', { nombre: 'X' }, { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('updates the role and returns 200 (skips if no role was created)', async () => {
    if (!createdRolId) return
    const nombre = `Rol IT Frontend Act ${TS}`
    const res = await http.put(`/platform/roles/${createdRolId}`,
      { nombre }, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data.nombre).toBe(nombre)
  })
})

// ──────────────────────────────────────────────────────────────
// Platform Operadores
// ──────────────────────────────────────────────────────────────
describe('GET /platform/usuarios', () => {
  it('returns 200 with array for platform token', async () => {
    const res = await http.get('/platform/usuarios', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 403 for staff token', async () => {
    const res = await http.get('/platform/usuarios', { headers: bearer(ST) })
    expect(res.status).toBe(403)
  })
})

describe('POST /platform/usuarios', () => {
  it('returns 400 when body is missing required fields', async () => {
    const res = await http.post('/platform/usuarios', {}, { headers: bearer(PT) })
    expect(res.status).toBe(400)
  })
})

// ──────────────────────────────────────────────────────────────
// NEW: GET /platform/permisos
// ──────────────────────────────────────────────────────────────
describe('GET /platform/permisos', () => {
  it('returns 200 with array and expected shape for platform token', async () => {
    const res = await http.get('/platform/permisos', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    if (res.data.length > 0) {
      const item = res.data[0]
      expect(item).toHaveProperty('id')
      expect(item).toHaveProperty('nombre')
      expect(item).toHaveProperty('modulo')
      expect(item).toHaveProperty('id_compania')
      expect(item).toHaveProperty('id_sucursal')
      expect(item).toHaveProperty('nombre_sucursal')
    }
  })

  it('returns 403 for staff token', async () => {
    const res = await http.get('/platform/permisos', { headers: bearer(ST) })
    expect(res.status).toBe(403)
  })

  it('returns 401 without token', async () => {
    const res = await http.get('/platform/permisos')
    expect(res.status).toBe(401)
  })
})

// ──────────────────────────────────────────────────────────────
// NEW: POST /platform/permisos
// ──────────────────────────────────────────────────────────────
describe('POST /platform/permisos', () => {
  it('returns 400 when body is missing required fields', async () => {
    const res = await http.post('/platform/permisos', { modulo: 'test' }, { headers: bearer(PT) })
    expect(res.status).toBe(400)
  })

  it('returns 403 for non-super-admin platform token', async () => {
    const tok = platformToken(99990, 'Test', 'operador')
    const res = await http.post('/platform/permisos',
      { nombre: 'x', modulo: 'x', id_compania: 1, id_sucursal: 1 },
      { headers: bearer(tok) })
    expect(res.status).toBe(403)
  })

  it('creates a permiso and returns 201 (skips if no compañía/sucursal in DB)', async () => {
    if (!companiaId || !sucursalId) return
    const nombre = `socios:it_${TS}`
    const res = await http.post('/platform/permisos',
      { nombre, modulo: 'socios', descripcion: 'Permiso IT Frontend',
        id_compania: companiaId, id_sucursal: sucursalId },
      { headers: bearer(PT) })
    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('id')
    expect(res.data.nombre).toBe(nombre)
    expect(res.data).toHaveProperty('nombre_sucursal')
    expect(res.data.nombre_sucursal).toBeTruthy()
    createdPermisoId = res.data.id as number
  })

  it('returns 409 when permiso nombre is duplicate within same compañía (skips if none created)', async () => {
    if (!createdPermisoId || !companiaId || !sucursalId) return
    const nombre = `socios:it_${TS}`
    const res = await http.post('/platform/permisos',
      { nombre, modulo: 'socios', id_compania: companiaId, id_sucursal: sucursalId },
      { headers: bearer(PT) })
    expect(res.status).toBe(409)
  })
})

// ──────────────────────────────────────────────────────────────
// NEW: PUT /platform/permisos/{id}
// ──────────────────────────────────────────────────────────────
describe('PUT /platform/permisos/{id}', () => {
  it('returns 404 for non-existent permiso', async () => {
    const res = await http.put('/platform/permisos/999999', { nombre: 'x' }, { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('updates permiso and returns 200 with nombre_sucursal (skips if none created)', async () => {
    if (!createdPermisoId) return
    const nombre = `socios:it_v2_${TS}`
    const res = await http.put(`/platform/permisos/${createdPermisoId}`,
      { nombre, descripcion: 'Actualizado' },
      { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data.nombre).toBe(nombre)
    expect(res.data).toHaveProperty('nombre_sucursal')
  })
})

// ──────────────────────────────────────────────────────────────
// NEW: GET /platform/roles/{id}/permisos/detalle
// ──────────────────────────────────────────────────────────────
describe('GET /platform/roles/{id}/permisos/detalle', () => {
  it('returns 404 for non-existent role', async () => {
    const res = await http.get('/platform/roles/999999/permisos/detalle', { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('returns 403 for staff token', async () => {
    const res = await http.get('/platform/roles/1/permisos/detalle', { headers: bearer(ST) })
    expect(res.status).toBe(403)
  })

  it('returns array with nombre_sucursal for existing role (skips if none created)', async () => {
    if (!createdRolId) return
    const res = await http.get(`/platform/roles/${createdRolId}/permisos/detalle`, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    expect(res.data.length).toBe(0) // freshly created role has no permissions
  })
})

// ──────────────────────────────────────────────────────────────
// NEW: POST /platform/roles/{id}/permisos (asignar)
// ──────────────────────────────────────────────────────────────
describe('POST /platform/roles/{id}/permisos', () => {
  it('returns 404 for non-existent role', async () => {
    const res = await http.post('/platform/roles/999999/permisos', { id_permiso: 1 }, { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('returns 400 when id_permiso is missing', async () => {
    const res = await http.post('/platform/roles/1/permisos', {}, { headers: bearer(PT) })
    expect(res.status).toBe(400)
  })

  it('returns 403 for staff token', async () => {
    const res = await http.post('/platform/roles/1/permisos', { id_permiso: 1 }, { headers: bearer(ST) })
    expect(res.status).toBe(403)
  })

  it('assigns permiso to role → 201, detalle shows it (skips if no data)', async () => {
    if (!createdRolId || !createdPermisoId) return
    const assign = await http.post(`/platform/roles/${createdRolId}/permisos`,
      { id_permiso: createdPermisoId }, { headers: bearer(PT) })
    expect(assign.status).toBe(201)

    const detail = await http.get(`/platform/roles/${createdRolId}/permisos/detalle`, { headers: bearer(PT) })
    expect(detail.status).toBe(200)
    const found = detail.data.find((p: { id: number }) => p.id === createdPermisoId)
    expect(found).toBeDefined()
    expect(found).toHaveProperty('nombre_sucursal')
    expect(found).toHaveProperty('modulo')
  })

  it('returns 409 when permiso already assigned (skips if no data)', async () => {
    if (!createdRolId || !createdPermisoId) return
    const res = await http.post(`/platform/roles/${createdRolId}/permisos`,
      { id_permiso: createdPermisoId }, { headers: bearer(PT) })
    expect(res.status).toBe(409)
  })
})

// ──────────────────────────────────────────────────────────────
// NEW: DELETE /platform/roles/{id}/permisos/{idPermiso}
// ──────────────────────────────────────────────────────────────
describe('DELETE /platform/roles/{id}/permisos/{idPermiso}', () => {
  it('returns 404 for non-existent role', async () => {
    const res = await http.delete('/platform/roles/999999/permisos/1', { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('returns 404 for non-existent assignment', async () => {
    const res = await http.delete('/platform/roles/1/permisos/999999', { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('returns 403 for staff token', async () => {
    const res = await http.delete('/platform/roles/1/permisos/1', { headers: bearer(ST) })
    expect(res.status).toBe(403)
  })

  it('soft-deletes → 204; re-assign reactivates → 201 (skips if no data)', async () => {
    if (!createdRolId || !createdPermisoId) return
    const del = await http.delete(`/platform/roles/${createdRolId}/permisos/${createdPermisoId}`,
      { headers: bearer(PT) })
    expect(del.status).toBe(204)

    // Should no longer appear in detalle
    const detail = await http.get(`/platform/roles/${createdRolId}/permisos/detalle`, { headers: bearer(PT) })
    const found = detail.data.find((p: { id: number }) => p.id === createdPermisoId)
    expect(found).toBeUndefined()

    // Re-assign reactivates (not 409)
    const reactivate = await http.post(`/platform/roles/${createdRolId}/permisos`,
      { id_permiso: createdPermisoId }, { headers: bearer(PT) })
    expect(reactivate.status).toBe(201)
  })

  it('returns 404 when already soft-deleted (skips if no data)', async () => {
    if (!createdRolId || !createdPermisoId) return
    // soft-delete first
    await http.delete(`/platform/roles/${createdRolId}/permisos/${createdPermisoId}`, { headers: bearer(PT) })
    // second delete should 404
    const res = await http.delete(`/platform/roles/${createdRolId}/permisos/${createdPermisoId}`,
      { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })
})

// ──────────────────────────────────────────────────────────────
// NEW: DELETE /platform/permisos/{id}
// ──────────────────────────────────────────────────────────────
describe('DELETE /platform/permisos/{id}', () => {
  it('returns 404 for non-existent permiso', async () => {
    const res = await http.delete('/platform/permisos/999999', { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('returns 403 for staff token', async () => {
    const res = await http.delete('/platform/permisos/1', { headers: bearer(ST) })
    expect(res.status).toBe(403)
  })

  it('soft-deletes permiso → 204 (skips if none created)', async () => {
    if (!createdPermisoId) return
    const res = await http.delete(`/platform/permisos/${createdPermisoId}`, { headers: bearer(PT) })
    expect(res.status).toBe(204)
    createdPermisoId = null // mark cleaned up
  })
})

// ──────────────────────────────────────────────────────────────
// PUT /platform/roles/{id}/permisos (bulk replace — legacy)
// ──────────────────────────────────────────────────────────────
describe('PUT /platform/roles/{id}/permisos (bulk replace)', () => {
  it('returns 404 for non-existent role', async () => {
    const res = await http.put('/platform/roles/999999/permisos', { id_permisos: [] }, { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('replaces all permisos and returns 200 (skips if none created)', async () => {
    if (!createdRolId) return
    const res = await http.put(`/platform/roles/${createdRolId}/permisos`,
      { id_permisos: [] }, { headers: bearer(PT) })
    expect(res.status).toBe(200)
  })
})

// ──────────────────────────────────────────────────────────────
// DELETE /platform/roles/{id}
// ──────────────────────────────────────────────────────────────
describe('DELETE /platform/roles/{id}', () => {
  it('returns 404 for non-existent role', async () => {
    const res = await http.delete('/platform/roles/999999', { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('deletes the test role → 204 (skips if none created)', async () => {
    if (!createdRolId) return
    const res = await http.delete(`/platform/roles/${createdRolId}`, { headers: bearer(PT) })
    expect(res.status).toBe(204)
    createdRolId = null
  })
})
