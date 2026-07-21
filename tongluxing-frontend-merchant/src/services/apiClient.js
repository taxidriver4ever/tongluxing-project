const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const TOKEN_KEY = 'tongluxing_merchant_token'
const USER_KEY = 'tongluxing_merchant_user'
const DEVICE_KEY = 'tongluxing_merchant_device_id'
const LONG_FIELDS = ['userId', 'merchantId', 'storeId', 'couponId', 'applicationId', 'activityId', 'productId']

export class ApiError extends Error {
  constructor(message, { status = 0, code = 0, payload = null } = {}) {
    super(message); this.name = 'ApiError'; this.status = status; this.code = code; this.payload = payload
  }
}

function parseLosslessly(text) {
  if (!text) return {}
  return JSON.parse(text.replace(new RegExp(`"(${LONG_FIELDS.join('|')})"\\s*:\\s*(-?\\d+)`, 'g'), '"$1":"$2"'))
}

export const getToken = () => localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || ''
export function getOrCreateDeviceId() {
  let id = localStorage.getItem(DEVICE_KEY)
  if (!id) {
    const random = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`
    id = `merchant-web-${random}`
    localStorage.setItem(DEVICE_KEY, id)
  }
  return id
}
export function getStoredUser() {
  const storage = localStorage.getItem(TOKEN_KEY) ? localStorage : sessionStorage
  try { return JSON.parse(storage.getItem(USER_KEY) || 'null') } catch { return null }
}
export function saveSession(token, user, remember = true) {
  clearSession(); const storage = remember ? localStorage : sessionStorage
  storage.setItem(TOKEN_KEY, token); storage.setItem(USER_KEY, JSON.stringify(user))
}
export function clearSession() {
  for (const storage of [localStorage, sessionStorage]) { storage.removeItem(TOKEN_KEY); storage.removeItem(USER_KEY) }
}

export async function apiRequest(path, options = {}) {
  let response
  try {
    response = await fetch(API_BASE + path, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}), ...options.headers },
    })
  } catch (error) { throw new ApiError('无法连接后端，请确认 18080 服务和 Vite 代理已启动', { payload: error }) }
  const text = await response.text()
  let payload
  try { payload = parseLosslessly(text) } catch { throw new ApiError(`接口返回无法解析（HTTP ${response.status}）`, { status: response.status }) }
  if (!response.ok || payload.success === false || (payload.code && payload.code !== 200)) {
    const error = new ApiError(payload.message || '接口请求失败', { status: response.status, code: payload.code, payload })
    if (response.status === 401) { clearSession(); window.dispatchEvent(new Event('merchant-auth-expired')) }
    throw error
  }
  return payload.data
}

export { API_BASE }
