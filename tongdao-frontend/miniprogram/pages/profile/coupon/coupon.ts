import { getUserCoupons, CouponSummary } from "../../../api/coupon"
import { getToken } from "../../../utils/auth-storage"

const TABS = [
  { key: "all",    label: "全部" },
  { key: "WASH",   label: "洗车" },
  { key: "FUEL",   label: "加油" },
  { key: "HOTEL",  label: "住宿" },
]

// 优惠券类型 → 样式映射
const TYPE_STYLE: Record<string, { typeLabel: string; accentClass: string; pillClass: string; amountClass: string; btnClass: string; btnText: string }> = {
  "PLATFORM": { typeLabel: "平台补贴券", accentClass: "orange", pillClass: "orange", amountClass: "orange", btnClass: "orange", btnText: "去使用" },
  "MERCHANT":  { typeLabel: "商家拉新券", accentClass: "blue",   pillClass: "blue",   amountClass: "blue",   btnClass: "blue",   btnText: "查看券码" },
  "REWARD":    { typeLabel: "邀请奖励券", accentClass: "green",  pillClass: "green",  amountClass: "green",  btnClass: "green",  btnText: "查看可用门店" },
}

const EXPIRING_WINDOW_MS = 7 * 24 * 60 * 60 * 1000

interface CouponRow extends CouponSummary {
  accentClass: string
  pillClass: string
  amountClass: string
  btnClass: string
  btnText: string
  typeLabel: string
  expireLabel: string
  expiring: boolean
  amountLabel: string
}

Page({
  data: {
    statusStyle: "",
    topbarStyle: "",
    loading: false,
    activeTab: "all",
    activeTabLabel: "",
    tabs: TABS,
    coupons: [] as CouponRow[],
    // 统计
    availableCount: 0,
    expiringCount: 0,
    usedCount: 0,
    expiredCount: 0,
    summaryText: "",
  },

  onLoad() {
    this.initSafeArea()
    this.setData({ activeTabLabel: "全部" })
    this.loadCoupons("all")
  },

  initSafeArea() {
    try {
      const menu = wx.getMenuButtonBoundingClientRect()
      const sys = wx.getSystemInfoSync()
      const statusH = sys.statusBarHeight || 24
      const statusStyle = 'height: ' + statusH + 'px;'
      const topbarH = Math.round(menu.bottom - statusH)
      const capsuleRightW = sys.screenWidth - menu.left
      const topbarStyle = 'height: ' + topbarH + 'px; padding-right: ' + capsuleRightW + 'px;'
      this.setData({ statusStyle, topbarStyle })
    } catch (_) {
      this.setData({
        statusStyle: 'height: 44px;',
        topbarStyle: 'height: 44px; padding-right: 190rpx;'
      })
    }
  },

  switchTab(e: WechatMiniprogram.TouchEvent) {
    const key = String(e.currentTarget.dataset.key)
    const tab = TABS.find(t => t.key === key)
    this.setData({ activeTab: key, activeTabLabel: tab ? tab.label : "" })
    this.loadCoupons(key)
  },

  async loadCoupons(tabKey: string) {
    if (!getToken()) return
    this.setData({ loading: true })
    try {
      const type = tabKey === "all" ? undefined : tabKey
      const [availRes, usedRes] = await Promise.all([
        getUserCoupons("AVAILABLE", type, 1, 50),
        getUserCoupons("USED",      type, 1, 1),
      ])

      const now = Date.now()

      const couponRows: CouponRow[] = (availRes.records || []).map((c: CouponSummary) => {
        const style = TYPE_STYLE[c.couponType] || TYPE_STYLE["PLATFORM"]
        const endTs = c.validEndAt ? new Date(c.validEndAt).getTime() : 0
        const expiring = endTs > 0 && endTs >= now && endTs - now < EXPIRING_WINDOW_MS
        const daysLeft = endTs > 0 ? Math.ceil((endTs - now) / 86400000) : 0
        const expireLabel = expiring
          ? (daysLeft <= 1 ? "今日到期" : "剩 " + daysLeft + " 天")
          : (c.validEndAt ? c.validEndAt.substring(0, 10) + " 到期" : "")
        const amountLabel = c.discountAmount >= 100
          ? "免费"
          : (c.couponType === "MERCHANT" && c.discountAmount < 1)
            ? Math.round(c.discountAmount * 10) + "折"
            : "减" + c.discountAmount

        return {
          ...c,
          ...style,
          expireLabel,
          expiring,
          amountLabel
        }
      })
      const expiringCount = couponRows.filter(item => item.expiring).length

      this.setData({
        coupons: couponRows,
        availableCount: availRes.total || 0,
        expiringCount,
        usedCount: usedRes.total || 0,
        expiredCount: 0,
        summaryText: (availRes.total || 0) + " 张可用" + (expiringCount > 0 ? " · " + expiringCount + " 张即将到期" : "")
      })
    } catch (_) {
      wx.showToast({ title: "加载失败，请重试", icon: "none" })
    } finally {
      this.setData({ loading: false })
    }
  },

  useCoupon(e: WechatMiniprogram.TouchEvent) {
    const id = Number(e.currentTarget.dataset.id)
    wx.showToast({ title: "券码 #" + id + " 查看功能即将上线", icon: "none" })
  },

  loadHistory() {
    wx.showToast({ title: "历史券功能即将上线", icon: "none" })
  },

  goBack() { wx.navigateBack() }
})
