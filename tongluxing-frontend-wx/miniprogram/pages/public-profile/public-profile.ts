import { getUserHomepage, UserHomepage } from "../../api/user"
import { getImageUrl } from "../../api/storage"
import { getUserId } from "../../utils/auth-storage"

function initial(value: string): string {
  return (value || "同").trim().slice(0, 1)
}

Page({
  data: {
    loading: true,
    error: "",
    homepage: null as UserHomepage | null,
    avatarUrl: "",
    avatarText: "同",
    distanceKm: "0.0",
    isOwner: false
  },
  onLoad(options: Record<string, string>) {
    const userId = Number(options.userId || getUserId())
    this.setData({ isOwner: String(userId) === String(getUserId()) })
    void this.load(userId)
  },
  async load(userId: number) {
    this.setData({ loading: true, error: "" })
    try {
      const homepage = await getUserHomepage(userId)
      let avatarUrl = ""
      if (homepage.profile.avatarImageKey) {
        try { avatarUrl = await getImageUrl(homepage.profile.avatarImageKey) } catch (_error) { avatarUrl = "" }
      }
      this.setData({
        homepage,
        avatarUrl,
        avatarText: initial(homepage.profile.nickname),
        distanceKm: ((homepage.profile.totalDistanceMeters || 0) / 1000).toFixed(1)
      })
    } catch (error) {
      this.setData({ error: error instanceof Error ? error.message : "个人主页加载失败" })
    } finally {
      this.setData({ loading: false })
    }
  },
  edit() { wx.navigateTo({ url: "/pages/profile-edit/profile-edit" }) },
  retry() {
    const pages = getCurrentPages()
    const options = (pages[pages.length - 1] as WechatMiniprogram.Page.Instance<WechatMiniprogram.IAnyObject, WechatMiniprogram.IAnyObject> & { options?: Record<string, string> }).options || {}
    void this.load(Number(options.userId || getUserId()))
  }
})
