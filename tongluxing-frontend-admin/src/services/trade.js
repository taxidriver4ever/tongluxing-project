import { apiRequest } from './apiClient.js'

const query = params => new URLSearchParams(Object.entries(params).filter(([, value]) => value !== '' && value != null)).toString()

export const getOverview = (params = {}) => apiRequest(`/v1/admin/operation-overview?${query(params)}`)
export const getOrders = (params = {}) => apiRequest(`/v1/admin/orders?${query(params)}`)
export const getOrder = id => apiRequest(`/v1/admin/orders/${id}`)
export const getRefunds = (params = {}) => apiRequest(`/v1/admin/refunds?${query(params)}`)
export const getRefund = id => apiRequest(`/v1/admin/refunds/${id}`)
export const auditRefund = (id, data) => apiRequest(`/v1/admin/refunds/${id}/audit`, { method: 'POST', body: JSON.stringify(data) })
export const getSettlements = (params = {}) => apiRequest(`/v1/admin/settlements?${query(params)}`)
export const triggerSettlement = (id, data) => apiRequest(`/v1/admin/settlements/${id}/trigger`, { method: 'POST', body: JSON.stringify(data) })
export const getVerificationRecords = (params = {}) => apiRequest(`/v1/admin/verifications/records?${query(params)}`)
export const getTransactions = (params = {}) => apiRequest(`/v1/admin/transactions?${query(params)}`)
