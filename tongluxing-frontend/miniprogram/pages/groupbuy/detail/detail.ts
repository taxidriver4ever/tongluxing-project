import { getGroupbuy, GroupbuyActivity } from "../../../api/groupbuy"

function emptyGroupbuy(): GroupbuyActivity {
  return {
    activityId: 0,
    merchantId: 0,
    productId: 0,
    initiatorUserId: 0,
    targetPeople: 0,
    currentPeople: 0,
    groupPrice: 0,
    activityStatus: "",
    startAt: "",
    expireAt: "",
    successAt: "",
    failedAt: "",
    participants: []
  }
}

Component({
  data: {
    loading: false,
    activityId: 0,
    activity: emptyGroupbuy(),
    title: "拼团商品",
    subtitle: "车队同行专享 · 支持到店核销",
    progressPercent: 0,
    progressText: "0/0 人"
  },
  lifetimes: {
    attached() {
      this.loadFromOptions()
    }
  },
  methods: {
    onLoad(options: Record<string, string>) {
      this.init(options)
    },
    loadFromOptions() {
      const pages = getCurrentPages()
      const current = pages.length ? pages[pages.length - 1] as any : null
      this.init(current && current.options ? current.options : {})
    },
    init(options: Record<string, string>) {
      const activityId = Number(options.activityId || 0)
      if (!activityId || this.data.activityId) return
      this.setData({ activityId })
      this.loadDetail(activityId)
    },
    async loadDetail(activityId: number) {
      this.setData({ loading: true })
      try {
        const activity = await getGroupbuy(activityId)
        const current = activity.currentPeople || 0
        const target = activity.targetPeople || 0
        this.setData({
          activity,
          title: "拼团商品 #" + activity.productId,
          subtitle: "商家 #" + activity.merchantId + " · " + (activity.activityStatus || "UNKNOWN"),
          progressPercent: target > 0 ? Math.min(100, Math.round((current / target) * 100)) : 0,
          progressText: current + "/" + target + " 人"
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      } finally {
        this.setData({ loading: false })
      }
    },
    goCheckout() {
      const activity = this.data.activity
      if (!activity.productId) {
        wx.showToast({ title: "缺少拼团商品信息", icon: "none" })
        return
      }
      wx.navigateTo({
        url: "/pages/order/checkout/checkout?productId=" + activity.productId + "&activityId=" + activity.activityId + "&quantity=1"
      })
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})
