/**
 * Integration test — Wizard flow completo:
 *   A) persona no existe → crear persona → crear compañía
 *   B) wizard con usuarios adicionales (Step 5)
 *
 * Reproduce el flujo exacto del frontend Step4 + Step5 + submit final:
 *   1. GET /personas/ci/{ci}        → 404  (persona no existe)
 *   2. GET /personas/correo/{correo} → 404  (confirmado por correo también)
 *   3. POST /personas               → 201  (crear persona, obtener id_persona)
 *   4. POST /companias/wizard       → 201  (crear compañía con id_persona resuelto)
 *   5. GET /companias/{id}/suscripcion → 200 estado=ACTIVO (verificar compañía activa)
 *   6. POST /companias/wizard sin ci en usuariosAdicionales → 400 (bug frontend: falta ci)
 *   7. POST /companias/wizard con ci en usuariosAdicionales → 201, usuariosCreados=3
 *
 * Servicios involucrados:
 *   auth-service     (port 8080)  — búsqueda y creación de persona
 *   platform-service (port 8081)  — wizard y suscripción
 *
 * Tokens:
 *   auth-service    usa HS384  → helpers/jwt.ts  staffToken() / platformToken()
 *   platform-service usa HS256  → signHS256() definido aquí (igual que platform-suscripcion.test.ts)
 *
 * Run:
 *   npx vitest run wizard-crear-compania.test.ts
 */

import { createHmac } from 'node:crypto'
import { beforeAll, afterAll, describe, it, expect } from 'vitest'
import { http, platformHttp, bearer } from './helpers/client'
import { staffToken } from './helpers/jwt'

// ── Token para auth-service (HS384, JWT_SECRET) ───────────────────────────────

const ST_PERSONAS = staffToken(['personas:crear', 'personas:leer'])

// ── Token para platform-service (HS256, PLATFORM_JWT_SECRET) ─────────────────

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

const PT = signHS256({ sub: '99990', tipo: 'plataforma', rol_plataforma: 'super_admin', nombre: 'Test SA' })

// ── Datos únicos por ejecución ────────────────────────────────────────────────

const TS  = Date.now()
const CI  = `WNE${TS % 1_000_000_000}`      // CI que NO existe en la BD
const CORREO_PERSONA = `wne_${TS}@test.com`  // correo que NO existe en la BD

// ── Estado compartido entre tests ────────────────────────────────────────────

let planId:     number | null = null
let personaId:  number | null = null
let companiaId: number | null = null

// ── Setup: crear plan base ────────────────────────────────────────────────────

beforeAll(async () => {
  for (let i = 0; i < 5; i++) {
    const r = await platformHttp.get('/planes', { headers: bearer(PT), timeout: 20_000 }).catch(() => null)
    if (r?.status !== undefined) break
  }

  const r = await platformHttp.post('/planes', {
    nombre:        `Plan Wizard NE ${TS}`,
    descripcion:   'Plan para test wizard persona nueva',
    precioMensual: 29.99,
  }, { headers: bearer(PT) }).catch(() => null)

  if (r?.status === 201) planId = r.data.id as number
}, 120_000)

// ── Cleanup ───────────────────────────────────────────────────────────────────

afterAll(async () => {
  if (companiaId) {
    await platformHttp.put(`/companias/${companiaId}/suspender`,
      { motivo: 'Cleanup wizard-crear-compania IT' },
      { headers: bearer(PT) },
    ).catch(() => {})
  }
  if (planId) {
    await platformHttp.put(`/planes/${planId}/desactivar`, {},
      { headers: bearer(PT) },
    ).catch(() => {})
  }
})

// ─────────────────────────────────────────────────────────────────────────────
// Flujo wizard: persona nueva → nueva compañía
// ─────────────────────────────────────────────────────────────────────────────

describe('Wizard — persona nueva, compañía nueva', () => {

  // Paso 1 del flujo frontend: usuario busca por CI → 404
  it('GET /personas/ci/{ci} devuelve 404 cuando la persona no existe', async () => {
    const res = await http.get(`/personas/ci/${CI}`, { headers: bearer(ST_PERSONAS) })
    expect(res.status).toBe(404)
  })

  // Paso 2 del flujo frontend: usuario busca por correo → 404
  it('GET /personas/correo/{correo} devuelve 404 cuando la persona no existe', async () => {
    const res = await http.get(`/personas/correo/${encodeURIComponent(CORREO_PERSONA)}`, {
      headers: bearer(ST_PERSONAS),
    })
    expect(res.status).toBe(404)
  })

  // Paso 3 del flujo frontend: frontend llama POST /personas con los datos del formulario
  it('POST /personas crea la persona y devuelve su id', async () => {
    const res = await http.post('/personas', {
      ci:     CI,
      nombre: `Admin Nueva Empresa ${TS}`,
      correo: CORREO_PERSONA,
    }, { headers: bearer(ST_PERSONAS) })

    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('id')
    expect(typeof res.data.id).toBe('number')
    expect(res.data.ci).toBe(CI)
    expect(res.data.correo).toBe(CORREO_PERSONA)

    personaId = res.data.id as number
  })

  // Paso 4 del flujo frontend: submit final del wizard con id_persona ya resuelto
  it('POST /companias/wizard con id_persona resuelto devuelve 201 con qrToken (skip si falta plan o persona)', async () => {
    if (!planId || !personaId) return

    const ruc = `${60000000000 + (TS % 1_000_000_000)}`

    const res = await platformHttp.post('/companias/wizard', {
      nombre:            `Gym Nueva Empresa ${TS}`,
      ruc,
      correo:            `gym_ne_${TS}@test.com`,
      idPlan:            planId,
      nombreSucursal:    'Sede Nueva Empresa',
      direccionSucursal: 'Av. Wizard NE 100',
      usuarioPrincipal: {
        id_persona: personaId,
        ci:         CI,
        nombre:     `Admin Nueva Empresa ${TS}`,
        correo:     CORREO_PERSONA,
        password:   'Password123!',
      },
    }, { headers: bearer(PT) })

    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('idCompania')
    expect(typeof res.data.idCompania).toBe('number')
    expect(res.data).toHaveProperty('idSucursal')
    expect(typeof res.data.idSucursal).toBe('number')
    expect(res.data).toHaveProperty('qrToken')
    expect(typeof res.data.qrToken).toBe('string')
    expect(res.data.qrToken.length).toBeGreaterThan(10)
    expect(res.data).toHaveProperty('usuarioPrincipal')
    expect(res.data.usuarioPrincipal).toHaveProperty('id')
    expect(res.data.usuariosCreados).toBe(1)

    companiaId = res.data.idCompania as number
  })

  // Paso 5 (verificación): la compañía recién creada tiene suscripción activa
  it('GET /companias/{id}/suscripcion devuelve estado=ACTIVO (skip si wizard no se ejecutó)', async () => {
    if (!companiaId) return

    const res = await platformHttp.get(`/companias/${companiaId}/suscripcion`, {
      headers: bearer(PT),
    })

    expect(res.status).toBe(200)
    expect(res.data.estado).toBe('ACTIVO')
    expect(res.data).toHaveProperty('idPlan', planId)
    expect(res.data).toHaveProperty('fechaInicio')
    expect(res.data).toHaveProperty('fechaFin')
  })

})

