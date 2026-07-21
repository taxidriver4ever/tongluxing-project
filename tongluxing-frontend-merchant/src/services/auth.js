import { reactive } from 'vue'
import { apiRequest, clearSession, getOrCreateDeviceId, getStoredUser, getToken, saveSession } from './apiClient.js'

export const authState = reactive({ user: getStoredUser(), merchant: null, validated: false })
export const hasSession = () => Boolean(getToken())

export async function loginMerchant({ phone, password, remember }) {
  const login = await apiRequest('/v1/auth/password-login', {
    method: 'POST', body: JSON.stringify({ phone: phone.trim(), password, deviceId: getOrCreateDeviceId() }),
  })
  const user = { userId: String(login.userId), phone: phone.trim() }
  saveSession(login.token, user, remember)
  try {
    const merchant = await apiRequest('/v1/merchants/center/overview')
    authState.user = user; authState.merchant = merchant; authState.validated = true
    return merchant
  } catch (error) { clearSession(); throw new Error(error.status === 403 ? '该账号尚未通过商家审核，请先在 App 提交入驻资料' : error.message) }
}

export async function ensureMerchantSession() {
  if (!hasSession()) return false
  if (authState.validated && authState.merchant) return true
  try { authState.merchant = await apiRequest('/v1/merchants/center/overview'); authState.user = getStoredUser(); authState.validated = true; return true }
  catch { clearLocalMerchantSession(); return false }
}

export async function logoutMerchant() {
  try {
    if (hasSession()) await apiRequest('/v1/auth/logout', { method: 'POST' })
  } finally {
    clearLocalMerchantSession()
  }
}

export function clearLocalMerchantSession() {
  clearSession(); authState.user = null; authState.merchant = null; authState.validated = false
}
