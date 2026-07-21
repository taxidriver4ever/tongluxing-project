import { apiRequest } from './apiClient.js'

export const getTickets = ({ status = '', page = 1, size = 20 } = {}) =>
  apiRequest(`/v1/admin/customer-service/tickets?status=${encodeURIComponent(status)}&page=${page}&size=${size}`)

export const assignTicket = id => apiRequest(`/v1/admin/customer-service/tickets/${id}/assign`, {
  method: 'POST', body: JSON.stringify({ requestId: `CS-ASSIGN-${id}-${Date.now()}` }),
})

export const replyTicket = (id, content) => apiRequest(`/v1/admin/customer-service/tickets/${id}/reply`, {
  method: 'POST', body: JSON.stringify({ content, imageKeys: [], requestId: `CS-REPLY-${id}-${Date.now()}` }),
})

export const closeTicket = (id, remark) => apiRequest(`/v1/admin/customer-service/tickets/${id}/close`, {
  method: 'POST', body: JSON.stringify({ remark, requestId: `CS-CLOSE-${id}-${Date.now()}` }),
})
