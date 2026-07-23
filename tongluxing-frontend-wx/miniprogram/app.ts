import { getCurrentUser } from "./api/auth"
import { getToken, isInviteViewedLocally, isPasswordSetLocally } from "./utils/auth-storage"
import { LOGIN_PAGE, onboardingRoute } from "./utils/onboarding"

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
    const route = onboardingRoute({
      passwordSet: me.passwordSet || isPasswordSetLocally(),
      miniInviteOnboardingCompleted: me.miniInviteOnboardingCompleted || isInviteViewedLocally()
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
