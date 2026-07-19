import { getOrder, Order } from "../../../api/order"

Component({
  data: {
    orderId: 0,
    order: null as Order | null,
    progressText: "订单已创建"
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
      const orderId = Number(options.orderId || 0)
      this.setData({ orderId })
      if (orderId) this.loadOrder(orderId)
    },
    async loadOrder(orderId: number) {
      try {
        const order = await getOrder(orderId)
        this.setData({
          order,
          progressText: "订单状态：" + (order.orderStatus || "-") + " · 支付状态：" + (order.paymentStatus || "-")
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      }
    },
    goCode() {
      wx.navigateTo({ url: "/pages/order/verification-code/verification-code?orderId=" + this.data.orderId })
    },
    goShare() {
      wx.showToast({ title: "已生成分享卡片", icon: "none" })
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})
