/**
 * Integration tests — Subscription lifecycle & advanced platform-service operations
 * Target: platform-service on port 8081  (TEST_PLATFORM_URL / application.yml → server.port: 8081)
 *
 * Happy-path coverage NOT present in platform-module.test.ts:
 *   GET  /modulos/check                        – public endpoint, no token required
 *   PUT  /sucursales/{id}                      – update branch name/address
 *   POST /companias/{id}/suscripcion/renovar   – renew active subscription
 *   POST /companias/{id}/suscripcion/upgrade   – switch to more expensive plan
 *   POST /companias/{id}/suscripcion/downgrade – schedule cheaper plan for next period
 *   POST /companias/wizard                     – full wizard gym + user registration
 *
 * Token generation mirrors JwtAuthenticationFilter:
 *   Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))  ← HS256 over base64-decoded key
 *
 * Run alone:
 *   TEST_PLATFORM_URL=http://localhost:8081/api/v1 npx vitest run platform-suscripcion.test.ts
 */

import { createHmac } from 'node:crypto'
import { beforeAll, afterAll, describe, it, expect } from 'vitest'
import { platformHttp, http, bearer } from './helpers/client'

// ── JWT helpers (HS256, mirrors platform-service JwtAuthenticationFilter) ─────

const B64_SECRET =
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
  const key    = Buffer.from(B64_SECRET, 'base64')
  const sig    = b64url(createHmac('sha256', key).update(`${header}.${body}`).digest())
  return `${header}.${body}.${sig}`
}

const PT      = signHS256({ sub: '99990', tipo: 'plataforma', rol_plataforma: 'super_admin', nombre: 'Test SA' })
const SOPORTE = signHS256({ sub: '99991', tipo: 'plataforma', rol_plataforma: 'soporte',     nombre: 'Test Soporte' })
const STAFF   = signHS256({ sub: '99993', tipo: 'staff', id_compania: 99999, id_sucursal: 99999, id_rol: 1, nombre: 'Test Staff', permisos: [] })

// Staff token with persona:crear permission (used for wizard persona setup)
const ST_PERSONAS = signHS256({
  sub: '99994', tipo: 'staff', id_compania: 99999, id_sucursal: 99999, id_rol: 1,
  nombre: 'Test Staff Personas', permisos: ['personas:crear', 'personas:leer'],
})

const TS = Date.now()

// ── Shared state populated in beforeAll ───────────────────────────────────────

let planBasicoId:     number | null = null  // cheap plan — gym is registered on this
let planPremiumId:    number | null = null  // expensive plan — used for upgrade
let caracCodigo:      string | null = null  // module code assigned to planBasico
let companiaId:       number | null = null
let sucursalId:       number | null = null
let suscripcionId:    number | null = null  // current compania_plan id
let personaId:        number | null = null  // created via auth-service for wizard test
let upgradeSucceeded: boolean       = false // guard for downgrade tests

// ── Setup ─────────────────────────────────────────────────────────────────────

// Wraps a call so it never throws; returns null on timeout/error.
async function tryCall<T>(fn: () => Promise<T>): Promise<T | null> {
  return fn().catch(() => null)
}

