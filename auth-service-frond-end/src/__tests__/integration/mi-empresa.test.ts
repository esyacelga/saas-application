/**
 * Integration tests — /mi-empresa endpoints (happy path)
 * Target: platform-service (port 8081)
 *
 * Staff JWTs for platform-service are HS256, signed with PLATFORM_JWT_SECRET.
 * Setup: discovers a real compania + sucursal using a super_admin token, then
 * builds a staff JWT scoped to those IDs. Tests are silently skipped when
 * no company exists in the database.
 *
 * Requires Cloudinary to be configured in platform-service for the logo test.
 *
 * Run:
 *   npx vitest run --config vitest.integration.config.ts mi-empresa.test.ts
 */
import { createHmac } from 'node:crypto'
import { beforeAll, afterAll, describe, it, expect } from 'vitest'
import { platformHttp, bearer } from './helpers/client'

// ── HS256 signer for platform-service ────────────────────────────────────────
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
  const body   = b64url(Buffer.from(JSON.stringify(full)))
  const key    = Buffer.from(PLATFORM_B64_SECRET, 'base64')
  const sig    = b64url(createHmac('sha256', key).update(`${header}.${body}`).digest())
  return `${header}.${body}.${sig}`
}

// Platform super_admin — used only for setup (discovering real IDs)
const PT = signHS256({ sub: '99990', tipo: 'plataforma', rol_plataforma: 'super_admin', nombre: 'Test SA' })

// ── Setup state ───────────────────────────────────────────────────────────────
let staffJwt:        string | null = null
let originalNombre:  string | null = null  // compania nombre before mutation
let originalNomSuc:  string | null = null  // sucursal nombre before mutation

beforeAll(async () => {
  // Discover a real company from the running backend
  const cRes = await platformHttp.get('/companias', { headers: bearer(PT) })
  if (cRes.status !== 200 || !Array.isArray(cRes.data) || cRes.data.length === 0) return

  const companiaId = cRes.data[0].id as number

  // Discover its first branch
  const sRes = await platformHttp.get(`/companias/${companiaId}/sucursales`, { headers: bearer(PT) })
  if (sRes.status !== 200 || !Array.isArray(sRes.data) || sRes.data.length === 0) return

  const sucursalId = sRes.data[0].id as number

  staffJwt = signHS256({
    sub:         '99991',
    tipo:        'staff',
    id_compania: companiaId,
    id_sucursal: sucursalId,
    id_rol:      1,
    nombre:      'Test Staff Mi Empresa',
    permisos:    [],
  })
})

// Restore mutations so repeated runs start from the same state
afterAll(async () => {
  if (!staffJwt) return
  await Promise.all([
    originalNombre
      ? platformHttp.patch('/mi-empresa', { nombre: originalNombre }, { headers: bearer(staffJwt) }).catch(() => {})
      : Promise.resolve(),
    originalNomSuc
      ? platformHttp.patch('/mi-empresa/sucursal', { nombre: originalNomSuc }, { headers: bearer(staffJwt) }).catch(() => {})
      : Promise.resolve(),
  ])
})

// ── GET /mi-empresa ───────────────────────────────────────────────────────────
describe('GET /mi-empresa', () => {
  it('returns 200 with company fields for staff token', async () => {
    if (!staffJwt) return
    const res = await platformHttp.get('/mi-empresa', { headers: bearer(staffJwt) })
    expect(res.status).toBe(200)
    expect(typeof res.data.id).toBe('number')
    expect(typeof res.data.nombre).toBe('string')
    expect(typeof res.data.ruc).toBe('string')
    expect(typeof res.data.activo).toBe('boolean')
    originalNombre = res.data.nombre as string
  })

  it('returns 403 for platform token', async () => {
    const res = await platformHttp.get('/mi-empresa', { headers: bearer(PT) })
    expect(res.status).toBe(403)
  })

  it('returns 401 without token', async () => {
    const res = await platformHttp.get('/mi-empresa')
    expect(res.status).toBe(401)
  })
})

