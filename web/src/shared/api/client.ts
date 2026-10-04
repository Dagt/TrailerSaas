import { webEnvironment } from '../config/environment'

// Transport and authentication are added by later API/identity issues.
export const apiClient = Object.freeze({
  baseUrl: webEnvironment.apiBaseUrl,
})
