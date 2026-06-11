const TOKEN_KEY = "tongdao_token"
const REFRESH_TOKEN_KEY = "tongdao_refresh_token"
const USER_ID_KEY = "tongdao_user_id"

export interface AuthSession {
  token: string
  refreshToken: string
  userId: number
}

export function getToken(): string {
  return wx.getStorageSync(TOKEN_KEY) || ""
}

export function getRefreshToken(): string {
  return wx.getStorageSync(REFRESH_TOKEN_KEY) || ""
}

export function saveSession(session: AuthSession): void {
  wx.setStorageSync(TOKEN_KEY, session.token)
  wx.setStorageSync(REFRESH_TOKEN_KEY, session.refreshToken)
  wx.setStorageSync(USER_ID_KEY, session.userId)
}

export function clearSession(): void {
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync(REFRESH_TOKEN_KEY)
  wx.removeStorageSync(USER_ID_KEY)
}
