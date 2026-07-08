import { createHmac } from 'node:crypto'

// Same base64 secret as application.yml: jwt.secret
const BASE64_SECRET =
  process.env.JWT_SECRET ??
  'Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tdGhpcy1rZXktbXVzdC1iZS0yNTYtYml0cw=='

// Attendance-service default jwt.secret (application.yml fallback).
// Set JWT_SECRET or TEST_ATTENDANCE_JWT_SECRET in the environment to override.
const ATTENDANCE_BASE64_SECRET =
  process.env.JWT_SECRET ??
  process.env.TEST_ATTENDANCE_JWT_SECRET ??
  'cGxhdGZvcm1TZWNyZXRLZXlGb3JHeW1BZG1pbmlzdHJhdG9yMjAyNg=='

function b64url(buf: Buffer): string {
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}

function sign(payload: Record<string, unknown>): string {
  const now = Math.floor(Date.now() / 1000)
  const full = { ...payload, iat: now, exp: now + 8 * 3600 }
  const header = b64url(Buffer.from(JSON.stringify({ alg: 'HS384', typ: 'JWT' })))
  const body = b64url(Buffer.from(JSON.stringify(full)))
  const key = Buffer.from(BASE64_SECRET, 'base64')
  const sig = b64url(createHmac('sha384', key).update(`${header}.${body}`).digest())
  return `${header}.${body}.${sig}`
}

/**
 * Signs a JWT compatible with attendance-service.
 * Selects HS256/HS384/HS512 based on key length to satisfy JJWT minimum key-size checks:
 *   ≥ 64 bytes → HS512 | ≥ 48 bytes → HS384 | ≥ 32 bytes → HS256
 */
function attendanceSign(payload: Record<string, unknown>): string {
  const now = Math.floor(Date.now() / 1000)
  const full = { ...payload, iat: now, exp: now + 8 * 3600 }
  const key = Buffer.from(ATTENDANCE_BASE64_SECRET, 'base64')
  const [alg, hashAlg] =
    key.length >= 64 ? ['HS512', 'sha512'] :
    key.length >= 48 ? ['HS384', 'sha384'] :
                       ['HS256', 'sha256']
  const header = b64url(Buffer.from(JSON.stringify({ alg, typ: 'JWT' })))
  const body   = b64url(Buffer.from(JSON.stringify(full)))
  const sig    = b64url(createHmac(hashAlg, key).update(`${header}.${body}`).digest())
  return `${header}.${body}.${sig}`
}

/** Mirrors JwtService.generatePlatformToken — super_admin by default */
export function platformToken(id = 99990, nombre = 'Test Frontend', rol = 'super_admin'): string {
  return sign({ sub: String(id), tipo: 'plataforma', rol_plataforma: rol, nombre })
}

/** Mirrors JwtService.generateStaffToken */
export function staffToken(
  permisos: string[] = [],
  id = 99991,
  idCompania = 99999,
  idSucursal = 99999,
  idRol = 99990,
): string {
  return sign({ sub: String(id), tipo: 'staff', id_compania: idCompania, id_sucursal: idSucursal, id_rol: idRol, nombre: 'Test Frontend Staff', permisos })
}

/**
 * Token for core-service endpoints.
 * Core uses id_compania (Long) and rol_plataforma to determine access level.
 * - rol_plataforma = 'admin_compania' → passes requireAdminOrDueno
 * - rol_plataforma = 'Recepción'      → passes requireRecepcionOrAbove
 */
export function coreAdminToken(id = 99991, idCompania = 1): string {
  return sign({ sub: String(id), tipo: 'staff', id_compania: idCompania, rol_plataforma: 'admin_compania', nombre: 'Test Core Admin' })
}

export function coreRecepcionToken(id = 99992, idCompania = 1): string {
  return sign({ sub: String(id), tipo: 'staff', id_compania: idCompania, rol_plataforma: 'Recepción', nombre: 'Test Core Recepcion' })
}

// ── Attendance-service tokens (port 8084) ────────────────────────────────────
// Claims read by JwtAuthenticationFilter: sub, tipo, rol_gym, rol_plataforma, id_compania

/** Staff dueño — passes requireStaff, requireDueno, requireStaffOrPlataforma */
export function attendanceDuenoToken(id = 99991, idCompania = 1): string {
  return attendanceSign({ sub: String(id), tipo: 'staff', rol_gym: 'dueno', id_compania: idCompania })
}

/** Staff admin_compania — also satisfies isDueno() (same as dueno) */
export function attendanceAdminToken(id = 99991, idCompania = 1): string {
  return attendanceSign({ sub: String(id), tipo: 'staff', rol_gym: 'admin_compania', id_compania: idCompania })
}

/** Staff recepcionista — passes requireStaff, requireStaffOrPlataforma; blocked by requireDueno */
export function attendanceRecepcionToken(id = 99992, idCompania = 1): string {
  return attendanceSign({ sub: String(id), tipo: 'staff', rol_gym: 'recepcion', id_compania: idCompania })
}

/** Staff entrenador — passes requireStaff; blocked by requireNotEntrenador */
export function attendanceEntrenadorToken(id = 99993, idCompania = 1): string {
  return attendanceSign({ sub: String(id), tipo: 'staff', rol_gym: 'entrenador', id_compania: idCompania })
}

/** Cliente — passes requireCliente; blocked by requireStaff */
export function attendanceClienteToken(id = 99994, idCompania = 1): string {
  return attendanceSign({ sub: String(id), tipo: 'cliente', id_compania: idCompania })
}

/** Plataforma super_admin — passes requireStaffOrPlataforma, requireDuenoOrPlataforma */
export function attendancePlataformaToken(id = 99990): string {
  return attendanceSign({ sub: String(id), tipo: 'plataforma', rol_plataforma: 'super_admin' })
}
