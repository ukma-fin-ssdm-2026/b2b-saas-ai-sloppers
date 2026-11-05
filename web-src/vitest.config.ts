import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    css: false,
    coverage: {
      provider: 'v8',
      // lcov for SonarQube Cloud; projectRoot '..' makes paths relative to the repo root (web-src/src/...)
      reporter: ['text', 'html', ['lcov', { projectRoot: '..' }]],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/**/*.d.ts'],
    },
  },
})
