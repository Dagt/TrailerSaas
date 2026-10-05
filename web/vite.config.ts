import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import { readWebEnvironment } from './src/shared/config/environment.ts'

export default defineConfig(({ mode }) => {
  readWebEnvironment(loadEnv(mode, process.cwd(), 'VITE_'))
  return {
    plugins: [react()],
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      clearMocks: true,
      restoreMocks: true,
    },
  }
})
