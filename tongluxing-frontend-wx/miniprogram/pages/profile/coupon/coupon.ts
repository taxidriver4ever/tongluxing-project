import { CouponSummary, getUserCoupons } from "../../../api/coupon"

interface CouponView {
  id: string
  title: string
  amount: string
  threshold: string
  expiry: string
  status: string
}

const STATUS_MAP: Record<string, string> = {
  available: "AVAILABLE",
  used: "USED",
  expired: "EXPIRED"
}

function money(value: number | undefined): string {
  const amount = Number(value || 0)
  return Number.isInteger(amount) ? String(amount) : amount.toFixed(2)
}

function toView(item: CouponSummary): CouponView {
  const threshold = Number(item.thresholdAmount || 0)
  return {
    id: String(item.id),
    title: item.couponName || "优惠券",
    amount: money(item.discountAmount),
    threshold: threshold > 0 ? "满" + money(threshold) + "元可用" : "无门槛",
    expiry: (item.validEndAt || "").replace("T", " ").slice(0, 16),
    status: item.couponStatus
  }
}

Page({
  data: {
    active: "available",
    tabs: [
      { key: "available", label: "可使用" },
      { key: "used", label: "已使用" },
      { key: "expired", label: "已过期" }
    ],
    visible: [] as CouponView[],
    loading: false,
    error: ""
  },

  onShow() {
    void this.load()
  },

  async load() {
    this.setData({ loading: true, error: "" })
    try {
      const status = STATUS_MAP[this.data.active] || "AVAILABLE"
      const page = await getUserCoupons(status, undefined, 1, 50)
      this.setData({ visible: (page.records || []).map(toView) })
    } catch (error) {
      this.setData({
        visible: [],
        error: error instanceof Error ? error.message : "优惠券加载失败"
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  back() {
    wx.navigateBack()
  },

  change(event: WechatMiniprogram.TouchEvent) {
    const active = String(event.currentTarget.dataset.key || "available")
    this.setData({ active }, () => {
      void this.load()
    })
  },

  detail(event: WechatMiniprogram.TouchEvent) {
    const id = String(event.currentTarget.dataset.id || "")
    if (!id) return
    wx.navigateTo({ url: "/pages/profile/coupon-detail/coupon-detail?id=" + id })
  },

  useCoupon() {
    wx.showToast({ title: "请在支持优惠券的订单中选择使用", icon: "none" })
  }
})
