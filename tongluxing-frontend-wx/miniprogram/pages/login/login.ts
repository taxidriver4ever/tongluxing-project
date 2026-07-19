import { saveSession } from "../../utils/auth-storage"

Page({
  data: { phone: "", password: "", loading: false },
  onPhoneInput(event: WechatMiniprogram.Input) {
    this.setData({ phone: event.detail.value })
  },
  onPasswordInput(event: WechatMiniprogram.Input) {
    this.setData({ password: event.detail.value })
  },
  onLogin() {
    if (!this.data.phone || !this.data.password) {
      wx.showToast({ title: "请输入手机号和密码", icon: "none" })
      return
    }
    this.completeLogin()
  },
  onWechatLogin() {
    this.completeLogin()
  },
  goInviteGrowthTest() {
    wx.navigateTo({ url: "/pages/dev/invite-growth-test/invite-growth-test" })
  },
  completeLogin() {
    if (this.data.loading) return
    this.setData({ loading: true })
    setTimeout(() => {
      saveSession({ token: "mock-token", refreshToken: "mock-refresh-token", userId: 10001 })
      wx.reLaunch({ url: "/pages/index/index" })
    }, 350)
  }
})
