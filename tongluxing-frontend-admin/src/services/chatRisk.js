import { apiRequest } from './apiClient.js'
export const listChatReports = (status='PENDING') => apiRequest(`/v1/admin/chat-risk/reports?status=${status}&limit=100`)
export const listMessageRisks = (status='PENDING') => apiRequest(`/v1/admin/chat-risk/message-risks?status=${status}&limit=100`)
export const listTripConfirmations = (status='OPEN') => apiRequest(`/v1/admin/chat-risk/trip-confirmations?status=${status}&limit=100`)
export const reviewChatReport = (id,decision,note) => apiRequest(`/v1/admin/chat-risk/reports/${id}/review`,{
  method:'POST',body:JSON.stringify({decision,note}),
})
