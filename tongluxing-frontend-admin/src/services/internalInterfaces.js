import { API_BASE, apiRequest } from './apiClient.js'

export async function getBackendHealth() {
  const startedAt = performance.now()
  const response = await fetch(`${API_BASE}/actuator/health`, { cache: 'no-store' })
  const payload = await response.json()
  if (!response.ok) throw new Error(payload.message || `健康检查失败（HTTP ${response.status}）`)
  return { status: payload.status || 'UNKNOWN', latency: Math.round(performance.now() - startedAt) }
}

export const processAdminCompensation = limit => apiRequest(`/v1/admin/compensation-tasks/process?limit=${limit}`, { method: 'POST' })
export const closeExpiredOrders = limit => apiRequest(`/v1/orders/internal/expired/close?limit=${limit}`, { method: 'POST' })
export const processOrderCompensation = limit => apiRequest(`/v1/orders/internal/compensation-tasks/process?limit=${limit}`, { method: 'POST' })
export const processVerificationCompensation = limit => apiRequest(`/v1/verifications/internal/compensation-tasks/process?limit=${limit}`, { method: 'POST' })
