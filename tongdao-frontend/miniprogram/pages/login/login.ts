import { wxPhoneLogin } from "../../api/auth"
import { saveSession } from "../../utils/auth-storage"

Component({
  data: {
    agreed: false,
    loading: false,
    showAgreementModal: false
  },
  methods: {
    toggleAgree() {
      this.setData({ agreed: !this.data.agreed })
    },
    closeAgreementModal() {
      this.setData({ showAgreementModal: false })
    },
    agreeAndContinue() {
      this.setData({ agreed: true, showAgreementModal: false })
    },
    onNeedAgree() {
      this.setData({ showAgreementModal: true })
    },
    async onWxPhoneLogin(event: any) {
      console.log("getPhoneNumber detail", event.detail)
      if (!this.data.agreed) {
        this.setData({ showAgreementModal: true })
        return
      }

      const detail = event.detail || {}
      if (detail.errMsg !== "getPhoneNumber:ok") {
        wx.showToast({ title: this.getPhoneAuthErrorMessage(detail.errMsg), icon: "none" })
        return
      }
      if (!detail.code) {
        wx.showToast({ title: "未获取到手机号授权码", icon: "none" })
        return
      }
      if (this.data.loading) {
        return
      }

      this.setData({ loading: true })
      try {
        const result = await wxPhoneLogin({
          code: detail.code,
          deviceId: "miniapp-device-id"
        })
        saveSession({
          token: result.token,
          refreshToken: result.refreshToken,
          userId: result.userId
        })
        wx.showToast({ title: "登录成功", icon: "success" })
        wx.reLaunch({
          url: result.isNewUser ? "/pages/profile/profile" : "/pages/index/index"
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    getPhoneAuthErrorMessage(errMsg: string): string {
      if (!errMsg) {
        return "手机号授权失败"
      }
      if (errMsg.indexOf("cancel") >= 0 || errMsg.indexOf("deny") >= 0 || errMsg.indexOf("用户取消") >= 0) {
        return "用户取消授权"
      }
      if (errMsg.indexOf("no permission") >= 0) {
        return "当前小程序未开通手机号授权能力"
      }
      console.error("getPhoneNumber failed", errMsg)
      return "手机号授权失败，请检查小程序权限"
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})
