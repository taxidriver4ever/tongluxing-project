import { markInviteViewedLocally, markPasswordSetLocally, saveSession } from "./auth-storage"
import type { LoginResponse } from "../api/auth"

export const LOGIN_PAGE = "/pages/login/login"
export const PASSWORD_PAGE = "/pages/login/bind-password/bind-password"
export const INVITE_PAGE = "/pages/login/scan-invite/scan-invite"
export const MAIN_PAGE = "/pages/index/index"

export interface OnboardingState {
  passwordSet: boolean
  miniInviteOnboardingCompleted: boolean
}

export function onboardingRoute(state: OnboardingState): string {
  if (!state.passwordSet) return PASSWORD_PAGE
  if (!state.miniInviteOnboardingCompleted) return INVITE_PAGE
  return MAIN_PAGE
}

export function completeLogin(response: LoginResponse): void {
  saveSession(response)
  if (response.passwordSet) markPasswordSetLocally()
  if (response.miniInviteOnboardingCompleted) markInviteViewedLocally()
  wx.reLaunch({ url: onboardingRoute(response) })
}
