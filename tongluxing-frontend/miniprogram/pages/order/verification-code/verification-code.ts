import { getOrder, Order } from "../../../api/order"

Component({
  data: {
    orderId: 0,
    order: null as Order | null,
    statusText: "请选择订单",
    codeText: "未生成"
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
          statusText: order.verificationStatus || "UNKNOWN",
          codeText: "订单 #" + order.orderId
        })
      } catch (error) {
        wx.showToast({ title: this.getErrorMessage(error), icon: "none" })
      }
    },
    getErrorMessage(error: unknown): string {
      return error instanceof Error ? error.message : "网络异常，请稍后重试"
    }
  }
})
