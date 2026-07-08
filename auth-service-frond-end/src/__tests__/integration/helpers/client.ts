import axios from 'axios'

/**
 * Bare axios instances per microservice — no interceptors, no browser deps.
 * TEST_API_URL        → auth-service (port 8080) — default for legacy tests
 * TEST_PLATFORM_URL   → platform-service (port 8081)
 * TEST_CORE_URL       → core-service (port 8083)
 * TEST_ATTENDANCE_URL → attendance-service (port 8084)
 */
const AUTH_BASE       = process.env.TEST_API_URL        ?? 'http://localhost:8080/api/v1'
const PLATFORM_BASE   = process.env.TEST_PLATFORM_URL   ?? 'http://localhost:8081/api/v1'
const CORE_BASE       = process.env.TEST_CORE_URL       ?? 'http://localhost:8083/api/v1'
const ATTENDANCE_BASE = process.env.TEST_ATTENDANCE_URL ?? 'http://localhost:8084/api/v1'

const COMMON_CONFIG = {
  validateStatus: () => true,  // never throw; always return response so we can assert status
  timeout: 30_000,
}

/** Auth-service (port 8080) */
export const http = axios.create({ baseURL: AUTH_BASE, ...COMMON_CONFIG })

/** Platform-service (port 8081) */
export const platformHttp = axios.create({ baseURL: PLATFORM_BASE, ...COMMON_CONFIG })

/** Core-service (port 8083) */
export const coreHttp = axios.create({ baseURL: CORE_BASE, ...COMMON_CONFIG })

/** Attendance-service (port 8084) */
export const attendanceHttp = axios.create({ baseURL: ATTENDANCE_BASE, ...COMMON_CONFIG })

/** Wrap Bearer token into auth header object */
export function bearer(token: string) {
  return { Authorization: `Bearer ${token}` }
}
