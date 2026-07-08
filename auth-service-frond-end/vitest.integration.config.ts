import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/__tests__/integration/**/*.test.ts'],
    globalSetup: ['src/__tests__/integration/helpers/setup.ts'],
    globals: true,
    testTimeout: 60_000,
    hookTimeout: 60_000,
    pool: 'forks',
    singleFork: true,
    reporters: ['verbose'],
  },
})
