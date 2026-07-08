/**
 * Happy-path integration tests — Auth Service (port 8080)
 *
 * Validates 2xx responses and verifies every response field against the real
 * Java DTOs (snake_case via Jackson).  Use the correct request field names:
 *   correo (not email), id_compania (not id_company), refresh_token, etc.
 *
 * Real-credential tests are gated behind env vars and skipped when absent:
 *   TEST_PLATFORM_EMAIL / TEST_PLATFORM_PASSWORD
 *   TEST_STAFF_EMAIL / TEST_STAFF_PASSWORD / TEST_STAFF_COMPANIA_ID
 *
 * Run:
 *   npx vitest run --config vitest.integration.config.ts auth-happy-path.test.ts
 */
import axios from 'axios'
import { beforeAll, afterAll, describe, it, expect } from 'vitest'
import { http, bearer } from './helpers/client'
import { platformToken, staffToken } from './helpers/jwt'

// ── Real-credential env vars ──────────────────────────────────────────────────
const PLATFORM_EMAIL    = process.env.TEST_PLATFORM_EMAIL
const PLATFORM_PASSWORD = process.env.TEST_PLATFORM_PASSWORD
const STAFF_EMAIL       = process.env.TEST_STAFF_EMAIL
const STAFF_PASSWORD    = process.env.TEST_STAFF_PASSWORD
const STAFF_COMPANIA_ID = process.env.TEST_STAFF_COMPANIA_ID
  ? Number(process.env.TEST_STAFF_COMPANIA_ID) : undefined

// ── Platform token (super_admin) signed with dev secret ───────────────────────
const PT = platformToken()

// ── Lifecycle state ───────────────────────────────────────────────────────────
let companiaId: number | null = null
let sucursalId: number | null = null
let createdPersonaId: number | null = null
let createdPersonaCi: string | null = null
let createdRolId:     number | null = null
let createdUserId:    number | null = null

const TS = Date.now()

// Generates a staff token scoped to the discovered company (or fallback 99999).
// Called inside test bodies, after beforeAll has populated companiaId.
function st(permisos: string[], idCompania: number = companiaId ?? 99999): string {
  return staffToken(permisos, 99991, idCompania)
}

// ── Setup: discover a real compañía from the running backend ──────────────────
beforeAll(async () => {
  const res = await http.get('/platform/companias', { headers: bearer(PT) })
  if (res.status === 200 && Array.isArray(res.data) && res.data.length > 0) {
    companiaId = res.data[0].id as number
    const suc = await http.get(`/platform/companias/${companiaId}/sucursales`, {
      headers: bearer(PT),
    })
    if (suc.status === 200 && Array.isArray(suc.data) && suc.data.length > 0) {
      sucursalId = suc.data[0].id as number
    }
  }
})

// ── Cleanup: best-effort deactivate created user and delete created role ───────
afterAll(async () => {
  if (createdUserId !== null && companiaId !== null) {
    await http
      .put(`/usuarios/${createdUserId}/desactivar`, {},
        { headers: bearer(st(['usuarios:crear'], companiaId)) })
      .catch(() => {})
  }
  if (createdRolId !== null && companiaId !== null) {
    await http
      .delete(`/roles/${createdRolId}`,
        { headers: bearer(st(['roles:crear'], companiaId)) })
      .catch(() => {})
  }
})

