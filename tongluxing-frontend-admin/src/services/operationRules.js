import { apiRequest } from './apiClient.js'

export const getOperationRule = endpoint => apiRequest(endpoint)

export const updateOperationRule = (endpoint, configKey, configValue, changeReason, effectiveAt = null) => apiRequest(endpoint, {
  method: 'PUT',
  body: JSON.stringify({
    configKey,
    configValue,
    effectiveAt: effectiveAt || null,
    requestId: `CONFIG-${configKey}-${Date.now()}`,
    changeReason,
  }),
})

export const runMonthlyCouponGrant = month => apiRequest(`/v1/admin/coupon-issuance-rules/run${month ? `?month=${month}` : ''}`, {
  method: 'POST',
})
