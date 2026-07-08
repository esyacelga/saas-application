/**
 * Integration tests — Auth endpoints
 * Real-credential tests read from env vars and are skipped when not set.
 */
import { describe, it, expect } from 'vitest'
import { http } from './helpers/client'

const PLATFORM_EMAIL = process.env.TEST_PLATFORM_EMAIL
const PLATFORM_PASSWORD = process.env.TEST_PLATFORM_PASSWORD
const STAFF_EMAIL = process.env.TEST_STAFF_EMAIL
const STAFF_PASSWORD = process.env.TEST_STAFF_PASSWORD

describe('POST /auth/platform/login', () => {
  it('returns 400 when body is missing required fields', async () => {
    const res = await http.post('/auth/platform/login', {})
    expect(res.status).toBe(400)
  })

  it('returns 401 for wrong credentials', async () => {
    const res = await http.post('/auth/platform/login', {
      correo: 'nobody@example.com',
      password: 'wrongpassword',
    })
    expect(res.status).toBe(401)
  })

  it.skipIf(!PLATFORM_EMAIL)('returns 200 and access_token for real credentials', async () => {
    const res = await http.post('/auth/platform/login', {
      correo: PLATFORM_EMAIL,
      password: PLATFORM_PASSWORD,
    })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('access_token')
    expect(typeof res.data.access_token).toBe('string')
  })
})

describe('POST /auth/login (staff)', () => {
  it('returns 400 when body is missing required fields', async () => {
    const res = await http.post('/auth/login', {})
    expect(res.status).toBe(400)
  })

  it('returns 401 for wrong credentials', async () => {
    const res = await http.post('/auth/login', {
      correo: 'nobody@example.com',
      password: 'wrongpassword',
      id_compania: 1,
    })
    expect(res.status).toBe(401)
  })

  it.skipIf(!STAFF_EMAIL)('returns 200 and access_token for real credentials', async () => {
    const res = await http.post('/auth/login', {
      correo: STAFF_EMAIL,
      password: STAFF_PASSWORD,
      id_compania: Number(process.env.TEST_STAFF_COMPANIA_ID ?? 1),
    })
    expect(res.status).toBe(200)
    expect(res.data).toHaveProperty('access_token')
  })
})

describe('POST /auth/app/login', () => {
  it('returns 400 when body is missing required fields', async () => {
    const res = await http.post('/auth/app/login', {})
    expect(res.status).toBe(400)
  })

  it('returns 401 for wrong credentials', async () => {
    const res = await http.post('/auth/app/login', {
      login: 'nobody@example.com',
      password: 'wrongpassword',
      id_compania: 1,
    })
    expect(res.status).toBeGreaterThanOrEqual(400)
    expect(res.status).toBeLessThan(500)
  })
})

describe('POST /auth/refresh', () => {
  it('returns 4xx when no refresh token is provided', async () => {
    const res = await http.post('/auth/refresh', {})
    expect(res.status).toBeGreaterThanOrEqual(400)
    expect(res.status).toBeLessThan(500)
  })

  it('returns 401 for an invalid refresh token', async () => {
    const res = await http.post('/auth/refresh', { refresh_token: 'invalid-token' })
    expect(res.status).toBe(401)
  })
})

describe('POST /auth/logout', () => {
  // Logout is designed to be idempotent — it accepts unauthenticated calls
  it('returns 2xx or 4xx (does not crash the server)', async () => {
    const res = await http.post('/auth/logout')
    expect(res.status).toBeLessThan(500)
  })
})

describe('POST /auth/password/reset-request', () => {
  it('returns 400 when email is missing', async () => {
    const res = await http.post('/auth/password/reset-request', {})
    expect(res.status).toBe(400)
  })

  it('returns 4xx (does not crash the server) for any email', async () => {
    const res = await http.post('/auth/password/reset-request', {
      email: 'nonexistent@example.com',
    })
    expect(res.status).toBeGreaterThanOrEqual(400)
    expect(res.status).toBeLessThan(500)
  })
})

describe('POST /auth/password/reset', () => {
  it('returns 400 when body is missing', async () => {
    const res = await http.post('/auth/password/reset', {})
    expect(res.status).toBe(400)
  })

  it('returns 4xx for an invalid reset token', async () => {
    const res = await http.post('/auth/password/reset', {
      token: 'invalid-token',
      new_password: 'NewPass123!',
    })
    expect(res.status).toBeGreaterThanOrEqual(400)
    expect(res.status).toBeLessThan(500)
  })
})
