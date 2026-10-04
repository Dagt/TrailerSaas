import { z } from 'zod'

const environmentSchema = z.object({
  VITE_API_BASE_URL: z
    .union([z.url({ protocol: /^https?$/ }), z.literal('/api/v1')])
    .default('/api/v1'),
})

export function readWebEnvironment(raw: { VITE_API_BASE_URL?: unknown }) {
  const environment = environmentSchema.parse(raw)

  return {
    apiBaseUrl: environment.VITE_API_BASE_URL,
  }
}

export const webEnvironment = readWebEnvironment(import.meta.env)
