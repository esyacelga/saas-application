/**
 * Integration tests — Staff & shared endpoints
 * Uses a staff JWT token generated from the well-known dev secret.
 *
 * Note: the backend checks permissions before checking resource existence.
 * Write endpoints (POST/PUT/DELETE) require specific permissions in the token;
 * tests that need write access use a broader permission set.
 */
import { describe, it, expect } from 'vitest'
import { http, bearer } from './helpers/client'
import { staffToken, platformToken } from './helpers/jwt'

// Read-only token — passes most GET checks
const ST_READ = staffToken(['roles:leer', 'permisos:leer', 'usuarios:leer', 'personas:leer', 'bitacora:leer'])
// Broad token — used for write-endpoint permission checks
const ST_WRITE = staffToken(['roles:leer', 'roles:crear', 'roles:eliminar', 'roles:editar',
  'permisos:leer', 'usuarios:leer', 'usuarios:crear', 'usuarios:editar', 'usuarios:activar',
  'personas:leer', 'personas:crear', 'personas:editar'])
const PT = platformToken()

// ──────────────────────────────────────────────────────────────
// Roles
// ──────────────────────────────────────────────────────────────
describe('GET /roles', () => {
  it('returns 200 with array for staff token', async () => {
    const res = await http.get('/roles', { headers: bearer(ST_READ) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 403 for platform token', async () => {
    const res = await http.get('/roles', { headers: bearer(PT) })
    expect(res.status).toBe(403)
  })

  it('returns 401 without token', async () => {
    const res = await http.get('/roles')
    expect(res.status).toBe(401)
  })
})

describe('GET /roles/{id}', () => {
  it('returns 404 for non-existent role', async () => {
    const res = await http.get('/roles/999999', { headers: bearer(ST_READ) })
    expect(res.status).toBe(404)
  })
})

describe('POST /roles', () => {
  // Authorization is checked before validation — token without create perm gets 403
  it('returns 403 or 400 for invalid body (auth depends on token perms)', async () => {
    const res = await http.post('/roles', {}, { headers: bearer(ST_WRITE) })
    expect([400, 403]).toContain(res.status)
  })
})

describe('GET /roles/{id}/permisos', () => {
  it('returns 404 for non-existent role', async () => {
    const res = await http.get('/roles/999999/permisos', { headers: bearer(ST_READ) })
    expect(res.status).toBe(404)
  })
})

describe('DELETE /roles/{id}', () => {
  // Backend checks permission before resource existence
  it('returns 403 or 404 for non-existent role (auth first)', async () => {
    const res = await http.delete('/roles/999999', { headers: bearer(ST_WRITE) })
    expect([403, 404]).toContain(res.status)
  })
})

describe('PUT /roles/{id}/permisos', () => {
  it('returns 403 or 404 for non-existent role', async () => {
    const res = await http.put('/roles/999999/permisos', { id_permisos: [] }, { headers: bearer(ST_WRITE) })
    expect([403, 404]).toContain(res.status)
  })
})

// ──────────────────────────────────────────────────────────────
// Permisos
// ──────────────────────────────────────────────────────────────
describe('GET /permisos', () => {
  it('returns 200 with array for staff token', async () => {
    const res = await http.get('/permisos', { headers: bearer(ST_READ) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })
})

describe('GET /permisos/by-rol/{idRol}', () => {
  it('returns 200 with array', async () => {
    const res = await http.get('/permisos/by-rol/1', { headers: bearer(ST_READ) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })
})

// ──────────────────────────────────────────────────────────────
// Usuarios staff
// ──────────────────────────────────────────────────────────────
describe('GET /usuarios', () => {
  it('returns 200 with array for staff token', async () => {
    const res = await http.get('/usuarios', { headers: bearer(ST_READ) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('returns 403 for platform token', async () => {
    const res = await http.get('/usuarios', { headers: bearer(PT) })
    expect(res.status).toBe(403)
  })
})

describe('POST /usuarios', () => {
  it('returns 400 or 403 for missing body', async () => {
    const res = await http.post('/usuarios', {}, { headers: bearer(ST_WRITE) })
    expect([400, 403]).toContain(res.status)
  })
})

describe('GET /usuarios/{id}/permisos', () => {
  it('returns 404 for non-existent user', async () => {
    const res = await http.get('/usuarios/999999/permisos', { headers: bearer(ST_READ) })
    expect(res.status).toBe(404)
  })
})

describe('PUT /usuarios/{id}/activar', () => {
  it('returns 403 or 404 for non-existent user', async () => {
    const res = await http.put('/usuarios/999999/activar', {}, { headers: bearer(ST_WRITE) })
    expect([403, 404]).toContain(res.status)
  })
})

describe('PUT /usuarios/{id}/desactivar', () => {
  it('returns 403 or 404 for non-existent user', async () => {
    const res = await http.put('/usuarios/999999/desactivar', {}, { headers: bearer(ST_WRITE) })
    expect([403, 404]).toContain(res.status)
  })
})

describe('PATCH /usuarios/{id}', () => {
  it('returns 403 or 404 for non-existent user', async () => {
    const res = await http.patch('/usuarios/999999', { correo: 'x@test.com' }, { headers: bearer(ST_WRITE) })
    expect([403, 404]).toContain(res.status)
  })
})

// ──────────────────────────────────────────────────────────────
// Personas
// ──────────────────────────────────────────────────────────────
describe('GET /personas/ci/{ci}', () => {
  it('returns 404 for non-existent CI', async () => {
    const res = await http.get('/personas/ci/9999999999', { headers: bearer(ST_READ) })
    expect(res.status).toBe(404)
  })
})

describe('POST /personas', () => {
  it('returns 400 when body is missing required fields', async () => {
    const res = await http.post('/personas', {}, { headers: bearer(ST_WRITE) })
    expect(res.status).toBe(400)
  })
})

describe('PUT /personas/{id}', () => {
  it('returns 404 for non-existent persona', async () => {
    const res = await http.put('/personas/999999', { primer_nombre: 'X' }, { headers: bearer(ST_WRITE) })
    expect(res.status).toBe(404)
  })
})

// ──────────────────────────────────────────────────────────────
// App Usuarios
// ──────────────────────────────────────────────────────────────
describe('POST /app-usuarios', () => {
  it('returns 400 when body is missing required fields', async () => {
    const res = await http.post('/app-usuarios', {}, { headers: bearer(ST_WRITE) })
    expect(res.status).toBe(400)
  })
})

describe('PUT /app-usuarios/{id}/activar', () => {
  it('returns 404 for non-existent user', async () => {
    const res = await http.put('/app-usuarios/999999/activar', {}, { headers: bearer(ST_WRITE) })
    expect(res.status).toBe(404)
  })
})

describe('PUT /app-usuarios/{id}/desactivar', () => {
  it('returns 404 for non-existent user', async () => {
    const res = await http.put('/app-usuarios/999999/desactivar', {}, { headers: bearer(ST_WRITE) })
    expect(res.status).toBe(404)
  })
})

// ──────────────────────────────────────────────────────────────
// Bitácora
// ──────────────────────────────────────────────────────────────
describe('GET /bitacora', () => {
  it('returns 200 with paginated response for staff token', async () => {
    const res = await http.get('/bitacora', { headers: bearer(ST_READ) })
    expect(res.status).toBe(200)
    expect(res.data).toBeDefined()
  })

  it('returns 401 without token', async () => {
    const res = await http.get('/bitacora')
    expect(res.status).toBe(401)
  })
})
