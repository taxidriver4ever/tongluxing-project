// import { getToken } from "./utils/auth-storage"
//
// const LOGIN_PAGE = "/pages/login/login"
// const MAIN_PAGE = "/pages/index/index"
//
// function getCurrentRoute(): string {
//   const pages = getCurrentPages()
//   if (pages.length === 0) {
//     return ""
//   }
//   return "/" + pages[pages.length - 1].route
// }
//
// function routeByAuthState(): void {
//   const token = getToken()
//   const currentRoute = getCurrentRoute()
//
//   if (token) {
//     if (currentRoute !== MAIN_PAGE) {
//       wx.reLaunch({
//         url: MAIN_PAGE
//       })
//     }
//     return
//   }
//
//   if (currentRoute !== LOGIN_PAGE) {
//     wx.reLaunch({
//       url: LOGIN_PAGE
//     })
//   }
// }

App<IAppOption>({
  // 暂时关闭登录路由守卫，后续恢复登录模块时打开。
  // onLaunch() {
  //   setTimeout(routeByAuthState, 0)
  // },
  globalData: {}
})
