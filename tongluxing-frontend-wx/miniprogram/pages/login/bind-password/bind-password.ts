import { setPassword } from "../../../api/auth"
import { markPasswordSetLocally } from "../../../utils/auth-storage"
import { INVITE_PAGE } from "../../../utils/onboarding"

Page({
  data: { password: "", confirm: "", loading: false },
  passwordInput(event: WechatMiniprogram.Input) { this.setData({ password: event.detail.value }) },
  confirmInput(event: WechatMiniprogram.Input) { this.setData({ confirm: event.detail.value }) },
  async submit() {
    if (this.data.password.length < 6 || this.data.password.length > 32) {
      wx.showToast({ title: "密码长度需为6-32位", icon: "none" })
      return
    }
    if (this.data.password !== this.data.confirm) {
      wx.showToast({ title: "两次密码不一致", icon: "none" })
      return
    }
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      await setPassword(this.data.password)
      markPasswordSetLocally()
      wx.reLaunch({ url: INVITE_PAGE })
    } catch (error) {
      wx.showToast({ title: error instanceof Error ? error.message : "密码设置失败", icon: "none" })
    } finally {
      this.setData({ loading: false })
    }
  },
  onUnload() {
    // 强制步骤不允许通过返回绕过；下次启动仍由后端 passwordSet 状态重新进入本页。
  }
})
