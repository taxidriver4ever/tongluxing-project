import { apiRequest } from './apiClient.js'

const STATUS_TO_COMPAT = {
  PENDING: 'PENDING',
  APPROVED: 'PASS',
  REJECTED: 'REJECT',
}

function queryString(params = {}, status = params.status) {
  const search = new URLSearchParams()
  search.set('page', String(params.page || 1))
  search.set('size', String(params.size || 20))
  if (status) search.set('status', status)
  if (params.keyword?.trim()) search.set('keyword', params.keyword.trim())
  return search.toString()
}

export function getDrivingLicenseApplications(params = {}) {
  return apiRequest(`/v1/admin/users/certifications?${queryString(params)}`)
}

export function getDrivingLicenseDetail(certificationId) {
  return apiRequest(`/v1/admin/users/certifications/${certificationId}`)
}

export function getVehicleCertificationDetail(certificationId) {
  return apiRequest(`/v1/admin/vehicles/certifications/${certificationId}`)
}

export async function getVehicleMaterialApplications(params = {}) {
  const canonicalRequest = apiRequest(`/v1/admin/vehicles/certifications?${queryString(params)}`)
  const compatStatus = params.status ? STATUS_TO_COMPAT[params.status] : ''
  const materialRequest = apiRequest(`/admin/vehicle/auth/list?${queryString(params, compatStatus)}`)
    .catch(() => ({ records: [] }))
  const [canonical, material] = await Promise.all([canonicalRequest, materialRequest])
  const materialById = new Map((material?.records || []).map(item => [String(item.applyId), item]))
  return {
    ...canonical,
    records: (canonical?.records || []).map(item => {
      const extra = materialById.get(String(item.certificationId)) || {}
      return {
        ...item,
        vehicleBrand: extra.vehicleBrand || '',
        vehicleModel: extra.vehicleModel || '',
        vehicleColor: extra.vehicleColor || '',
        plateNumber: extra.plateNumber || item.plateNoMask,
        registrationLicenseImages: extra.registrationLicenseImages || [],
        vehicleImages: extra.vehicleImages || [],
        rejectReason: extra.rejectReason || '',
        reviewedAt: extra.auditTime || null,
      }
    }),
  }
}

export async function getCertificationStatusCounts(module) {
  const loader = module === 'driver' ? getDrivingLicenseApplications : getVehicleMaterialApplications
  const statuses = ['PENDING', 'APPROVED', 'REJECTED']
  const values = await Promise.all(statuses.map(status => loader({ status, page: 1, size: 1 })))
  return Object.fromEntries(statuses.map((status, index) => [status, Number(values[index]?.total || 0)]))
}

export function auditCertification(module, certificationId, data) {
  const scope = module === 'driver' ? 'users' : 'vehicles'
  return apiRequest(`/v1/admin/${scope}/certifications/${certificationId}/audit`, {
    method: 'POST',
    body: JSON.stringify({
      auditResult: data.auditResult,
      rejectReason: data.auditResult === 'REJECTED' ? data.rejectReason.trim() : '',
      requestId: data.requestId,
    }),
  })
}
