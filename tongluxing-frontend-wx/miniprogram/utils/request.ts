import { clearSession, getToken } from "./auth-storage"

const BASE_URL = "http://127.0.0.1:18080/api"
const LOGIN_PAGE = "/pages/login/login"

let isRedirectingToLogin = false

interface ApiResult<T> {
  code: number
  message: string
  data: T
  success: boolean
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE"
  data?: WechatMiniprogram.IAnyObject
}

function handleUnauthorized(): void {
  clearSession()

  const pages = getCurrentPages()
  const currentPage = pages.length > 0 ? pages[pages.length - 1].route : ""
  if (currentPage === "pages/login/login" || isRedirectingToLogin) {
    return
  }

  isRedirectingToLogin = true
  wx.showToast({
    title: "登录已过期，请重新登录",
    icon: "none"
  })

  setTimeout(() => {
    wx.reLaunch({
      url: LOGIN_PAGE,
      complete() {
        isRedirectingToLogin = false
      }
    })
  }, 500)
}

export function request<T>(url: string, options?: RequestOptions): Promise<T> {
  const requestOptions = options || {}
  const token = getToken()
  const header: Record<string, string> = {
    "content-type": "application/json",
    "X-Client-Type": "MINI_PROGRAM"
  }

  if (token) {
    header.Authorization = "Bearer " + token
  }

  return new Promise<T>((resolve, reject) => {
    wx.request<ApiResult<T>>({
      url: BASE_URL + url,
      method: requestOptions.method || "GET",
      data: requestOptions.data,
      header,
      success(response) {
        const body = response.data
        if (response.statusCode === 401 || (body && body.code === 401)) {
          handleUnauthorized()
          reject(new Error((body && body.message) || "登录已过期，请重新登录"))
          return
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error((body && body.message) || ("请求失败：HTTP " + response.statusCode)))
          return
        }
        if (body && body.success) {
          resolve(body.data)
          return
        }
        reject(new Error((body && body.message) || "请求失败"))
      },
      fail(error) {
        reject(new Error(error.errMsg || "网络异常，请稍后重试"))
      }
    })
  })
}
