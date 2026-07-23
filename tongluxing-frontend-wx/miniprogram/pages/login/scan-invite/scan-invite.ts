import { completeMiniInviteOnboarding } from "../../../api/auth"
import { bindInviteCode, validateInviteQr } from "../../../api/invite"
import { markInviteViewedLocally } from "../../../utils/auth-storage"
import { MAIN_PAGE } from "../../../utils/onboarding"

function extractToken(content: string): string {
  const match = content.match(/[?&]token=([^&]+)/)
  return match ? decodeURIComponent(match[1]) : ""
}

Page({
  data: { code: "", loading: false, profileMode: false },
  onLoad(options: Record<string, string>) {
    const profileMode = options.mode === "profile"
    this.setData({ profileMode, code: options.code || "" })
    if (!profileMode) {
      markInviteViewedLocally()
      void completeMiniInviteOnboarding().catch(() => undefined)
    }
  },
  codeInput(event: WechatMiniprogram.Input) {
    this.setData({ code: event.detail.value.trim().toUpperCase() })
  },
  async chooseFromAlbum() {
    try {
      const result = await wx.scanCode({ onlyFromCamera: false, scanType: ["qrCode"] })
      const token = extractToken(result.result)
      if (token) {
        const validation = await validateInviteQr(token)
        if (!validation.valid || !validation.inviteCode) throw new Error("邀请二维码无效或已过期")
        this.setData({ code: validation.inviteCode })
        wx.showToast({ title: "邀请码验证成功", icon: "success" })
        return
      }
      this.setData({ code: result.result.trim().toUpperCase() })
      wx.showToast({ title: "已识别邀请码", icon: "success" })
    } catch (error) {
      const message = error instanceof Error ? error.message : "未识别到有效邀请码"
      if (!message.includes("cancel")) wx.showToast({ title: message, icon: "none" })
    }
  },
  async submit() {
    const code = this.data.code.trim().toUpperCase()
    if (!code) {
      wx.showToast({ title: "请输入或识别邀请码", icon: "none" })
      return
    }
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      await bindInviteCode(code)
      wx.showToast({ title: "邀请码绑定成功", icon: "success" })
      setTimeout(() => this.finish(), 450)
    } catch (error) {
      wx.showToast({ title: error instanceof Error ? error.message : "邀请码验证失败", icon: "none" })
    } finally {
      this.setData({ loading: false })
    }
  },
  skip() { this.finish() },
  back() { wx.navigateBack({ fail: () => wx.reLaunch({ url: MAIN_PAGE }) }) },
  finish() {
    if (this.data.profileMode) wx.navigateBack({ fail: () => wx.reLaunch({ url: MAIN_PAGE }) })
    else wx.reLaunch({ url: MAIN_PAGE })
  }
})
