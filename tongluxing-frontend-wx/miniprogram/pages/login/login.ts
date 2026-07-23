import { passwordLogin, wxPhoneLogin } from "../../api/auth"
import { getDeviceId } from "../../utils/device"
import { completeLogin } from "../../utils/onboarding"

interface PhoneNumberDetail {
  code?: string
  errMsg?: string
}

Page({
  data: { phone: "", password: "", loading: false },
  onPhoneInput(event: WechatMiniprogram.Input) {
    this.setData({ phone: event.detail.value.trim() })
  },
  onPasswordInput(event: WechatMiniprogram.Input) {
    this.setData({ password: event.detail.value })
  },
  async onLogin() {
    const phone = this.data.phone.trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: "请输入正确手机号", icon: "none" })
      return
    }
    if (this.data.password.length < 6) {
      wx.showToast({ title: "请输入至少6位密码", icon: "none" })
      return
    }
    await this.runLogin(() => passwordLogin({
      phone,
      password: this.data.password,
      deviceId: getDeviceId(),
      clientType: "MINI_PROGRAM"
    }))
  },
  async onWechatLogin(event: WechatMiniprogram.CustomEvent<PhoneNumberDetail>) {
    const code = event.detail.code
    if (!code) {
      wx.showToast({ title: "需要授权手机号才能登录", icon: "none" })
      return
    }
    await this.runLogin(() => wxPhoneLogin({ code, deviceId: getDeviceId() }))
  },
  async runLogin(action: () => Promise<import("../../api/auth").LoginResponse>) {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      completeLogin(await action())
    } catch (error) {
      wx.showToast({ title: error instanceof Error ? error.message : "登录失败", icon: "none" })
    } finally {
      this.setData({ loading: false })
    }
  }
})