// ─────────────────────────────────────────────────────────────────────────────
// 1. Connectivity
// ─────────────────────────────────────────────────────────────────────────────
describe('GET /actuator/health — backend connectivity', () => {
  it('returns 200 with status UP', async () => {
    const baseUrl = (process.env.TEST_API_URL ?? 'http://localhost:8080/api/v1')
      .replace('/api/v1', '')
    const healthClient = axios.create({
      baseURL: baseUrl,
      validateStatus: () => true,
      timeout: 5_000,
    })
    const res = await healthClient.get('/actuator/health')
    expect(res.status).toBe(200)
    expect(res.data.status).toBe('UP')
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 2. GET /auth/companias-por-correo (public)
// ─────────────────────────────────────────────────────────────────────────────
describe('GET /auth/companias-por-correo', () => {
  it('200 — returns empty array for unknown correo', async () => {
    const res = await http.get('/auth/companias-por-correo', {
      params: { correo: `nobody_hp_${TS}@example.com` },
    })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    expect(res.data).toHaveLength(0)
  })

  it('200 — returns empty array when correo param is absent (defaults to "")', async () => {
    const res = await http.get('/auth/companias-por-correo')
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('200 — items have id (number) and nombre (string) for a known correo [skips if no creds]', async () => {
    if (!STAFF_EMAIL) return
    const res = await http.get('/auth/companias-por-correo', {
      params: { correo: STAFF_EMAIL },
    })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    if (res.data.length > 0) {
      expect(typeof res.data[0].id).toBe('number')
      expect(typeof res.data[0].nombre).toBe('string')
    }
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 3. Platform login — full flow (login → refresh → logout)
// ─────────────────────────────────────────────────────────────────────────────
describe('Platform auth flow — login → refresh → logout', () => {
  let accessToken:  string | null = null
  let refreshToken: string | null = null

  it.skipIf(!PLATFORM_EMAIL)(
    'POST /auth/platform/login — 200 with LoginPlatformResponse shape',
    async () => {
      const res = await http.post('/auth/platform/login', {
        correo:   PLATFORM_EMAIL,
        password: PLATFORM_PASSWORD,
      })
      expect(res.status).toBe(200)
      // Top-level fields
      expect(typeof res.data.access_token).toBe('string')
      expect(typeof res.data.refresh_token).toBe('string')
      expect(typeof res.data.expires_in).toBe('number')
      expect(res.data.expires_in).toBeGreaterThan(0)
      // Nested usuario
      const u = res.data.usuario
      expect(typeof u.id).toBe('number')
      expect(typeof u.nombre).toBe('string')
      expect(['super_admin', 'soporte', 'viewer']).toContain(u.rol_plataforma)

      accessToken  = res.data.access_token
      refreshToken = res.data.refresh_token
    },
  )

  it.skipIf(!PLATFORM_EMAIL)(
    'POST /auth/refresh — 200 with new access_token (token is single-use)',
    async () => {
      if (!refreshToken) return
      const res = await http.post('/auth/refresh', { refresh_token: refreshToken })
      expect(res.status).toBe(200)
      expect(typeof res.data.access_token).toBe('string')
      expect(typeof res.data.expires_in).toBe('number')
      expect(res.data.access_token).not.toBe(accessToken)
      accessToken  = res.data.access_token
      refreshToken = null
    },
  )

  it.skipIf(!PLATFORM_EMAIL)(
    'POST /auth/logout — 2xx with valid Bearer token',
    async () => {
      if (!accessToken) return
      const res = await http.post('/auth/logout', {}, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })
      expect(res.status).toBeLessThan(300)
    },
  )
})

// ─────────────────────────────────────────────────────────────────────────────
// 4. Staff login — full flow (login → refresh → logout)
// ─────────────────────────────────────────────────────────────────────────────
describe('Staff auth flow — login → refresh → logout', () => {
  let accessToken:  string | null = null
  let refreshToken: string | null = null

  it.skipIf(!STAFF_EMAIL || !STAFF_COMPANIA_ID)(
    'POST /auth/login — 200 with LoginStaffResponse shape',
    async () => {
      const res = await http.post('/auth/login', {
        correo:      STAFF_EMAIL,
        password:    STAFF_PASSWORD,
        id_compania: STAFF_COMPANIA_ID,
      })
      expect(res.status).toBe(200)
      expect(typeof res.data.access_token).toBe('string')
      expect(typeof res.data.refresh_token).toBe('string')
      expect(typeof res.data.expires_in).toBe('number')
      expect(typeof res.data.requiere_cambio_pwd).toBe('boolean')
      const u = res.data.usuario
      expect(typeof u.id).toBe('number')
      expect(typeof u.nombre).toBe('string')
      expect(typeof u.correo).toBe('string')
      expect(typeof u.id_rol).toBe('number')
      expect(typeof u.nombre_rol).toBe('string')

      accessToken  = res.data.access_token
      refreshToken = res.data.refresh_token
    },
  )

  it.skipIf(!STAFF_EMAIL || !STAFF_COMPANIA_ID)(
    'POST /auth/refresh — 200 using staff refresh token',
    async () => {
      if (!refreshToken) return
      const res = await http.post('/auth/refresh', { refresh_token: refreshToken })
      expect(res.status).toBe(200)
      expect(typeof res.data.access_token).toBe('string')
      expect(res.data.access_token).not.toBe(accessToken)
      accessToken = res.data.access_token
    },
  )

  it.skipIf(!STAFF_EMAIL || !STAFF_COMPANIA_ID)(
    'POST /auth/logout — 2xx with valid staff Bearer token',
    async () => {
      if (!accessToken) return
      const res = await http.post('/auth/logout', {}, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })
      expect(res.status).toBeLessThan(300)
    },
  )
})

// ─────────────────────────────────────────────────────────────────────────────
// 5. Staff GET endpoints — response shape validation
// ─────────────────────────────────────────────────────────────────────────────
describe('GET /roles — RolResponse shape', () => {
  it('200 — array with id (number), nombre (string), descripcion', async () => {
    const res = await http.get('/roles', { headers: bearer(st(['roles:leer'])) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    if (res.data.length > 0) {
      const r = res.data[0]
      expect(typeof r.id).toBe('number')
      expect(typeof r.nombre).toBe('string')
      expect('descripcion' in r).toBe(true)
    }
  })
})

describe('GET /permisos — PermisoResponse shape', () => {
  it('200 — array with id, nombre (string), modulo (string), descripcion', async () => {
    const res = await http.get('/permisos', { headers: bearer(st(['roles:leer'])) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    if (res.data.length > 0) {
      const p = res.data[0]
      expect(typeof p.id).toBe('number')
      expect(typeof p.nombre).toBe('string')
      expect(typeof p.modulo).toBe('string')
      expect('descripcion' in p).toBe(true)
    }
  })
})

describe('GET /usuarios — UsuarioStaffResponse shape', () => {
  it('200 — array with id, nombre, correo, id_rol, nombre_rol, activo (boolean), ultimo_acceso', async () => {
    const res = await http.get('/usuarios', { headers: bearer(st(['usuarios:leer'])) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    if (res.data.length > 0) {
      const u = res.data[0]
      expect(typeof u.id).toBe('number')
      expect(typeof u.nombre).toBe('string')
      expect(typeof u.correo).toBe('string')
      expect(typeof u.id_rol).toBe('number')
      expect(typeof u.nombre_rol).toBe('string')
      expect(typeof u.activo).toBe('boolean')
      expect('ultimo_acceso' in u).toBe(true)
    }
  })
})

describe('GET /bitacora — BitacoraPagedResponse shape', () => {
  it('200 — response has total (number), pagina (number), datos (array)', async () => {
    const res = await http.get('/bitacora', { headers: bearer(st(['usuarios:leer'])) })
    expect(res.status).toBe(200)
    expect(typeof res.data.total).toBe('number')
    expect(typeof res.data.pagina).toBe('number')
    expect(Array.isArray(res.data.datos)).toBe(true)
  })

  it('200 — datos.length respects ?size=3', async () => {
    const res = await http.get('/bitacora', {
      params: { page: 0, size: 3 },
      headers: bearer(st(['usuarios:leer'])),
    })
    expect(res.status).toBe(200)
    expect(res.data.datos.length).toBeLessThanOrEqual(3)
  })

  it('entry shape has id, modulo, accion, fecha when datos is non-empty', async () => {
    const res = await http.get('/bitacora', { headers: bearer(st(['usuarios:leer'])) })
    expect(res.status).toBe(200)
    if (res.data.datos.length > 0) {
      const e = res.data.datos[0]
      expect('id' in e).toBe(true)
      expect('modulo' in e).toBe(true)
      expect('accion' in e).toBe(true)
      expect('fecha' in e).toBe(true)
    }
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 6. Platform GET endpoints — response shape validation
// ─────────────────────────────────────────────────────────────────────────────
describe('GET /platform/companias — CompaniaBasicaResponse shape', () => {
  it('200 — array with id (number), nombre (string), activo (boolean)', async () => {
    const res = await http.get('/platform/companias', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    if (res.data.length > 0) {
      const c = res.data[0]
      expect(typeof c.id).toBe('number')
      expect(typeof c.nombre).toBe('string')
      expect(typeof c.activo).toBe('boolean')
    }
  })
})

describe('GET /platform/roles — RolPlataformaResponse shape', () => {
  it('200 — array with id (number), nombre (string)', async () => {
    const res = await http.get('/platform/roles', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    if (res.data.length > 0) {
      const r = res.data[0]
      expect(typeof r.id).toBe('number')
      expect(typeof r.nombre).toBe('string')
    }
  })
})

describe('GET /platform/usuarios — PlatformUsuarioResponse shape', () => {
  it('200 — array with id, nombre, correo, rol_plataforma, activo', async () => {
    const res = await http.get('/platform/usuarios', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    if (res.data.length > 0) {
      const u = res.data[0]
      expect(typeof u.id).toBe('number')
      expect(typeof u.nombre).toBe('string')
      expect(typeof u.correo).toBe('string')
      expect(typeof u.rol_plataforma).toBe('string')
      expect(typeof u.activo).toBe('boolean')
    }
  })
})

describe('GET /platform/permisos — PermisoPlataformaResponse shape', () => {
  it('200 — array with id, nombre, modulo, id_compania, id_sucursal, nombre_sucursal', async () => {
    const res = await http.get('/platform/permisos', { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    if (res.data.length > 0) {
      const p = res.data[0]
      expect(typeof p.id).toBe('number')
      expect(typeof p.nombre).toBe('string')
      expect(typeof p.modulo).toBe('string')
      expect('id_compania' in p).toBe(true)
      expect('id_sucursal' in p).toBe(true)
      expect('nombre_sucursal' in p).toBe(true)
    }
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 7. Persona lifecycle — POST /personas → GET /personas/ci/{ci}  (public, no auth)
// ─────────────────────────────────────────────────────────────────────────────
describe('Persona lifecycle (public endpoints)', () => {
  const ci = `IT${TS % 1_000_000_000}`

  it('POST /personas — 201 with PersonaResponse shape', async () => {
    const res = await http.post('/personas', {
      ci,
      nombre: `HP Test ${TS}`,
      correo: `hp_persona_${TS}@test.com`,
    })
    expect(res.status).toBe(201)
    expect(typeof res.data.id).toBe('number')
    expect(res.data.ci).toBe(ci)
    expect(typeof res.data.nombre).toBe('string')
    expect('telefono' in res.data).toBe(true)
    expect('correo' in res.data).toBe(true)
    expect('foto_url' in res.data).toBe(true)
    createdPersonaId = res.data.id as number
    createdPersonaCi = ci
  })

  it('GET /personas/ci/{ci} — 200 returns the created persona', async () => {
    if (!createdPersonaCi) return
    const res = await http.get(`/personas/ci/${createdPersonaCi}`)
    expect(res.status).toBe(200)
    expect(res.data.id).toBe(createdPersonaId)
    expect(res.data.ci).toBe(createdPersonaCi)
    expect(typeof res.data.nombre).toBe('string')
  })

  it('POST /personas — 409 Conflict on duplicate ci', async () => {
    if (!createdPersonaCi) return
    const res = await http.post('/personas', {
      ci,
      nombre: 'Duplicate',
      correo: `dup_hp_${TS}@test.com`,
    })
    expect(res.status).toBe(409)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 8. Staff role lifecycle  [skips entirely if no compañía in DB]
// ─────────────────────────────────────────────────────────────────────────────
describe('Staff role lifecycle — POST /roles → GET /roles/{id}', () => {
  it('POST /roles — 201 with RolResponse shape', async () => {
    if (!companiaId) return
    const res = await http.post(
      '/roles',
      { nombre: `Rol HP ${TS}`, descripcion: 'Happy-path integration test' },
      { headers: bearer(st(['roles:crear'])) },
    )
    expect(res.status).toBe(201)
    expect(typeof res.data.id).toBe('number')
    expect(res.data.nombre).toBe(`Rol HP ${TS}`)
    expect('descripcion' in res.data).toBe(true)
    createdRolId = res.data.id as number
  })

  it('GET /roles/{id} — 200 returns the created role', async () => {
    if (!createdRolId || !companiaId) return
    const res = await http.get(`/roles/${createdRolId}`, {
      headers: bearer(st(['roles:leer'])),
    })
    expect(res.status).toBe(200)
    expect(res.data.id).toBe(createdRolId)
    expect(typeof res.data.nombre).toBe('string')
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 9. Staff user lifecycle  [skips if prerequisites are missing]
// ─────────────────────────────────────────────────────────────────────────────
describe('Staff user lifecycle — POST /usuarios → permisos → desactivar → activar', () => {
  it('POST /usuarios — 201 with UsuarioStaffResponse shape', async () => {
    if (!createdPersonaId || !createdRolId || !companiaId || !sucursalId) return
    const res = await http.post(
      '/usuarios',
      {
        id_persona:        createdPersonaId,
        correo:            `hp_staff_${TS}@test.com`,
        id_rol:            createdRolId,
        id_sucursal:       sucursalId,
        password_temporal: `HappyPath${TS}!`,
      },
      { headers: bearer(st(['usuarios:crear'])) },
    )
    expect(res.status).toBe(201)
    expect(typeof res.data.id).toBe('number')
    expect(res.data.correo).toBe(`hp_staff_${TS}@test.com`)
    expect(typeof res.data.activo).toBe('boolean')
    expect(res.data.id_rol).toBe(createdRolId)
    expect(typeof res.data.nombre_rol).toBe('string')
    createdUserId = res.data.id as number
  })

  it('GET /usuarios/{id}/permisos — 200 with permission array', async () => {
    if (!createdUserId || !companiaId) return
    const res = await http.get(`/usuarios/${createdUserId}/permisos`, {
      headers: bearer(st(['usuarios:leer'])),
    })
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
  })

  it('PUT /usuarios/{id}/desactivar — 200', async () => {
    if (!createdUserId || !companiaId) return
    const res = await http.put(`/usuarios/${createdUserId}/desactivar`, {}, {
      headers: bearer(st(['usuarios:crear'])),
    })
    expect(res.status).toBe(200)
  })

  it('PUT /usuarios/{id}/activar — 200 (restores user)', async () => {
    if (!createdUserId || !companiaId) return
    const res = await http.put(`/usuarios/${createdUserId}/activar`, {}, {
      headers: bearer(st(['usuarios:crear'])),
    })
    expect(res.status).toBe(200)
  })
})
