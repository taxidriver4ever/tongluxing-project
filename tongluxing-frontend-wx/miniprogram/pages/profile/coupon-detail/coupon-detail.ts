import { UserCouponDetail, getUserCouponDetail } from "../../../api/coupon"

interface CouponDetailView {
  id: string
  title: string
  amount: string
  threshold: string
  expiry: string
  status: string
  code: string
}

function money(value: number | undefined): string {
  const amount = Number(value || 0)
  return Number.isInteger(amount) ? String(amount) : amount.toFixed(2)
}

function toView(item: UserCouponDetail): CouponDetailView {
  const threshold = Number(item.thresholdAmount || 0)
  return {
    id: String(item.id),
    title: item.couponName || "优惠券",
    amount: money(item.discountAmount),
    threshold: threshold > 0 ? "满" + money(threshold) + "元可用" : "无门槛",
    expiry: (item.validEndAt || "").replace("T", " ").slice(0, 16),
    status: item.couponStatus,
    code: "TLX" + String(item.id)
  }
}

Page({
  data: {
    coupon: null as CouponDetailView | null,
    loading: true,
    error: ""
  },

  onLoad(options: Record<string, string>) {
    const id = Number(options.id)
    if (!Number.isFinite(id) || id <= 0) {
      this.setData({ loading: false, error: "优惠券参数无效" })
      return
    }
    void this.load(id)
  },

  async load(id: number) {
    try {
      const detail = await getUserCouponDetail(id)
      this.setData({ coupon: toView(detail), error: "" })
    } catch (error) {
      this.setData({
        coupon: null,
        error: error instanceof Error ? error.message : "优惠券详情加载失败"
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  use() {
    wx.showToast({ title: "请在支持优惠券的订单中选择使用", icon: "none" })
  }
})
