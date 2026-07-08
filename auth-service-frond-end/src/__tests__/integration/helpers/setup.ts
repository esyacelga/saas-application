/**
 * Global setup — waits for all three microservices to be ready before
 * any test runs. Retries every 2s for up to 60s per service.
 *
 * Configured in vitest.integration.config.ts via globalSetup.
 */

import axios from 'axios'

const SERVICES = [
  { name: 'auth-service      (8080)', url: process.env.TEST_API_URL        ?? 'http://localhost:8080/api/v1', probe: '/users' },
  { name: 'platform-service  (8081)', url: process.env.TEST_PLATFORM_URL   ?? 'http://localhost:8081/api/v1', probe: '/health' },
  { name: 'core-service      (8083)', url: process.env.TEST_CORE_URL       ?? 'http://localhost:8083/api/v1', probe: '/health' },
  { name: 'attendance-service(8084)', url: process.env.TEST_ATTENDANCE_URL ?? 'http://localhost:8084/api/v1', probe: '/asistencias/hoy' },
]

const RETRY_INTERVAL_MS = 2_000
const MAX_WAIT_MS       = 60_000

async function waitForService(name: string, baseUrl: string, probe: string): Promise<void> {
  const start = Date.now()
  const client = axios.create({ baseURL: baseUrl, timeout: 3_000, validateStatus: () => true })

  process.stdout.write(`  Waiting for ${name} `)

  while (Date.now() - start < MAX_WAIT_MS) {
    try {
      const res = await client.get(probe)
      // Accept any non-5xx response: 401/403/404 mean the web + DB layers are up
      if (res.status < 500) {
        process.stdout.write(` ready (${res.status})\n`)
        return
      }
    } catch {
      // ECONNREFUSED or timeout — service not up yet
    }
    process.stdout.write('.')
    await new Promise(r => setTimeout(r, RETRY_INTERVAL_MS))
  }

  throw new Error(`${name} did not become ready within ${MAX_WAIT_MS / 1000}s`)
}

export async function setup(): Promise<void> {
  console.log('\n[setup] Waiting for microservices...')
  await Promise.all(
    SERVICES.map(s => waitForService(s.name, s.url, s.probe))
  )
  console.log('[setup] All services ready — starting tests\n')
}
