import { request } from "../utils/request"

export interface PasswordLoginRequest {
  phone: string
  password: string
  deviceId: string
  clientType: "MINI_PROGRAM"
}

export interface WxPhoneLoginRequest {
  code?: string
  deviceId: string
}

export interface LoginResponse {
  token: string
  refreshToken: string
  userId: number
  isNewUser: boolean
  passwordSet: boolean
  miniInviteOnboardingCompleted: boolean
  expireSeconds: number
}

export interface CurrentUser {
  userId: number
  phone: string
  loginStatus: string
  passwordSet: boolean
  miniInviteOnboardingCompleted: boolean
}

export interface RefreshTokenResponse {
  token: string
  refreshToken: string
  expireSeconds: number
}

export interface LogoutResponse {
  success: boolean
}

export function passwordLogin(data: PasswordLoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>("/v1/auth/password-login", { method: "POST", data })
}

export function wxPhoneLogin(data: WxPhoneLoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>("/v1/auth/wx-phone-login", { method: "POST", data })
}

export function setPassword(password: string): Promise<void> {
  return request<void>("/v1/auth/set-password", { method: "POST", data: { password } })
}

export function completeMiniInviteOnboarding(): Promise<void> {
  return request<void>("/v1/auth/mini-onboarding/invite-viewed", { method: "POST" })
}

export function getCurrentUser(): Promise<CurrentUser> {
  return request<CurrentUser>("/v1/auth/me")
}

export function logout(): Promise<LogoutResponse> {
  return request<LogoutResponse>("/v1/auth/logout", { method: "POST" })
}

export function refreshToken(refreshTokenValue: string): Promise<RefreshTokenResponse> {
  return request<RefreshTokenResponse>("/v1/auth/refresh-token", {
    method: "POST",
    data: { refreshToken: refreshTokenValue }
  })
}
