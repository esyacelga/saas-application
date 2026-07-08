/**
 * CORS Debug Script
 * Replica exactamente lo que el browser hace: preflight OPTIONS + POST login.
 * Node no aplica CORS, así que vemos la respuesta real del backend.
 *
 * Uso: node cors-debug.mjs
 */

const BACKEND   = 'http://localhost:8080'
const ENDPOINT  = '/api/v1/auth/login'
const ORIGIN    = 'http://localhost:5173'
const URL_FULL  = `${BACKEND}${ENDPOINT}`

const BODY = {
  correo:      'root@gym-admin.local',
  password:    'seya1922',
  id_compania: 1,
}

function printHeaders(label, headers) {
  console.log(`\n  ${label}:`)
  for (const [k, v] of Object.entries(headers)) {
    console.log(`    ${k}: ${v}`)
  }
}

async function step(label, request) {
  console.log('\n' + '─'.repeat(60))
  console.log(`▶  ${label}`)
  console.log('─'.repeat(60))
  printHeaders('Request headers', request.headers)

  const res = await fetch(URL_FULL, request)
  const responseHeaders = Object.fromEntries(res.headers.entries())

  console.log(`\n  Status: ${res.status} ${res.statusText}`)
  printHeaders('Response headers', responseHeaders)

  const text = await res.text().catch(() => '')
  if (text) console.log(`\n  Body: ${text.slice(0, 500)}`)

  return { status: res.status, headers: responseHeaders }
}

// ── 1. Preflight OPTIONS (exactamente como lo envía Chrome) ──────────────────
const preflight = await step('PREFLIGHT OPTIONS (lo que el browser envía primero)', {
  method: 'OPTIONS',
  headers: {
    'Origin':                         ORIGIN,
    'Access-Control-Request-Method':  'POST',
    'Access-Control-Request-Headers': 'content-type',
  },
})

// ── 2. Diagnóstico del preflight ─────────────────────────────────────────────
console.log('\n' + '═'.repeat(60))
console.log('  DIAGNÓSTICO PREFLIGHT')
console.log('═'.repeat(60))

const acao  = preflight.headers['access-control-allow-origin']
const acac  = preflight.headers['access-control-allow-credentials']
const acam  = preflight.headers['access-control-allow-methods']
const acah  = preflight.headers['access-control-allow-headers']

console.log(`\n  Access-Control-Allow-Origin      : ${acao  ?? '❌ AUSENTE'}`)
console.log(`  Access-Control-Allow-Credentials : ${acac  ?? '❌ AUSENTE'}`)
console.log(`  Access-Control-Allow-Methods     : ${acam  ?? '❌ AUSENTE'}`)
console.log(`  Access-Control-Allow-Headers     : ${acah  ?? '❌ AUSENTE'}`)

if (acao === '*') {
  console.log('\n  ❌ PROBLEMA: Allow-Origin es "*"')
  console.log('     Con withCredentials=true el browser RECHAZA "*".')
  console.log(`     El backend debe responder: Access-Control-Allow-Origin: ${ORIGIN}`)
} else if (acao === ORIGIN) {
  console.log('\n  ✅ Allow-Origin es correcto')
} else {
  console.log(`\n  ⚠️  Allow-Origin inesperado: "${acao}"`)
}

if (acac !== 'true') {
  console.log('\n  ❌ PROBLEMA: Access-Control-Allow-Credentials no es "true"')
  console.log('     El backend debe agregar: Access-Control-Allow-Credentials: true')
}

// ── 3. POST real ─────────────────────────────────────────────────────────────
await step('POST LOGIN (la petición real)', {
  method:  'POST',
  headers: {
    'Origin':       ORIGIN,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify(BODY),
})

console.log('\n' + '═'.repeat(60))
console.log('  FIN DEL DIAGNÓSTICO')
console.log('═'.repeat(60) + '\n')
