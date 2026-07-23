import { getCurrentUser } from "./api/auth"
import { getToken, isPasswordSetLocally } from "./utils/auth-storage"
import { authenticatedRoute, LOGIN_PAGE } from "./utils/onboarding"

function currentRoute(): string {
  const pages = getCurrentPages()
  return pages.length ? "/" + pages[pages.length - 1].route : ""
}

async function bootstrap(): Promise<void> {
  if (!getToken()) {
    if (currentRoute() !== LOGIN_PAGE) wx.reLaunch({ url: LOGIN_PAGE })
    return
  }

  try {
    const me = await getCurrentUser()
    const route = authenticatedRoute({
      passwordSet: me.passwordSet || isPasswordSetLocally()
    })
    if (currentRoute() !== route) wx.reLaunch({ url: route })
  } catch (_error) {
    // request.ts 会统一处理 401；网络暂时不可用时不反复打断当前页面。
  }
}

App<IAppOption>({
  onLaunch() {
    void bootstrap()
  },
  globalData: {}
})