beforeAll(async () => {
  // warm-up: loop until the R2DBC pool responds (cold-start can exceed 30 s on first hit).
  // We try up to 5 times with 20 s each before giving up.
  for (let i = 0; i < 5; i++) {
    const r = await platformHttp.get('/planes', { headers: bearer(PT), timeout: 20_000 }).catch(() => null)
    if (r?.status !== undefined) break
  }

  // 1 — Create basic plan (cheap, used for the gym subscription)
  const rBasico = await tryCall(() => platformHttp.post('/planes', {
    nombre:        `Plan Básico SUS ${TS}`,
    descripcion:   'Basic plan – suscripcion tests',
    precioMensual: 19.99,
  }, { headers: bearer(PT) }))
  if (rBasico?.status === 201) planBasicoId = rBasico.data.id as number

  // 2 — Create premium plan (more expensive, used for upgrade; cheaper for downgrade)
  const rPremium = await tryCall(() => platformHttp.post('/planes', {
    nombre:        `Plan Premium SUS ${TS}`,
    descripcion:   'Premium plan – suscripcion tests',
    precioMensual: 59.99,
  }, { headers: bearer(PT) }))
  if (rPremium?.status === 201) planPremiumId = rPremium.data.id as number

  // 3 — Create caracteristica and assign it to the basic plan (for modulos/check)
  const codigo = `sus_mod_${TS}`
  const rCarac = await tryCall(() => platformHttp.post('/caracteristicas', {
    codigo,
    nombre:  `Módulo SUS ${TS}`,
    modulo:  codigo,
  }, { headers: bearer(PT) }))
  if (rCarac?.status === 201) {
    caracCodigo = codigo
    if (planBasicoId) {
      await tryCall(() => platformHttp.put(`/planes/${planBasicoId}/caracteristicas`,
        { caracteristicaIds: [rCarac.data.id] },
        { headers: bearer(PT) },
      ))
    }
  }

  // 4 — Register a gym on the basic plan
  if (planBasicoId) {
    const ruc = `${40000000000 + (TS % 1000000000)}`
    const rGym = await tryCall(() => platformHttp.post('/companias', {
      nombre:          `Gym SUS Test ${TS}`,
      ruc,
      correo:          `gym_sus_${TS}@test.com`,
      idPlan:          planBasicoId,
      nombreSucursal:  'Sede SUS Principal',
      direccionSucursal: 'Av. Suscripcion 123',
    }, { headers: bearer(PT) }))

    if (rGym?.status === 201) {
      companiaId    = rGym.data.idCompania   as number
      sucursalId    = rGym.data.idSucursal   as number
      suscripcionId = rGym.data.idCompaniaPlan as number
    }
  }

  // 5 — Create persona via auth-service (for wizard test); skip gracefully if unavailable
  // CI kept short (≤12 chars) to avoid DB column-length errors
  const rPersona = await tryCall(() => http.post('/personas', {
    ci:     `WIZ${TS % 1_000_000_000}`,
    nombre: `Test Wizard FE ${TS}`,
    correo: `wiz_persona_${TS}@test.com`,
  }, { headers: bearer(ST_PERSONAS) }))

  if (rPersona?.status === 201) {
    personaId = rPersona.data.id as number
  }
}, 300_000)

// ── Cleanup ───────────────────────────────────────────────────────────────────

afterAll(async () => {
  if (companiaId) {
    await platformHttp.put(`/companias/${companiaId}/suspender`,
      { motivo: 'Cleanup suscripcion IT test' },
      { headers: bearer(PT) },
    ).catch(() => {})
  }
  if (planBasicoId) {
    await platformHttp.put(`/planes/${planBasicoId}/desactivar`, {},
      { headers: bearer(PT) },
    ).catch(() => {})
  }
  if (planPremiumId) {
    await platformHttp.put(`/planes/${planPremiumId}/desactivar`, {},
      { headers: bearer(PT) },
    ).catch(() => {})
  }
})

// ─────────────────────────────────────────────────────────────────────────────
// GET /modulos/check  — public endpoint, no token required
// ─────────────────────────────────────────────────────────────────────────────

