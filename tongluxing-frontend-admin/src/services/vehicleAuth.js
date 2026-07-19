import { API_BASE, apiRequest } from './apiClient.js'

export function getVehicleAuthApplications(params = {}) {
  const search = new URLSearchParams()
  search.set('page', String(params.page || 1))
  search.set('size', String(params.size || 20))
  if (params.status) search.set('status', params.status)
  if (params.keyword?.trim()) search.set('keyword', params.keyword.trim())
  return apiRequest(`/admin/vehicle/auth/list?${search}`)
}

export async function getVehicleAuthStatusCounts() {
  const statuses = ['PENDING', 'PASS', 'REJECT']
  const values = await Promise.all(statuses.map(status => getVehicleAuthApplications({ status, page: 1, size: 1 })))
  return Object.fromEntries(statuses.map((status, index) => [status, Number(values[index]?.total || 0)]))
}

export function auditVehicleAuth(data) {
  return apiRequest('/admin/vehicle/auth/audit', {
    method: 'POST',
    body: JSON.stringify({
      ...data,
      applyId: String(data.applyId),
      rejectReason: data.status === 'REJECT' ? data.rejectReason.trim() : '',
    }),
  })
}

export const vehicleAuthApiBase = API_BASE
