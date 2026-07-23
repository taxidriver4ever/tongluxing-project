import { markPasswordSetLocally, saveSession } from "./auth-storage"
import type { LoginResponse } from "../api/auth"

export const LOGIN_PAGE = "/pages/login/login"
export const PASSWORD_PAGE = "/pages/login/bind-password/bind-password"
export const INVITE_PAGE = "/pages/login/scan-invite/scan-invite"
export const MAIN_PAGE = "/pages/index/index"

export interface AuthRouteState {
  passwordSet: boolean
}

/**
 * 普通登录只检查密码是否已经设置：
 * - 未设置密码：说明微信注册流程尚未完成，继续进入强制设置密码页；
 * - 已设置密码：直接进入主页，不再因为邀请码引导状态进入邀请码页。
 *
 * 邀请码页只由 bind-password 页面在“首次注册并完成密码设置”后主动打开。
 */
export function authenticatedRoute(state: AuthRouteState): string {
  return state.passwordSet ? MAIN_PAGE : PASSWORD_PAGE
}

export function completeLogin(response: LoginResponse): void {
  saveSession(response)
  if (response.passwordSet) markPasswordSetLocally()
  wx.reLaunch({ url: authenticatedRoute(response) })
}
