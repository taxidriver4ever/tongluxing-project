import { apiRequest } from './apiClient.js'

export const getMerchantApplications = ({ status = '', page = 1, size = 20 } = {}) => apiRequest(`/v1/admin/merchants/applications?status=${encodeURIComponent(status)}&page=${page}&size=${size}`)
export const getMerchantApplication = id => apiRequest(`/v1/admin/merchants/applications/${id}`)
export const auditMerchantApplication = (id, auditResult, rejectReason = '') => apiRequest(`/v1/admin/merchants/applications/${id}/audit`, { method: 'POST', body: JSON.stringify({ auditResult, rejectReason, requestId: `MERCHANT-${id}-${Date.now()}` }) })
export const getMerchantCoupons = ({ status = '', page = 1, size = 20 } = {}) => apiRequest(`/v1/admin/merchant-coupons?status=${encodeURIComponent(status)}&page=${page}&size=${size}`)
export const getMerchantCoupon = id => apiRequest(`/v1/admin/merchant-coupons/${id}`)
export const auditMerchantCoupon = (id, auditResult, rejectReason = '') => apiRequest(`/v1/admin/merchant-coupons/${id}/audit`, { method: 'POST', body: JSON.stringify({ auditResult, rejectReason, requestId: `COUPON-${id}-${Date.now()}` }) })
export const updateMerchantCouponStatus = (id, status, reason = '') => apiRequest(`/v1/admin/merchant-coupons/${id}/status`, { method: 'PUT', body: JSON.stringify({ status, reason }) })
