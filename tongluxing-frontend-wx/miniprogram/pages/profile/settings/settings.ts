const SETTING_KEY = "tlx-mini-local-settings"
interface LocalSettings { location: boolean; notification: boolean }

Page({
  data: { location: true, notification: true, cache: "本地缓存" },
  onLoad() {
    const saved = wx.getStorageSync(SETTING_KEY) as LocalSettings | ""
    if (saved) this.setData({ location: saved.location !== false, notification: saved.notification !== false })
  },
  persist() { wx.setStorageSync(SETTING_KEY, { location: this.data.location, notification: this.data.notification }) },
  toggleLocation(event: WechatMiniprogram.SwitchChange) { this.setData({ location: event.detail.value }); this.persist() },
  toggleNotification(event: WechatMiniprogram.SwitchChange) { this.setData({ notification: event.detail.value }); this.persist() },
  privacy() { wx.navigateTo({ url: "/pages/privacy/privacy" }) },
  async clear() {
    const result = await wx.showModal({ title: "清理缓存", content: "将清理地点历史和页面临时数据，不会退出登录。" })
    if (!result.confirm) return
    const keys = ["tlx-location-history", "tlx-page-cache", "tlx-search-keywords"]
    keys.forEach(key => wx.removeStorageSync(key))
    wx.showToast({ title: "缓存已清理", icon: "success" })
    this.setData({ cache: "已清理" })
  }
})
