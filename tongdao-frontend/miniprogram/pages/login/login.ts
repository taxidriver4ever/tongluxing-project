import { getCurrentUser, login, sendSmsCode } from "../../api/auth"
import { saveSession } from "../../utils/auth-storage"

const PHONE_PATTERN = /^1[3-9]\d{9}$/

Component({
  data: {
    phone: "",
    code: "",
    agreed: false,
    codeSent: false,
    countdown: 0,
    countdownText: "重发",
    sendDisabled: true,
    loginDisabled: true,
    resendDisabled: false,
    loading: false,
    showAgreementModal: false
  },
  lifetimes: {
    detached() {
      const timer = (this as unknown as { countdownTimer?: number }).countdownTimer
      if (timer) {
        clearInterval(timer)
      }
    }
  },
  methods: {
    onPhoneInput(event: any) {
      this.setData({ phone: event.detail.value })
      this.updateButtonState()
    },
    onCodeInput(event: any) {
      this.setData({ code: event.detail.value })
      this.updateButtonState()
    },
    toggleAgree() {
      this.setData({ agreed: !this.data.agreed })
      this.updateButtonState()
    },
    closeAgreementModal() {
      this.setData({ showAgreementModal: false })
    },
    agreeAndContinue() {
      this.setData({ agreed: true, showAgreementModal: false })
    },
    async onSendCode() {
      if (!this.validatePhone()) {
        return
      }
      if (!this.data.agreed) {
        this.setData({ showAgreementModal: true })
        return
      }
      if (this.data.countdown > 0 || this.data.loading) {
        return
      }

      this.setData({ loading: true })
      this.updateButtonState()
      try {
        const result = await sendSmsCode({
          phone: this.data.phone,
          scene: "login"
        })
        wx.showToast({ title: "验证码已发送", icon: "none" })
        this.startCountdown(result.expireSeconds || 60)
        this.setData({ codeSent: true })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
        this.updateButtonState()
      }
    },
    async onLogin() {
      if (!this.validatePhone()) {
        return
      }
      if (!this.data.agreed) {
        this.setData({ showAgreementModal: true })
        return
      }
      if (!/^\d{6}$/.test(this.data.code)) {
        wx.showToast({ title: "请输入6位验证码", icon: "none" })
        return
      }

      this.setData({ loading: true })
      this.updateButtonState()
      try {
        const result = await login({
          phone: this.data.phone,
          code: this.data.code,
          deviceId: "miniapp-device-id"
        })
        saveSession({
          token: result.token,
          refreshToken: result.refreshToken,
          userId: result.userId
        })
        await getCurrentUser()
        wx.showToast({ title: "登录成功", icon: "success" })
        wx.redirectTo({ url: result.isNewUser ? "/pages/profile/profile" : "/pages/index/index" })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
        this.updateButtonState()
      }
    },
    validatePhone(): boolean {
      if (!this.data.phone) {
        wx.showToast({ title: "请输入手机号", icon: "none" })
        return false
      }
      if (!PHONE_PATTERN.test(this.data.phone)) {
        wx.showToast({ title: "请输入正确的手机号", icon: "none" })
        return false
      }
      return true
    },
    startCountdown(seconds: number) {
      const host = this as unknown as { countdownTimer?: number }
      if (host.countdownTimer) {
        clearInterval(host.countdownTimer)
      }

      this.setData({ countdown: seconds, countdownText: `${seconds}s`, resendDisabled: true })
      this.updateButtonState()
      host.countdownTimer = setInterval(() => {
        const next = this.data.countdown - 1
        if (next <= 0) {
          clearInterval(host.countdownTimer)
          host.countdownTimer = undefined
          this.setData({ countdown: 0, countdownText: "重发", resendDisabled: false })
          this.updateButtonState()
          return
        }
        this.setData({ countdown: next, countdownText: `${next}s`, resendDisabled: true })
        this.updateButtonState()
      }, 1000)
    },
    updateButtonState() {
      this.setData({
        sendDisabled: !this.data.phone || !this.data.agreed || this.data.loading,
        loginDisabled: !this.data.phone || this.data.code.length !== 6 || !this.data.agreed || this.data.loading,
        resendDisabled: this.data.countdown > 0 || this.data.loading
      })
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})
