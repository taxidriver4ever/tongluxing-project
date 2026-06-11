import { request } from "../utils/request"

export interface SendSmsCodeRequest {
  phone: string
  scene: "login"
}

export interface SendSmsCodeResponse {
  expireSeconds: number
}

export interface LoginRequest {
  phone: string
  code: string
  deviceId: string
}

export interface LoginResponse {
  token: string
  refreshToken: string
  userId: number
  isNewUser: boolean
  expireSeconds: number
}

export interface CurrentUser {
  userId: number
  phone: string
  loginStatus: string
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface RefreshTokenResponse {
  token: string
  expireSeconds: number
}

export interface LogoutResponse {
  success: boolean
}

export function sendSmsCode(data: SendSmsCodeRequest): Promise<SendSmsCodeResponse> {
  return request<SendSmsCodeResponse>("/v1/auth/sms-code", {
    method: "POST",
    data
  })
}

export function login(data: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>("/v1/auth/login", {
    method: "POST",
    data
  })
}

export function getCurrentUser(): Promise<CurrentUser> {
  return request<CurrentUser>("/v1/auth/me")
}

export function logout(): Promise<LogoutResponse> {
  return request<LogoutResponse>("/v1/auth/logout", {
    method: "POST"
  })
}

export function refreshToken(data: RefreshTokenRequest): Promise<RefreshTokenResponse> {
  return request<RefreshTokenResponse>("/v1/auth/refresh-token", {
    method: "POST",
    data
  })
}