// ─────────────────────────────────────────────────────────────────────────────
// Usuarios adicionales (Step 5)
// ─────────────────────────────────────────────────────────────────────────────

describe('Wizard — Step 5: usuarios adicionales', () => {

  // Reproduce exactamente lo que envía el frontend hoy:
  // usuariosAdicionales solo tiene { nombre, correo, password } — sin ci.
  // El backend tiene ci como @NotBlank en UsuarioWizardDto, por lo que devuelve 400.
  // Este test documenta el bug: el formulario Step5 no recoge CI para usuarios adicionales.
  it('BUG: POST /companias/wizard sin ci en usuariosAdicionales devuelve 400 (skip si no hay plan)', async () => {
    if (!planId) return

    const TS2 = TS + 2
    const ruc = `${62000000000 + (TS2 % 1_000_000_000)}`

    const res = await platformHttp.post('/companias/wizard', {
      nombre:           `Gym Step5 Bug ${TS2}`,
      ruc,
      idPlan:           planId,
      nombreSucursal:   'Sede Step5 Bug',
      usuarioPrincipal: {
        ci:       `ADM${TS2 % 1_000_000_000}`,
        nombre:   `Admin Step5 Bug ${TS2}`,
        correo:   `adm_step5_bug_${TS2}@test.com`,
        password: 'Password123!',
      },
      // Payload tal como lo construye el frontend hoy — sin campo ci
      usuariosAdicionales: [
        { nombre: 'Empleado Uno', correo: `emp1_${TS2}@test.com`, password: 'Password123!' },
        { nombre: 'Empleado Dos', correo: `emp2_${TS2}@test.com`, password: 'Password123!' },
      ],
    }, { headers: bearer(PT) })

    // El backend rechaza porque ci es @NotBlank en UsuarioWizardDto
    expect(res.status).toBe(400)
  })

  // Payload correcto: cada usuario adicional incluye ci.
  // Este es el comportamiento esperado una vez corregido el frontend (Step5 debe pedir CI)
  // o el backend relaje la validación de ci para adicionales.
  it('POST /companias/wizard con ci en usuariosAdicionales devuelve 201 y usuariosCreados=3 (skip si no hay plan)', async () => {
    if (!planId) return

    const TS3 = TS + 3
    const ruc = `${63000000000 + (TS3 % 1_000_000_000)}`
    let companiaStep5Id: number | null = null

    const res = await platformHttp.post('/companias/wizard', {
      nombre:           `Gym Step5 OK ${TS3}`,
      ruc,
      idPlan:           planId,
      nombreSucursal:   'Sede Step5 OK',
      usuarioPrincipal: {
        ci:       `ADM${TS3 % 1_000_000_000}`,
        nombre:   `Admin Step5 OK ${TS3}`,
        correo:   `adm_step5_ok_${TS3}@test.com`,
        password: 'Password123!',
      },
      usuariosAdicionales: [
        { ci: `EMP1${TS3 % 100_000_000}`, nombre: 'Empleado Uno', correo: `emp1_${TS3}@test.com`, password: 'Password123!' },
        { ci: `EMP2${TS3 % 100_000_000}`, nombre: 'Empleado Dos', correo: `emp2_${TS3}@test.com`, password: 'Password123!' },
      ],
    }, { headers: bearer(PT) })

    expect(res.status).toBe(201)
    expect(res.data).toHaveProperty('idCompania')
    expect(res.data).toHaveProperty('qrToken')
    expect(res.data.usuariosCreados).toBe(3)  // 1 admin + 2 adicionales

    companiaStep5Id = res.data.idCompania as number

    // Cleanup inline — suspender para no dejar datos basura
    if (companiaStep5Id) {
      await platformHttp.put(`/companias/${companiaStep5Id}/suspender`,
        { motivo: 'Cleanup wizard Step5 IT' },
        { headers: bearer(PT) },
      ).catch(() => {})
    }
  })

})