// ── PATCH /mi-empresa ─────────────────────────────────────────────────────────
describe('PATCH /mi-empresa', () => {
  it('updates company fields and returns updated data', async () => {
    if (!staffJwt || !originalNombre) return
    const nuevo = `${originalNombre} [test]`
    const res = await platformHttp.patch(
      '/mi-empresa',
      { nombre: nuevo, telefono: '0999123456', whatsapp: '0999123456', correo: 'integration@test.com' },
      { headers: bearer(staffJwt) },
    )
    expect(res.status).toBe(200)
    expect(res.data.nombre).toBe(nuevo)
    expect(res.data.telefono).toBe('0999123456')
  })
})

// ── POST /mi-empresa/logo ─────────────────────────────────────────────────────
describe('POST /mi-empresa/logo', () => {
  it('uploads a PNG and returns company with logo_url', async () => {
    if (!staffJwt) return

    // Minimal valid 1×1 gray pixel PNG (IHDR + IDAT + IEND, grayscale 8-bit)
    const PNG_1x1 = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA6fptVAAAACklEQVQI12Ng' +
      'AAAAAgAB4iG8MwAAAABJRU5ErkJggg==',
      'base64',
    )

    const form = new FormData()
    form.append('file', new Blob([PNG_1x1], { type: 'image/png' }), 'logo-test.png')

    // Do NOT set Content-Type manually — axios adds the boundary automatically
    const res = await platformHttp.post('/mi-empresa/logo', form, {
      headers: bearer(staffJwt),
    })
    expect(res.status).toBe(200)
    expect(typeof res.data.logo_url).toBe('string')
    expect(res.data.logo_url).toMatch(/^https?:\/\//)
  })
})

// ── GET /mi-empresa/sucursal ──────────────────────────────────────────────────
describe('GET /mi-empresa/sucursal', () => {
  it('returns 200 with branch fields for staff token', async () => {
    if (!staffJwt) return
    const res = await platformHttp.get('/mi-empresa/sucursal', { headers: bearer(staffJwt) })
    expect(res.status).toBe(200)
    expect(typeof res.data.id).toBe('number')
    expect(typeof res.data.nombre).toBe('string')
    expect(typeof res.data.qr_token).toBe('string')
    expect(res.data.qr_token.length).toBeGreaterThan(0)
    originalNomSuc = res.data.nombre as string
  })

  it('returns 403 for platform token', async () => {
    const res = await platformHttp.get('/mi-empresa/sucursal', { headers: bearer(PT) })
    expect(res.status).toBe(403)
  })

  it('returns 401 without token', async () => {
    const res = await platformHttp.get('/mi-empresa/sucursal')
    expect(res.status).toBe(401)
  })
})

// ── PATCH /mi-empresa/sucursal ────────────────────────────────────────────────
describe('PATCH /mi-empresa/sucursal', () => {
  it('updates branch name and returns updated data', async () => {
    if (!staffJwt || !originalNomSuc) return
    const nuevo = `${originalNomSuc} [test]`
    const res = await platformHttp.patch(
      '/mi-empresa/sucursal',
      { nombre: nuevo, direccion: 'Av. Test 123' },
      { headers: bearer(staffJwt) },
    )
    expect(res.status).toBe(200)
    expect(res.data.nombre).toBe(nuevo)
  })
})

// ── POST /mi-empresa/sucursal/qr/renovar ─────────────────────────────────────
describe('POST /mi-empresa/sucursal/qr/renovar', () => {
  it('generates a new QR token different from the current one', async () => {
    if (!staffJwt) return

    const before = await platformHttp.get('/mi-empresa/sucursal', { headers: bearer(staffJwt) })
    if (before.status !== 200) return
    const oldToken = before.data.qr_token as string

    const res = await platformHttp.post('/mi-empresa/sucursal/qr/renovar', {}, {
      headers: bearer(staffJwt),
    })
    expect(res.status).toBe(200)
    expect(typeof res.data.qr_token).toBe('string')
    expect(res.data.qr_token).not.toBe(oldToken)
    expect(res.data.qr_token_expira).toBeDefined()
  })

  it('returns 403 for platform token', async () => {
    const res = await platformHttp.post('/mi-empresa/sucursal/qr/renovar', {}, { headers: bearer(PT) })
    expect(res.status).toBe(403)
  })

  it('returns 401 without token', async () => {
    const res = await platformHttp.post('/mi-empresa/sucursal/qr/renovar', {})
    expect(res.status).toBe(401)
  })
})