describe('GET /modulos/check — public endpoint', () => {
  it('returns 200 permitido=true when plan includes the requested module (skips if no gym)', async () => {
    if (!companiaId || !caracCodigo) return
    const res = await platformHttp.get('/modulos/check', {
      params: { id_compania: companiaId, codigo: caracCodigo },
    })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('permitido', true)
    expect(res.data).toHaveProperty('plan')
    expect(typeof res.data.plan).toBe('string')
  })

  it('returns 403 modulo_no_incluido when module is not in the plan (skips if no gym)', async () => {
    if (!companiaId) return
    const res = await platformHttp.get('/modulos/check', {
      params: { id_compania: companiaId, codigo: 'modulo_que_no_existe' },
    })
    expect(res.status).toBe(403)
    expect(res.data).toHaveProperty('permitido', false)
    expect(res.data.razon).toBe('modulo_no_incluido')
  })

  it('returns 402 plan_vencido for a company without any subscription', async () => {
    const res = await platformHttp.get('/modulos/check', {
      params: { id_compania: 99999999, codigo: 'cualquier_modulo' },
    })
    expect(res.status).toBe(402)
    expect(res.data).toHaveProperty('permitido', false)
    expect(res.data.razon).toBe('plan_vencido')
  })

  it('returns 400 when required query params are missing', async () => {
    const res = await platformHttp.get('/modulos/check')
    expect(res.status).toBeGreaterThanOrEqual(400)
    expect(res.status).toBeLessThan(500)
  })

  it('accepts request from staff token (endpoint is permitAll)', async () => {
    if (!companiaId || !caracCodigo) return
    const res = await platformHttp.get('/modulos/check', {
      headers: bearer(STAFF),
      params:  { id_compania: companiaId, codigo: caracCodigo },
    })
    // permitAll — any token or no token is accepted
    expect(res.status).toBeLessThan(500)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// PUT /sucursales/{id}  — update branch name and address
// ─────────────────────────────────────────────────────────────────────────────

describe('PUT /sucursales/{id} — update branch', () => {
  it('returns 200 with updated name and address (skips if no sucursal)', async () => {
    if (!sucursalId) return
    const nuevoNombre = `Sede SUS Actualizada ${TS}`
    const res = await platformHttp.put(`/sucursales/${sucursalId}`, {
      nombre:    nuevoNombre,
      direccion: 'Av. Actualizada 456',
    }, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data.nombre).toBe(nuevoNombre)
    expect(res.data.direccion ?? res.data.address).toBeTruthy()
  })

  it('returns 404 for a non-existent sucursal', async () => {
    const res = await platformHttp.put('/sucursales/999999', {
      nombre: 'No existe',
    }, { headers: bearer(PT) })
    expect(res.status).toBe(404)
  })

  it('returns 200 for soporte token (PUT sucursales uses requirePlataforma, not requireSuperAdmin)', async () => {
    if (!sucursalId) return
    const res = await platformHttp.put(`/sucursales/${sucursalId}`,
      { nombre: 'Intento Soporte' },
      { headers: bearer(SOPORTE) },
    )
    expect(res.status).toBe(200)
  })

  it('returns 401 without token', async () => {
    if (!sucursalId) return
    const res = await platformHttp.put(`/sucursales/${sucursalId}`,
      { nombre: 'Sin token' },
    )
    expect(res.status).toBe(401)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// POST /companias/{id}/suscripcion/renovar  — subscription renewal
// ─────────────────────────────────────────────────────────────────────────────

describe('POST /companias/{id}/suscripcion/renovar — renewal', () => {
  it('returns 200 with tipoCambio=RENOVACION and estado=ACTIVO (skips if no gym)', async () => {
    if (!companiaId || !planBasicoId) return
    const res = await platformHttp.post(`/companias/${companiaId}/suscripcion/renovar`, {
      idPlan: planBasicoId,
      meses:  1,
    }, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data.tipoCambio).toBe('RENOVACION')
    expect(res.data.estado).toBe('ACTIVO')
    expect(res.data).toHaveProperty('fechaInicio')
    expect(res.data).toHaveProperty('fechaFin')
  })

  it('returns 200 and historial grows to at least 2 rows after renewal (skips if no gym)', async () => {
    if (!companiaId) return
    const res = await platformHttp.get(`/companias/${companiaId}/suscripcion/historial`,
      { headers: bearer(PT) },
    )
    expect(res.status).toBe(200)
    expect(Array.isArray(res.data)).toBe(true)
    expect((res.data as unknown[]).length).toBeGreaterThanOrEqual(2)
  })

  it('returns 403 for soporte (only super_admin can renew)', async () => {
    if (!companiaId || !planBasicoId) return
    const res = await platformHttp.post(`/companias/${companiaId}/suscripcion/renovar`,
      { idPlan: planBasicoId, meses: 1 },
      { headers: bearer(SOPORTE) },
    )
    expect(res.status).toBe(403)
  })

  it('returns 401 without token', async () => {
    if (!companiaId) return
    const res = await platformHttp.post(`/companias/${companiaId}/suscripcion/renovar`,
      { idPlan: 1, meses: 1 },
    )
    expect(res.status).toBe(401)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// POST /companias/{id}/suscripcion/upgrade  — plan upgrade
// ─────────────────────────────────────────────────────────────────────────────

describe('POST /companias/{id}/suscripcion/upgrade — plan upgrade', () => {
  it('returns 200 with idCompaniaPlanNuevo, planAnteriorCancelado=true and montoAPagar (skips if no gym)', async () => {
    if (!companiaId || !planPremiumId) return
    const res = await platformHttp.post(`/companias/${companiaId}/suscripcion/upgrade`, {
      idPlanNuevo: planPremiumId,
    }, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('idCompaniaPlanNuevo')
    expect(typeof res.data.idCompaniaPlanNuevo).toBe('number')
    expect(res.data).toHaveProperty('planAnteriorCancelado', true)
    expect(res.data).toHaveProperty('montoAPagar')
    upgradeSucceeded = true
  })

  it('active subscription after upgrade is the premium plan with tipoCambio=UPGRADE (skips if upgrade did not run)', async () => {
    if (!companiaId || !upgradeSucceeded) return
    const res = await platformHttp.get(`/companias/${companiaId}/suscripcion`,
      { headers: bearer(PT) },
    )
    expect(res.status).toBe(200)
    expect(res.data.estado).toBe('ACTIVO')
    expect(res.data.tipoCambio).toBe('UPGRADE')
    expect(res.data.idPlan).toBe(planPremiumId)
  })

  it('returns 400 when body is missing idPlanNuevo', async () => {
    if (!companiaId) return
    const res = await platformHttp.post(`/companias/${companiaId}/suscripcion/upgrade`,
      {},
      { headers: bearer(PT) },
    )
    expect(res.status).toBe(400)
  })

  it('returns 403 for soporte (only super_admin can upgrade)', async () => {
    if (!companiaId || !planPremiumId) return
    const res = await platformHttp.post(`/companias/${companiaId}/suscripcion/upgrade`,
      { idPlanNuevo: planPremiumId },
      { headers: bearer(SOPORTE) },
    )
    expect(res.status).toBe(403)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// POST /companias/{id}/suscripcion/downgrade  — schedule cheaper plan
// (runs AFTER upgrade so the active plan is premium → downgrade to basic)
// ─────────────────────────────────────────────────────────────────────────────

describe('POST /companias/{id}/suscripcion/downgrade — plan downgrade', () => {
  // Downgrade requires being on premium (more expensive plan) → can only run after a successful upgrade
  it('returns 200 with estado=PROGRAMADO and efectivoDe date (skips if upgrade did not succeed)', async () => {
    if (!companiaId || !planBasicoId || !upgradeSucceeded) return
    const res = await platformHttp.post(`/companias/${companiaId}/suscripcion/downgrade`, {
      idPlanNuevo: planBasicoId,
    }, { headers: bearer(PT) })
    expect(res.status).toBe(200)
    expect(res.data.estado).toBe('PROGRAMADO')
    expect(res.data).toHaveProperty('idCompaniaPlanNuevo')
    expect(res.data).toHaveProperty('efectivoDe')
    expect(typeof res.data.efectivoDe).toBe('string')
  })

  it('current active subscription remains ACTIVO after scheduling downgrade (skips if upgrade did not succeed)', async () => {
    if (!companiaId || !upgradeSucceeded) return
    const res = await platformHttp.get(`/companias/${companiaId}/suscripcion`,
      { headers: bearer(PT) },
    )
    expect(res.status).toBe(200)
    expect(res.data.estado).toBe('ACTIVO')
    expect(res.data.tipoCambio).toBe('UPGRADE')
  })

  it('returns 400 when body is missing idPlanNuevo', async () => {
    if (!companiaId) return
    const res = await platformHttp.post(`/companias/${companiaId}/suscripcion/downgrade`,
      {},
      { headers: bearer(PT) },
    )
    expect(res.status).toBe(400)
  })

  it('returns 403 for soporte (only super_admin can downgrade)', async () => {
    if (!companiaId || !planBasicoId) return
    const res = await platformHttp.post(`/companias/${companiaId}/suscripcion/downgrade`,
      { idPlanNuevo: planBasicoId },
      { headers: bearer(SOPORTE) },
    )
    expect(res.status).toBe(403)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// POST /companias/wizard  — full gym + user registration
// ─────────────────────────────────────────────────────────────────────────────

describe('POST /companias/wizard — wizard gym registration', () => {
  // ── caso A: persona ya existe (id_persona proporcionado) ──────────────────────
  it('returns 201 with existing persona (id_persona provided, skips if no persona or plan)', async () => {
    if (!personaId || !planBasicoId) return
    const ruc = `${50000000000 + (TS % 1000000000)}`
    const res = await platformHttp.post('/companias/wizard', {
      nombre:           `Gym Wizard FE ${TS}`,
      ruc,
      correo:           `wizard_${TS}@test.com`,
      telefono:         '022111111',
      idPlan:           planBasicoId,
      nombreSucursal:   'Sede Wizard FE',
      direccionSucursal: 'Av. Wizard 789',
      usuarioPrincipal: {
        id_persona: personaId,
        ci:         `WIZ${TS % 1_000_000_000}`,
        nombre:     `Test Wizard FE ${TS}`,
        correo:     `admin_wizard_${TS}@test.com`,
        password:   'Password123!',
      },
    }, { headers: bearer(PT) })

    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('idCompania')
    expect(typeof res.data.idCompania).toBe('number')
    expect(res.data).toHaveProperty('qrToken')
    expect(typeof res.data.qrToken).toBe('string')
    expect(res.data.qrToken.length).toBeGreaterThan(10)
    expect(res.data).toHaveProperty('usuarioPrincipal')
    expect(res.data.usuarioPrincipal).toHaveProperty('id')
    expect(res.data.usuariosCreados).toBe(1)
  })

  // ── caso B: persona nueva (sin id_persona — backend la crea inline) ──────────
  it('returns 201 with new persona (no id_persona, backend creates persona inline, skips if no plan)', async () => {
    if (!planBasicoId) return
    const TS2  = TS + 1
    const ruc  = `${51000000000 + (TS2 % 1000000000)}`
    const res  = await platformHttp.post('/companias/wizard', {
      nombre:           `Gym Wizard Nueva ${TS2}`,
      ruc,
      correo:           `wizard2_${TS2}@test.com`,
      idPlan:           planBasicoId,
      nombreSucursal:   'Sede Wizard Nueva',
      direccionSucursal: 'Av. Nueva 456',
      usuarioPrincipal: {
        ci:       `NWZ${TS2 % 1_000_000_000}`,
        nombre:   `Admin Nueva ${TS2}`,
        correo:   `admin_nueva_${TS2}@test.com`,
        telefono: '0991234567',
        password: 'Password123!',
      },
    }, { headers: bearer(PT) })

    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('idCompania')
    expect(typeof res.data.idCompania).toBe('number')
    expect(res.data).toHaveProperty('qrToken')
    expect(res.data.usuarioPrincipal).toHaveProperty('id')
    expect(res.data.usuariosCreados).toBe(1)
  })

  // ── validaciones ──────────────────────────────────────────────────────────────
  it('returns 400 when usuarioPrincipal is missing (validation)', async () => {
    if (!planBasicoId) return
    const res = await platformHttp.post('/companias/wizard', {
      nombre:         `Gym Sin Usuario ${TS}`,
      ruc:            '9999999999001',
      idPlan:         planBasicoId,
      nombreSucursal: 'Sede Test',
    }, { headers: bearer(PT) })
    expect(res.status).toBe(400)
  })

  it('returns 400 when ci is missing inside usuarioPrincipal (Bean Validation)', async () => {
    if (!planBasicoId) return
    const res = await platformHttp.post('/companias/wizard', {
      nombre:         `Gym Sin CI ${TS}`,
      ruc:            '8888888888001',
      idPlan:         planBasicoId,
      nombreSucursal: 'Sede Test',
      usuarioPrincipal: {
        nombre:   'Sin CI Admin',
        correo:   'sin_ci@test.com',
        password: 'Password123!',
      },
    }, { headers: bearer(PT) })
    expect(res.status).toBe(400)
  })

  // ── autorización ──────────────────────────────────────────────────────────────
  it('returns 403 for soporte token (only super_admin can use wizard)', async () => {
    if (!planBasicoId) return
    const res = await platformHttp.post('/companias/wizard', {
      nombre:         `Gym Soporte ${TS}`,
      ruc:            '7777777777001',
      idPlan:         planBasicoId,
      nombreSucursal: 'Sede Test',
      usuarioPrincipal: {
        ci:       '9999999999',
        nombre:   'Soporte Admin',
        correo:   'soporte@test.com',
        password: 'Password123!',
      },
    }, { headers: bearer(SOPORTE) })
    expect(res.status).toBe(403)
  })

  it('returns 401 without token', async () => {
    const res = await platformHttp.post('/companias/wizard', { nombre: 'x' })
    expect(res.status).toBe(401)
  })
})