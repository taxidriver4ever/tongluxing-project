import { reactive } from 'vue'
import {
  apiRequest, clearAdminSession, getAdminToken, readStoredOperator, saveAdminSession,
} from './apiClient.js'

export const adminAuthState = reactive({
  operator: readStoredOperator(),
  validated: false,
})

export const hasAdminSession = () => Boolean(getAdminToken())

export async function loginAdmin({ username, password, remember = true }) {
  const data = await apiRequest('/v1/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: username.trim(), password }),
  })
  const operator = {
    operatorId: String(data.operatorId),
    username: data.username,
    displayName: data.displayName,
    expireSeconds: data.expireSeconds,
  }
  saveAdminSession(data.token, operator, remember)
  adminAuthState.operator = operator
  adminAuthState.validated = true
  return operator
}

export async function ensureAdminSession() {
  if (!hasAdminSession()) return false
  if (adminAuthState.validated && adminAuthState.operator) return true
  try {
    const data = await apiRequest('/v1/admin/auth/me')
    const operator = {
      operatorId: String(data.operatorId),
      username: data.username,
      displayName: data.displayName,
      expireAt: data.expireAt,
    }
    const remember = Boolean(localStorage.getItem('tongluxing_admin_token'))
    saveAdminSession(getAdminToken(), operator, remember)
    adminAuthState.operator = operator
    adminAuthState.validated = true
    return true
  } catch {
    clearLocalAdminSession()
    return false
  }
}

export async function logoutAdmin() {
  try {
    if (hasAdminSession()) await apiRequest('/v1/admin/auth/logout', { method: 'POST' })
  } finally {
    clearLocalAdminSession()
  }
}

export function clearLocalAdminSession() {
  clearAdminSession()
  adminAuthState.operator = null
  adminAuthState.validated = false
}
