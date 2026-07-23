import { getMyInviteCode, getMyInviteQr, getInviteSummary, InviteCode, InviteQr, InviteRewardProgress, validateInviteQr } from "../../../api/invite"

function displayTime(value: string): string {
  if (!value) return "—"
  return value.slice(0, 16).replace("T", " ")
}

Page({
  data: {
    loading: true,
    error: "",
    code: null as InviteCode | null,
    qr: null as InviteQr | null,
    summary: null as InviteRewardProgress | null,
    qrImage: "",
    expiresText: "—"
  },
  onLoad() { void this.load() },
  async load() {
    this.setData({ loading: true, error: "" })
    try {
      const [code, qr, summary] = await Promise.all([getMyInviteCode(), getMyInviteQr(), getInviteSummary()])
      this.setData({ code, qr, summary, qrImage: "data:image/png;base64," + qr.qrImageBase64, expiresText: displayTime(qr.expiresAt) })
    } catch (error) {
      this.setData({ error: error instanceof Error ? error.message : "邀请信息加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  copyCode() {
    const code = this.data.code && this.data.code.inviteCode
    if (!code) return
    wx.setClipboardData({ data: code })
  },
  copyLink() {
    const text = (this.data.qr && this.data.qr.qrContent) || (this.data.code && this.data.code.inviteCode) || ""
    if (text) wx.setClipboardData({ data: text })
  },
  async validate() {
    const token = this.data.qr && this.data.qr.qrToken
    if (!token) return
    try {
      const result = await validateInviteQr(token)
      wx.showToast({ title: result.valid ? "二维码有效" : "二维码状态：" + result.status, icon: result.valid ? "success" : "none" })
    } catch (error) {
      wx.showToast({ title: error instanceof Error ? error.message : "验证失败", icon: "none" })
    }
  },
  async saveQr() {
    const base64 = this.data.qr && this.data.qr.qrImageBase64
    const code = this.data.code && this.data.code.inviteCode
    if (!base64 || !code) return
    const path = wx.env.USER_DATA_PATH + "/同路行邀请码_" + code + ".png"
    try {
      wx.getFileSystemManager().writeFileSync(path, base64, "base64")
      await new Promise<void>((resolve, reject) => wx.saveImageToPhotosAlbum({ filePath: path, success: () => resolve(), fail: reject }))
      wx.showToast({ title: "二维码已保存", icon: "success" })
    } catch (error) {
      wx.showToast({ title: "保存失败，请检查相册权限", icon: "none" })
    }
  },
  onShareAppMessage() {
    const code = (this.data.code && this.data.code.inviteCode) || ""
    return { title: "加入同路行，和我一起自驾出发", path: "/pages/login/scan-invite/scan-invite?code=" + encodeURIComponent(code) }
  }
})
