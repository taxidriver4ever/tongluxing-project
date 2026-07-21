import { apiRequest } from './apiClient.js'

export const getMatchDashboard = (limit = 50) => apiRequest(`/v1/admin/matches/dashboard?limit=${limit}`)
