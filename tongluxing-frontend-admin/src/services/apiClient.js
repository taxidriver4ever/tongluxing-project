const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const TOKEN_KEY = 'tongluxing_admin_token'
const OPERATOR_KEY = 'tongluxing_admin_operator'
const LONG_FIELDS = ['applyId', 'certificationId', 'userId', 'vehicleId', 'auditUserId', 'auditLogId', 'operatorId']

export class ApiError extends Error {
  constructor(message, { status = 0, code = 0, payload = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.payload = payload
  }
}

function parseResponseLosslessly(text) {
  if (!text) return {}
  const normalized = text.replace(
    new RegExp(`"(${LONG_FIELDS.join('|')})"\\s*:\\s*(-?\\d+)`, 'g'),
    '"$1":"$2"',
  )
  return JSON.parse(normalized)
}

function storageWithToken() {
  if (localStorage.getItem(TOKEN_KEY)) return localStorage
  if (sessionStorage.getItem(TOKEN_KEY)) return sessionStorage
  return null
}

export function getAdminToken() {
  return storageWithToken()?.getItem(TOKEN_KEY) || ''
}

export function saveAdminSession(token, operator, remember = true) {
  clearAdminSession()
  const storage = remember ? localStorage : sessionStorage
  storage.setItem(TOKEN_KEY, token)
  storage.setItem(OPERATOR_KEY, JSON.stringify(operator))
}

export function readStoredOperator() {
  const storage = storageWithToken()
  if (!storage) return null
  try {
    return JSON.parse(storage.getItem(OPERATOR_KEY) || 'null')
  } catch {
    return null
  }
}

export function clearAdminSession() {
  for (const storage of [localStorage, sessionStorage]) {
    storage.removeItem(TOKEN_KEY)
    storage.removeItem(OPERATOR_KEY)
  }
}

export async function apiRequest(path, options = {}) {
  const token = getAdminToken()
  let response
  try {
    response = await fetch(API_BASE + path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    })
  } catch (error) {
    throw new ApiError('无法连接后端，请确认 18080 服务和 Vite 代理已启动', { payload: error })
  }

  const text = await response.text()
  let payload
  try {
    payload = parseResponseLosslessly(text)
  } catch {
    throw new ApiError(`接口返回了无法解析的内容（HTTP ${response.status}）`, { status: response.status })
  }

  if (!response.ok || payload.success === false || (payload.code && payload.code !== 200)) {
    const error = new ApiError(payload.message || '接口请求失败', {
      status: response.status,
      code: payload.code,
      payload,
    })
    if ((response.status === 401 || response.status === 403)
      && !['/v1/admin/auth/login', '/v1/admin/auth/me'].includes(path)) {
      clearAdminSession()
      window.dispatchEvent(new CustomEvent('admin-auth-expired'))
    }
    throw error
  }
  return payload.data
}

export { API_BASE }
