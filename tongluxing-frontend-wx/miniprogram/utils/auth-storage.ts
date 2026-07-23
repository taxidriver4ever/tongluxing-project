const TOKEN_KEY = "tongluxing_token"
const REFRESH_TOKEN_KEY = "tongluxing_refresh_token"
const USER_ID_KEY = "tongluxing_user_id"
const PASSWORD_SET_KEY = "tongluxing_password_set"
const INVITE_VIEWED_KEY = "tongluxing_invite_viewed"

export interface AuthSession {
  token: string
  refreshToken: string
  userId: number
  passwordSet?: boolean
  miniInviteOnboardingCompleted?: boolean
}

export function getToken(): string {
  return String(wx.getStorageSync(TOKEN_KEY) || "")
}

export function getRefreshToken(): string {
  return String(wx.getStorageSync(REFRESH_TOKEN_KEY) || "")
}

export function getUserId(): number {
  return Number(wx.getStorageSync(USER_ID_KEY) || 0)
}

export function isPasswordSetLocally(): boolean {
  return Boolean(wx.getStorageSync(PASSWORD_SET_KEY))
}

export function isInviteViewedLocally(): boolean {
  return Boolean(wx.getStorageSync(INVITE_VIEWED_KEY))
}

export function markPasswordSetLocally(): void {
  wx.setStorageSync(PASSWORD_SET_KEY, true)
}

export function markInviteViewedLocally(): void {
  wx.setStorageSync(INVITE_VIEWED_KEY, true)
}

export function saveSession(session: AuthSession): void {
  wx.setStorageSync(TOKEN_KEY, session.token)
  wx.setStorageSync(REFRESH_TOKEN_KEY, session.refreshToken)
  wx.setStorageSync(USER_ID_KEY, session.userId)
  wx.setStorageSync(PASSWORD_SET_KEY, Boolean(session.passwordSet))
  wx.setStorageSync(INVITE_VIEWED_KEY, Boolean(session.miniInviteOnboardingCompleted))
}

export function clearSession(): void {
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync(REFRESH_TOKEN_KEY)
  wx.removeStorageSync(USER_ID_KEY)
  wx.removeStorageSync(PASSWORD_SET_KEY)
  wx.removeStorageSync(INVITE_VIEWED_KEY)
}
