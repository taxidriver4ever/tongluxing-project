import { apiRequest } from './apiClient.js'
export const listSosEvents = (status = '') =>
  apiRequest(`/v1/admin/sos/events?status=${encodeURIComponent(status)}&limit=100`)
export const acceptSosEvent = id => apiRequest(`/v1/admin/sos/events/${id}/accept`, { method: 'POST' })
export const resolveSosEvent = (id, resolutionNote) =>
  apiRequest(`/v1/admin/sos/events/${id}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ resolutionNote }),
  })
