import { apiRequest } from './apiClient.js'
export const getPartnerApplications = (status='') => apiRequest(`/v1/admin/merchant-partners/applications?status=${encodeURIComponent(status)}&page=1&size=100`)
export const auditPartnerApplication = (id,result,reason='') => apiRequest(`/v1/admin/merchant-partners/applications/${id}/audit`,{method:'POST',body:JSON.stringify({auditResult:result,rejectReason:reason,requestId:`PARTNER-${id}-${Date.now()}`})})
export const auditPartnerCancellation = (id,result,reason='') => apiRequest(`/v1/admin/merchant-partners/applications/${id}/cancellation-audit`,{method:'POST',body:JSON.stringify({auditResult:result,rejectReason:reason,requestId:`PARTNER-CANCEL-${id}-${Date.now()}`})})
export const getPartnerCoupons = (status='') => apiRequest(`/v1/admin/partner-coupons?status=${encodeURIComponent(status)}&page=1&size=100`)
export const auditPartnerCoupon = (id,result) => apiRequest(`/v1/admin/partner-coupons/${id}/audit`,{method:'POST',body:JSON.stringify({auditResult:result,rejectReason:result==='REJECTED'?'平台审核不通过':'',requestId:`PARTNER-COUPON-${id}-${Date.now()}`})})
export const getPlatformCoupons = (status='') => apiRequest(`/v1/admin/coupon-templates?status=${encodeURIComponent(status)}`)
export const createPlatformCoupon = data => apiRequest('/v1/admin/coupon-templates',{method:'POST',body:JSON.stringify(data)})
export const updatePlatformCouponStatus = (id,status) => apiRequest(`/v1/admin/coupon-templates/${id}/status`,{method:'PUT',body:JSON.stringify({status})})

export const issuePlatformCoupon = data => apiRequest('/v1/admin/coupon-templates/issues',{method:'POST',body:JSON.stringify(data)})
