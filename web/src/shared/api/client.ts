import { readWebEnvironment } from '../config/environment'

const webEnvironment = readWebEnvironment(import.meta.env)

// Transport and authentication are added by later API/identity issues.
export const apiClient = Object.freeze({
  baseUrl: webEnvironment.apiBaseUrl,
})
